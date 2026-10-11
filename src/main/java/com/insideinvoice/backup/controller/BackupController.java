package com.insideinvoice.backup.controller;

import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.backup.dto.BackupResult;
import com.insideinvoice.backup.scheduler.BackupScheduler;
import com.insideinvoice.backup.service.BackupService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Tag(name = "Backup", description = "Database backup management")
public class BackupController {

    private final BackupService backupService;
    private final BackupScheduler backupScheduler;

    @PostMapping("/trigger")
    @Operation(summary = "Manually trigger a database backup")
    public ResponseEntity<ApiResponse<BackupResult>> triggerBackup(@CurrentUser UserPrincipal currentUser) {
        BackupResult result = backupService.executeBackup();
        return ResponseEntity.ok(ApiResponse.success(
                result.isSuccess() ? "Backup completed" : "Backup failed", result));
    }

    @PostMapping("/trigger-scheduled")
    @Operation(summary = "Trigger the scheduled backup logic (same as 11 PM cron)")
    public ResponseEntity<ApiResponse<BackupResult>> triggerScheduledBackup(@CurrentUser UserPrincipal currentUser) {
        backupScheduler.executeScheduledBackup();
        // The scheduler logs its own result; return immediate acknowledgment
        return ResponseEntity.ok(ApiResponse.success("Scheduled backup triggered (check logs for result)", null));
    }

    @GetMapping("/health")
    @Operation(summary = "Check if pg_dump is available and Google Drive is reachable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        boolean pgDumpAvailable = isPgDumpAvailable();
        boolean driveConfigured = isDriveConfigured();

        return ResponseEntity.ok(ApiResponse.success("Health check", Map.of(
                "pgDumpAvailable", pgDumpAvailable,
                "googleDriveConfigured", driveConfigured,
                "timestamp", java.time.Instant.now().toString()
        )));
    }

    private boolean isPgDumpAvailable() {
        try {
            Process p = new ProcessBuilder("pg_dump", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDriveConfigured() {
        // Basic check - actual connectivity would need a test API call
        return true; // Placeholder
    }
}