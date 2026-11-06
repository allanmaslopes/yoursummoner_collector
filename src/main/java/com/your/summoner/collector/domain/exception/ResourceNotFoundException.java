package com.your.summoner.collector.domain.exception;

/** Recurso inexistente (invocador, campeão, partida). */
public final class ResourceNotFoundException extends DomainException {

    private final String resource;
    private final String identifier;

    public ResourceNotFoundException(String resource, String identifier) {
        super("%s not found: %s".formatted(resource, identifier));
        this.resource = resource;
        this.identifier = identifier;
    }

    public String resource() {
        return resource;
    }

    public String identifier() {
        return identifier;
    }
}
