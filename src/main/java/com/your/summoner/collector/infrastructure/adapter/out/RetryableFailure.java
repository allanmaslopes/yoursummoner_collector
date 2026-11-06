package com.your.summoner.collector.infrastructure.adapter.out;

import com.your.summoner.collector.domain.exception.ExternalServiceException;

import java.util.function.Predicate;

/** Retry só para falha transitória (5xx, timeout, conexão). Nunca 4xx: o 429 sobe com o Retry-After. */
public class RetryableFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return failure instanceof ExternalServiceException e && e.isRetryable();
    }
}
