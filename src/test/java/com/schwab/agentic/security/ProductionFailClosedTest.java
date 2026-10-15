package com.schwab.agentic.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.AgenticApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ProductionFailClosedTest {

    @Test
    void productionContextFailsToStartWithoutOidcConfiguration() {
        Throwable failure = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(AgenticApplication.class)
                .profiles("prod")
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:failclosed",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--app.database.url=jdbc:postgresql://db.example.test/agentic",
                        "--app.database.username=test-user",
                        "--app.database.password=test-only-password",
                        "--app.security.issuer-uri=",
                        "--app.security.audience="));
        assertTrue(
                hasMessage(failure, "APP_OIDC_ISSUER_URI and APP_OIDC_AUDIENCE"),
                "Startup failure should name the missing OIDC configuration");
    }

    private static boolean hasMessage(Throwable failure, String expected) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
