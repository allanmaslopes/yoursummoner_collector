package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.LeagueSource;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.RequestBudget;
import com.your.summoner.collector.application.port.out.RouteKey;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Tier;
import org.springframework.stereotype.Service;

/** Enfileira os jogadores Mestre+ de uma plataforma. */
@Service
public class SeedService {

    private final LeagueSource league;
    private final PlayerQueue queue;
    private final RequestBudget budget;

    public SeedService(LeagueSource league, PlayerQueue queue, RequestBudget budget) {
        this.league = league;
        this.queue = queue;
        this.budget = budget;
    }

    public void seed(Platform platform) throws InterruptedException {
        for (Tier tier : Tier.values()) {
            budget.acquire(new RouteKey(platform.host(), "league"));
            queue.offer(platform, tier, league.masterPlusPuuids(platform, tier));
        }
    }
}
