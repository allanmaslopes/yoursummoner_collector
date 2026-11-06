package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import com.your.summoner.collector.application.service.SeedService;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.infrastructure.config.CollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Semeia a fila com os jogadores Mestre+ de cada plataforma, na subida e pelo cron. */
@Component
@ConditionalOnProperty(name = "collector.enabled", havingValue = "true", matchIfMissing = true)
public class SeedScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeedScheduler.class);

    private final SeedService seed;
    private final CollectorProperties props;

    public SeedScheduler(SeedService seed, CollectorProperties props) {
        this.seed = seed;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedOnStartup() {
        Thread.ofVirtual().name("collector-seed").start(this::seedAll);
    }

    @Scheduled(cron = "${collector.seed-cron}")
    void seedAll() {
        for (Platform platform : props.platforms()) {
            try {
                seed.seed(platform);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Semente de {} falhou ({})", platform, e.getClass().getSimpleName());
            }
        }
    }
}
