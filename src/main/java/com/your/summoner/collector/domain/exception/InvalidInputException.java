package com.your.summoner.collector.domain.exception;

/** Entrada inválida do usuário (Riot ID vazio, lane desconhecida, filtro fora do intervalo...). */
public final class InvalidInputException extends DomainException {

    public InvalidInputException(String message) {
        super(message);
    }
}
