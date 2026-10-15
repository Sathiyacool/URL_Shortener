package com.schwab.agentic.shortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank @Size(max = 2048) String url,
        @Pattern(regexp = "[A-Za-z0-9_-]{4,32}", message = "Alias must be 4 to 32 URL-safe characters")
                String alias,
        Instant expiresAt) {
}
