package com.your.summoner.collector.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Partida reduzida e anônima: nenhum identificador de jogador (PUUID, Riot ID, nome). Contrato com o processor.
 */
public record CollectedMatch(int schemaVersion, String matchId, Platform platform, Region region, String patch,
                             String gameVersion, int queueId, int gameDurationSec, Instant gameStartHour,
                             int winningTeam, Tier tier, List<Participant> participants, List<Frame> frames,
                             List<TimelineEvent> events, Instant collectedAt) {

    public static final int SCHEMA_VERSION = 2;

    public CollectedMatch {
        Objects.requireNonNull(matchId);
        participants = copy(participants);
        frames = copy(frames);
        events = copy(events);
    }

    public record Participant(int slot, int team, int championId, String position, boolean win, List<Integer> items,
                              List<Integer> summonerSpells, Runes runes, int kills, int deaths, int assists,
                              int physicalDamage, int magicDamage, int trueDamage, int damageTaken,
                              int goldEarned, int cs, int champLevel, int visionScore,
                              int timeCCingOthers, int totalHeal) {
        public Participant {
            items = copy(items);
            summonerSpells = copy(summonerSpells);
        }
    }

    public record Runes(int primaryStyle, int subStyle, List<Integer> perks, StatPerks statPerks) {
        public Runes {
            perks = copy(perks);
        }

        public Runes(int primaryStyle, int subStyle, List<Integer> perks) {
            this(primaryStyle, subStyle, perks, null);
        }
    }

    public record StatPerks(int offense, int flex, int defense) {}

    public enum EventType { BUY, SELL, UNDO, SKILL }

    /** {@code frame} referencia {@link CollectedMatch#frames()} só em BUY e SKILL. */
    public record TimelineEvent(int t, int slot, EventType type, Integer itemId, Integer skillSlot,
                                Integer frame) {
    }

    public record Frame(int minute, List<SlotState> slots, int blueKills, int redKills) {
        public Frame {
            slots = copy(slots);
        }
    }

    public record SlotState(int slot, int gold, int totalGold, int level, int xp, int cs) {
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
