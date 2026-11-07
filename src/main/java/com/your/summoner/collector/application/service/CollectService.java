package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.CollectorState;
import com.your.summoner.collector.application.port.out.MatchSource;
import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.MatchCap;
import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.QueuedPlayer;
import com.your.summoner.collector.application.port.out.RequestBudget;
import com.your.summoner.collector.application.port.out.RouteKey;
import com.your.summoner.collector.application.port.out.SeenMatches;
import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.exception.ExternalAuthenticationException;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Region;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Coleta um jogador da fila por chamada. A partida só é gravada inteira, no fim, e só se a coleta não pausou. */
@Service
public class CollectService {

    private static final int RANKED_SOLO = 420;
    // shortcut: janela fixa de 28 dias em vez do início real do patch anterior; trocar se o catálogo passar a dar datas.
    private static final Duration LOOKBACK = Duration.ofDays(28);
    private static final Duration CAP_COUNT_TTL = Duration.ofSeconds(60);

    private record CapKey(String patch, Region region) { }

    private record CachedCount(long value, Instant readAt) { }

    /** Sinal interno de que a chave foi recusada no meio do caminho. */
    private static final class Paused extends RuntimeException {
        Paused() {
            super(null, null, false, false);
        }
    }

    private final PlayerQueue queue;
    private final MatchSource source;
    private final SeenMatches seen;
    private final MatchStore store;
    private final RequestBudget budget;
    private final PatchCatalog patches;
    private final MatchReducer reducer;
    private final CollectorState state;
    private final Clock clock;
    private final int maxMatchesPerPatch;
    private final Map<CapKey, CachedCount> patchCounts = new ConcurrentHashMap<>();

    public CollectService(PlayerQueue queue, MatchSource source, SeenMatches seen, MatchStore store,
                          RequestBudget budget, PatchCatalog patches, MatchReducer reducer, CollectorState state,
                          Clock clock, MatchCap cap) {
        this.queue = queue;
        this.source = source;
        this.seen = seen;
        this.store = store;
        this.budget = budget;
        this.patches = patches;
        this.reducer = reducer;
        this.state = state;
        this.clock = clock;
        this.maxMatchesPerPatch = cap.maxMatchesPerPatch();
    }

    public CollectOutcome collectOne(Region region) {
        List<PatchVersion> accepted = patches.currentAndPrevious();
        if (accepted.isEmpty()) {
            return CollectOutcome.IDLE; // sem saber o patch, não grava nada
        }
        if (maxMatchesPerPatch > 0 && accepted.stream().allMatch(patch -> capped(patch, region))) {
            return CollectOutcome.CAPPED;
        }
        Optional<QueuedPlayer> next = queue.poll(region);
        if (next.isEmpty()) {
            return CollectOutcome.IDLE;
        }
        QueuedPlayer player = next.get();
        try {
            return collect(region, player, accepted);
        } catch (Paused | ExternalAuthenticationException e) {
            queue.offer(player.platform(), player.tier(), List.of(player.puuid()));
            return CollectOutcome.IDLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            queue.offer(player.platform(), player.tier(), List.of(player.puuid()));
            return CollectOutcome.IDLE;
        }
    }

    private CollectOutcome collect(Region region, QueuedPlayer player, List<PatchVersion> accepted)
            throws InterruptedException {
        acquire(region, "match-ids");
        List<String> ids = source.rankedMatchIds(region, player.puuid(), Instant.now(clock).minus(LOOKBACK));
        checkPause();
        CollectOutcome result = CollectOutcome.NOT_FOUND;
        boolean first = true;
        for (String id : ids) {
            CollectOutcome o = collectMatch(region, player, id, accepted);
            result = first || o.compareTo(result) < 0 ? o : result;
            first = false;
        }
        return result;
    }

    private CollectOutcome collectMatch(Region region, QueuedPlayer player, String id, List<PatchVersion> accepted)
            throws InterruptedException {
        if (!seen.markIfNew(id)) {
            return CollectOutcome.DUPLICATE;
        }
        try {
            return download(region, player, id, accepted);
        } catch (RuntimeException | InterruptedException e) {
            seen.forget(id); // não foi tratada: a próxima passada precisa poder baixar de novo
            throw e;
        }
    }

    private CollectOutcome download(Region region, QueuedPlayer player, String id, List<PatchVersion> accepted)
            throws InterruptedException {
        acquire(region, "match");
        Optional<RiotMatch> match = source.match(region, id);
        checkPause();
        if (match.isEmpty()) {
            return CollectOutcome.NOT_FOUND;
        }
        RiotMatch.Info info = match.get().info();
        if (info.queueId() != RANKED_SOLO) {
            return CollectOutcome.DISCARDED_QUEUE;
        }
        Optional<PatchVersion> patch = PatchVersion.fromGameVersion(info.gameVersion()).filter(accepted::contains);
        if (patch.isEmpty()) {
            return CollectOutcome.DISCARDED_PATCH;
        }
        if (capped(patch.get(), region)) {
            return CollectOutcome.CAPPED;
        }
        acquire(region, "timeline");
        Optional<RiotTimeline> timeline = source.timeline(region, id);
        checkPause();
        if (timeline.isEmpty()) {
            return CollectOutcome.NOT_FOUND;
        }
        CollectedMatch reduced;
        try {
            reduced = reducer.reduce(match.get(), timeline.get(), player.platform(), player.tier(), Instant.now(clock));
        } catch (IllegalArgumentException e) {
            return CollectOutcome.DISCARDED_PATCH;
        }
        if (!store.saveIfAbsent(reduced)) {
            return CollectOutcome.DUPLICATE;
        }
        incrementCachedCount(reduced.patch(), region);
        return CollectOutcome.SAVED;
    }

    private void acquire(Region region, String method) throws InterruptedException {
        budget.acquire(new RouteKey(region.host(), method));
    }

    private void checkPause() {
        if (state.paused()) {
            throw new Paused();
        }
    }

    private boolean capped(PatchVersion patch, Region region) {
        if (maxMatchesPerPatch == 0) {
            return false;
        }
        CapKey key = new CapKey(patch.toString(), region);
        Instant now = Instant.now(clock);
        CachedCount count = patchCounts.compute(key, (ignored, current) ->
                current != null && current.readAt().plus(CAP_COUNT_TTL).isAfter(now)
                        ? current : new CachedCount(store.countByPatchAndRegion(key.patch(), key.region()), now));
        return count.value() >= maxMatchesPerPatch;
    }

    private void incrementCachedCount(String patch, Region region) {
        if (maxMatchesPerPatch > 0) {
            CapKey key = new CapKey(patch, region);
            patchCounts.computeIfPresent(key, (ignored, count) -> new CachedCount(count.value() + 1, count.readAt()));
        }
    }
}
