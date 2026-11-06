package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.your.summoner.collector.application.port.out.LeagueSource;
import com.your.summoner.collector.application.port.out.MatchSource;
import com.your.summoner.collector.domain.exception.RateLimitExceededException;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import com.your.summoner.collector.support.RiotApiWireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotSourcesTest extends RiotApiWireMock {
    private static final String IDS = RIOT + "/americas/lol/match/v5/matches/by-puuid/P1/ids";
    private static final String MATCH = RIOT + "/americas/lol/match/v5/matches/BR1_3000000001";

    @Autowired LeagueSource league;
    @Autowired MatchSource matches;

    private static void json(String path, String body) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void leagueReturnsPuuids() {
        for (Tier tier : Tier.values()) {
            String name = tier.name().toLowerCase();
            json(RIOT + "/br1/lol/league/v4/" + name + "leagues/by-queue/RANKED_SOLO_5x5",
                    "{\"tier\":\"%s\",\"entries\":[{\"puuid\":\"%s-1\",\"leaguePoints\":10,\"summonerId\":\"x\"},{\"puuid\":\"%s-2\"}]}"
                            .formatted(tier, name, name));
        }

        for (Tier tier : Tier.values()) {
            String name = tier.name().toLowerCase();
            assertThat(league.masterPlusPuuids(Platform.BR1, tier)).containsExactly(name + "-1", name + "-2");
        }
    }

    @Test
    void matchIdsSendsQueueTypeAndStartTime() {
        json(IDS, "[\"BR1_1\",\"BR1_2\"]");

        var ids = matches.rankedMatchIds(Region.AMERICAS, "P1", Instant.ofEpochSecond(1790000000L));

        assertThat(ids).containsExactly("BR1_1", "BR1_2");
        wireMock.verify(getRequestedFor(urlPathEqualTo(IDS))
                .withQueryParam("queue", equalTo("420")).withQueryParam("type", equalTo("ranked"))
                .withQueryParam("startTime", equalTo("1790000000")).withQueryParam("count", equalTo("20")));
    }

    @Test
    void matchAndTimelineDecode() {
        json(MATCH, fixture("match.json"));
        json(MATCH + "/timeline", fixture("timeline.json"));

        var match = matches.match(Region.AMERICAS, "BR1_3000000001").orElseThrow();
        assertThat(match.metadata().matchId()).isEqualTo("BR1_3000000001");
        assertThat(match.info().gameVersion()).isEqualTo("16.22.712.1234");
        assertThat(match.info().queueId()).isEqualTo(420);
        assertThat(match.info().gameDuration()).isEqualTo(1834);
        assertThat(match.info().gameStartTimestamp()).isEqualTo(1792160000000L);
        assertThat(match.info().platformId()).isEqualTo("BR1");
        assertThat(match.info().teams()).extracting("teamId", "win").containsExactly(
                org.assertj.core.groups.Tuple.tuple(100, true), org.assertj.core.groups.Tuple.tuple(200, false));
        assertThat(match.info().participants()).hasSize(10);
        var jungle = match.info().participants().get(1);
        assertThat(jungle.participantId()).isEqualTo(2);
        assertThat(jungle.teamPosition()).isEqualTo("JUNGLE");
        assertThat(jungle.item6()).isEqualTo(3364);
        assertThat(jungle.summoner1Id()).isEqualTo(4);
        assertThat(jungle.neutralMinionsKilled()).isEqualTo(80);
        assertThat(jungle.perks().styles()).hasSize(2);
        assertThat(jungle.perks().styles().get(0).style()).isEqualTo(8000);
        assertThat(jungle.perks().styles().get(0).selections()).extracting("perk")
                .containsExactly(8005, 8006, 8007, 8008);

        var timeline = matches.timeline(Region.AMERICAS, "BR1_3000000001").orElseThrow();
        assertThat(timeline.info().frameInterval()).isEqualTo(60000);
        assertThat(timeline.info().frames()).hasSize(4);
        var second = timeline.info().frames().get(1);
        assertThat(second.timestamp()).isEqualTo(60000);
        assertThat(second.participantFrames()).hasSize(10);
        assertThat(second.participantFrames().get(3).totalGold()).isEqualTo(753);
        assertThat(second.events()).extracting("type").containsExactly(
                "ITEM_PURCHASED", "ITEM_SOLD", "ITEM_UNDO", "CHAMPION_KILL", "SKILL_LEVEL_UP");
        assertThat(second.events().get(3).killerId()).isEqualTo(6);
        assertThat(second.events().get(4).skillSlot()).isEqualTo(2);
        assertThat(timeline.info().frames().get(0).events().get(0).itemId()).isEqualTo(1055);
    }

    @Test
    void notFoundIsEmpty() {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404).withBody("{}")));

        assertThat(league.masterPlusPuuids(Platform.BR1, Tier.MASTER)).isEmpty();
        assertThat(matches.rankedMatchIds(Region.AMERICAS, "P1", Instant.EPOCH)).isEmpty();
        assertThat(matches.match(Region.AMERICAS, "BR1_9")).isEmpty();
        assertThat(matches.timeline(Region.AMERICAS, "BR1_9")).isEmpty();
    }

    @Test
    void requestsCarryTestKeyOnly() {
        json(MATCH, fixture("match.json"));
        json(IDS, "[]");

        matches.match(Region.AMERICAS, "BR1_3000000001");
        matches.rankedMatchIds(Region.AMERICAS, "P1", Instant.EPOCH);

        List<LoggedRequest> requests = wireMock.findAll(anyRequestedFor(anyUrl()));
        assertThat(requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.getHeader("X-Riot-Token")).isEqualTo("test-key");
            assertThat(request.getUrl()).doesNotContain("test-key").doesNotContainIgnoringCase("api_key");
            assertThat(request.getHeaders().keys()).doesNotContain("Authorization");
        });
    }

    @Test
    void observerSeesRateLimitHeaders() {
        wireMock.stubFor(get(urlPathEqualTo(IDS)).willReturn(aResponse().withBody("[]")
                .withHeader("Content-Type", "application/json")
                .withHeader("X-App-Rate-Limit", "20:1,100:120").withHeader("X-App-Rate-Limit-Count", "1:1,1:120")
                .withHeader("X-Method-Rate-Limit", "2000:10").withHeader("X-Method-Rate-Limit-Count", "1:10")));
        wireMock.stubFor(get(urlPathEqualTo(MATCH)).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "7").withBody("{}")));
        wireMock.stubFor(get(urlPathEqualTo(RIOT + "/br1/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5"))
                .willReturn(aResponse().withStatus(404).withBody("{}")));

        matches.rankedMatchIds(Region.AMERICAS, "P1", Instant.EPOCH);
        assertThatThrownBy(() -> matches.match(Region.AMERICAS, "BR1_3000000001"))
                .isInstanceOf(RateLimitExceededException.class);
        league.masterPlusPuuids(Platform.BR1, Tier.MASTER);

        assertThat(SEEN).hasSize(3);
        var ok = SEEN.get(0);
        assertThat(ok.host()).isEqualTo("americas");
        assertThat(ok.method()).isEqualTo("match-ids");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.headers()).containsEntry("X-App-Rate-Limit", "20:1,100:120")
                .containsEntry("X-App-Rate-Limit-Count", "1:1,1:120")
                .containsEntry("X-Method-Rate-Limit", "2000:10")
                .containsEntry("X-Method-Rate-Limit-Count", "1:10").doesNotContainKey("Retry-After");
        var limited = SEEN.get(1);
        assertThat(limited.method()).isEqualTo("match");
        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.headers()).containsEntry("Retry-After", "7");
        assertThat(SEEN.get(2).host()).isEqualTo("br1");
        assertThat(SEEN.get(2).method()).isEqualTo("league");
        assertThat(SEEN.get(2).status()).isEqualTo(404);
    }
}
