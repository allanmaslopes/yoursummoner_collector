package com.your.summoner.collector.infrastructure.adapter.in.rest;

import com.your.summoner.collector.infrastructure.ratelimit.CollectorPause;
import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.application.service.CollectOutcome;
import com.your.summoner.collector.application.service.CollectService;
import com.your.summoner.collector.application.service.SeedService;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.PatchVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"collector.enabled=true", "collector.platforms=BR1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MatchStore store;
    @MockitoBean
    private PlayerQueue queue;
    @MockitoBean
    private CollectorPause state; // é o CollectorState do contexto
    @MockitoBean
    private CollectService collect; // os workers só recebem IDLE
    @MockitoBean
    private SeedService seed;
    @MockitoBean
    private PatchCatalog patches;

    @Test
    void statusShowsPerRegionCountsAndState() throws Exception {
        when(collect.collectOne(any())).thenReturn(CollectOutcome.IDLE);
        when(queue.pending(Region.AMERICAS)).thenReturn(42);
        when(store.countSince(any(), eq(Region.AMERICAS))).thenReturn(7L, 70L);
        when(patches.currentAndPrevious()).thenReturn(java.util.List.of(new PatchVersion(16, 22), new PatchVersion(16, 21)));
        when(store.countByPatchAndRegion("16.22", Region.AMERICAS)).thenReturn(15_000L);
        when(store.countByPatchAndRegion("16.21", Region.AMERICAS)).thenReturn(4L);
        when(state.paused()).thenReturn(true);

        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED_KEY"))
                .andExpect(jsonPath("$.regions.length()").value(1))
                .andExpect(jsonPath("$.regions[0].region").value("AMERICAS"))
                .andExpect(jsonPath("$.regions[0].pendingPlayers").value(42))
                .andExpect(jsonPath("$.regions[0].matchesLastHour").value(7))
                .andExpect(jsonPath("$.regions[0].matchesLastDay").value(70))
                .andExpect(jsonPath("$.regions[0].capped").value(false))
                .andExpect(jsonPath("$.regions[0].matchesByPatch['16.22']").value(15000))
                .andExpect(jsonPath("$.regions[0].matchesByPatch['16.21']").value(4))
                .andExpect(jsonPath("$.regions[0].budgetUsed").isMap());
    }
}
