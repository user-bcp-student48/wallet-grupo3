package com.bcp.wallet.identity;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@ApplicationScoped
public class PiiCipher {
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    @ConfigProperty(name = "identity.encryption-key.path", defaultValue = "data/identity.key")
    String keyPath;

    private byte[] key;

    @PostConstruct
    void initialize() {
        try {
            Path path = Path.of(keyPath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                key = Files.readAllBytes(path);
            } else {
                byte[] generatedKey = new byte[KEY_BYTES];
                new SecureRandom().nextBytes(generatedKey);
                try {
                    Files.write(path, generatedKey);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    generatedKey = Files.readAllBytes(path);
                }
                key = generatedKey;
                restrictKeyFile(path);
            }
            if (key.length != KEY_BYTES) {
                throw new IllegalStateException("Identity encryption key must contain 32 bytes");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize local identity encryption", error);
        }
    }

    public EncryptedProfile encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new EncryptedProfile(
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder().encodeToString(cipher.doFinal(plaintext)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Could not encrypt identity profile", error);
        }
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Could not fingerprint identity document", error);
        }
    }

    private void restrictKeyFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // Windows volumes may not support POSIX file permissions.
        }
    }
}