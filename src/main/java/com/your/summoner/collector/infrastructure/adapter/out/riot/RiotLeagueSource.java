package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.your.summoner.collector.application.port.out.LeagueSource;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Tier;
import com.your.summoner.collector.infrastructure.adapter.out.UpstreamNotFoundException;
import com.your.summoner.collector.infrastructure.adapter.out.riot.api.RiotLeagueApi;
import com.your.summoner.collector.infrastructure.adapter.out.riot.dto.RiotLeagueListDto;
import com.your.summoner.collector.infrastructure.config.RiotApiProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class RiotLeagueSource implements LeagueSource {
    private final RiotLeagueApi api;
    private final RiotApiProperties properties;
    private final RiotResilience resilience;

    public RiotLeagueSource(RiotLeagueApi api, RiotApiProperties properties, RiotResilience resilience) {
        this.api = api;
        this.properties = properties;
        this.resilience = resilience;
    }

    @Override
    public List<String> masterPlusPuuids(Platform platform, Tier tier) {
        URI base = URI.create(properties.baseUrlTemplate().replace("{host}", platform.host()));
        try {
            var list = resilience.call("riot-league",
                    () -> api.byTier(base, tier.name().toLowerCase(Locale.ROOT)));
            return list == null || list.entries() == null ? List.of() : list.entries().stream()
                    .map(RiotLeagueListDto.Entry::puuid).filter(Objects::nonNull).toList();
        } catch (UpstreamNotFoundException e) {
            return List.of();
        }
    }
}
