package com.htpt.migration.controller;

import com.htpt.migration.model.Node;
import com.htpt.migration.service.CoordinatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final CoordinatorService coordinatorService;

    @GetMapping
    public ResponseEntity<Collection<Node>> getAllNodes() {
        return ResponseEntity.ok(coordinatorService.getAllNodes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Node> getNode(@PathVariable String id) {
        Node node = coordinatorService.getNode(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(node);
    }

    @GetMapping("/topology")
    public ResponseEntity<Map<String, Object>> getTopology() {
        return ResponseEntity.ok(coordinatorService.getTopology());
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<?> getNodeMetrics(@PathVariable String id) {
        Node node = coordinatorService.getNode(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(node.getMetrics());
    }
}
