package com.your.summoner.collector.infrastructure.adapter.in.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DisabledJobsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noWorkerOrSchedulerStartsWhenCollectorIsDisabled() {
        assertThat(context.getBeansOfType(RegionWorkers.class)).isEmpty();
        assertThat(context.getBeansOfType(SeedScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(RetentionScheduler.class)).isEmpty();
    }
}
