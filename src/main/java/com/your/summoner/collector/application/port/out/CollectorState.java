package com.your.summoner.collector.application.port.out;

public interface CollectorState {
    /** True enquanto a coleta está pausada (chave da Riot recusada). */
    boolean paused();
}
