package com.your.summoner.collector.domain.exception;

/** Resposta inesperada (4xx não tratado, payload ilegível). Não é transitória. */
public final class ExternalBadResponseException extends ExternalServiceException {

    public ExternalBadResponseException(String service, String message) {
        this(service, message, null);
    }

    public ExternalBadResponseException(String service, String message, Throwable cause) {
        super(service, Reason.BAD_RESPONSE, message, cause);
    }
}
