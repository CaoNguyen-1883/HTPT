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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemoScenarioService {

    private final MigrationService migrationService;
    private final CoordinatorService coordinatorService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Demo 1: Mobile Agent
     * Agent di chuyển tuần tự qua tất cả 5 nodes để thu thập dữ liệu
     */
    public void runMobileAgentDemo() {
        executor.submit(() -> {
            try {
                log.info("Starting Mobile Agent Demo...");
                broadcastDemoEvent("demo:started", "Mobile Agent Demo started");

                // Upload Mobile Agent code
                String agentCode = """
                    def collectedData = []

                    def collect() {
                        def nodeData = [
                            node: nodeId,
                            timestamp: System.currentTimeMillis(),
                            cpuSample: Math.random() * 100,
                            memorySample: Math.random() * 100,
                            diskUsage: Math.random() * 500
                        ]
                        collectedData << nodeData

                        return [
                            status: "collected",
                            totalNodes: collectedData.size(),
                            data: collectedData
                        ]
                    }

                    collect()
                    """;

                CodePackage code = migrationService.uploadCode(new CodeDTO(
                    "MobileAgent",
                    agentCode,
                    "collect",
                    "node-1"
                ));

                broadcastDemoEvent("demo:progress", "Agent uploaded to node-1");

                // Di chuyển tuần tự qua các nodes: 1 -> 2 -> 3 -> 4 -> 5
                String[] nodeSequence = {"node-1", "node-2", "node-3", "node-4", "node-5"};

                for (int i = 0; i < nodeSequence.length - 1; i++) {
                    String source = nodeSequence[i];
                    String target = nodeSequence[i + 1];

                    broadcastDemoEvent("demo:progress",
                        String.format("Migrating agent: %s -> %s", source, target));

                    MigrationRequest request = new MigrationRequest();
                    request.setCodeId(code.getId());
                    request.setSourceNodeId(source);
                    request.setTargetNodeId(target);
                    request.setType(Migration.MigrationType.STRONG); // Mang theo data đã thu thập

                    Migration migration = migrationService.initiateMigration(request);

                    // Đợi migration hoàn thành
                    while (true) {
                        Migration current = migrationService.getMigration(migration.getId());
                        if (current.getStatus() == Migration.MigrationStatus.COMPLETED) {
                            broadcastDemoEvent("demo:progress",
                                String.format("Agent arrived at %s, collected data from %d nodes",
                                    target, i + 2));
                            break;
                        } else if (current.getStatus() == Migration.MigrationStatus.FAILED) {
                            throw new RuntimeException("Migration failed: " + current.getErrorMessage());
                        }
                        Thread.sleep(500);
                    }

                    // Delay giữa các migrations để dễ quan sát
                    Thread.sleep(1000);
                }

                broadcastDemoEvent("demo:completed",
                    "Mobile Agent completed! Visited all 5 nodes and collected data.");
                log.info("Mobile Agent Demo completed successfully");

            } catch (Exception e) {
                log.error("Mobile Agent Demo failed: {}", e.getMessage());
                broadcastDemoEvent("demo:error", "Demo failed: " + e.getMessage());
            }
        });
    }

    /**
     * Demo 2: Load Balancing
     * Tự động di trú task từ node quá tải sang node rảnh
     */
    public void runLoadBalancingDemo() {
        executor.submit(() -> {
            try {
                log.info("Starting Load Balancing Demo...");
                broadcastDemoEvent("demo:started", "Load Balancing Demo started");

                // Simulate node-2 bị quá tải
                String overloadedNode = "node-2";
                simulateHighLoad(overloadedNode, 85.0);
                broadcastDemoEvent("demo:progress",
                    String.format("%s is overloaded (CPU: 85%%)", overloadedNode));

                Thread.sleep(2000);

                // Upload heavy task
                String heavyTaskCode = """
                    def heavyComputation() {
                        def result = 0
                        for (int i = 0; i < 1000000; i++) {
                            result += Math.sqrt(i) * Math.sin(i)
                        }
                        return [
                            node: nodeId,
                            result: result,
                            message: "Heavy computation completed on " + nodeId
                        ]
                    }

                    heavyComputation()
                    """;

                CodePackage code = migrationService.uploadCode(new CodeDTO(
                    "HeavyTask",
                    heavyTaskCode,
                    "heavyComputation",
                    overloadedNode
                ));

                broadcastDemoEvent("demo:progress", "Heavy task running on overloaded node");
                Thread.sleep(2000);

                // Tìm node có load thấp nhất
                Node bestTarget = coordinatorService.findBestTargetNode(overloadedNode);
                if (bestTarget == null) {
                    throw new RuntimeException("No available target node found");
                }

                broadcastDemoEvent("demo:progress",
                    String.format("Found best target: %s (CPU: %.0f%%)",
                        bestTarget.getId(), bestTarget.getMetrics().getCpuUsage()));

                // Di trú task
                MigrationRequest request = new MigrationRequest();
                request.setCodeId(code.getId());
                request.setSourceNodeId(overloadedNode);
                request.setTargetNodeId(bestTarget.getId());
                request.setType(Migration.MigrationType.WEAK);

                Migration migration = migrationService.initiateMigration(request);

                // Đợi hoàn thành
                while (true) {
                    Migration current = migrationService.getMigration(migration.getId());
                    if (current.getStatus() == Migration.MigrationStatus.COMPLETED) {
                        break;
                    } else if (current.getStatus() == Migration.MigrationStatus.FAILED) {
                        throw new RuntimeException("Migration failed");
                    }
                    Thread.sleep(500);
                }

                // Giảm load của node ban đầu
                simulateHighLoad(overloadedNode, 35.0);
                broadcastDemoEvent("demo:progress",
                    String.format("%s load reduced to 35%%", overloadedNode));

                broadcastDemoEvent("demo:completed",
                    String.format("Load Balancing completed! Task moved from %s to %s",
                        overloadedNode, bestTarget.getId()));
                log.info("Load Balancing Demo completed successfully");

            } catch (Exception e) {
                log.error("Load Balancing Demo failed: {}", e.getMessage());
                broadcastDemoEvent("demo:error", "Demo failed: " + e.getMessage());
            }
        });
    }

    /**
     * Demo 3: Fault Tolerance
     * Di trú khi phát hiện node sắp fail, với checkpoint state
     */
    public void runFaultToleranceDemo() {
        executor.submit(() -> {
            try {
                log.info("Starting Fault Tolerance Demo...");
                broadcastDemoEvent("demo:started", "Fault Tolerance Demo started");

                String failingNode = "node-3";

                // Upload stateful task
                String statefulCode = """
                    // Stateful computation with checkpoint
                    def checkpoint = [
                        iteration: 0,
                        partialSum: 0,
                        processedItems: [],
                        startTime: System.currentTimeMillis()
                    ]

                    def process() {
                        // Continue from checkpoint
                        for (int i = checkpoint.iteration; i < 100; i++) {
                            checkpoint.iteration = i
                            checkpoint.partialSum += i * i
                            checkpoint.processedItems << "item_" + i

                            // Simulate work
                            Thread.sleep(10)
                        }

                        return [
                            node: nodeId,
                            finalSum: checkpoint.partialSum,
                            itemsProcessed: checkpoint.processedItems.size(),
                            duration: System.currentTimeMillis() - checkpoint.startTime
                        ]
                    }

                    process()
                    """;

                CodePackage code = migrationService.uploadCode(new CodeDTO(
                    "StatefulTask",
                    statefulCode,
                    "process",
                    failingNode
                ));

                broadcastDemoEvent("demo:progress",
                    String.format("Stateful task started on %s", failingNode));

                Thread.sleep(3000);

                // Simulate node failure warning
                broadcastDemoEvent("demo:warning",
                    String.format("WARNING: %s showing signs of failure!", failingNode));

                Thread.sleep(2000);

                // Checkpoint và di trú
                broadcastDemoEvent("demo:progress", "Creating checkpoint of current state...");
                Thread.sleep(1000);

                // Tìm node backup
                Node backupNode = coordinatorService.findBestTargetNode(failingNode);
                if (backupNode == null) {
                    throw new RuntimeException("No backup node available");
                }

                broadcastDemoEvent("demo:progress",
                    String.format("Migrating to backup node: %s (Strong mobility with state)",
                        backupNode.getId()));

                // Di trú với state (Strong Mobility)
                MigrationRequest request = new MigrationRequest();
                request.setCodeId(code.getId());
                request.setSourceNodeId(failingNode);
                request.setTargetNodeId(backupNode.getId());
                request.setType(Migration.MigrationType.STRONG);

                Migration migration = migrationService.initiateMigration(request);

                // Đợi hoàn thành
                while (true) {
                    Migration current = migrationService.getMigration(migration.getId());
                    if (current.getStatus() == Migration.MigrationStatus.COMPLETED) {
                        break;
                    } else if (current.getStatus() == Migration.MigrationStatus.FAILED) {
                        throw new RuntimeException("Migration failed");
                    }
                    Thread.sleep(500);
                }

                // Đánh dấu node cũ là offline
                coordinatorService.updateNodeStatus(failingNode, Node.NodeStatus.OFFLINE);
                broadcastDemoEvent("demo:progress",
                    String.format("%s marked as OFFLINE", failingNode));

                Thread.sleep(1000);

                broadcastDemoEvent("demo:completed",
                    String.format("Fault Tolerance completed! State preserved and task resumed on %s",
                        backupNode.getId()));

                // Restore node sau demo
                Thread.sleep(3000);
                coordinatorService.updateNodeStatus(failingNode, Node.NodeStatus.ONLINE);
                broadcastDemoEvent("demo:info", String.format("%s recovered and back online", failingNode));

                log.info("Fault Tolerance Demo completed successfully");

            } catch (Exception e) {
                log.error("Fault Tolerance Demo failed: {}", e.getMessage());
                broadcastDemoEvent("demo:error", "Demo failed: " + e.getMessage());
            }
        });
    }

    private void simulateHighLoad(String nodeId, double cpuUsage) {
        Node node = coordinatorService.getNode(nodeId);
        if (node != null && node.getMetrics() != null) {
            node.getMetrics().setCpuUsage(cpuUsage);
            coordinatorService.updateMetrics(nodeId, node.getMetrics());
        }
    }

    private void broadcastDemoEvent(String type, String message) {
        messagingTemplate.convertAndSend("/topic/demo", Map.of(
            "type", type,
            "message", message,
            "timestamp", System.currentTimeMillis()
        ));
    }
}
