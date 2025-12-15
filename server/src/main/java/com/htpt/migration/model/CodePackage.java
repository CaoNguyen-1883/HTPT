package com.htpt.migration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodePackage {
    private String id;
    private String name;
    private String code;
    private String entryPoint;
    private String currentNodeId;
    private CodeState state;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeState {
        private Map<String, Object> variables;
        private int executionPoint;
        private String output;
    }
    }
