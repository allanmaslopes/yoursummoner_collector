package com.your.summoner.collector.domain.exception;

import java.time.Duration;
import java.util.Optional;

/** A Riot respondeu 429. {@code retryAfter} vem do header Retry-After, quando presente. */
public final class RateLimitExceededException extends DomainException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Riot API rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
