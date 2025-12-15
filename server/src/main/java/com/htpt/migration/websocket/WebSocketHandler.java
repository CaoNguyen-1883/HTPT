package com.htpt.migration.websocket;

import com.htpt.migration.model.CodePackage;
import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import com.htpt.migration.service.CoordinatorService;
import com.htpt.migration.service.LogBroadcastService;
import com.htpt.migration.service.MigrationService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@Profile({ "coordinator", "demo" })
@Slf4j
@RequiredArgsConstructor
public class WebSocketHandler {

    private final CoordinatorService coordinatorService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogBroadcastService logService;
    private final MigrationService migrationService;

    // Node đăng ký
    @MessageMapping("/node/register")
    public void registerNode(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("id");
        String host = (String) payload.get("host");
        int port = payload.get("port") instanceof Integer
            ? (Integer) payload.get("port")
            : Integer.parseInt(payload.get("port").toString());

        Node node = Node.builder()
            .id(nodeId)
            .host(host)
            .port(port)
            .role(Node.NodeRole.WORKER)
            .status(Node.NodeStatus.ONLINE)
            .connectedAt(Instant.now())
            .metrics(
                NodeMetrics.builder()
                    .cpuUsage(Math.random() * 30) // Random initial value
                    .memoryUsage(Math.random() * 40)
                    .activeProcesses(0)
                    .uptime(0)
                    .build()
            )
            .build();

        coordinatorService.registerNode(node);

        // Send acknowledgment
        messagingTemplate.convertAndSend(
            "/topic/node/" + nodeId + "/registered",
            Map.of(
                "status",
                "ok",
                "nodeId",
                nodeId,
                "timestamp",
                System.currentTimeMillis()
            )
        );

        log.info("Node {} registered via WebSocket", nodeId);
    }

    // Node ngắt kết nối
    @MessageMapping("/node/unregister")
    public void unregisterNode(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");
        coordinatorService.unregisterNode(nodeId);
        log.info("Node {} unregistered", nodeId);
    }

    // Cập nhật metrics
    @MessageMapping("/node/metrics")
    public void updateMetrics(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");

        NodeMetrics metrics = NodeMetrics.builder()
            .cpuUsage(((Number) payload.get("cpu")).doubleValue())
            .memoryUsage(((Number) payload.get("memory")).doubleValue())
            .activeProcesses(((Number) payload.get("processes")).intValue())
            .uptime(((Number) payload.getOrDefault("uptime", 0)).longValue())
            .build();

        coordinatorService.updateMetrics(nodeId, metrics);
        log.debug(
            "Metrics updated for node {}: CPU={}%, MEM={}%",
            nodeId,
            metrics.getCpuUsage(),
            metrics.getMemoryUsage()
        );
    }

    // Heartbeat
    @MessageMapping("/node/heartbeat")
    public void heartbeat(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");

        Node node = coordinatorService.getNode(nodeId);
        if (node != null) {
            node.setStatus(Node.NodeStatus.ONLINE);
        }

        // Send pong
        messagingTemplate.convertAndSend(
            "/topic/node/" + nodeId + "/pong",
            Map.of("timestamp", System.currentTimeMillis())
        );
    }

    // Code execution completed - nhận kết quả thực từ Worker
    @MessageMapping("/node/execution-complete")
    public void executionComplete(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");
        String codeId = (String) payload.get("codeId");
        String result = (String) payload.get("result");
        String error = (String) payload.get("error");
        String consoleOutput = (String) payload.get("consoleOutput");
        String status = (String) payload.get("status");

        log.info("=== REAL EXECUTION RESULT FROM {} ===", nodeId);
        log.info("  Code ID: {}", codeId);
        log.info("  Status: {}", status);
        log.info("  Result: {}", result);
        if (consoleOutput != null && !consoleOutput.isEmpty()) {
            log.info("  Console Output:\n{}", consoleOutput);
        }
        if (error != null && !error.isEmpty()) {
            log.error("  Error: {}", error);
        }
        log.info("=====================================");

        // Log console output to frontend
        if (consoleOutput != null && !consoleOutput.isEmpty()) {
            // Split by newlines and log each line
            String[] lines = consoleOutput.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    logService.info(nodeId, "CONSOLE", line.trim());
                }
            }
        }

        // Log execution result
        if ("error".equals(status) && error != null && !error.isEmpty()) {
            logService.error(nodeId, "EXEC_ERROR", error);
        } else {
            logService.success(nodeId, "EXEC_RESULT", "Return: " + result);
        }

        // Broadcast to clients
        messagingTemplate.convertAndSend(
            "/topic/execution/" + codeId,
            Map.of(
                "nodeId",
                nodeId,
                "codeId",
                codeId,
                "result",
                result != null ? result : "",
                "consoleOutput",
                consoleOutput != null ? consoleOutput : "",
                "error",
                error != null ? error : "",
                "status",
                status,
                "timestamp",
                System.currentTimeMillis()
            )
        );
    }

    // Migration acknowledgment from node
    @MessageMapping("/node/migration-ack")
    public void migrationAck(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");
        String migrationId = (String) payload.get("migrationId");
        String status = (String) payload.get("status");

        log.info(
            "Migration {} acknowledged by node {}: {}",
            migrationId,
            nodeId,
            status
        );
    }

    // State captured from worker - nhận state thực từ Worker source
    @MessageMapping("/node/state-captured")
    @SuppressWarnings("unchecked")
    public void stateCaptured(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");
        String codeId = (String) payload.get("codeId");
        Map<String, Object> variables = (Map<String, Object>) payload.get(
            "variables"
        );
        int executionPoint = ((Number) payload.getOrDefault(
                "executionPoint",
                0
            )).intValue();
        String output = (String) payload.get("output");

        log.info("=== REAL STATE CAPTURED FROM {} ===", nodeId);
        log.info("  Code ID: {}", codeId);
        log.info("  Variables: {}", variables);
        log.info("  Execution Point: {}", executionPoint);
        log.info("  Output: {}", output);
        log.info("===================================");

        // Log to frontend
        logService.success(
            nodeId,
            "STATE_CAPTURED",
            String.format(
                "Captured %d variables",
                variables != null ? variables.size() : 0
            )
        );

        // Lưu state vào MigrationService để sử dụng trong Strong migration
        CodePackage.CodeState state = CodePackage.CodeState.builder()
            .variables(variables)
            .executionPoint(executionPoint)
            .output(output)
            .build();
        migrationService.saveCapturedState(codeId, state);

        // Broadcast để frontend biết
        messagingTemplate.convertAndSend(
            "/topic/state/" + codeId,
            Map.of(
                "nodeId",
                nodeId,
                "codeId",
                codeId,
                "variables",
                variables != null ? variables : Map.of(),
                "executionPoint",
                executionPoint,
                "output",
                output != null ? output : "",
                "timestamp",
                System.currentTimeMillis()
            )
        );
    }
}
