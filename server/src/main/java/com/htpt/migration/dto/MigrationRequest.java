package com.htpt.migration.dto;

import com.htpt.migration.model.Migration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MigrationRequest {
    private String codeId;
    private String sourceNodeId;
    private String targetNodeId;
    private Migration.MigrationType type;
}
