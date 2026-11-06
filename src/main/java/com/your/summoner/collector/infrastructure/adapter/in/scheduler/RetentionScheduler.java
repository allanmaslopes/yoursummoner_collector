package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import com.your.summoner.collector.application.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Apaga diariamente as partidas de patches fora da retenção. */
@Component
@ConditionalOnProperty(name = "collector.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retention;

    public RetentionScheduler(RetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "0 30 4 * * *")
    void run() {
        try {
            log.info("Retenção apagou {} partidas", retention.run());
        } catch (Exception e) {
            log.warn("Retenção falhou ({})", e.getClass().getSimpleName());
        }
    }
}
