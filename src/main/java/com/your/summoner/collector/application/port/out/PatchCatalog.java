package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.domain.model.PatchVersion;

import java.util.List;

public interface PatchCatalog {
    /** Patch atual e anterior (mais novo primeiro). Vazio enquanto nunca se soube o patch: a coleta espera. */
    List<PatchVersion> currentAndPrevious();
}
