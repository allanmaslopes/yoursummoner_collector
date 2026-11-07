package com.your.summoner.collector.infrastructure.config;

import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.application.port.out.MatchCap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Chaves {@code collector.*} do pipeline (a fração do orçamento fica em RateLimitProperties). */
@ConfigurationProperties("collector")
public record CollectorProperties(
        @DefaultValue("true") boolean enabled,
        List<Platform> platforms,
        @DefaultValue("0 0 */6 * * *") String seedCron,
        @DefaultValue("3") int retentionPatches,
        @DefaultValue("15000") int maxMatchesPerPatch,
        @DefaultValue("http://localhost:8080/your-summoner") String yoursummonerApiUrl) implements MatchCap {

    public CollectorProperties {
        platforms = platforms == null || platforms.isEmpty() ? List.of(Platform.values()) : List.copyOf(platforms);
    }
}
