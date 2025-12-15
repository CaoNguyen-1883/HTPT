package com.htpt.migration.controller;

import com.htpt.migration.dto.CodeDTO;
import com.htpt.migration.model.CodePackage;
import com.htpt.migration.service.MigrationService;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/code")
@Profile({ "coordinator", "demo" })
@RequiredArgsConstructor
public class CodeController {

    private final MigrationService migrationService;

    @PostMapping
    public ResponseEntity<CodePackage> uploadCode(@RequestBody CodeDTO dto) {
        CodePackage codePackage = migrationService.uploadCode(dto);
        return ResponseEntity.ok(codePackage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CodePackage> getCode(@PathVariable String id) {
        CodePackage codePackage = migrationService.getCodePackage(id);
        if (codePackage == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(codePackage);
    }

    @GetMapping
    public ResponseEntity<Collection<CodePackage>> getAllCode() {
        return ResponseEntity.ok(migrationService.getAllCodePackages());
    }
}
