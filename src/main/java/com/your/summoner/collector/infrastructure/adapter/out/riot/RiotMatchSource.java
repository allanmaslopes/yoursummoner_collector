package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.your.summoner.collector.application.port.out.MatchSource;
import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.infrastructure.adapter.out.UpstreamNotFoundException;
import com.your.summoner.collector.infrastructure.adapter.out.riot.api.RiotMatchApi;
import com.your.summoner.collector.infrastructure.config.RiotApiProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class RiotMatchSource implements MatchSource {
    private static final int RANKED_SOLO = 420;
    private static final int PAGE = 20;

    private final RiotMatchApi api;
    private final RiotApiProperties properties;
    private final RiotResilience resilience;

    public RiotMatchSource(RiotMatchApi api, RiotApiProperties properties, RiotResilience resilience) {
        this.api = api;
        this.properties = properties;
        this.resilience = resilience;
    }

    @Override
    public List<String> rankedMatchIds(Region region, String puuid, Instant since) {
        try {
            var ids = resilience.call("riot-match", () -> api.idsByPuuid(base(region), puuid, RANKED_SOLO, "ranked",
                    since.getEpochSecond(), PAGE));
            return ids == null ? List.of() : List.copyOf(ids);
        } catch (UpstreamNotFoundException e) {
            return List.of();
        }
    }

    @Override
    public Optional<RiotMatch> match(Region region, String id) {
        try {
            return Optional.ofNullable(resilience.call("riot-match", () -> api.byId(base(region), id)));
        } catch (UpstreamNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RiotTimeline> timeline(Region region, String id) {
        try {
            return Optional.ofNullable(resilience.call("riot-match", () -> api.timeline(base(region), id)));
        } catch (UpstreamNotFoundException e) {
            return Optional.empty();
        }
    }

    private URI base(Region region) {
        return URI.create(properties.baseUrlTemplate().replace("{host}", region.host()));
    }
}
