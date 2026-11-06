package com.your.summoner.collector.domain.exception;

/**
 * Falha ao falar com um serviço externo (Riot API, Data Dragon, Community Dragon). Uma subclasse
 * por razão: o circuit breaker do MicroProfile só filtra por classe e apenas
 * {@link ExternalServiceUnavailableException} é transitória (conta para o CB e vale retry).
 */
public abstract sealed class ExternalServiceException extends DomainException
        permits ExternalServiceUnavailableException, ExternalAuthenticationException, ExternalBadResponseException {

    public enum Reason {
        /** 5xx, timeout ou falha de conexão. */
        UNAVAILABLE,
        /** 401/403: API key inválida ou expirada. */
        AUTHENTICATION,
        /** Resposta inesperada (4xx não tratado, payload ilegível). */
        BAD_RESPONSE
    }

    private final String service;
    private final Reason reason;

    protected ExternalServiceException(String service, Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.reason = reason;
    }

    public String service() {
        return service;
    }

    public Reason reason() {
        return reason;
    }

    public boolean isRetryable() {
        return this instanceof ExternalServiceUnavailableException;
    }
}
