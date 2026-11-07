package com.your.summoner.collector.application.port.out;

/** Configura o teto de partidas guardadas por patch e regiÃ£o. */
public interface MatchCap {
    int maxMatchesPerPatch();
}
