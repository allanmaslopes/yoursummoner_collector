package com.your.summoner.collector.application.service;

/** Resultado de {@code collectOne}; em um jogador com várias partidas vale o primeiro da ordem abaixo. */
public enum CollectOutcome { SAVED, DUPLICATE, DISCARDED_PATCH, DISCARDED_QUEUE, NOT_FOUND, CAPPED, IDLE }
