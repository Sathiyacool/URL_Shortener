package com.schwab.agentic.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Agentic URL Shortener API",
                version = "1.0.0",
                description = "REST API for URL management and controlled orchestration runs. "
                        + "Swagger UI and the OpenAPI document are available only in the local profile."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Production profile: OIDC JWT with the configured issuer/audience and agentic.operator scope.")
public class OpenApiConfiguration {
}
