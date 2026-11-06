package com.your.summoner.collector.application.port.out;

import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Tier;

public record QueuedPlayer(Platform platform, Tier tier, String puuid) {}
