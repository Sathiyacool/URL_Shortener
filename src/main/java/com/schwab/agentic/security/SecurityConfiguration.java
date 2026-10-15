package com.schwab.agentic.security;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    @Profile("!prod")
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("prod")
    SecurityFilterChain productionSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/approvals/**",
                                "/api/runs/**",
                                "/api/scenarios/**",
                                "/api/metrics/**",
                                "/api/urls/*/stats",
                                "/actuator/**")
                        .hasAuthority("SCOPE_agentic.operator")
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @Profile("prod")
    JwtDecoder productionJwtDecoder(
            @Value("${app.security.issuer-uri:}") String issuerUri,
            @Value("${app.security.audience:}") String audience) {
        validateOidcConfiguration(issuerUri, audience);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(audience);
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult issuerResult = issuerValidator.validate(jwt);
            return issuerResult.hasErrors() ? issuerResult : audienceValidator.validate(jwt);
        });
        return decoder;
    }

    static void validateOidcConfiguration(String issuerUri, String audience) {
        if (issuerUri == null || issuerUri.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException(
                    "Production security requires APP_OIDC_ISSUER_URI and APP_OIDC_AUDIENCE");
        }
        URI issuer;
        try {
            issuer = URI.create(issuerUri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_OIDC_ISSUER_URI must be an absolute HTTPS issuer URI", exception);
        }
        if (!"https".equalsIgnoreCase(issuer.getScheme())
                || issuer.getHost() == null
                || issuer.getUserInfo() != null
                || issuer.getQuery() != null
                || issuer.getFragment() != null) {
            throw new IllegalStateException("APP_OIDC_ISSUER_URI must be an absolute HTTPS issuer URI");
        }
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required token audience is missing", null));
    }
}
