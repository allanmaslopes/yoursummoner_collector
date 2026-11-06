package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import com.your.summoner.collector.application.service.CollectOutcome;
import com.your.summoner.collector.application.service.CollectService;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.infrastructure.config.CollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Um laço de coleta por região, cada um em sua virtual thread. */
@Component
@ConditionalOnProperty(name = "collector.enabled", havingValue = "true", matchIfMissing = true)
public class RegionWorkers implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RegionWorkers.class);
    static final Duration WAIT = Duration.ofSeconds(30);

    private final CollectService collect;
    private final Set<Region> regions = EnumSet.noneOf(Region.class);
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public RegionWorkers(CollectService collect, CollectorProperties props) {
        this.collect = collect;
        props.platforms().stream().map(Platform::matchRegion).forEach(regions::add);
    }

    @Override
    public synchronized void start() {
        running = true;
        for (Region region : regions) {
            threads.add(Thread.ofVirtual().name("collector-" + region).start(() -> loop(region)));
        }
        log.info("Coleta iniciada nas regiões {}", regions);
    }

    @Override
    public synchronized void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop(Region region) {
        while (running) {
            try {
                if (collect.collectOne(region) == CollectOutcome.IDLE) {
                    Thread.sleep(WAIT);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log.warn("Coleta de {} falhou ({}): nova tentativa em {} s", region, e.getClass().getSimpleName(),
                        WAIT.toSeconds());
                try {
                    Thread.sleep(WAIT);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }
}
