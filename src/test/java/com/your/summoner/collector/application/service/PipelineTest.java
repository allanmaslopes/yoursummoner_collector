package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.LeagueSource;
import com.your.summoner.collector.application.port.out.MatchSource;
import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.QueuedPlayer;
import com.your.summoner.collector.application.port.out.RequestBudget;
import com.your.summoner.collector.application.port.out.RouteKey;
import com.your.summoner.collector.application.port.out.SeenMatches;
import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineTest {

    private static final List<PatchVersion> PATCHES = List.of(new PatchVersion(16, 22), new PatchVersion(16, 21));
    private static final QueuedPlayer PLAYER = new QueuedPlayer(Platform.BR1, Tier.CHALLENGER, "puuid-abc");

    private final PlayerQueue queue = mock(PlayerQueue.class);
    private final MatchSource source = mock(MatchSource.class);
    private final MatchStore store = mock(MatchStore.class);
    private final MatchReducer reducer = mock(MatchReducer.class);
    private final PatchCatalog catalog = () -> PATCHES;
    private final Set<String> seenIds = new HashSet<>();
    private final SeenMatches seen = new SeenMatches() {
        public boolean markIfNew(String id) {
            return seenIds.add(id);
        }

        public void forget(String id) {
            seenIds.remove(id);
        }
    };
    private final List<RouteKey> acquired = new ArrayList<>();
    private final RequestBudget budget = acquired::add;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    private CollectService service;

    @BeforeEach
    void setUp() {
        service = new CollectService(queue, source, seen, store, budget, catalog, reducer, paused::get, clock);
        when(queue.poll(Region.AMERICAS)).thenReturn(Optional.of(PLAYER));
        when(source.rankedMatchIds(any(), anyString(), any())).thenReturn(List.of("BR1_1"));
        when(source.timeline(any(), anyString())).thenReturn(Optional.of(new RiotTimeline(
                new RiotTimeline.Info(60000, List.of()))));
        when(store.saveIfAbsent(any())).thenReturn(true);
    }

    private static RiotMatch match(String id, int queueId, String gameVersion) {
        return new RiotMatch(new RiotMatch.Metadata(id),
                new RiotMatch.Info(gameVersion, queueId, 1800, 0, "BR1", List.of(), List.of()));
    }

    private void matchIs(String id, int queueId, String version) {
        when(source.match(Region.AMERICAS, id)).thenReturn(Optional.of(match(id, queueId, version)));
    }

    private CollectedMatch reduced() {
        CollectedMatch m = new CollectedMatch(1, "BR1_1", Platform.BR1, Region.AMERICAS, "16.22", "16.22.1", 420, 1800,
                Instant.EPOCH, 100, Tier.CHALLENGER, List.of(), List.of(), Instant.EPOCH);
        when(reducer.reduce(any(), any(), any(), any(), any())).thenReturn(m);
        return m;
    }

    @Test
    void savesNewRankedMatchOfRecentPatch() {
        matchIs("BR1_1", 420, "16.22.700.1");
        reduced();

        assertThat(service.collectOne(Region.AMERICAS)).isEqualTo(CollectOutcome.SAVED);

        verify(reducer).reduce(any(), any(), org.mockito.ArgumentMatchers.eq(Platform.BR1),
                org.mockito.ArgumentMatchers.eq(Tier.CHALLENGER), any());
        verify(store).saveIfAbsent(any());
        verify(source).rankedMatchIds(Region.AMERICAS, "puuid-abc", Instant.parse("2026-10-09T12:00:00Z"));
    }

    @Test
    void oldPatchAndOtherQueueAreDiscardedOnce() {
        when(source.rankedMatchIds(any(), anyString(), any())).thenReturn(List.of("BR1_1", "BR1_2"));
        matchIs("BR1_1", 420, "16.10.1.1");
        matchIs("BR1_2", 450, "16.22.1.1");

        assertThat(service.collectOne(Region.AMERICAS)).isEqualTo(CollectOutcome.DISCARDED_PATCH);
        assertThat(service.collectOne(Region.AMERICAS)).isEqualTo(CollectOutcome.DUPLICATE);

        verify(source, times(1)).match(Region.AMERICAS, "BR1_1");
        verify(source, times(1)).match(Region.AMERICAS, "BR1_2");
        verify(source, never()).timeline(any(), anyString());
        verify(store, never()).saveIfAbsent(any());
    }

    @Test
    void duplicateIdIsSkippedWithoutDownload() {
        seenIds.add("BR1_1");

        assertThat(service.collectOne(Region.AMERICAS)).isEqualTo(CollectOutcome.DUPLICATE);

        verify(source, never()).match(any(), anyString());
    }

    @Test
    void everyRiotCallAcquiresBudget() {
        matchIs("BR1_1", 420, "16.22.1.1");
        reduced();

        service.collectOne(Region.AMERICAS);

        assertThat(acquired).containsExactly(new RouteKey("americas", "match-ids"),
                new RouteKey("americas", "match"), new RouteKey("americas", "timeline"));
    }

    @Test
    void pauseMidwayReturnsPlayerToQueue() {
        matchIs("BR1_1", 420, "16.22.1.1");
        reduced();
        when(source.timeline(any(), anyString())).thenAnswer(i -> {
            paused.set(true); // a chave foi recusada durante esta chamada
            return Optional.of(new RiotTimeline(new RiotTimeline.Info(60000, List.of())));
        });

        assertThat(service.collectOne(Region.AMERICAS)).isEqualTo(CollectOutcome.IDLE);

        verify(queue).offer(Platform.BR1, Tier.CHALLENGER, List.of("puuid-abc"));
        verify(store, never()).saveIfAbsent(any());
        assertThat(seenIds).isEmpty(); // a partida volta a poder ser baixada
    }

    @Test
    void seedOffersAllTiers() throws Exception {
        LeagueSource league = mock(LeagueSource.class);
        for (Tier t : Tier.values()) {
            when(league.masterPlusPuuids(Platform.KR, t)).thenReturn(List.of("p-" + t));
        }

        new SeedService(league, queue, budget).seed(Platform.KR);

        for (Tier t : Tier.values()) {
            verify(queue).offer(Platform.KR, t, List.of("p-" + t));
        }
        assertThat(acquired).containsOnly(new RouteKey("kr", "league")).hasSize(3);
    }

    @Test
    void retentionKeepsThreePatches() {
        new RetentionService(store, catalog, 3).run();

        verify(store).deleteOlderThan(new PatchVersion(16, 20));
    }
}
