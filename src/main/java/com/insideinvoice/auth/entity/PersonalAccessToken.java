package com.insideinvoice.auth.entity;

import com.insideinvoice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "personal_access_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccessToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "scopes", nullable = false, length = 255)
    private String scopes;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * Exact number of API calls authenticated with this token — incremented on
     * every request (V52), unlike the throttled lastUsedAt of the original
     * V50 design. Null-safe everywhere it is read (legacy rows predate V52).
     */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private Long hitCount = 0L;

    /** Client address of the most recent call (first X-Forwarded-For hop). */
    @Column(name = "last_used_ip", length = 64)
    private String lastUsedIp;

    /** User-Agent of the most recent call — the "client" behind the token. */
    @Column(name = "last_used_agent", length = 255)
    private String lastUsedAgent;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
