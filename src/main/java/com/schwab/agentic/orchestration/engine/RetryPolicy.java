package com.schwab.agentic.orchestration.engine;

import java.time.Duration;
import java.util.Objects;

public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maximumDelay;
    private final double multiplier;

    public RetryPolicy(int maxAttempts, Duration initialDelay, Duration maximumDelay, double multiplier) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts must be at least one");
        }
        this.initialDelay = Objects.requireNonNull(initialDelay);
        this.maximumDelay = Objects.requireNonNull(maximumDelay);
        if (initialDelay.isNegative() || maximumDelay.isNegative() || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Retry delays must be non-negative and maximum delay must be >= initial delay");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("Retry multiplier must be finite and at least one");
        }
        this.maxAttempts = maxAttempts;
        this.multiplier = multiplier;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean shouldRetry(int completedAttempts, Exception failure) {
        return completedAttempts < maxAttempts
                && !(failure instanceof InterruptedException)
                && !(failure instanceof TaskStoppedException);
    }

    public Duration delayBeforeAttempt(int nextAttemptNumber) {
        if (nextAttemptNumber <= 1 || initialDelay.isZero()) {
            return Duration.ZERO;
        }
        long maximumMillis = maximumDelay.toMillis();
        double delayMillis = initialDelay.toMillis();
        for (int attempt = 2; attempt < nextAttemptNumber; attempt++) {
            if (delayMillis >= maximumMillis / multiplier) {
                return maximumDelay;
            }
            delayMillis *= multiplier;
        }
        return Duration.ofMillis(Math.min((long) Math.ceil(delayMillis), maximumMillis));
    }
}
