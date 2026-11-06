package com.your.summoner.collector.domain.exception;

/** 5xx, timeout ou falha de conexão: única falha externa que abre o circuit breaker e vale retry. */
public final class ExternalServiceUnavailableException extends ExternalServiceException {

    public ExternalServiceUnavailableException(String service, String message) {
        this(service, message, null);
    }

    public ExternalServiceUnavailableException(String service, String message, Throwable cause) {
        super(service, Reason.UNAVAILABLE, message, cause);
    }
}
