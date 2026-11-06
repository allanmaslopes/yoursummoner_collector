package com.your.summoner.collector.domain.exception;

/**
 * Raiz das exceções conhecidas da aplicação. Cada subclasse vira um problem+json específico
 * no adapter de entrada.
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidInputException, ResourceNotFoundException, RateLimitExceededException,
        ExternalServiceException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
