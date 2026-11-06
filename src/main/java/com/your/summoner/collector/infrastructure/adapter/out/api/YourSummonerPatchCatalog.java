package com.your.summoner.collector.infrastructure.adapter.out.api;

import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.domain.model.PatchVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Patch atual e anterior pela API principal; cache de 1 h e, se a API falhar, o último valor bom. */
@Component
public class YourSummonerPatchCatalog implements PatchCatalog {

    private static final Logger log = LoggerFactory.getLogger(YourSummonerPatchCatalog.class);
    static final Duration TTL = Duration.ofHours(1);

    private final YourSummonerPatchApi api;
    private final Clock clock;
    private List<PatchVersion> lastGood = List.of();
    private Instant fetchedAt;

    public YourSummonerPatchCatalog(YourSummonerPatchApi api, Clock clock) {
        this.api = api;
        this.clock = clock;
    }

    @Override
    public synchronized List<PatchVersion> currentAndPrevious() {
        Instant now = clock.instant();
        if (fetchedAt != null && Duration.between(fetchedAt, now).compareTo(TTL) < 0) {
            return lastGood;
        }
        try {
            List<PatchVersion> fresh = api.patches().stream()
                    .map(p -> PatchVersion.parse(p.version()))
                    .flatMap(java.util.Optional::stream)
                    .distinct()
                    .sorted(Collections.reverseOrder())
                    .limit(2)
                    .toList();
            if (!fresh.isEmpty()) {
                lastGood = fresh;
                fetchedAt = now;
            } else {
                log.warn("Catálogo de patches veio vazio: mantendo o último valor bom");
            }
        } catch (RuntimeException e) {
            log.warn("Catálogo de patches indisponível ({}): mantendo o último valor bom", e.getClass().getSimpleName());
        }
        return lastGood;
    }
}
