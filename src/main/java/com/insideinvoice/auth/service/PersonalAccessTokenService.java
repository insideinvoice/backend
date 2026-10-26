package com.insideinvoice.auth.service;

import com.insideinvoice.auth.entity.PersonalAccessToken;
import java.util.List;

public interface PersonalAccessTokenService {
    /** Creates a PAT, returns the raw token (shown once) plus the persisted entity. */
    CreateResult create(Long userId, Long businessId, String name, String scopes, java.time.LocalDateTime expiresAt);

    List<PersonalAccessToken> listForUser(Long userId);

    void revoke(Long id, Long userId, Long businessId);

    /**
     * Returns the PAT matching the raw token, or empty if invalid/expired/revoked.
     * A successful resolve RECORDS the call: hit count +1 and the calling
     * client's IP / User-Agent (V52), so the PAT screen can show usage.
     */
    java.util.Optional<PersonalAccessToken> resolve(String rawToken, String clientIp, String userAgent);

    record CreateResult(String rawToken, PersonalAccessToken entity) {}
}
