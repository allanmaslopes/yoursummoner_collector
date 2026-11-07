package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import com.your.summoner.collector.application.port.out.CollectorState;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.service.CollectOutcome;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.infrastructure.config.CollectorProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectorMetricsTest {

    @Test
    void metricsCountOutcomes() {
        var registry = new SimpleMeterRegistry();
        PlayerQueue queue = mock(PlayerQueue.class);
        CollectorState state = mock(CollectorState.class);
        when(queue.pending(Region.AMERICAS)).thenReturn(5);
        when(state.paused()).thenReturn(true);
        var props = new CollectorProperties(true, java.util.List.of(Platform.BR1), "0 0 */6 * * *", 3, 15_000, "http://x");
        var metrics = new CollectorMetrics(registry, queue, state, props);

        metrics.record(Region.AMERICAS, CollectOutcome.SAVED);
        metrics.record(Region.AMERICAS, CollectOutcome.SAVED);
        metrics.record(Region.AMERICAS, CollectOutcome.DUPLICATE);

        assertThat(registry.get("collector.matches").tags("region", "AMERICAS", "outcome", "SAVED").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("collector.matches").tags("outcome", "DUPLICATE").counter().count()).isEqualTo(1);
        assertThat(registry.get("collector.queue.pending").tag("region", "AMERICAS").gauge().value()).isEqualTo(5);
        assertThat(registry.get("collector.paused").gauge().value()).isEqualTo(1);
        assertThat(metrics.lastOutcome(Region.AMERICAS)).contains(CollectOutcome.DUPLICATE);
        assertThat(metrics.lastOutcome(Region.EUROPE)).isEmpty();
    }
}
