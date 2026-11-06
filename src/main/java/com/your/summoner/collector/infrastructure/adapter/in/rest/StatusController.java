package com.your.summoner.collector.infrastructure.adapter.in.rest;

import com.your.summoner.collector.application.port.out.CollectorState;
import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.infrastructure.adapter.in.rest.StatusResponse.RegionStatus;
import com.your.summoner.collector.infrastructure.adapter.in.scheduler.CollectorMetrics;
import com.your.summoner.collector.infrastructure.config.CollectorProperties;
import com.your.summoner.collector.infrastructure.ratelimit.RegionRateLimiter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
class StatusController {

    private final CollectorProperties props;
    private final CollectorState state;
    private final PlayerQueue queue;
    private final MatchStore store;
    private final CollectorMetrics metrics;
    private final RegionRateLimiter limiter;
    private final Clock clock;

    StatusController(CollectorProperties props, CollectorState state, PlayerQueue queue, MatchStore store,
                     CollectorMetrics metrics, RegionRateLimiter limiter, Clock clock) {
        this.props = props;
        this.state = state;
        this.queue = queue;
        this.store = store;
        this.metrics = metrics;
        this.limiter = limiter;
        this.clock = clock;
    }

    @GetMapping("/status")
    StatusResponse status() {
        Instant now = Instant.now(clock);
        String st = !props.enabled() ? "DISABLED" : state.paused() ? "PAUSED_KEY" : "RUNNING";
        List<RegionStatus> regions = props.platforms().stream().map(Platform::matchRegion).distinct()
                .map(r -> region(r, now)).toList();
        return new StatusResponse(st, regions);
    }

    private RegionStatus region(Region r, Instant now) {
        Map<String, String> budget = new LinkedHashMap<>();
        put(budget, r.host());
        props.platforms().stream().filter(p -> p.matchRegion() == r).forEach(p -> put(budget, p.host()));
        return new RegionStatus(r.name(), queue.pending(r), store.countSince(now.minus(Duration.ofHours(1)), r),
                store.countSince(now.minus(Duration.ofDays(1)), r),
                metrics.lastOutcome(r).map(Enum::name).orElse(null), budget);
    }

    private void put(Map<String, String> budget, String host) {
        String usage = limiter.usage(host);
        if (usage != null) {
            budget.put(host, usage);
        }
    }
}
