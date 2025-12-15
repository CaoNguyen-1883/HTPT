package com.htpt.migration.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "migrations")
public class Migration {

    @Id
    private String id;

    @Column(nullable = false)
    private String codeId;

    @Column(nullable = false)
    private String sourceNodeId;

    @Column(nullable = false)
    private String targetNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MigrationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MigrationStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    @Column(length = 1000)
    private String errorMessage;

    public enum MigrationType {
        WEAK, // Chỉ di chuyển code
        STRONG, // Di chuyển code + state
    }

    public enum MigrationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
    }
}
