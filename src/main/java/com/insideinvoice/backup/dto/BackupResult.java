package com.insideinvoice.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupResult {

    private boolean success;
    private String filename;
    private Long fileSizeBytes;
    private String driveFileId;
    private String errorMessage;
    private long durationMs;
    private LocalDateTime completedAt;

    public static BackupResult success(String filename, Long fileSizeBytes, String driveFileId, long durationMs) {
        return BackupResult.builder()
                .success(true)
                .filename(filename)
                .fileSizeBytes(fileSizeBytes)
                .driveFileId(driveFileId)
                .durationMs(durationMs)
                .completedAt(LocalDateTime.now())
                .build();
    }

    public static BackupResult failure(String errorMessage, long durationMs) {
        return BackupResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .completedAt(LocalDateTime.now())
                .build();
    }
}
