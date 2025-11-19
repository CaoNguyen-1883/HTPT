package com.htpt.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeDTO {
    private String name;
    private String code;
    private String entryPoint;
    private String initialNodeId;
}
