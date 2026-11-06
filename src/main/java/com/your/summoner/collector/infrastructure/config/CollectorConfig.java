package com.your.summoner.collector.infrastructure.config;

import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.application.port.out.PatchCatalog;
import com.your.summoner.collector.application.service.RetentionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class CollectorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RetentionService retentionService(MatchStore store, PatchCatalog patches, CollectorProperties props) {
        return new RetentionService(store, patches, props.retentionPatches());
    }
}
