package com.schwab.agentic.security;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfiguration {

    @Bean
    static BeanFactoryPostProcessor validateProductionDatabaseConfiguration(Environment environment) {
        return beanFactory -> validateDatabaseConfiguration(
                environment.getProperty("app.database.url", ""),
                environment.getProperty("app.database.username", ""),
                environment.getProperty("app.database.password", ""),
                environment.getProperty("app.database.ssl-mode", "verify-full"));
    }

    static void validateDatabaseConfiguration(String url, String username, String password) {
        validateDatabaseConfiguration(url, username, password, "verify-full");
    }

    static void validateDatabaseConfiguration(String url, String username, String password, String sslMode) {
        if (url == null || !url.startsWith("jdbc:postgresql://")
                || username == null || username.isBlank()
                || password == null || password.isBlank()
                || sslMode == null
                || !Set.of("require", "verify-ca", "verify-full").contains(sslMode.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Production database requires PostgreSQL connection settings and TLS via DATABASE_SSL_MODE");
        }
        URI jdbcTarget;
        try {
            jdbcTarget = URI.create(url.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL must identify a PostgreSQL host and database", exception);
        }
        if (jdbcTarget.getHost() == null
                || jdbcTarget.getPath() == null
                || jdbcTarget.getPath().length() < 2
                || jdbcTarget.getUserInfo() != null
                || containsCredentialParameter(jdbcTarget.getQuery())
                || hasInsecureSslMode(jdbcTarget.getQuery())) {
            throw new IllegalStateException(
                    "DATABASE_URL must identify a PostgreSQL host/database without embedded credentials or disabled TLS");
        }
    }

    private static boolean containsCredentialParameter(String query) {
        if (query == null) {
            return false;
        }
        return Arrays.stream(query.split("&"))
                .map(parameter -> decode(parameter.split("=", 2)[0]))
                .anyMatch(key -> key.equalsIgnoreCase("user") || key.equalsIgnoreCase("password"));
    }

    private static boolean hasInsecureSslMode(String query) {
        if (query == null) {
            return false;
        }
        return Arrays.stream(query.split("&"))
                .filter(parameter -> parameter.regionMatches(true, 0, "sslmode=", 0, "sslmode=".length()))
                .map(parameter -> decode(parameter.substring("sslmode=".length())))
                .anyMatch(mode -> !Set.of("require", "verify-ca", "verify-full").contains(mode.toLowerCase(Locale.ROOT)));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
