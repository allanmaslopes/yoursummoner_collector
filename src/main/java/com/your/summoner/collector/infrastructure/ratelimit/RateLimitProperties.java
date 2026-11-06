package com.your.summoner.collector.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Fração da cota informada pela Riot que o coletor pode usar. */
@ConfigurationProperties("collector")
public record RateLimitProperties(@DefaultValue("0.6") double budgetFraction) {}
