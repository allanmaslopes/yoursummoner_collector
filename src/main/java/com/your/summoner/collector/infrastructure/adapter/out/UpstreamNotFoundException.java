package com.your.summoner.collector.infrastructure.adapter.out;

/** 404 de um serviço externo. Cada adapter decide se vira {@code Optional.empty()} ou lista vazia. */
public class UpstreamNotFoundException extends RuntimeException {

    public UpstreamNotFoundException(String message) {
        super(message);
    }
}
