package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;

import java.util.List;
import java.util.Optional;

public interface PlayerQueue {
    /** Enfileira sem duplicar por (plataforma, puuid), mantendo o maior tier. */
    void offer(Platform p, Tier tier, List<String> puuids);

    /** Remove e devolve o próximo jogador da região (maior tier, mais antigo). */
    Optional<QueuedPlayer> poll(Region r);

    int pending(Region r);
}
