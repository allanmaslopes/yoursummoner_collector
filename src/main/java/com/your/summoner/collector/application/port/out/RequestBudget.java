package com.your.summoner.collector.application.port.out;

public interface RequestBudget {

    /** Bloqueia até haver orçamento para a rota (e a coleta não estar pausada). */
    void acquire(RouteKey key) throws InterruptedException;
}
