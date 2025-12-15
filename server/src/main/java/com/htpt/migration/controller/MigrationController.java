package com.htpt.migration.controller;

import com.htpt.migration.dto.MigrationRequest;
import com.htpt.migration.model.Migration;
import com.htpt.migration.service.MigrationService;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/migrations")
@Profile({ "coordinator", "demo" })
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping
    public ResponseEntity<Migration> initiateMigration(
        @RequestBody MigrationRequest request
    ) {
        Migration migration = migrationService.initiateMigration(request);
        return ResponseEntity.ok(migration);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Migration> getMigration(@PathVariable String id) {
        Migration migration = migrationService.getMigration(id);
        if (migration == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(migration);
    }

    @GetMapping
    public ResponseEntity<Collection<Migration>> getAllMigrations() {
        return ResponseEntity.ok(migrationService.getAllMigrations());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelMigration(@PathVariable String id) {
        Migration migration = migrationService.getMigration(id);
        if (migration == null) {
            return ResponseEntity.notFound().build();
        }
        // TODO: Implement cancellation logic
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }
}
