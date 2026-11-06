package com.your.summoner.collector.infrastructure.ratelimit;

import com.your.summoner.collector.application.port.out.RiotCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Liga o limitador como orçamento da coleta e como observador das respostas da Riot. */
@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    @Bean
    CollectorPause collectorPause(RiotCredentials credentials) {
        return new CollectorPause(Clock.systemUTC(), credentials::riotApiKey);
    }

    @Bean
    RegionRateLimiter regionRateLimiter(RateLimitProperties props, CollectorPause pause) {
        return new RegionRateLimiter(Clock.systemUTC(), d -> Thread.sleep(d), props, pause);
    }
}
