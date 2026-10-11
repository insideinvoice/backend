package com.insideinvoice.backup.controller;

import com.insideinvoice.auth.dto.request.LoginRequest;
import com.insideinvoice.auth.dto.response.ApiResponse;
import com.insideinvoice.auth.dto.response.LoginResponse;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.auth.service.AuthService;
import com.insideinvoice.backup.dto.BackupResult;
import com.insideinvoice.backup.scheduler.BackupScheduler;
import com.insideinvoice.backup.service.BackupService;
import com.insideinvoice.security.CurrentUser;
import com.insideinvoice.security.JwtTokenProvider;
import com.insideinvoice.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Tag(name = "Backup", description = "Database backup management")
public class BackupController {

    private final BackupService backupService;
    private final BackupScheduler backupScheduler;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

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
        return ResponseEntity.ok(ApiResponse.success("Scheduled backup triggered (check logs for result)", null));
    }

    @PostMapping("/login")
    @Operation(summary = "Login to get JWT token for backup download")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(userPrincipal.getId()).orElseThrow();
            String token = jwtTokenProvider.generateAccessToken(
                    user.getId(), user.getEmail(), user.getBusinessId(), user.getName());
            return ResponseEntity.ok(ApiResponse.success("Login successful", new LoginResponse(token, user.getName())));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid credentials"));
        }
    }

@GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Download raw SQL dump (restorable with pg_restore/psql)")
    public ResponseEntity<StreamingResponseBody> downloadBackup(
            @CurrentUser UserPrincipal currentUser,
            HttpServletResponse response) throws IOException {

        // Verify user has permission (admin only for raw dump download)
        if (!currentUser.isAdmin()) {
            response.setStatus(403);
            return ResponseEntity.status(403).body(out -> out.write("Forbidden: Admin only".getBytes()));
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String filename = "insideinvoice-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + ".sql";

        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("X-Backup-Timestamp", now.toInstant().toString());

        StreamingResponseBody stream = out -> {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("backup-", ".sql");
                backupService.createRawDump(tempFile);
                Files.copy(tempFile, out);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Backup interrupted", e);
            } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @GetMapping("/health")
    @Operation(summary = "Check if pg_dump is available and Google Drive is reachable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        boolean pgDumpAvailable = isPgDumpAvailable();
        return ResponseEntity.ok(ApiResponse.success("Health check", Map.of(
                "pgDumpAvailable", pgDumpAvailable,
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
}