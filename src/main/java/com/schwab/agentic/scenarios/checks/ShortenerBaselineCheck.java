package com.schwab.agentic.scenarios.checks;

import com.schwab.agentic.guardrails.RegisteredCheck;
import com.schwab.agentic.guardrails.RegisteredCheckId;
import com.schwab.agentic.guardrails.RegisteredCheckResult;
import com.schwab.agentic.shortener.api.CreateShortUrlRequest;
import com.schwab.agentic.shortener.service.ShortUrlService;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ShortenerBaselineCheck implements RegisteredCheck {

    private final ShortUrlService shortUrlService;

    public ShortenerBaselineCheck(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @Override
    public RegisteredCheckId id() {
        return RegisteredCheckId.SHORTENER_BASELINE;
    }

    @Override
    public RegisteredCheckResult run() {
        long startedAt = System.nanoTime();
        var created = shortUrlService.create(new CreateShortUrlRequest(
                "https://example.com/agentic-baseline-check", null, null));
        var resolved = shortUrlService.recordClickAndResolve(created.getCode(), "registered-check", "baseline");
        var statistics = shortUrlService.getStats(created.getCode());
        boolean passed = created.getDestinationUrl().equals(resolved.getDestinationUrl())
                && statistics.totalClicks() == 1;
        return new RegisteredCheckResult(
                passed,
                passed ? "Create, resolve, and click analytics baseline passed" : "Shortener baseline check failed",
                Duration.ofNanos(System.nanoTime() - startedAt));
    }
}
