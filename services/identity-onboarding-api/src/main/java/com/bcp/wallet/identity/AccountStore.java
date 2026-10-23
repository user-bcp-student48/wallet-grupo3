package com.bcp.wallet.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AccountStore {
    @ConfigProperty(name = "identity.database.path", defaultValue = "data/identity.sqlite")
    String databasePath;

    @Inject
    PiiCipher piiCipher;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    void initialize() {
        try {
            Path path = Path.of(databasePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS accounts (
                            id TEXT PRIMARY KEY,
                            account_type TEXT NOT NULL,
                            document_type TEXT NOT NULL,
                            document_fingerprint TEXT NOT NULL UNIQUE,
                            profile_nonce TEXT NOT NULL,
                            profile_ciphertext TEXT NOT NULL,
                            google_subject TEXT UNIQUE,
                            status TEXT NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize local account database", error);
        }
    }

    public RegisteredAccount create(AccountRegistration registration) {
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        String fingerprint = piiCipher.fingerprint(
                registration.documentType() + ":" + registration.documentNumber());
        EncryptedProfile encrypted = piiCipher.encrypt(serialize(registration));
        String maskedDocument = "*".repeat(Math.max(0, registration.documentNumber().length() - 4))
                + registration.documentNumber().substring(Math.max(0, registration.documentNumber().length() - 4));

        try (Connection connection = connect(); var statement = connection.prepareStatement("""
                INSERT INTO accounts (
                    id, account_type, document_type, document_fingerprint,
                    profile_nonce, profile_ciphertext, google_subject, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, registration.accountType());
            statement.setString(3, registration.documentType());
            statement.setString(4, fingerprint);
            statement.setString(5, encrypted.nonce());
            statement.setString(6, encrypted.ciphertext());
            statement.setString(7, registration.googleSubject());
            statement.setString(8, "PENDING");
            statement.setString(9, createdAt);
            statement.executeUpdate();
            return new RegisteredAccount(id, registration.accountType(), registration.documentType(),
                    maskedDocument, "PENDING", createdAt);
        } catch (SQLException error) {
            if (error.getMessage() != null && error.getMessage().contains("UNIQUE constraint failed")) {
                throw new DuplicateAccountException();
            }
            throw new IllegalStateException("Could not save local account", error);
        }
    }

    private byte[] serialize(AccountRegistration registration) {
        try {
                return objectMapper.writeValueAsBytes(Map.ofEntries(
                    Map.entry("accountType", registration.accountType()),
                    Map.entry("documentType", registration.documentType()),
                    Map.entry("documentNumber", registration.documentNumber()),
                    Map.entry("firstNames", value(registration.firstNames())),
                    Map.entry("paternalSurname", value(registration.paternalSurname())),
                    Map.entry("maternalSurname", value(registration.maternalSurname())),
                    Map.entry("legalName", value(registration.legalName())),
                    Map.entry("tradeName", value(registration.tradeName())),
                    Map.entry("email", value(registration.email())),
                    Map.entry("phone", value(registration.phone())),
                    Map.entry("googleSubject", value(registration.googleSubject()))));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize local account", error);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + Path.of(databasePath).toAbsolutePath());
    }
}

class DuplicateAccountException extends RuntimeException {
}