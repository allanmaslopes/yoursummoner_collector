package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.your.summoner.collector.domain.exception.ExternalAuthenticationException;
import com.your.summoner.collector.domain.exception.ExternalBadResponseException;
import com.your.summoner.collector.domain.exception.ExternalServiceUnavailableException;
import com.your.summoner.collector.domain.exception.RateLimitExceededException;
import com.your.summoner.collector.infrastructure.adapter.out.UpstreamNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public final class RiotErrorDecoder implements ErrorDecoder {
    public static final String SERVICE = "Riot API";

    public RiotErrorDecoder(ObjectMapper ignored) {
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String message = "%s answered %d".formatted(SERVICE, status);
        return switch (status) {
            case 401, 403 -> new ExternalAuthenticationException(SERVICE, message);
            case 404 -> new UpstreamNotFoundException(message);
            case 429 -> new RateLimitExceededException(retryAfter(response));
            default -> status >= 500
                    ? new ExternalServiceUnavailableException(SERVICE, message)
                    : new ExternalBadResponseException(SERVICE, message);
        };
    }

    private Duration retryAfter(Response response) {
        var values = response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Retry-After"))
                .flatMap(entry -> entry.getValue().stream()).findFirst();
        try {
            long seconds = Long.parseLong(values.orElse("").trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
