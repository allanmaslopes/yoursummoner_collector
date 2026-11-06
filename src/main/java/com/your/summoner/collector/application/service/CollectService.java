package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.CollectorState;
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
import com.your.summoner.collector.domain.exception.ExternalAuthenticationException;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Region;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Coleta um jogador da fila por chamada. A partida só é gravada inteira, no fim, e só se a coleta não pausou. */
@Service
public class CollectService {

    private static final int RANKED_SOLO = 420;
    // shortcut: janela fixa de 28 dias em vez do início real do patch anterior; trocar se o catálogo passar a dar datas.
    private static final Duration LOOKBACK = Duration.ofDays(28);

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

    public CollectService(PlayerQueue queue, MatchSource source, SeenMatches seen, MatchStore store,
                          RequestBudget budget, PatchCatalog patches, MatchReducer reducer, CollectorState state,
                          Clock clock) {
        this.queue = queue;
        this.source = source;
        this.seen = seen;
        this.store = store;
        this.budget = budget;
        this.patches = patches;
        this.reducer = reducer;
        this.state = state;
        this.clock = clock;
    }

    public CollectOutcome collectOne(Region region) {
        List<PatchVersion> accepted = patches.currentAndPrevious();
        if (accepted.isEmpty()) {
            return CollectOutcome.IDLE; // sem saber o patch, não grava nada
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
        if (PatchVersion.fromGameVersion(info.gameVersion()).filter(accepted::contains).isEmpty()) {
            return CollectOutcome.DISCARDED_PATCH;
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
        return store.saveIfAbsent(reduced) ? CollectOutcome.SAVED : CollectOutcome.DUPLICATE;
    }

    private void acquire(Region region, String method) throws InterruptedException {
        budget.acquire(new RouteKey(region.host(), method));
    }

    private void checkPause() {
        if (state.paused()) {
            throw new Paused();
        }
    }
}
