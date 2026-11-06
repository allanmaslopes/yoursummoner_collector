package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Partida anônima. patchMajor/patchMinor numéricos servem à limpeza por patch. Records do domínio viram subdocumentos. */
@Document("matches")
public record MatchDocument(@Id String id, int schemaVersion, String matchId, Platform platform, Region region,
                            String patch, int patchMajor, int patchMinor, String gameVersion, int queueId,
                            int gameDurationSec, Instant gameStartHour, int winningTeam, Tier tier,
                            List<CollectedMatch.Participant> participants, List<CollectedMatch.TimelineEvent> events,
                            Instant collectedAt) {

    static MatchDocument from(CollectedMatch m) {
        PatchVersion pv = PatchVersion.parse(m.patch())
                .orElseThrow(() -> new IllegalArgumentException("invalid patch: " + m.patch()));
        return new MatchDocument(m.matchId(), m.schemaVersion(), m.matchId(), m.platform(), m.region(), m.patch(),
                pv.major(), pv.minor(), m.gameVersion(), m.queueId(), m.gameDurationSec(), m.gameStartHour(),
                m.winningTeam(), m.tier(), m.participants(), m.events(), m.collectedAt());
    }
}
