package com.htpt.migration.service;

import com.htpt.migration.dto.CodeDTO;
import com.htpt.migration.dto.MigrationRequest;
import com.htpt.migration.model.CodePackage;
import com.htpt.migration.model.Migration;
import com.htpt.migration.model.Node;
import com.htpt.migration.repository.CodePackageRepository;
import com.htpt.migration.repository.MigrationRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile({ "coordinator", "demo" })
@Slf4j
@RequiredArgsConstructor
public class MigrationService {

    private final CoordinatorService coordinatorService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogBroadcastService logService;
    private final MigrationRepository migrationRepository;
    private final CodePackageRepository codePackageRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    // Lưu state được capture từ worker (để sử dụng trong Strong migration)
    // Keep in-memory for performance (state is temporary during migration)
    private final Map<String, CodePackage.CodeState> capturedStates =
        new ConcurrentHashMap<>();

    // Khởi tạo di trú
    public Migration initiateMigration(MigrationRequest request) {
        String migrationId = UUID.randomUUID().toString().substring(0, 8);

        // Nếu không chỉ định source, lấy từ code package
        String sourceNodeId = request.getSourceNodeId();
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            CodePackage code = codePackageRepository
                .findById(request.getCodeId())
                .orElse(null);
            if (code != null) {
                sourceNodeId = code.getCurrentNodeId();
            }
        }

        Migration migration = Migration.builder()
            .id(migrationId)
            .codeId(request.getCodeId())
            .sourceNodeId(sourceNodeId)
            .targetNodeId(request.getTargetNodeId())
            .type(
                request.getType() != null
                    ? request.getType()
                    : Migration.MigrationType.WEAK
            )
            .status(Migration.MigrationStatus.PENDING)
            .progress(0)
            .startTime(Instant.now())
            .build();

        migrationRepository.save(migration);

        // Broadcast migration created
        broadcastMigrationUpdate(migration);

        // Thực hiện di trú async
        executor.submit(() -> executeMigration(migration));

