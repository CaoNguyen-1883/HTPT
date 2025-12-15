package com.htpt.migration.repository;

import com.htpt.migration.model.Migration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationRepository extends JpaRepository<Migration, String> {

    // Find migrations by code ID
    List<Migration> findByCodeId(String codeId);

    // Find migrations by source node
    List<Migration> findBySourceNodeId(String sourceNodeId);

    // Find migrations by target node
    List<Migration> findByTargetNodeId(String targetNodeId);

    // Find migrations by status
    List<Migration> findByStatus(Migration.MigrationStatus status);

    // Find migrations by type
    List<Migration> findByType(Migration.MigrationType type);

    // Get recent migrations (ordered by start time descending)
    List<Migration> findTop10ByOrderByStartTimeDesc();
}
