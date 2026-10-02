package com.insideinvoice.backup.service;

import com.insideinvoice.backup.config.BackupProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int VERSION = 1;

    private final BackupProperties backupProperties;

    public void encryptFile(Path inputFile, Path outputFile) throws IOException {
        byte[] keyHex = parseHexKey(backupProperties.getEncryption().getKey());
        SecretKey secretKey = new SecretKeySpec(keyHex, "AES");

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] inputBytes = Files.readAllBytes(inputFile);
            byte[] cipherText = cipher.doFinal(inputBytes);

            byte[] versionBytes = ByteBuffer.allocate(4).putInt(VERSION).array();

            try (OutputStream os = Files.newOutputStream(outputFile)) {
                os.write(versionBytes);
                os.write(iv);
                os.write(cipherText);
            }

            log.info("Backup encryption completed, output size: {} bytes", Files.size(outputFile));
        } catch (Exception e) {
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        }
    }

    public void decryptFile(Path inputFile, Path outputFile) throws IOException {
        byte[] keyHex = parseHexKey(backupProperties.getEncryption().getKey());
        SecretKey secretKey = new SecretKeySpec(keyHex, "AES");

        try (InputStream is = Files.newInputStream(inputFile)) {
            byte[] versionBytes = is.readNBytes(4);
            int version = ByteBuffer.wrap(versionBytes).getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported encryption version: " + version);
            }

            byte[] iv = is.readNBytes(GCM_IV_LENGTH);
            if (iv.length != GCM_IV_LENGTH) {
                throw new IOException("Invalid IV length");
            }

            byte[] cipherText = is.readAllBytes();

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            Files.write(outputFile, plainText);

            log.info("Backup decryption completed, output size: {} bytes", plainText.length);
        } catch (Exception e) {
            throw new IOException("Decryption failed: " + e.getMessage(), e);
        }
    }

    private byte[] parseHexKey(String hexKey) {
        if (hexKey == null || hexKey.length() != 64) {
            throw new IllegalArgumentException("Encryption key must be 64 hexadecimal characters (32 bytes)");
        }
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) ((Character.digit(hexKey.charAt(i * 2), 16) << 4)
                    + Character.digit(hexKey.charAt(i * 2 + 1), 16));
        }
        return key;
    }
}
