package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Tier;

import java.util.List;

public interface LeagueSource {
    /** PUUIDs da liga Mestre+ da fila solo/duo da plataforma (vazio se a Riot não achar). */
    List<String> masterPlusPuuids(Platform platform, Tier tier);
}
