package com.htpt.migration.service;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Coordinator Service
 *
 * Chỉ active khi profile = "coordinator" hoặc "demo"
 * Quản lý tất cả worker nodes trong cluster.
 */
@Service
@Profile("coordinator")
@Slf4j
@RequiredArgsConstructor
public class CoordinatorService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            log.info("===========================================");
            log.info("  COORDINATOR SERVICE STARTED");
            log.info("  IP Address: {}", hostAddress);
            log.info("  Waiting for worker connections...");
            log.info("===========================================");
        } catch (Exception e) {
            log.info("===========================================");
            log.info("  COORDINATOR SERVICE STARTED");
            log.info("  Waiting for worker connections...");
            log.info("===========================================");
        }
    }

    // Đăng ký node mới
    public void registerNode(Node node) {
        node.setConnectedAt(Instant.now());
        node.setStatus(Node.NodeStatus.ONLINE);
        node.setRole(Node.NodeRole.WORKER);

        nodes.put(node.getId(), node);
        log.info(
            ">>> Node REGISTERED: {} at {}:{}",
            node.getId(),
            node.getHost(),
            node.getPort()
        );

        // Broadcast đến tất cả clients
        broadcastTopology();
    }

    // Hủy đăng ký node
    public void unregisterNode(String nodeId) {
        Node removed = nodes.remove(nodeId);
        if (removed != null) {
            log.info(
                "<<< Node UNREGISTERED: {} (was at {}:{})",
                nodeId,
                removed.getHost(),
                removed.getPort()
            );
            broadcastTopology();
        }
    }

    // Cập nhật metrics của node
    public void updateMetrics(String nodeId, NodeMetrics metrics) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.setMetrics(metrics);
            messagingTemplate.convertAndSend(
                "/topic/metrics/" + nodeId,
                metrics
            );
        }
    }

    // Cập nhật status của node
    public void updateNodeStatus(String nodeId, Node.NodeStatus status) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.setStatus(status);
            broadcastTopology();
        }
    }

    // Broadcast topology đến clients
    private void broadcastTopology() {
        messagingTemplate.convertAndSend("/topic/nodes", getTopology());
    }

    // Lấy topology
    public Map<String, Object> getTopology() {
        return Map.of(
            "nodes",
            new ArrayList<>(nodes.values()),
            "timestamp",
            System.currentTimeMillis()
        );
    }

    // Tìm node tốt nhất cho di trú (load balancing)
    public Node findBestTargetNode(String excludeNodeId) {
        return nodes
            .values()
            .stream()
            .filter(n -> !n.getId().equals(excludeNodeId))
            .filter(n -> n.getStatus() == Node.NodeStatus.ONLINE)
            .filter(n -> n.getMetrics() != null)
            .min(Comparator.comparingDouble(n -> n.getMetrics().getLoadScore()))
            .orElse(null);
    }

    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<Node> getAllNodes() {
        return nodes.values();
    }

    public boolean nodeExists(String nodeId) {
        return nodes.containsKey(nodeId);
    }
}
