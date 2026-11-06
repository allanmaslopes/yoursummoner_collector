package com.your.summoner.collector.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.Map;

/** Estado do coletor por região; só contagens, nunca dados de jogador. */
public record StatusResponse(String state, List<RegionStatus> regions) {

    public record RegionStatus(String region, int pendingPlayers, long matchesLastHour, long matchesLastDay,
                               String lastOutcome, Map<String, String> budgetUsed) {
    }
}
