package com.htpt.migration.service;

import com.htpt.migration.dto.CodeDTO;
import com.htpt.migration.dto.MigrationRequest;
import com.htpt.migration.model.CodePackage;
import com.htpt.migration.model.Migration;
import com.htpt.migration.model.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationService {

    private final CoordinatorService coordinatorService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogBroadcastService logService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    private final Map<String, Migration> migrations = new ConcurrentHashMap<>();
    private final Map<String, CodePackage> codePackages = new ConcurrentHashMap<>();

    // Khởi tạo di trú
    public Migration initiateMigration(MigrationRequest request) {
        String migrationId = UUID.randomUUID().toString().substring(0, 8);

        // Nếu không chỉ định source, lấy từ code package
        String sourceNodeId = request.getSourceNodeId();
        if (sourceNodeId == null || sourceNodeId.isEmpty()) {
            CodePackage code = codePackages.get(request.getCodeId());
            if (code != null) {
                sourceNodeId = code.getCurrentNodeId();
            }
        }

        Migration migration = Migration.builder()
            .id(migrationId)
            .codeId(request.getCodeId())
            .sourceNodeId(sourceNodeId)
            .targetNodeId(request.getTargetNodeId())
            .type(request.getType() != null ? request.getType() : Migration.MigrationType.WEAK)
            .status(Migration.MigrationStatus.PENDING)
            .progress(0)
            .startTime(Instant.now())
            .build();

        migrations.put(migrationId, migration);

        // Broadcast migration created
        broadcastMigrationUpdate(migration);

        // Thực hiện di trú async
        executor.submit(() -> executeMigration(migration));

        log.info("Migration initiated: {} from {} to {}", migrationId, sourceNodeId, request.getTargetNodeId());
        return migration;
    }

    // Thực hiện di trú
    private void executeMigration(Migration migration) {
        try {
            migration.setStatus(Migration.MigrationStatus.IN_PROGRESS);
            broadcastMigrationUpdate(migration);

            // Log migration start
            logService.logMigrationStart(migration.getId(), migration.getSourceNodeId(),
                migration.getTargetNodeId(), migration.getType().name());

            // Cập nhật status nodes
            coordinatorService.updateNodeStatus(migration.getSourceNodeId(), Node.NodeStatus.MIGRATING);
            coordinatorService.updateNodeStatus(migration.getTargetNodeId(), Node.NodeStatus.MIGRATING);

            // Step 1: Lấy code từ source node
            updateProgress(migration, 10, "Preparing migration");
            CodePackage codePackage = codePackages.get(migration.getCodeId());

            if (codePackage == null) {
                throw new RuntimeException("Code package not found: " + migration.getCodeId());
            }

            updateProgress(migration, 20, "Fetching code from source node");
            logService.info(migration.getSourceNodeId(), "FETCH",
                String.format("Fetching code \"%s\" (id: %s)", codePackage.getName(), codePackage.getId()));

            messagingTemplate.convertAndSend("/topic/node/" + migration.getSourceNodeId() + "/fetch",
                Map.of("codeId", migration.getCodeId(), "migrationId", migration.getId()));

            // Step 2: Nếu strong mobility, lấy state
            if (migration.getType() == Migration.MigrationType.STRONG) {
                updateProgress(migration, 40, "Capturing execution state");

                // Capture state với data thực tế hơn
                Map<String, Object> stateVars = Map.of(
                    "counter", 10,
                    "data", Arrays.asList(
                        Map.of("node", migration.getSourceNodeId(), "value", 42.5),
                        Map.of("node", "previous", "value", 38.2)
                    ),
                    "timestamp", System.currentTimeMillis()
                );

                CodePackage.CodeState state = CodePackage.CodeState.builder()
                    .variables(stateVars)
                    .executionPoint(15)
                    .output("Captured at " + Instant.now())
                    .build();
                codePackage.setState(state);

                logService.logStateCheckpoint(migration.getSourceNodeId(), stateVars);

                messagingTemplate.convertAndSend("/topic/node/" + migration.getSourceNodeId() + "/capture-state",
                    Map.of("codeId", migration.getCodeId()));
            } else {
                updateProgress(migration, 40, "Skipping state capture (weak migration)");
                logService.info(migration.getSourceNodeId(), "STATE", "No state capture needed (weak mobility)");
            }

            // Step 3: Dừng execution trên source
            updateProgress(migration, 60, "Stopping execution on source node");
            logService.info(migration.getSourceNodeId(), "STOP", "Stopping execution");

            messagingTemplate.convertAndSend("/topic/node/" + migration.getSourceNodeId() + "/stop",
                Map.of("codeId", migration.getCodeId()));

            // Step 4: Transfer đến target node
            updateProgress(migration, 80, "Transferring code to target node");
            logService.logMigrationTransfer(migration.getId());

            codePackage.setCurrentNodeId(migration.getTargetNodeId());

            messagingTemplate.convertAndSend("/topic/node/" + migration.getTargetNodeId() + "/receive",
                codePackage);

            logService.logCodeReceive(migration.getTargetNodeId(),
                migration.getType() == Migration.MigrationType.STRONG);

            // Step 5: Khởi động trên target
            updateProgress(migration, 95, "Starting execution on target node");
            logService.logExecutionStart(migration.getTargetNodeId(), codePackage.getId());

            messagingTemplate.convertAndSend("/topic/node/" + migration.getTargetNodeId() + "/execute",
                Map.of("codeId", codePackage.getId()));

            // Log execution result
            Object result = Map.of(
                "status", "success",
                "node", migration.getTargetNodeId(),
                "output", String.format("%s() executed successfully", codePackage.getEntryPoint())
            );
            logService.logExecutionOutput(migration.getTargetNodeId(), codePackage.getId(), result);

            // Complete
            updateProgress(migration, 100, "Migration completed successfully");
            migration.setStatus(Migration.MigrationStatus.COMPLETED);
            migration.setEndTime(Instant.now());
            broadcastMigrationUpdate(migration);

            // Reset node status
            coordinatorService.updateNodeStatus(migration.getSourceNodeId(), Node.NodeStatus.ONLINE);
            coordinatorService.updateNodeStatus(migration.getTargetNodeId(), Node.NodeStatus.ONLINE);

            long duration = migration.getEndTime().toEpochMilli() - migration.getStartTime().toEpochMilli();
            logService.logMigrationComplete(migration.getId(), duration);

        } catch (Exception e) {
            migration.setStatus(Migration.MigrationStatus.FAILED);
            migration.setErrorMessage(e.getMessage());
            migration.setEndTime(Instant.now());
            broadcastMigrationUpdate(migration);

            // Reset node status
            coordinatorService.updateNodeStatus(migration.getSourceNodeId(), Node.NodeStatus.ONLINE);
            coordinatorService.updateNodeStatus(migration.getTargetNodeId(), Node.NodeStatus.ONLINE);

            logService.logMigrationFailed(migration.getId(), e.getMessage());
        }
    }

    private void updateProgress(Migration migration, int progress, String message) {
        migration.setProgress(progress);

        // Broadcast progress
        messagingTemplate.convertAndSend("/topic/migration/" + migration.getId(),
            Map.of(
                "migrationId", migration.getId(),
                "progress", progress,
                "message", message,
                "timestamp", System.currentTimeMillis()
            ));

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
            .metadata(Map.of(
                "createdAt", System.currentTimeMillis(),
                "version", "1.0"
            ))
            .build();

        codePackages.put(codeId, codePackage);

        // Notify the target node
        if (dto.getInitialNodeId() != null) {
            messagingTemplate.convertAndSend("/topic/node/" + dto.getInitialNodeId() + "/code-uploaded",
                codePackage);
        }

        // Log code upload
        logService.logCodeUpload(dto.getInitialNodeId(), codeId, dto.getName());

        return codePackage;
    }

    public Migration getMigration(String id) {
        return migrations.get(id);
    }

    public Collection<Migration> getAllMigrations() {
        return migrations.values();
    }

    public CodePackage getCodePackage(String id) {
        return codePackages.get(id);
    }

    public Collection<CodePackage> getAllCodePackages() {
        return codePackages.values();
    }
}