        log.info(
            "Migration initiated: {} from {} to {}",
            migrationId,
            sourceNodeId,
            request.getTargetNodeId()
        );
        return migration;
    }

    // Thực hiện di trú
    private void executeMigration(Migration migration) {
        try {
            migration.setStatus(Migration.MigrationStatus.IN_PROGRESS);
            broadcastMigrationUpdate(migration);

            // Log migration start
            logService.logMigrationStart(
                migration.getId(),
                migration.getSourceNodeId(),
                migration.getTargetNodeId(),
                migration.getType().name()
            );

            // Cập nhật status nodes
            coordinatorService.updateNodeStatus(
                migration.getSourceNodeId(),
                Node.NodeStatus.MIGRATING
            );
            coordinatorService.updateNodeStatus(
                migration.getTargetNodeId(),
                Node.NodeStatus.MIGRATING
            );

            // Step 1: Lấy code từ source node
            updateProgress(migration, 10, "Preparing migration");
            CodePackage codePackage = codePackageRepository
                .findById(migration.getCodeId())
                .orElse(null);

            if (codePackage == null) {
                throw new RuntimeException(
                    "Code package not found: " + migration.getCodeId()
                );
            }

            updateProgress(migration, 20, "Fetching code from source node");
            logService.info(
                migration.getSourceNodeId(),
                "FETCH",
                String.format(
                    "Fetching code \"%s\" (id: %s)",
                    codePackage.getName(),
                    codePackage.getId()
                )
            );

            messagingTemplate.convertAndSend(
                "/topic/node/" + migration.getSourceNodeId() + "/fetch",
                Map.of(
                    "codeId",
                    migration.getCodeId(),
                    "migrationId",
                    migration.getId()
                )
            );

            // Step 2: Nếu strong mobility, yêu cầu Worker capture state thực
            if (migration.getType() == Migration.MigrationType.STRONG) {
                updateProgress(
                    migration,
                    40,
                    "Capturing execution state from worker"
                );

                // Gửi lệnh capture state đến Worker source
                logService.info(
                    migration.getSourceNodeId(),
                    "STATE",
                    "Requesting state capture from worker..."
                );

                messagingTemplate.convertAndSend(
                    "/topic/node/" +
                        migration.getSourceNodeId() +
                        "/capture-state",
                    Map.of("codeId", migration.getCodeId())
                );

                // Chờ state từ Worker (tối đa 5 giây)
                CodePackage.CodeState capturedState = null;
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(500);
                    capturedState = capturedStates.get(migration.getCodeId());
                    if (capturedState != null) {
                        break;
                    }
                }

                if (capturedState != null) {
                    codePackage.setState(capturedState);
                    logService.logStateCheckpoint(
                        migration.getSourceNodeId(),
                        capturedState.getVariables()
                    );
                    // Xóa khỏi map sau khi sử dụng
                    capturedStates.remove(migration.getCodeId());
                } else {
                    logService.warning(
                        migration.getSourceNodeId(),
                        "STATE",
                        "No state received from worker, continuing with empty state"
                    );
                }
            } else {
                updateProgress(
                    migration,
                    40,
                    "Skipping state capture (weak migration)"
                );
                logService.info(
                    migration.getSourceNodeId(),
                    "STATE",
                    "No state capture needed (weak mobility)"
                );
            }

            // Step 3: Dừng execution trên source
            updateProgress(migration, 60, "Stopping execution on source node");
            logService.info(
                migration.getSourceNodeId(),
                "STOP",
                "Stopping execution"
            );

            messagingTemplate.convertAndSend(
                "/topic/node/" + migration.getSourceNodeId() + "/stop",
                Map.of("codeId", migration.getCodeId())
            );

            // Step 4: Transfer đến target node
            updateProgress(migration, 80, "Transferring code to target node");
            logService.logMigrationTransfer(migration.getId());

            codePackage.setCurrentNodeId(migration.getTargetNodeId());

            messagingTemplate.convertAndSend(
                "/topic/node/" + migration.getTargetNodeId() + "/receive",
                codePackage
            );

            logService.logCodeReceive(
                migration.getTargetNodeId(),
                migration.getType() == Migration.MigrationType.STRONG
            );

            // Step 5: Khởi động trên target
            updateProgress(migration, 95, "Starting execution on target node");
            logService.logExecutionStart(
                migration.getTargetNodeId(),
                codePackage.getId()
            );

            // Gửi lệnh execute đến Worker - Worker sẽ thực sự chạy code
            // và gửi kết quả về qua /app/node/execution-complete
            messagingTemplate.convertAndSend(
                "/topic/node/" + migration.getTargetNodeId() + "/execute",
                Map.of("codeId", codePackage.getId())
            );

            // Không fake result nữa - kết quả thực sẽ được Worker gửi về
            // và WebSocketHandler sẽ broadcast ra frontend
            logService.info(
                migration.getTargetNodeId(),
                "WAITING",
                "Waiting for execution result from worker..."
            );

            // Complete
            updateProgress(migration, 100, "Migration completed successfully");
            migration.setStatus(Migration.MigrationStatus.COMPLETED);
            migration.setEndTime(Instant.now());
            broadcastMigrationUpdate(migration);

            // Reset node status
            coordinatorService.updateNodeStatus(
                migration.getSourceNodeId(),
                Node.NodeStatus.ONLINE
            );
            coordinatorService.updateNodeStatus(
                migration.getTargetNodeId(),
                Node.NodeStatus.ONLINE
            );

            long duration =
                migration.getEndTime().toEpochMilli() -
                migration.getStartTime().toEpochMilli();
            logService.logMigrationComplete(migration.getId(), duration);
        } catch (Exception e) {
            migration.setStatus(Migration.MigrationStatus.FAILED);
            migration.setErrorMessage(e.getMessage());
            migration.setEndTime(Instant.now());
            broadcastMigrationUpdate(migration);

            // Reset node status
            coordinatorService.updateNodeStatus(
                migration.getSourceNodeId(),
                Node.NodeStatus.ONLINE
            );
            coordinatorService.updateNodeStatus(
                migration.getTargetNodeId(),
                Node.NodeStatus.ONLINE
            );

            logService.logMigrationFailed(migration.getId(), e.getMessage());
        }
    }

    private void updateProgress(
        Migration migration,
        int progress,
        String message
    ) {
        migration.setProgress(progress);
        migrationRepository.save(migration); // Save to database

        // Broadcast progress
        messagingTemplate.convertAndSend(
            "/topic/migration/" + migration.getId(),
            Map.of(
                "migrationId",
                migration.getId(),
                "progress",
                progress,
                "message",
                message,
                "timestamp",
                System.currentTimeMillis()
            )
        );

        // Also broadcast full migration update
        broadcastMigrationUpdate(migration);

        // Simulate network delay for demo visualization
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void broadcastMigrationUpdate(Migration migration) {
        messagingTemplate.convertAndSend("/topic/migrations", migration);
    }

    // Upload code
    public CodePackage uploadCode(CodeDTO dto) {
        String codeId = UUID.randomUUID().toString().substring(0, 8);

        CodePackage codePackage = CodePackage.builder()
            .id(codeId)
            .name(dto.getName())
            .code(dto.getCode())
            .entryPoint(dto.getEntryPoint())
            .currentNodeId(dto.getInitialNodeId())
            .metadata(
                Map.of(
                    "createdAt",
                    System.currentTimeMillis(),
                    "version",
                    "1.0"
                )
            )
            .build();

        codePackageRepository.save(codePackage);

        // Notify the target node
        if (dto.getInitialNodeId() != null) {
            messagingTemplate.convertAndSend(
                "/topic/node/" + dto.getInitialNodeId() + "/code-uploaded",
                codePackage
            );
        }

        // Log code upload
        logService.logCodeUpload(dto.getInitialNodeId(), codeId, dto.getName());

        return codePackage;
    }

    public Migration getMigration(String id) {
        return migrationRepository.findById(id).orElse(null);
    }

    public Collection<Migration> getAllMigrations() {
        return migrationRepository.findAll();
    }

    public CodePackage getCodePackage(String id) {
        return codePackageRepository.findById(id).orElse(null);
    }

    public Collection<CodePackage> getAllCodePackages() {
        return codePackageRepository.findAll();
    }

    /**
     * Lưu state được capture từ Worker (gọi từ WebSocketHandler)
     */
    public void saveCapturedState(String codeId, CodePackage.CodeState state) {
        capturedStates.put(codeId, state);
        log.info(
            "State saved for code {}: {} variables",
            codeId,
            state.getVariables() != null ? state.getVariables().size() : 0
        );
    }
}
