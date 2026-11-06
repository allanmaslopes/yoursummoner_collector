package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.domain.model.PatchVersion;

import java.util.List;

/** Apaga partidas de patches antigos, mantendo os {@code keep} mais novos. Criado como bean na infraestrutura. */
public class RetentionService {

    private final MatchStore store;
    private final PatchCatalog patches;
    private final int keep;

    public RetentionService(MatchStore store, PatchCatalog patches, int keep) {
        this.store = store;
        this.patches = patches;
        this.keep = Math.max(1, keep);
    }

    /** Devolve quantas partidas apagou. */
    public long run() {
        List<PatchVersion> current = patches.currentAndPrevious();
        if (current.isEmpty()) {
            return 0;
        }
        PatchVersion newest = current.get(0);
        // shortcut: não sabemos quantos patches tem a major anterior; na virada de major não apaga nada
        // até o minor passar de keep-1, em vez de arriscar apagar patch que ainda vale.
        int oldestMinor = newest.minor() - (keep - 1);
        return oldestMinor < 0 ? 0 : store.deleteOlderThan(new PatchVersion(newest.major(), oldestMinor));
    }
}
