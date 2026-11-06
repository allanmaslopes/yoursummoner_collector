package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Region;

import java.time.Instant;

public interface MatchStore {
    /** Grava a partida; false se o matchId já existia. */
    boolean saveIfAbsent(CollectedMatch m);

    /** Apaga as partidas de patch menor que {@code oldestKept}; devolve quantas. */
    long deleteOlderThan(PatchVersion oldestKept);

    long countSince(Instant since, Region r);
}
