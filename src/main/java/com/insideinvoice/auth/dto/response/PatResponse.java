package com.insideinvoice.auth.dto.response;

import com.insideinvoice.auth.entity.PersonalAccessToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatResponse {
    private Long id;
    private String name;
    private String tokenPrefix;
    private String scopes;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private boolean revoked;
    /** Exact API-call count for this token (V52) — null-safe for legacy rows. */
    private Long hitCount;
    /** Client address of the most recent call. */
    private String lastUsedIp;
    /** User-Agent of the most recent call. */
    private String lastUsedAgent;

    public static PatResponse from(PersonalAccessToken t) {
        return PatResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .tokenPrefix(t.getTokenPrefix() + "…")
                .scopes(t.getScopes())
                .expiresAt(t.getExpiresAt())
                .lastUsedAt(t.getLastUsedAt())
                .revokedAt(t.getRevokedAt())
                .createdAt(t.getCreatedAt())
                .revoked(t.getRevokedAt() != null)
                .hitCount(t.getHitCount() == null ? 0L : t.getHitCount())
                .lastUsedIp(t.getLastUsedIp())
                .lastUsedAgent(t.getLastUsedAgent())
                .build();
    }
}
