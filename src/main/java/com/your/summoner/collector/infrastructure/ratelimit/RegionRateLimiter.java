package com.your.summoner.collector.infrastructure.ratelimit;

import com.your.summoner.collector.infrastructure.adapter.out.riot.RateLimitObserver;
import com.your.summoner.collector.application.port.out.RequestBudget;
import com.your.summoner.collector.application.port.out.RouteKey;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Janela deslizante por host (App limit) e por host+método (Method limit), com teto = limite x fração.
 * Quem não tem vaga dorme o tempo calculado até a próxima (sem busy-wait) e tenta de novo.
 */
public class RegionRateLimiter implements RequestBudget, RateLimitObserver {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private static final List<RateLimitHeaders.Limit> DEFAULT_APP =
            List.of(new RateLimitHeaders.Limit(20, 1), new RateLimitHeaders.Limit(100, 120));
    private static final long DEFAULT_RETRY_AFTER_MS = 10_000;

    /** Limites e instantes (ms) das chamadas dentro da maior janela, em ordem crescente. */
    private static final class Scope {
        List<RateLimitHeaders.Limit> limits;
        final ArrayDeque<Long> stamps = new ArrayDeque<>();

        Scope(List<RateLimitHeaders.Limit> limits) {
            this.limits = limits;
        }
    }

    private final Clock clock;
    private final Sleeper sleeper;
    private final double fraction;
    private final CollectorPause pause;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Scope> app = new HashMap<>();
    private final Map<String, Scope> method = new HashMap<>();
    private final Map<String, Long> blockedUntil = new HashMap<>();

    public RegionRateLimiter(Clock clock, Sleeper sleeper, RateLimitProperties props, CollectorPause pause) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.fraction = props.budgetFraction();
        this.pause = pause;
    }

    @Override
    public void acquire(RouteKey key) throws InterruptedException {
        while (true) {
            Duration paused = pause.remaining(); // fora do lock: pode consultar o cofre
            if (!paused.isZero()) {
                sleeper.sleep(paused);
                continue;
            }
            long waitMs = tryReserve(key);
            if (waitMs == 0) {
                return;
            }
            sleeper.sleep(Duration.ofMillis(waitMs));
        }
    }

    /** Chamada a cada resposta da Riot: ajusta os limites e reage a 429 e 401/403. */
    @Override
    public void onResponse(String host, String method, int status, Map<String, String> headers) {
        if (status == 401 || status == 403) {
            pause.pause();
        }
        lock.lock();
        try {
            long now = clock.millis();
            if (status == 429) {
                String ra = header(headers, "Retry-After");
                long ms = DEFAULT_RETRY_AFTER_MS;
                try {
                    if (ra != null) {
                        ms = Long.parseLong(ra.trim()) * 1000;
                    }
                } catch (NumberFormatException ignored) {
                    // header inválido: usa o padrão
                }
                blockedUntil.merge(host, now + ms, Math::max);
            }
            List<RateLimitHeaders.Limit> a = RateLimitHeaders.parse(header(headers, "X-App-Rate-Limit"));
            if (!a.isEmpty()) {
                app.computeIfAbsent(host, h -> new Scope(a)).limits = a;
            }
            List<RateLimitHeaders.Limit> m = RateLimitHeaders.parse(header(headers, "X-Method-Rate-Limit"));
            if (!m.isEmpty()) {
                this.method.computeIfAbsent(methodKey(host, method), k -> new Scope(m)).limits = m;
            }
        } finally {
            lock.unlock();
        }
    }

    /** "usadas/teto" na maior janela do host, ou null se o host ainda não foi usado. Só leitura. */
    public String usage(String host) {
        lock.lock();
        try {
            Scope a = app.get(host);
            if (a == null || a.limits.isEmpty()) {
                return null;
            }
            RateLimitHeaders.Limit l = a.limits.stream()
                    .max(java.util.Comparator.comparingInt(RateLimitHeaders.Limit::windowSec)).get();
            long since = clock.millis() - l.windowSec() * 1000L;
            long used = a.stamps.stream().filter(t -> t > since).count();
            return used + "/" + Math.max(1, (int) Math.floor(l.limit() * fraction));
        } finally {
            lock.unlock();
        }
    }

    /** 0 = vaga reservada; senão, ms até valer a pena tentar de novo. */
    private long tryReserve(RouteKey key) {
        lock.lock();
        try {
            long now = clock.millis();
            long wait = Math.max(0, blockedUntil.getOrDefault(key.host(), 0L) - now);
            Scope a = app.computeIfAbsent(key.host(), h -> new Scope(DEFAULT_APP));
            Scope m = method.computeIfAbsent(methodKey(key.host(), key.method()), k -> new Scope(List.of()));
            wait = Math.max(wait, Math.max(waitFor(a, now), waitFor(m, now)));
            if (wait > 0) {
                return wait;
            }
            if (!a.limits.isEmpty()) {
                a.stamps.addLast(now);
            }
            if (!m.limits.isEmpty()) {
                m.stamps.addLast(now);
            }
            return 0;
        } finally {
            lock.unlock();
        }
    }

    private long waitFor(Scope s, long now) {
        long maxWindow = 0;
        for (RateLimitHeaders.Limit l : s.limits) {
            maxWindow = Math.max(maxWindow, l.windowSec() * 1000L);
        }
        while (!s.stamps.isEmpty() && s.stamps.peekFirst() <= now - maxWindow) {
            s.stamps.removeFirst();
        }
        long wait = 0;
        for (RateLimitHeaders.Limit l : s.limits) {
            int cap = Math.max(1, (int) Math.floor(l.limit() * fraction));
            long windowMs = l.windowSec() * 1000L;
            int seen = 0;
            for (var it = s.stamps.descendingIterator(); it.hasNext(); ) {
                long t = it.next();
                if (t <= now - windowMs) {
                    break;
                }
                if (++seen == cap) { // t é a cap-ésima chamada mais recente: a vaga abre quando ela sai da janela
                    wait = Math.max(wait, t + windowMs - now);
                    break;
                }
            }
        }
        return wait;
    }

    private static String methodKey(String host, String method) {
        return host + '|' + method;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }
}
