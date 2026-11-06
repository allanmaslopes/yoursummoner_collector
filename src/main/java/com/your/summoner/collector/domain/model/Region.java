package com.your.summoner.collector.domain.model;

import java.util.Locale;

/** Roteamento regional da Riot (account-v1, match-v5). */
public enum Region {
    AMERICAS,
    EUROPE,
    ASIA,
    SEA;

    /** Subdomínio usado no host {@code https://{host}.api.riotgames.com}. */
    public String host() {
        return name().toLowerCase(Locale.ROOT);
    }
}
