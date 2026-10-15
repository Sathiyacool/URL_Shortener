package com.schwab.agentic.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

    @Test
    void rejectsMissingOrNonHttpsOidcConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> SecurityConfiguration.validateOidcConfiguration("", ""));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> SecurityConfiguration.validateOidcConfiguration("http://issuer.example", "service"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseConfiguration("", "", ""));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseConfiguration(
                        "jdbc:postgresql://embedded-user@db.example.test/agentic", "user", "test-password"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseConfiguration(
                        "jdbc:postgresql://db.example.test/agentic?password=embedded",
                        "user",
                        "test-password",
                        "verify-full"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ProductionConfiguration.validateDatabaseConfiguration(
                        "jdbc:postgresql://db.example.test/agentic", "user", "test-password", "disable"));
    }

    @Test
    void acceptsHttpsIssuerAndValidatesRequiredAudience() {
        assertDoesNotThrow(
                () -> SecurityConfiguration.validateOidcConfiguration("https://issuer.example/tenant", "service"));
        assertDoesNotThrow(() -> ProductionConfiguration.validateDatabaseConfiguration(
                "jdbc:postgresql://db.example.test/agentic", "test-user", "test-password"));
        Jwt matchingAudience = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("aud", List.of("service", "other"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Jwt missingAudience = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("aud", List.of("other"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertTrue(!SecurityConfiguration.audienceValidator("service").validate(matchingAudience).hasErrors());
        assertTrue(SecurityConfiguration.audienceValidator("service").validate(missingAudience).hasErrors());
    }
}
