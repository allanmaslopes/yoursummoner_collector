package com.your.summoner.collector.domain.model;

import com.your.summoner.collector.domain.exception.InvalidInputException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Plataformas (servidores) do League of Legends e seus roteamentos regionais. */
public enum Platform {
    BR1(Region.AMERICAS),
    LA1(Region.AMERICAS),
    LA2(Region.AMERICAS),
    NA1(Region.AMERICAS),
    EUW1(Region.EUROPE),
    EUN1(Region.EUROPE),
    TR1(Region.EUROPE),
    RU(Region.EUROPE),
    ME1(Region.EUROPE),
    KR(Region.ASIA),
    JP1(Region.ASIA),
    OC1(Region.SEA),
    SG2(Region.SEA),
    TW2(Region.SEA),
    VN2(Region.SEA);

    private final Region matchRegion;

    Platform(Region matchRegion) {
        this.matchRegion = matchRegion;
    }

    /** Região usada pela match-v5. */
    public Region matchRegion() {
        return matchRegion;
    }

    /** Região usada pela account-v1, que não atende em SEA. */
    public Region accountRegion() {
        return matchRegion == Region.SEA ? Region.ASIA : matchRegion;
    }

    /** Subdomínio usado no host {@code https://{host}.api.riotgames.com}. */
    public String host() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Platform from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Platform is empty or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown platform '%s'. Valid values: %s".formatted(value,
                    Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))));
        }
    }

    /** Ids de partida carregam a plataforma como prefixo: {@code BR1_3012345678}. */
    public static Platform fromMatchId(String matchId) {
        if (matchId == null || !matchId.trim().matches("[A-Za-z0-9]+_\\d+")) {
            throw new InvalidInputException("Invalid match id '%s'. Expected format PLATFORM_NUMBER".formatted(matchId));
        }
        return from(matchId.trim().substring(0, matchId.trim().indexOf('_')));
    }
}
