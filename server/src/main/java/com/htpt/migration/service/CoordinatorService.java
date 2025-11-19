package com.htpt.migration.service;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoordinatorService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    // Đăng ký node mới
    public void registerNode(Node node) {
        nodes.put(node.getId(), node);
        log.info("Node registered: {} at {}:{}", node.getId(), node.getHost(), node.getPort());

        // Broadcast đến tất cả clients
        broadcastTopology();
    }

    // Hủy đăng ký node
    public void unregisterNode(String nodeId) {
        nodes.remove(nodeId);
        log.info("Node unregistered: {}", nodeId);

        broadcastTopology();
    }

    // Cập nhật metrics của node
    public void updateMetrics(String nodeId, NodeMetrics metrics) {
        Node node = nodes.get(nodeId);
        if (node != null) {
            node.setMetrics(metrics);
            messagingTemplate.convertAndSend("/topic/metrics/" + nodeId, metrics);
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
            "nodes", new ArrayList<>(nodes.values()),
            "timestamp", System.currentTimeMillis()
        );
    }

    // Tìm node tốt nhất cho di trú (load balancing)
    public Node findBestTargetNode(String excludeNodeId) {
        return nodes.values().stream()
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
