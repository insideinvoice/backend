package com.insideinvoice.backup.scheduler;

import com.insideinvoice.backup.config.BackupProperties;
import com.insideinvoice.backup.dto.BackupResult;
import com.insideinvoice.backup.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;

    private final BackupService backupService;
    private final BackupProperties backupProperties;
    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Kolkata")
    public void executeScheduledBackup() {
        if (!backupProperties.isEnabled()) {
            log.info("Backup is disabled, skipping scheduled backup");
            return;
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            log.warn("Backup already in progress, skipping this execution");
            return;
        }

        try {
            log.info("Starting scheduled database backup");

            BackupResult result = null;
            int attempt = 0;

            while (attempt < MAX_RETRIES) {
                attempt++;
                log.info("Backup attempt {}/{}", attempt, MAX_RETRIES);

                result = backupService.executeBackup();

                if (result.isSuccess()) {
                    log.info("Backup succeeded on attempt {}", attempt);
                    break;
                }

                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAY_MS * attempt;
                    log.warn("Backup failed on attempt {}, retrying in {}ms: {}",
                            attempt, delay, result.getErrorMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted");
                        break;
                    }
                }
            }

            if (result != null && result.isSuccess()) {
                log.info("Scheduled backup completed successfully - file: {}, size: {} bytes, duration: {}ms",
                        result.getFilename(), result.getFileSizeBytes(), result.getDurationMs());
                backupService.cleanupOldBackups();
            } else {
                log.error("Scheduled backup failed after {} attempts: {}",
                        MAX_RETRIES, result != null ? result.getErrorMessage() : "Unknown error");
            }

        } catch (Exception e) {
            log.error("Scheduled backup encountered unexpected error: {}", e.getMessage());
        } finally {
            backupInProgress.set(false);
        }
    }
}
