package com.bcp.wallet.identity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.regex.Pattern;

@Path("/api/v1/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountRegistrationResource {
    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern RUC = Pattern.compile("\\d{11}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Inject
    AccountStore accountStore;

    @Inject
    GoogleIdentityVerifier googleVerifier;

    @GET
    @Path("/config")
    public Map<String, Object> registrationConfig() {
        return Map.of(
                "googleEnabled", googleVerifier.isEnabled(),
                "googleClientId", googleVerifier.publicClientId());
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(JsonNode request) {
        String accountType = text(request, "accountType");
        String documentNumber = text(request, "documentNumber").replaceAll("\\s", "");
        String documentType;
        AccountRegistration registration;

        if ("PERSON".equals(accountType)) {
            if (!DNI.matcher(documentNumber).matches()) return invalid("DNI debe tener 8 dígitos");
            String firstNames = text(request, "firstNames");
            String paternalSurname = text(request, "paternalSurname");
            String maternalSurname = text(request, "maternalSurname");
            String email = text(request, "email");
            if (firstNames.isBlank() || paternalSurname.isBlank() || maternalSurname.isBlank()) {
                return invalid("NOMBRES_Y_APELLIDOS_REQUERIDOS");
            }
            if (!validEmail(email)) return invalid("EMAIL_INVALIDO");
            documentType = "DNI";
            registration = new AccountRegistration(accountType, documentType, documentNumber,
                    firstNames, paternalSurname, maternalSurname, null, null, email,
                    optionalText(request, "phone"), null);
        } else if ("BUSINESS".equals(accountType)) {
            if (!RUC.matcher(documentNumber).matches()) return invalid("RUC debe tener 11 dígitos");
            String legalName = text(request, "legalName");
            String email = text(request, "email");
            if (legalName.isBlank()) return invalid("RAZON_SOCIAL_REQUERIDA");
            if (!validEmail(email)) return invalid("EMAIL_INVALIDO");
            documentType = "RUC";
            registration = new AccountRegistration(accountType, documentType, documentNumber,
                    null, null, null, legalName, optionalText(request, "tradeName"), email,
                    optionalText(request, "phone"), null);
        } else {
            return invalid("TIPO_DE_CUENTA_INVALIDO");
        }
        return save(registration);
    }

    @POST
    @Path("/register/google")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerWithGoogle(JsonNode request) {
        if (!googleVerifier.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "GOOGLE_SIGN_IN_NOT_CONFIGURED"))
                    .build();
        }
        String documentNumber = text(request, "documentNumber").replaceAll("\\s", "");
        if (!DNI.matcher(documentNumber).matches()) return invalid("DNI debe tener 8 dígitos");
        String credential = text(request, "credential");
        if (credential.isBlank()) return invalid("GOOGLE_CREDENTIAL_REQUIRED");

        try {
            GoogleIdentity google = googleVerifier.verify(credential);
            AccountRegistration registration = new AccountRegistration("PERSON", "DNI", documentNumber,
                    google.firstNames(), google.surname(), "", null, null, google.email(),
                    optionalText(request, "phone"), google.subject());
            return save(registration);
        } catch (InvalidGoogleCredentialException error) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "INVALID_GOOGLE_CREDENTIAL"))
                    .build();
        } catch (GoogleVerificationUnavailableException error) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "GOOGLE_VERIFICATION_UNAVAILABLE"))
                    .build();
        }
    }

    private Response save(AccountRegistration registration) {
        try {
            return Response.status(Response.Status.CREATED).entity(accountStore.create(registration)).build();
        } catch (DuplicateAccountException error) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "ACCOUNT_ALREADY_REGISTERED"))
                    .build();
        }
    }

    private Response invalid(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message))
                .build();
    }

    private boolean validEmail(String email) {
        return email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    private String text(JsonNode request, String field) {
        return request == null ? "" : request.path(field).asText("").trim();
    }

    private String optionalText(JsonNode request, String field) {
        String value = text(request, field);
        return value.isBlank() ? null : value;
    }
}