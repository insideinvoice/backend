package com.insideinvoice.einvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * AES-GCM encryption for provider credentials. The ciphertext stored in
 * {@code einvoice_configs.credentials_encrypted} is
 * {@code base64(iv[12] || ciphertext+tag)}; the key comes from
 * {@code app.einvoice.credential-key} (base64 or raw) when set, otherwise it
 * is derived deterministically from the JWT secret so a deployment without
 * the extra property still keeps credentials encrypted at rest. Plaintext
 * credentials never leave this service and are never logged.
 */
@Service
public class EInvoiceCredentialService {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public EInvoiceCredentialService(
            @Value("${app.einvoice.credential-key:}") String configuredKey,
            @Value("${app.jwt.secret:}") String jwtSecret) {
        this.key = deriveKey(configuredKey, jwtSecret);
    }

    private static SecretKey deriveKey(String configuredKey, String jwtSecret) {
        try {
            String source = (configuredKey != null && !configuredKey.isBlank())
                    ? configuredKey : jwtSecret;
            byte[] material = source.getBytes(StandardCharsets.UTF_8);
            // Accept a base64 key directly; anything else is hashed to 32 bytes.
            try {
                byte[] decoded = Base64.getDecoder().decode(source.trim());
                if (decoded.length == 32) {
                    return new SecretKeySpec(decoded, "AES");
                }
            } catch (IllegalArgumentException ignored) {
                // not base64 — fall through to the SHA-256 path
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(material), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive e-invoice credential key", e);
        }
    }

    /** Normalises and encrypts a credentials map into the stored envelope. */
    public String encrypt(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) return null;
        try {
            // TreeMap: stable field order -> stable plaintext -> comparable hashes.
            byte[] plaintext = mapper.writeValueAsBytes(new TreeMap<>(credentials));
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Credential encryption failed", e);
        }
    }

    /** True when the stored envelope contains a non-blank value for the field. */
    public boolean hasField(String envelope, String field) {
        Object v = decrypt(envelope).get(field);
        return v != null && !String.valueOf(v).isBlank();
    }

    /** Decrypts the stored envelope back to a credentials map (never logged). */
    public Map<String, String> decrypt(String envelope) {
        if (envelope == null || envelope.isBlank()) return Map.of();
        try {
            byte[] all = Base64.getDecoder().decode(envelope);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plaintext = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return mapper.readValue(plaintext, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Credential decryption failed", e);
        }
    }
}
