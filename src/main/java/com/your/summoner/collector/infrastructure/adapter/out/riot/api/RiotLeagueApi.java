package com.your.summoner.collector.infrastructure.adapter.out.riot.api;

import com.your.summoner.collector.infrastructure.adapter.out.riot.RiotFeignConfig;
import com.your.summoner.collector.infrastructure.adapter.out.riot.dto.RiotLeagueListDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;

@FeignClient(name = "riot-league", url = "https://api.riotgames.com", configuration = RiotFeignConfig.class)
public interface RiotLeagueApi {
    /** {@code tier} em minúsculas: challenger, grandmaster ou master. */
    @GetMapping("/lol/league/v4/{tier}leagues/by-queue/RANKED_SOLO_5x5")
    RiotLeagueListDto byTier(URI baseUri, @PathVariable("tier") String tier);
}
