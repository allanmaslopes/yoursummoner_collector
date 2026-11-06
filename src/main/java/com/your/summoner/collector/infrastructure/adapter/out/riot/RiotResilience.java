package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.your.summoner.collector.infrastructure.adapter.out.ExternalCalls;
import com.your.summoner.collector.infrastructure.adapter.out.RetryableFailure;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class RiotResilience {
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;

    public RiotResilience(@Value("${riot.resilience.retry-delay:250ms}") Duration delay,
                          @Value("${riot.resilience.retry-jitter:100ms}") Duration jitter,
                          @Value("${riot.resilience.minimum-calls:20}") int minimumCalls,
                          @Value("${riot.resilience.sliding-window:20}") int slidingWindow) {
        var transientFailure = new RetryableFailure();
        var circuitConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50).minimumNumberOfCalls(minimumCalls).slidingWindowSize(slidingWindow)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordException(transientFailure)
                .ignoreException(failure -> !transientFailure.test(failure)).build();
        circuitBreakers = CircuitBreakerRegistry.of(circuitConfig);
        var retryConfig = RetryConfig.custom().maxAttempts(3).retryOnException(transientFailure)
                .intervalFunction(attempt -> Math.max(0, delay.toMillis() + (jitter.isZero() ? 0
                        : ThreadLocalRandom.current().nextLong(-jitter.toMillis(), jitter.toMillis() + 1))))
                .build();
        retries = RetryRegistry.of(retryConfig);
    }

    public <T> T call(String name, Supplier<T> request) {
        var circuit = circuitBreakers.circuitBreaker(name);
        var retry = retries.retry(name);
        // The circuit observes every HTTP attempt, including retries.
        return retry.executeSupplier(() -> circuit.executeSupplier(
                () -> ExternalCalls.call(RiotErrorDecoder.SERVICE, request)));
    }

    public CircuitBreakerRegistry circuitBreakers() {
        return circuitBreakers;
    }

    public RetryRegistry retries() {
        return retries;
    }
}
