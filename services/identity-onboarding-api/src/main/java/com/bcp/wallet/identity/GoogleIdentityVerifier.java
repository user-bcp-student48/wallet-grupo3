package com.bcp.wallet.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class GoogleIdentityVerifier {
    @ConfigProperty(name = "identity.google.client-id", defaultValue = "disabled")
    String clientId;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public boolean isEnabled() {
        return !clientId.isBlank() && !clientId.equals("disabled");
    }

    public String publicClientId() {
        return isEnabled() ? clientId : "";
    }

    public GoogleIdentity verify(String credential) {
        if (!isEnabled()) {
            throw new IllegalStateException("Google registration is not configured");
        }
        try {
            URI uri = URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                    + URLEncoder.encode(credential, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new InvalidGoogleCredentialException();
            }
            JsonNode claims = objectMapper.readTree(response.body());
            String issuer = text(claims, "iss");
            boolean verifiedEmail = "true".equalsIgnoreCase(text(claims, "email_verified"));
            long expiresAt = claims.path("exp").asLong(0);
            if (!clientId.equals(text(claims, "aud"))
                    || !(issuer.equals("accounts.google.com") || issuer.equals("https://accounts.google.com"))
                    || !verifiedEmail
                    || expiresAt <= Instant.now().getEpochSecond()
                    || text(claims, "sub").isBlank()
                    || text(claims, "email").isBlank()) {
                throw new InvalidGoogleCredentialException();
            }
            return new GoogleIdentity(text(claims, "sub"), text(claims, "email"),
                    text(claims, "given_name"), text(claims, "family_name"));
        } catch (InvalidGoogleCredentialException error) {
            throw error;
        } catch (Exception error) {
            throw new GoogleVerificationUnavailableException(error);
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}

class InvalidGoogleCredentialException extends RuntimeException {
}

class GoogleVerificationUnavailableException extends RuntimeException {
    GoogleVerificationUnavailableException(Throwable cause) {
        super(cause);
    }
}