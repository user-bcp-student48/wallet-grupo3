package com.bcp.wallet.identity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class OnboardingResourceTest {
    @BeforeEach
    void clearsLocalAccountsBetweenTests() throws SQLException {
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + Path.of("target/test-identity.sqlite").toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM accounts");
        }
    }

    @Test
    void reportsLocalHealthWithoutCallingUpstream() {
        given()
                .when().get("/api/v1/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("identity-onboarding-api"));
    }

    @Test
    void validatesRequiredFieldsBeforeCallingKycProvider() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/kyc/verifications")
                .then()
                .statusCode(400)
                .body("error", equalTo("DOCUMENT_FIELDS_REQUIRED"));
    }

    @Test
    void rejectsNonTestDocumentType() {
        given()
                .contentType("application/json")
                .body("{\"documentType\":\"DNI\",\"document\":\"TEST-001\"}")
                .when().post("/api/v1/kyc/verifications")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_DOCUMENT_TYPE"));
    }

    @Test
    void rejectsDocumentWithoutTestPrefix() {
        given()
                .contentType("application/json")
                .body("{\"documentType\":\"TEST\",\"document\":\"12345678\"}")
                .when().post("/api/v1/kyc/verifications")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_DOCUMENT"));
    }

    @Test
    void rejectsUnsupportedScenario() {
        given()
                .contentType("application/json")
                .body("{\"documentType\":\"TEST\",\"document\":\"TEST-001\",\"scenario\":\"OTHER\"}")
                .when().post("/api/v1/kyc/verifications")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_SCENARIO"));
    }

    @Test
    void protectsHumanDecisionWithOperatorKey() {
        given()
                .contentType("application/json")
                .header("X-Operator-Key", "wrong-key")
                .body("{\"decision\":\"ACTIVE\"}")
                .when().post("/api/v1/kyc/verifications/kyc-id/decision")
                .then()
                .statusCode(403)
                .body("error", equalTo("FORBIDDEN"));
    }

    @Test
    void validatesHumanDecisionEnum() {
        given()
                .contentType("application/json")
                .header("X-Operator-Key", "test-operator")
                .body("{\"decision\":\"APPROVED\"}")
                .when().post("/api/v1/kyc/verifications/kyc-id/decision")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_DECISION"));
    }

            @Test
            void registersPersonWithDniAndReturnsMaskedDocument() {
            given()
                .contentType("application/json")
                .body("""
                    {"accountType":"PERSON","documentNumber":"12345678",
                     "firstNames":"Ana Maria","paternalSurname":"Perez",
                     "maternalSurname":"Rojas","email":"ana@example.test"}
                    """)
                .when().post("/api/v1/accounts/register")
                .then()
                .statusCode(201)
                .body("accountType", equalTo("PERSON"))
                .body("documentType", equalTo("DNI"))
                .body("maskedDocument", equalTo("****5678"));
            }

            @Test
            void registersBusinessWithRuc() {
            given()
                .contentType("application/json")
                .body("""
                    {"accountType":"BUSINESS","documentNumber":"20123456789",
                     "legalName":"Empresa de Prueba SAC","email":"contacto@example.test"}
                    """)
                .when().post("/api/v1/accounts/register")
                .then()
                .statusCode(201)
                .body("accountType", equalTo("BUSINESS"))
                .body("documentType", equalTo("RUC"))
                .body("maskedDocument", equalTo("*******6789"));
            }

            @Test
            void rejectsInvalidDniWithoutSavingAccount() {
            given()
                .contentType("application/json")
                .body("""
                    {"accountType":"PERSON","documentNumber":"1234",
                     "firstNames":"Ana","paternalSurname":"Perez",
                     "maternalSurname":"Rojas","email":"ana@example.test"}
                    """)
                .when().post("/api/v1/accounts/register")
                .then()
                .statusCode(400)
                .body("error", equalTo("DNI debe tener 8 dígitos"));
            }

            @Test
            void rejectsDuplicateDocument() {
            String body = """
                {"accountType":"PERSON","documentNumber":"87654321",
                 "firstNames":"Rosa","paternalSurname":"Diaz",
                 "maternalSurname":"Leon","email":"rosa@example.test"}
                """;
            given().contentType("application/json").body(body)
                .when().post("/api/v1/accounts/register").then().statusCode(201);
            given().contentType("application/json").body(body)
                .when().post("/api/v1/accounts/register")
                .then().statusCode(409).body("error", equalTo("ACCOUNT_ALREADY_REGISTERED"));
            }

            @Test
            void exposesGoogleAvailabilityAndRejectsGoogleSignupWhenUnconfigured() {
            given().when().get("/api/v1/accounts/config")
                .then().statusCode(200).body("googleEnabled", equalTo(false));
            given().contentType("application/json")
                .body("{\"documentNumber\":\"12345678\",\"credential\":\"token\"}")
                .when().post("/api/v1/accounts/register/google")
                .then().statusCode(503).body("error", equalTo("GOOGLE_SIGN_IN_NOT_CONFIGURED"));
            }
}