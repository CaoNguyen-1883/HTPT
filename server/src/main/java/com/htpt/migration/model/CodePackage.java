package com.htpt.migration.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "code_packages")
public class CodePackage {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private String entryPoint;

    @Column(nullable = true)
    private String currentNodeId;

    @Transient
    private CodeState state;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String stateJson;

    @Transient
    private Map<String, Object> metadata;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String metadataJson;

    // Convert state to JSON before saving
    @PrePersist
    @PreUpdate
    private void beforeSave() {
        if (state != null) {
            try {
                stateJson = objectMapper.writeValueAsString(state);
            } catch (JsonProcessingException e) {
                stateJson = null;
            }
        }
        if (metadata != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                metadataJson = null;
            }
        }
    }

    // Convert JSON back to objects after loading
    @PostLoad
    private void afterLoad() {
        if (stateJson != null) {
            try {
                state = objectMapper.readValue(stateJson, CodeState.class);
            } catch (JsonProcessingException e) {
                state = null;
            }
        }
        if (metadataJson != null) {
            try {
                metadata = objectMapper.readValue(
                    metadataJson,
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (JsonProcessingException e) {
                metadata = null;
            }
        }
    }

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
