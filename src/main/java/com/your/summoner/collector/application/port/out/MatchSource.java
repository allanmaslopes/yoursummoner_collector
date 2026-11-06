package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.model.Region;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchSource {
    /** Ids das partidas ranqueadas solo (fila 420) do jogador desde {@code since}. */
    List<String> rankedMatchIds(Region region, String puuid, Instant since);

    Optional<RiotMatch> match(Region region, String id);

    Optional<RiotTimeline> timeline(Region region, String id);
}
