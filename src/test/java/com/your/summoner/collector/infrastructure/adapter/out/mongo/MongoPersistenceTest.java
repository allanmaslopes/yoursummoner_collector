package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.QueuedPlayer;
import com.your.summoner.collector.application.port.out.SeenMatches;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.CollectedMatch.EventType;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MongoPersistenceTest {
    @Autowired MatchStore matches;
    @Autowired SeenMatches seen;
    @Autowired PlayerQueue queue;
    @Autowired MongoTemplate mongo;

    @BeforeEach
    void clean() {
        for (String c : List.of("matches", "seen_matches", "player_queue")) {
            mongo.remove(new Query(), c);
        }
    }

    private static CollectedMatch match(String id, String patch, Region region, Instant collectedAt) {
        var frame = new CollectedMatch.Frame(1,
                List.of(new CollectedMatch.SlotState(1, 100, 500, 3, 800, 20)), 1, 2);
        var participant = new CollectedMatch.Participant(1, 100, 266, "TOP", true, List.of(3006), List.of(4, 12),
                new CollectedMatch.Runes(8000, 8100, List.of(8005), new CollectedMatch.StatPerks(5005, 5008, 5011)),
                1, 2, 3, 100, 200, 300, 400, 5000, 150, 11, 20);
        var event = new CollectedMatch.TimelineEvent(60, 1, EventType.BUY, 3006, null, 0);
        return new CollectedMatch(2, id, Platform.BR1, region, patch, patch + ".1.1", 420, 1800,
                Instant.parse("2026-11-06T10:00:00Z"), 100, Tier.CHALLENGER, List.of(participant), List.of(frame),
                List.of(event), collectedAt);
    }

    @Test
    void saveIfAbsentIsIdempotent() {
        var m = match("BR1_1", "16.22", Region.AMERICAS, Instant.now());
        assertThat(matches.saveIfAbsent(m)).isTrue();
        assertThat(matches.saveIfAbsent(m)).isFalse();
        assertThat(mongo.getCollection("matches").countDocuments()).isEqualTo(1);
    }

    @Test
    void concurrentSaveOfSameMatchKeepsOne() throws Exception {
        var m = match("BR1_2", "16.22", Region.AMERICAS, Instant.now());
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                results.add(pool.submit(() -> matches.saveIfAbsent(m)));
            }
        }
        long trues = 0;
        for (var f : results) {
            if (f.get()) {
                trues++;
            }
        }
        assertThat(trues).isEqualTo(1);
        assertThat(mongo.getCollection("matches").countDocuments()).isEqualTo(1);
    }

    @Test
    void deleteOlderThanKeepsRecentPatches() {
        Instant now = Instant.now();
        matches.saveIfAbsent(match("a", "16.9", Region.AMERICAS, now));
        matches.saveIfAbsent(match("b", "15.24", Region.AMERICAS, now));
        matches.saveIfAbsent(match("c", "16.22", Region.AMERICAS, now));
        matches.saveIfAbsent(match("d", "16.23", Region.EUROPE, now));
        assertThat(matches.deleteOlderThan(new PatchVersion(16, 22))).isEqualTo(2);
        assertThat(mongo.findDistinct(new Query(), "matchId", "matches", String.class)).containsExactlyInAnyOrder("c", "d");
        assertThat(matches.countSince(now.minusSeconds(60), Region.AMERICAS)).isEqualTo(1);
        assertThat(matches.countSince(now.plusSeconds(60), Region.AMERICAS)).isZero();
    }

    @Test
    void seenMatchesMarksOnce() {
        assertThat(seen.markIfNew("BR1_9")).isTrue();
        assertThat(seen.markIfNew("BR1_9")).isFalse();
        assertThat(seen.markIfNew("BR1_10")).isTrue();
    }

    @Test
    void queueDeduplicatesAndPrefersHigherTier() {
        queue.offer(Platform.BR1, Tier.MASTER, List.of("p1", "p2"));
        queue.offer(Platform.BR1, Tier.CHALLENGER, List.of("p1"));
        queue.offer(Platform.BR1, Tier.MASTER, List.of("p1", "p2"));
        queue.offer(Platform.NA1, Tier.GRANDMASTER, List.of("p3"));
        queue.offer(Platform.KR, Tier.CHALLENGER, List.of("p4"));
        assertThat(queue.pending(Region.AMERICAS)).isEqualTo(3);
        assertThat(queue.pending(Region.ASIA)).isEqualTo(1);
        assertThat(queue.poll(Region.AMERICAS)).contains(new QueuedPlayer(Platform.BR1, Tier.CHALLENGER, "p1"));
        assertThat(queue.poll(Region.AMERICAS)).contains(new QueuedPlayer(Platform.NA1, Tier.GRANDMASTER, "p3"));
        assertThat(queue.poll(Region.AMERICAS)).contains(new QueuedPlayer(Platform.BR1, Tier.MASTER, "p2"));
        assertThat(queue.poll(Region.AMERICAS)).isEmpty();
        assertThat(queue.pending(Region.AMERICAS)).isZero();
    }

    @Test
    void queueEntriesExpire() {
        IndexInfo ttl = mongo.indexOps("player_queue").getIndexInfo().stream()
                .filter(i -> i.getIndexFields().stream().anyMatch(f -> f.getKey().equals("enqueuedAt")) && i.getExpireAfter().isPresent())
                .findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter()).contains(Duration.ofSeconds(86400));
        IndexInfo seenTtl = mongo.indexOps("seen_matches").getIndexInfo().stream()
                .filter(i -> i.getExpireAfter().isPresent()).findFirst().orElseThrow();
        assertThat(seenTtl.getExpireAfter()).contains(Duration.ofDays(30));
    }

    @Test
    void storedDocumentHasNoPlayerIdentifiers() {
        matches.saveIfAbsent(match("BR1_3", "16.22", Region.AMERICAS, Instant.now()));
        Document raw = mongo.findOne(new Query(), Document.class, "matches");
        String json = raw.toJson();
        assertThat(json).contains("BR1_3");
        assertThat(json).doesNotContain("puuid", "riotId", "summonerName", "summonerId", "gameName", "tagLine");
    }

    @Test
    void storedDocumentMapsFramesAndEventReferences() {
        matches.saveIfAbsent(match("BR1_4", "16.22", Region.AMERICAS, Instant.now()));

        Document raw = mongo.findOne(new Query(), Document.class, "matches");
        List<Document> frames = raw.getList("frames", Document.class);
        List<Document> events = raw.getList("events", Document.class);

        assertThat(raw.getInteger("schemaVersion")).isEqualTo(2);
        assertThat(frames).singleElement().satisfies(frame -> {
            assertThat(frame.getInteger("minute")).isEqualTo(1);
            assertThat(frame.getInteger("blueKills")).isEqualTo(1);
            assertThat(frame.getInteger("redKills")).isEqualTo(2);
            assertThat(frame.getList("slots", Document.class)).singleElement()
                    .satisfies(slot -> assertThat(slot.getInteger("slot")).isEqualTo(1));
        });
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getInteger("frame")).isZero();
            assertThat(event).doesNotContainKey("situation");
        });
    }

    @Test
    void statPerksRoundTrip() {
        matches.saveIfAbsent(match("BR1_5", "16.22", Region.AMERICAS, Instant.now()));

        Document raw = mongo.findOne(new Query(), Document.class, "matches");

        assertThat(raw.getList("participants", Document.class)).singleElement().satisfies(participant ->
                assertThat(participant.get("runes", Document.class).get("statPerks", Document.class)
                        .getInteger("offense")).isEqualTo(5005));
    }
}
