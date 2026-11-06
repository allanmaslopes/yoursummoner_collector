package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.CollectedMatch.EventType;
import com.your.summoner.collector.domain.model.CollectedMatch.TimelineEvent;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchReducerTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build(); // como o do Spring Boot
    private static RiotMatch match;
    private static RiotTimeline timeline;
    private final MatchReducer reducer = new MatchReducer();

    @BeforeAll
    static void load() throws Exception {
        match = read("match.json", RiotMatch.class);
        timeline = read("timeline.json", RiotTimeline.class);
    }

    private static <T> T read(String name, Class<T> type) throws Exception {
        try (InputStream in = MatchReducerTest.class.getResourceAsStream("/riot/" + name)) {
            return JSON.readValue(in, type);
        }
    }

    private CollectedMatch reduce() {
        return reducer.reduce(match, timeline, Platform.BR1, Tier.MASTER, NOW);
    }

    private static TimelineEvent event(CollectedMatch c, EventType type, int slot, int t) {
        return c.events().stream().filter(e -> e.type() == type && e.slot() == slot && e.t() == t).findFirst()
                .orElseThrow();
    }

    private static RiotTimeline timelineOf(RiotTimeline.Frame... frames) {
        return new RiotTimeline(new RiotTimeline.Info(60000, List.of(frames)));
    }

    @Test
    void reducesParticipantsAndWinner() {
        CollectedMatch c = reduce();
        assertThat(c.schemaVersion()).isEqualTo(CollectedMatch.SCHEMA_VERSION);
        assertThat(c.matchId()).isEqualTo("BR1_3000000001");
        assertThat(c.region()).isEqualTo(Region.AMERICAS);
        assertThat(c.patch()).isEqualTo("16.22");
        assertThat(c.gameVersion()).isEqualTo("16.22.712.1234");
        assertThat(c.queueId()).isEqualTo(420);
        assertThat(c.gameDurationSec()).isEqualTo(1834);
        assertThat(c.winningTeam()).isEqualTo(100);
        assertThat(c.tier()).isEqualTo(Tier.MASTER);
        assertThat(c.collectedAt()).isEqualTo(NOW);
        assertThat(c.participants()).hasSize(10);
        CollectedMatch.Participant p = c.participants().get(0);
        assertThat(p.slot()).isEqualTo(1);
        assertThat(p.team()).isEqualTo(100);
        assertThat(p.championId()).isEqualTo(266);
        assertThat(p.position()).isEqualTo("TOP");
        assertThat(p.win()).isTrue();
        assertThat(p.cs()).isEqualTo(124);
        assertThat(p.summonerSpells()).containsExactly(4, 14);
        assertThat(p.runes().primaryStyle()).isEqualTo(8000);
        assertThat(p.runes().subStyle()).isEqualTo(8100);
        assertThat(p.runes().perks()).containsExactly(8005, 8006, 8007, 8008, 9111, 9105);
    }

    @Test
    void itemsWithoutZeros() {
        assertThat(reduce().participants().get(0).items()).containsExactly(1055, 3340);
    }

    @Test
    void buyHasSituationFromPreviousFrame() {
        CollectedMatch.Situation s = event(reduce(), EventType.BUY, 3, 61).situation();
        assertThat(s.slots()).hasSize(10);
        assertThat(s.slots().get(0)).isEqualTo(new CollectedMatch.SlotState(1, 601, 751, 3, 400, 8));
    }

    @Test
    void skillEventsHaveSlot() {
        TimelineEvent e = event(reduce(), EventType.SKILL, 2, 80);
        assertThat(e.skillSlot()).isNotNull();
        assertThat(e.itemId()).isNull();
        assertThat(e.situation()).isNotNull();
    }

    @Test
    void sellAndUndoHaveNoSituation() {
        CollectedMatch c = reduce();
        assertThat(event(c, EventType.SELL, 3, 62).situation()).isNull();
        assertThat(event(c, EventType.UNDO, 3, 63).situation()).isNull();
        assertThat(c.events()).extracting(TimelineEvent::t).isSorted();
    }

    @Test
    void killsCountedUntilEvent() {
        CollectedMatch c = reduce();
        assertThat(event(c, EventType.BUY, 3, 61).situation().redKills()).isZero();
        CollectedMatch.Situation afterRedKill = event(c, EventType.SKILL, 2, 80).situation();
        assertThat(afterRedKill.redKills()).isEqualTo(1);
        assertThat(afterRedKill.blueKills()).isZero();
        assertThat(event(c, EventType.BUY, 1, 121).situation().blueKills()).isZero();
        CollectedMatch.Situation afterBoth = event(c, EventType.SKILL, 6, 140).situation();
        assertThat(afterBoth.blueKills()).isEqualTo(1);
        assertThat(afterBoth.redKills()).isEqualTo(1);
    }

    @Test
    void eventBeforeFirstFrameUsesFrameZero() {
        var pf = Map.of(1, new RiotTimeline.ParticipantFrame(500, 500, 1, 0, 0, 0));
        // primeiro frame em 60 s; evento em 0,5 s cai no frame 0; evento após o último frame usa o último
        var tl = timelineOf(new RiotTimeline.Frame(60000, pf, List.of(
                new RiotTimeline.Event("ITEM_PURCHASED", 500, 1, 1055, 0, 0),
                new RiotTimeline.Event("ITEM_PURCHASED", 900000, 1, 1055, 0, 0))));
        CollectedMatch c = reducer.reduce(match, tl, Platform.BR1, Tier.MASTER, NOW);
        assertThat(c.events()).hasSize(2);
        assertThat(c.events()).allSatisfy(e -> assertThat(e.situation().slots().get(0).gold()).isEqualTo(500));
    }

    @Test
    void leaverHasZeroedState() {
        var tl = timelineOf(new RiotTimeline.Frame(0,
                Map.of(1, new RiotTimeline.ParticipantFrame(1, 2, 3, 4, 5, 6)),
                List.of(new RiotTimeline.Event("ITEM_PURCHASED", 1000, 1, 1055, 0, 0))));
        CollectedMatch.Situation s = reducer.reduce(match, tl, Platform.BR1, Tier.MASTER, NOW)
                .events().get(0).situation();
        assertThat(s.slots().get(0)).isEqualTo(new CollectedMatch.SlotState(1, 1, 2, 3, 4, 11));
        assertThat(s.slots().get(2)).isEqualTo(new CollectedMatch.SlotState(3, 0, 0, 0, 0, 0));
    }

    @Test
    void gameStartTruncatedToHour() {
        assertThat(reduce().gameStartHour())
                .isEqualTo(Instant.ofEpochMilli(1792160000000L).truncatedTo(ChronoUnit.HOURS));
    }

    @Test
    void reducedMatchHasNoPlayerIdentifiers() throws Exception {
        String json = JSON.writeValueAsString(reduce());
        assertThat(json).doesNotContain("puuid", "riotId", "summonerName", "summonerId", "gameName", "tagLine");
        List<String> values = new ArrayList<>();
        try (InputStream in = MatchReducerTest.class.getResourceAsStream("/riot/match.json")) {
            for (JsonNode n : JSON.readTree(in).path("info").path("participants")) {
                for (String f : List.of("puuid", "riotIdGameName", "summonerName", "summonerId")) {
                    values.add(n.path(f).asString());
                }
            }
        }
        assertThat(values).hasSize(40).allSatisfy(v -> assertThat(v).isNotBlank());
        values.forEach(v -> assertThat(json).doesNotContain(v));
    }
}
