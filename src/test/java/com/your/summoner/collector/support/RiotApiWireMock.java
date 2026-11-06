package com.your.summoner.collector.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.your.summoner.collector.CollectorApplication;
import com.your.summoner.collector.infrastructure.adapter.out.riot.RateLimitObserver;
import com.your.summoner.collector.infrastructure.adapter.out.riot.RiotResilience;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/** Riot isolada em WireMock e Mongo embarcado (perfil test), compartilhados por todos os testes dos adapters. */
@SpringBootTest(classes = CollectorApplication.class, properties = {
        "riot.api.read-timeout=2s",
        "riot.resilience.retry-delay=10ms", "riot.resilience.retry-jitter=0ms",
        "riot.resilience.minimum-calls=4", "riot.resilience.sliding-window=4"})
@ActiveProfiles("test")
@Import(RiotApiWireMock.ObserverConfig.class)
public abstract class RiotApiWireMock {
    public static final String RIOT = "/riot";
    protected static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    public record Seen(String host, String method, int status, Map<String, String> headers) {}

    public static final List<Seen> SEEN = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class ObserverConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        RateLimitObserver recordingObserver() {
            return (host, method, status, headers) -> SEEN.add(new Seen(host, method, status, headers));
        }
    }

    @DynamicPropertySource
    static void riotProperties(DynamicPropertyRegistry registry) {
        registry.add("riot.api.base-url-template", () -> wireMock.baseUrl() + RIOT + "/{host}");
    }

    @Autowired RiotResilience resilience;

    @BeforeEach
    @AfterEach
    protected void resetRiotState() {
        resilience.circuitBreakers().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        wireMock.resetAll();
        SEEN.clear();
    }

    public static String fixture(String name) {
        try (var in = RiotApiWireMock.class.getResourceAsStream("/riot/" + name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
