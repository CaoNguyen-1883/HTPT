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
public class Migration {
    private String id;
    private String codeId;
    private String sourceNodeId;
    private String targetNodeId;
    private MigrationType type;
    private MigrationStatus status;
    private int progress;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;

    public enum MigrationType {
        WEAK,   // Chỉ di chuyển code
        STRONG  // Di chuyển code + state
    }

    public enum MigrationStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }
}
