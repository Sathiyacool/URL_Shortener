package com.schwab.agentic.shortener.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateShortUrlRequest(
        @Schema(
                        description = "Absolute HTTP(S) destination URL. The service stores and redirects to it; it does not fetch it.",
                        example = "https://example.com/articles")
                @NotBlank
                @Size(max = 2048)
                String url,
        @Schema(
                        description = "Optional 4-32 character URL-safe alias. Must be unique.",
                        example = "article1")
        @Pattern(regexp = "[A-Za-z0-9_-]{4,32}", message = "Alias must be 4 to 32 URL-safe characters")
                String alias,
        @Schema(
                        description = "Optional future expiration instant; omitted values use the configured default.",
                        example = "2027-01-01T00:00:00Z")
        Instant expiresAt) {
}
