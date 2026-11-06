package com.your.summoner.collector.infrastructure.adapter.out.riot.api;

import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.infrastructure.adapter.out.riot.RiotFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.List;

@FeignClient(name = "riot-match", url = "https://api.riotgames.com", configuration = RiotFeignConfig.class)
public interface RiotMatchApi {
    /** {@code startTime} em segundos desde a época. */
    @GetMapping("/lol/match/v5/matches/by-puuid/{puuid}/ids")
    List<String> idsByPuuid(URI baseUri, @PathVariable("puuid") String puuid,
                            @RequestParam("queue") int queue, @RequestParam("type") String type,
                            @RequestParam("startTime") long startTime, @RequestParam("count") int count);

    @GetMapping("/lol/match/v5/matches/{matchId}")
    RiotMatch byId(URI baseUri, @PathVariable("matchId") String matchId);

    @GetMapping("/lol/match/v5/matches/{matchId}/timeline")
    RiotTimeline timeline(URI baseUri, @PathVariable("matchId") String matchId);
}
