package com.your.summoner.collector.infrastructure.adapter.out.riot;

import java.util.Map;

/**
 * Vê cada resposta da Riot (sucesso ou erro). {@code host} é "br1" ou "americas"; {@code method} é
 * "league", "match-ids", "match" ou "timeline"; {@code headers} traz só X-App-Rate-Limit,
 * X-App-Rate-Limit-Count, X-Method-Rate-Limit, X-Method-Rate-Limit-Count e Retry-After, quando presentes.
 */
public interface RateLimitObserver {
    void onResponse(String host, String method, int status, Map<String, String> headers);
}
