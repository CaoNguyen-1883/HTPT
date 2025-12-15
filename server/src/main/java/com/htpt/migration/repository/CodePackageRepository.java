package com.htpt.migration.repository;

import com.htpt.migration.model.CodePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodePackageRepository extends JpaRepository<CodePackage, String> {

    // Find code packages by name
    List<CodePackage> findByName(String name);

    // Find code packages by current node
    List<CodePackage> findByCurrentNodeId(String currentNodeId);

    // Get all code packages ordered by name
    List<CodePackage> findAllByOrderByNameAsc();
}
