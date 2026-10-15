package com.schwab.agentic.shortener.api;

import java.time.Instant;

public record ClickRecordResponse(Instant clickedAt, String referrer, String userAgent) {
}
