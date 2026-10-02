package com.insideinvoice.backup.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "backup")
public class BackupProperties {

    private boolean enabled = false;
    private int retentionDays = 30;
    private long timeoutMinutes = 30;

    private GoogleDrive googleDrive = new GoogleDrive();
    private Encryption encryption = new Encryption();

    @Getter
    @Setter
    public static class GoogleDrive {
        private String rootFolderId;
        private String clientId;
        private String clientSecret;
        private String refreshToken;
    }

    @Getter
    @Setter
    public static class Encryption {
        private String key;
    }
}
