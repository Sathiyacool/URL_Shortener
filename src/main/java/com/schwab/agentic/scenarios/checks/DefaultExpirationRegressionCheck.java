package com.schwab.agentic.scenarios.checks;

import com.schwab.agentic.guardrails.RegisteredCheck;
import com.schwab.agentic.guardrails.RegisteredCheckId;
import com.schwab.agentic.guardrails.RegisteredCheckResult;
import com.schwab.agentic.shortener.api.CreateShortUrlRequest;
import com.schwab.agentic.shortener.domain.ShortUrl;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import com.schwab.agentic.shortener.service.ExpiredShortUrlException;
import com.schwab.agentic.shortener.service.ShortUrlService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultExpirationRegressionCheck implements RegisteredCheck {

    private final ShortUrlService shortUrlService;
    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;
    private final Duration defaultExpiration;

    public DefaultExpirationRegressionCheck(
            ShortUrlService shortUrlService,
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository,
            Clock clock,
            @Value("${app.default-expiration:0s}") Duration defaultExpiration) {
        this.shortUrlService = shortUrlService;
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
        this.defaultExpiration = defaultExpiration;
    }

    @Override
    public RegisteredCheckId id() {
        return RegisteredCheckId.DEFAULT_EXPIRATION_REGRESSION;
    }

    @Override
    public RegisteredCheckResult run() {
        long startedAt = System.nanoTime();
        var created = shortUrlService.create(new CreateShortUrlRequest(
                "https://example.com/default-expiration-check", null, null));
        boolean hasConfiguredDefault = defaultExpiration.isZero()
                ? created.getExpiresAt() == null
                : created.getExpiresAt().equals(created.getCreatedAt().plus(defaultExpiration));

        String expiredCode = "exp-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = clock.instant();
        shortUrlRepository.save(new ShortUrl(expiredCode, "https://example.com/expired-check", now.minusSeconds(2), now.minusSeconds(1)));
        boolean expiredRedirectRejected;
        try {
            shortUrlService.recordClickAndResolve(expiredCode, null, null);
            expiredRedirectRejected = false;
        } catch (ExpiredShortUrlException exception) {
            expiredRedirectRejected = true;
        }
        boolean noClickRecorded = clickEventRepository.countByShortCode(expiredCode) == 0;
        boolean passed = hasConfiguredDefault && expiredRedirectRejected && noClickRecorded;
        String summary = passed
                ? "Default TTL and expired-link redirect regression passed"
                : "Default TTL or expired-link redirect regression failed";
        return new RegisteredCheckResult(
                passed, summary, Duration.ofNanos(System.nanoTime() - startedAt));
    }
}
