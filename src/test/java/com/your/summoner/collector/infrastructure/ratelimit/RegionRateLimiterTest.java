package com.your.summoner.collector.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.your.summoner.collector.application.port.out.RouteKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RegionRateLimiterTest {

    /** Relógio manual: o sleeper o avança, então os testes não dormem. */
    private static final class FakeClock extends Clock {
        final AtomicLong ms = new AtomicLong(1_000_000);

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(ms.get()); }
        @Override public long millis() { return ms.get(); }

        void advanceTo(long target) { ms.accumulateAndGet(target, Math::max); }
    }

    private final FakeClock clock = new FakeClock();
    private final List<Long> sleeps = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> key = new AtomicReference<>("k1");
    private final CollectorPause pause = new CollectorPause(clock, () -> Optional.ofNullable(key.get()));
    private final RegionRateLimiter limiter = new RegionRateLimiter(clock, d -> {
        sleeps.add(d.toMillis());
        clock.advanceTo(clock.millis() + d.toMillis());
    }, new RateLimitProperties(0.6), pause);

    private static final RouteKey BR_MATCH = new RouteKey("br1", "match");

    @Test
    void parsesHeaders() {
        assertThat(RateLimitHeaders.parse("20:1,100:120"))
                .containsExactly(new RateLimitHeaders.Limit(20, 1), new RateLimitHeaders.Limit(100, 120));
        assertThat(RateLimitHeaders.parse("x,1:,0:5,:3")).isEmpty();
        assertThat(RateLimitHeaders.parse(null)).isEmpty();
    }

    @Test
    void respectsBudgetFraction() throws Exception {
        limiter.onResponse("br1", "match", 200, Map.of("X-App-Rate-Limit", "100:120"));
        for (int i = 0; i < 60; i++) {
            limiter.acquire(BR_MATCH);
        }
        assertThat(sleeps).isEmpty();
        limiter.acquire(BR_MATCH); // 61ª: 60 = floor(100 x 0,6)
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(119_000L, 120_000L);
    }

    @Test
    void defaultLimitsApplyBeforeFirstResponse() throws Exception {
        for (int i = 0; i < 12; i++) { // floor(20 x 0,6) por segundo
            limiter.acquire(BR_MATCH);
        }
        assertThat(sleeps).isEmpty();
        limiter.acquire(BR_MATCH);
        assertThat(sleeps).containsExactly(1000L);
    }

    @Test
    void methodLimitAlsoApplies() throws Exception {
        limiter.onResponse("br1", "match", 200, Map.of("X-App-Rate-Limit", "1000:10", "x-method-rate-limit", "10:10"));
        for (int i = 0; i < 6; i++) {
            limiter.acquire(BR_MATCH);
        }
        limiter.acquire(new RouteKey("br1", "timeline")); // outro método: sem limite
        assertThat(sleeps).isEmpty();
        limiter.acquire(BR_MATCH); // 7ª do método
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void retryAfterBlocksOnlyThatHost() throws Exception {
        limiter.onResponse("br1", "match", 429, Map.of("Retry-After", "5"));
        limiter.acquire(new RouteKey("na1", "match"));
        assertThat(sleeps).isEmpty();
        limiter.acquire(BR_MATCH);
        assertThat(sleeps).containsExactly(5000L);
    }

    @Test
    void tooManyRequestsWithoutRetryAfterWaitsTenSeconds() throws Exception {
        limiter.onResponse("br1", "match", 429, Map.of());
        limiter.acquire(BR_MATCH);
        assertThat(sleeps).containsExactly(10_000L);
    }

    @Test
    void unauthorizedPausesAllRegionsAndResumesOnNewKey() throws Exception {
        // a cada espera da pausa, a chave só muda na 2ª checagem (10 min depois)
        List<Long> pauseSleeps = new ArrayList<>();
        RegionRateLimiter l = new RegionRateLimiter(clock, d -> {
            pauseSleeps.add(d.toMillis());
            clock.advanceTo(clock.millis() + d.toMillis());
            if (pauseSleeps.size() == 2) {
                key.set("k2");
            }
        }, new RateLimitProperties(0.6), pause);

        l.onResponse("br1", "match", 401, Map.of());
        assertThat(pause.isPaused()).isTrue();
        long start = clock.millis();

        l.acquire(new RouteKey("na1", "match")); // outra região também bloqueia
        l.acquire(new RouteKey("euw1", "match"));

        assertThat(pause.isPaused()).isFalse();
        assertThat(clock.millis() - start).isGreaterThanOrEqualTo(Duration.ofMinutes(10).toMillis());
        assertThat(pauseSleeps).hasSize(2);
    }

    @Test
    void pauseProbesAgainAfterThirtyMinutesWithSameKey() throws Exception {
        RegionRateLimiter l = new RegionRateLimiter(clock, d -> clock.advanceTo(clock.millis() + d.toMillis()),
                new RateLimitProperties(0.6), pause);
        l.onResponse("br1", "match", 401, Map.of());
        long start = clock.millis();

        l.acquire(new RouteKey("br1", "match")); // a chave nunca muda

        assertThat(pause.isPaused()).isFalse();
        assertThat(clock.millis() - start).isGreaterThanOrEqualTo(Duration.ofMinutes(30).toMillis());
    }

    @Test
    void forbiddenAlsoPauses() {
        limiter.onResponse("br1", "match", 403, Map.of());
        assertThat(pause.isPaused()).isTrue();
    }

    @Test
    void manyVirtualThreadsNeverExceedBudget() throws Exception {
        limiter.onResponse("br1", "match", 200, Map.of("X-App-Rate-Limit", "10:1")); // teto 6 por segundo
        long start = clock.millis();
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ts.add(Thread.ofVirtual().start(() -> {
                try {
                    limiter.acquire(BR_MATCH);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Thread t : ts) {
            t.join();
        }
        // 30 chamadas a 6/s precisam de pelo menos 4 janelas completas
        assertThat(clock.millis() - start).isGreaterThanOrEqualTo(4000);
    }
}
