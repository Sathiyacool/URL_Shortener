package com.schwab.agentic.shortener.api;

import java.util.List;

public record ClickStatsResponse(long totalClicks, List<ClickRecordResponse> recentClicks) {
}
