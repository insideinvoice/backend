package com.insideinvoice.backup.service;

import com.google.api.services.drive.model.File;
import com.insideinvoice.backup.config.BackupProperties;
import com.insideinvoice.backup.dto.BackupResult;
import com.insideinvoice.backup.util.DatabaseConnectionParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
    private static final String BACKUP_FILENAME_PATTERN = "insideinvoice-";

    private final BackupProperties backupProperties;
    private final EncryptionService encryptionService;
    private final GoogleDriveService googleDriveService;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public BackupResult executeBackup() {
        Instant start = Instant.now();
        Path tempDir = null;
        Path dumpFile = null;
        Path encryptedFile = null;

        try {
            ZonedDateTime now = ZonedDateTime.now(IST);
            String monthFolder = now.format(MONTH_FORMAT);
            String dateFolder = now.format(DATE_FORMAT);
            String filename = "insideinvoice-" + now.format(FILE_FORMAT) + ".sql";

            log.info("Inside Invoice backup started at {}", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            tempDir = Files.createTempDirectory("insideinvoice-backup-");
            dumpFile = tempDir.resolve(filename);
            encryptedFile = tempDir.resolve(filename + ".enc");

            DatabaseConnectionParser.ParsedDatabase db = DatabaseConnectionParser.parse(jdbcUrl);
            runPgDump(db, dumpFile);

            if (!Files.exists(dumpFile) || Files.size(dumpFile) == 0) {
                throw new IOException("pg_dump produced empty or missing output file");
            }

            long dumpSize = Files.size(dumpFile);
            log.info("PostgreSQL dump completed, file size: {} bytes", dumpSize);

            encryptionService.encryptFile(dumpFile, encryptedFile);
            log.info("Backup encryption completed");

            String rootFolderId = backupProperties.getGoogleDrive().getRootFolderId();

            String monthFolderId = googleDriveService.findOrCreateFolder(
                    rootFolderId, monthFolder, "application/vnd.google-apps.folder");

            String dateFolderId = googleDriveService.findOrCreateFolder(
                    monthFolderId, dateFolder, "application/vnd.google-apps.folder");

            String encryptedFilename = filename + ".enc";

            if (googleDriveService.fileExists(dateFolderId, encryptedFilename)) {
                log.info("Backup file already exists for today, skipping upload: {}", encryptedFilename);
            } else {
                log.info("Upload started");
                String fileId = googleDriveService.uploadFile(
                        dateFolderId, encryptedFilename, encryptedFile, "application/octet-stream");
                log.info("Google Drive upload completed, file id: {}", fileId);
            }

            cleanupTempFiles(tempDir);

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Inside Invoice backup completed successfully in {}ms", durationMs);

            return BackupResult.success(encryptedFilename, dumpSize, null, durationMs);

        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Inside Invoice backup failed: {}", e.getMessage());
            cleanupTempFiles(tempDir);
            return BackupResult.failure(e.getMessage(), durationMs);
        }
    }

    public List<BackupResult> cleanupOldBackups() {
        List<BackupResult> results = new ArrayList<>();
        try {
            String rootFolderId = backupProperties.getGoogleDrive().getRootFolderId();
            int retentionDays = backupProperties.getRetentionDays();

            ZonedDateTime cutoff = ZonedDateTime.now(IST).minusDays(retentionDays);
            List<File> monthFolders = googleDriveService.listFiles(rootFolderId);

            for (File monthFolder : monthFolders) {
                if (!isMonthFolder(monthFolder.getName())) {
                    continue;
                }

                ZonedDateTime monthDate = parseMonthFolder(monthFolder.getName());
                if (monthDate == null || monthDate.plusMonths(1).isAfter(cutoff)) {
                    continue;
                }

                List<File> dateFolders = googleDriveService.listFiles(monthFolder.getId());
                for (File dateFolder : dateFolders) {
                    if (!isDateFolder(dateFolder.getName())) {
                        continue;
                    }

                    ZonedDateTime folderDate = parseDateFolder(dateFolder.getName());
                    if (folderDate == null || folderDate.isAfter(cutoff)) {
                        continue;
                    }

                    List<File> backupFiles = googleDriveService.listFiles(dateFolder.getId());
                    for (File backupFile : backupFiles) {
                        if (backupFile.getName().startsWith(BACKUP_FILENAME_PATTERN)) {
                            googleDriveService.deleteFile(backupFile.getId());
                            log.info("Deleted old backup: {}", backupFile.getName());
                        }
                    }

                    googleDriveService.deleteFile(dateFolder.getId());
                    log.info("Deleted old date folder: {}", dateFolder.getName());
                }

                googleDriveService.deleteFile(monthFolder.getId());
                log.info("Deleted old month folder: {}", monthFolder.getName());
            }

            log.info("Retention cleanup completed");
        } catch (Exception e) {
            log.error("Retention cleanup failed: {}", e.getMessage());
        }
        return results;
    }

    private void runPgDump(DatabaseConnectionParser.ParsedDatabase db, Path outputFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("--host");
        command.add(db.host());
        command.add("--port");
        command.add(String.valueOf(db.port()));
        command.add("--username");
        command.add(dbUsername);
        command.add("--no-privileges");
        command.add("--no-owner");

        if (jdbcUrl.contains("sslmode=")) {
            command.add("--sslmode=require");
        }

        command.add("--file");
        command.add(outputFile.toString());
        command.add(db.database());

        log.info("Executing pg_dump for database: {}", db.database());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        String output;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            output = reader.lines().reduce("", (a, b) -> a + "\n" + b);
        }

        boolean completed = process.waitFor(backupProperties.getTimeoutMinutes(), TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("pg_dump process timed out after " + backupProperties.getTimeoutMinutes() + " minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String safeError = output.replaceAll("(?i)password\\s*=\\s*\\S+", "password=***");
            throw new IOException("pg_dump failed with exit code " + exitCode + ": " + safeError);
        }
    }

    private void cleanupTempFiles(Path tempDir) {
        if (tempDir == null) return;
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path);
                            }
                        });
                log.info("Cleanup completed");
            }
        } catch (IOException e) {
            log.warn("Temp directory cleanup failed: {}", e.getMessage());
        }
    }

    private boolean isMonthFolder(String name) {
        return name != null && name.matches("\\d{4}-\\d{2}");
    }

    private boolean isDateFolder(String name) {
        return name != null && name.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private ZonedDateTime parseMonthFolder(String name) {
        try {
            return ZonedDateTime.parse(name + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(IST));
        } catch (Exception e) {
            return null;
        }
    }

    private ZonedDateTime parseDateFolder(String name) {
        try {
            return ZonedDateTime.parse(name, DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(IST));
        } catch (Exception e) {
            return null;
        }
    }
}
