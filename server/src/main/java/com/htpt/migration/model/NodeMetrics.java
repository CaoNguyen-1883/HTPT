package com.htpt.migration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeMetrics {
    private double cpuUsage;
    private double memoryUsage;
    private int activeProcesses;
    private long uptime;

    public double getLoadScore() {
        return cpuUsage * 0.6 + memoryUsage * 0.4;
    }
}
