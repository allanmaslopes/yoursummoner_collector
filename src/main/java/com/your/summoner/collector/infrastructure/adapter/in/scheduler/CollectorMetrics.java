package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import com.your.summoner.collector.application.port.out.CollectorState;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.service.CollectOutcome;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.infrastructure.config.CollectorProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Métricas do coletor (contador por resultado, fila pendente, pausa) e o último resultado de cada região. */
@Component
public class CollectorMetrics {

    private final MeterRegistry registry;
    private final Map<Region, CollectOutcome> last = new ConcurrentHashMap<>();

    public CollectorMetrics(MeterRegistry registry, PlayerQueue queue, CollectorState state,
                            CollectorProperties props) {
        this.registry = registry;
        Gauge.builder("collector.paused", () -> state.paused() ? 1 : 0).register(registry);
        props.platforms().stream().map(Platform::matchRegion).distinct().forEach(region ->
                Gauge.builder("collector.queue.pending", () -> queue.pending(region))
                        .tag("region", region.name()).register(registry));
    }

    public void record(Region region, CollectOutcome outcome) {
        registry.counter("collector.matches", "region", region.name(), "outcome", outcome.name()).increment();
        last.put(region, outcome);
    }

    public Optional<CollectOutcome> lastOutcome(Region region) {
        return Optional.ofNullable(last.get(region));
    }
}
