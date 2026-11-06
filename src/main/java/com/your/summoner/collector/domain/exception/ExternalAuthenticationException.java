package com.your.summoner.collector.domain.exception;

/** 401/403: credencial inválida ou expirada. Problema de configuração, não de disponibilidade. */
public final class ExternalAuthenticationException extends ExternalServiceException {

    public ExternalAuthenticationException(String service, String message) {
        super(service, Reason.AUTHENTICATION, message, null);
    }
}
