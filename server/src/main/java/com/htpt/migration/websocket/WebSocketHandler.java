package com.htpt.migration.websocket;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import com.htpt.migration.service.CoordinatorService;
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

    // Code execution completed
    @MessageMapping("/node/execution-complete")
    public void executionComplete(@Payload Map<String, Object> payload) {
        String nodeId = (String) payload.get("nodeId");
        String codeId = (String) payload.get("codeId");
        String result = (String) payload.get("result");

        log.info(
            "Execution completed on node {}: code={}, result={}",
            nodeId,
            codeId,
            result
        );

        // Broadcast to clients
        messagingTemplate.convertAndSend(
            "/topic/execution/" + codeId,
            Map.of(
                "nodeId",
                nodeId,
                "codeId",
                codeId,
                "result",
                result,
                "status",
                "completed",
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
}
