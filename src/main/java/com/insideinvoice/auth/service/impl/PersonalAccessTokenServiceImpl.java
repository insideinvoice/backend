package com.insideinvoice.auth.service.impl;

import com.insideinvoice.auth.entity.PersonalAccessToken;
import com.insideinvoice.auth.repository.PersonalAccessTokenRepository;
import com.insideinvoice.auth.service.PersonalAccessTokenService;
import com.insideinvoice.auth.service.PersonalAccessTokenService.CreateResult;
import com.insideinvoice.exception.BadRequestException;
import com.insideinvoice.exception.BusinessAccessViolationException;
import com.insideinvoice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonalAccessTokenServiceImpl implements PersonalAccessTokenService {

    private static final String PREFIX = "pat_";
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final PersonalAccessTokenRepository repository;

    @Override
    @Transactional
    public CreateResult create(Long userId, Long businessId, String name, String scopes, LocalDateTime expiresAt) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Token name is required");
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Expiry must be in the future");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String hash = sha256Hex(raw);
        String prefix = raw.substring(0, Math.min(12, raw.length()));

        PersonalAccessToken entity = PersonalAccessToken.builder()
                .userId(userId)
                .businessId(businessId)
                .name(name)
                .tokenHash(hash)
                .tokenPrefix(prefix)
                .scopes((scopes == null || scopes.isBlank()) ? "invoices:read,invoices:write" : scopes.trim())
                .expiresAt(expiresAt)
                .build();
        repository.save(entity);
        return new CreateResult(raw, entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalAccessToken> listForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public void revoke(Long id, Long userId, Long businessId) {
        PersonalAccessToken token = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PersonalAccessToken", "id", id));
        if (!token.getUserId().equals(userId) || !token.getBusinessId().equals(businessId)) {
            throw new BusinessAccessViolationException("You can only revoke your own tokens");
        }
        if (token.getRevokedAt() != null) {
            return; // already revoked — idempotent
        }
        token.setRevokedAt(LocalDateTime.now());
        repository.save(token);
    }

    @Override
    @Transactional
    public Optional<PersonalAccessToken> resolve(String rawToken, String clientIp, String userAgent) {
        if (rawToken == null || !rawToken.startsWith(PREFIX)) return Optional.empty();
        String hash = sha256Hex(rawToken);
        Optional<PersonalAccessToken> found = repository.findByTokenHash(hash);
        if (found.isEmpty()) return Optional.empty();
        PersonalAccessToken t = found.get();
        if (t.getRevokedAt() != null) return Optional.empty();
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now())) return Optional.empty();

        // Every authenticated call is one recorded hit. The V50 code throttled
        // this write to once a minute to spare the DB, but the PAT screen now
        // shows an exact call count, so each request writes once — one small
        // single-row UPDATE per API call, in the same transaction.
        LocalDateTime now = LocalDateTime.now();
        t.setHitCount((t.getHitCount() == null ? 0L : t.getHitCount()) + 1);
        t.setLastUsedAt(now);
        t.setLastUsedIp(truncate(clientIp, 64));
        t.setLastUsedAgent(truncate(userAgent, 255));
        repository.save(t);
        return Optional.of(t);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(trimmed.length(), max));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
