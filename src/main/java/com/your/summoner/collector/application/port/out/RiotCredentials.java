package com.your.summoner.collector.application.port.out;

import java.util.Optional;

/** Chave atual da Riot, resolvida a cada requisição. */
public interface RiotCredentials {
    Optional<String> riotApiKey();
}
