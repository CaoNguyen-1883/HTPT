package com.htpt.migration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    private String id;
    private String host;
    private int port;
    private NodeRole role;
    private NodeStatus status;
    private NodeMetrics metrics;
    private Instant connectedAt;

    public enum NodeRole {
        COORDINATOR, WORKER
    }

    public enum NodeStatus {
        ONLINE, OFFLINE, BUSY, MIGRATING
    }
}
