package com.schwab.agentic.shortener.api;

import java.time.Instant;

public record ShortUrlResponse(
        String code,
        String shortUrl,
        String url,
        Instant createdAt,
        Instant expiresAt) {
}
