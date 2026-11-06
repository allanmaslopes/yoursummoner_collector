package com.your.summoner.collector.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("riot.api")
public record RiotApiProperties(
        @DefaultValue("") String token,
        @DefaultValue("https://{host}.api.riotgames.com") String baseUrlTemplate,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("6s") Duration readTimeout) {

    @Override
    public String toString() {
        return "RiotApiProperties[baseUrlTemplate=" + baseUrlTemplate + "]";
    }
}
