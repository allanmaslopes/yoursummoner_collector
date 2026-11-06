package com.your.summoner.collector.infrastructure.adapter.out.api;

import com.your.summoner.collector.domain.model.PatchVersion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YourSummonerPatchCatalogTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-06T12:00:00Z"));
    private final Clock clock = new Clock() {
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(java.time.ZoneId z) {
            return this;
        }

        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void patchCatalogKeepsLastGoodValue() {
        YourSummonerPatchApi api = mock(YourSummonerPatchApi.class);
        YourSummonerPatchCatalog catalog = new YourSummonerPatchCatalog(api, clock);
        org.mockito.Mockito.doThrow(new IllegalStateException("down")).when(api).patches();
        assertThat(catalog.currentAndPrevious()).isEmpty(); // nunca soube o patch

        org.mockito.Mockito.doReturn(List.of(new YourSummonerPatchApi.PatchDto("16.22.1", true),
                new YourSummonerPatchApi.PatchDto("16.22.0", true), new YourSummonerPatchApi.PatchDto("16.21.2", true),
                new YourSummonerPatchApi.PatchDto("16.20.1", false))).when(api).patches();
        assertThat(catalog.currentAndPrevious()).containsExactly(new PatchVersion(16, 22), new PatchVersion(16, 21));

        now.set(now.get().plus(Duration.ofHours(2)));
        org.mockito.Mockito.doThrow(new IllegalStateException("down")).when(api).patches();
        assertThat(catalog.currentAndPrevious()).containsExactly(new PatchVersion(16, 22), new PatchVersion(16, 21));
    }
}
