package com.your.summoner.collector.infrastructure.adapter.out.riot;

import com.your.summoner.collector.application.port.out.RiotCredentials;
import com.your.summoner.collector.domain.exception.ExternalAuthenticationException;
import com.your.summoner.collector.infrastructure.config.RiotApiProperties;
import feign.Request;
import feign.RequestInterceptor;
import feign.ResponseInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/** Deliberately not component scanned: applies only to the Riot clients. */
public class RiotFeignConfig {
    @Bean
    RequestInterceptor riotToken(RiotCredentials credentials) {
        return template -> {
            String token = credentials.riotApiKey().filter(value -> !value.isBlank())
                    .orElseThrow(() -> new ExternalAuthenticationException(
                            RiotErrorDecoder.SERVICE, "Riot API token is missing"));
            template.removeHeader("X-Riot-Token");
            template.header("X-Riot-Token", token);
        };
    }

    @Bean
    ResponseInterceptor riotRateLimitInterceptor(RateLimitObserver observer, RiotApiProperties properties) {
        return new RateLimitInterceptor(observer, properties.baseUrlTemplate());
    }

    @Bean
    RiotErrorDecoder riotErrorDecoder(ObjectMapper mapper) {
        return new RiotErrorDecoder(mapper);
    }

    @Bean
    Request.Options riotTimeouts(RiotApiProperties properties) {
        return new Request.Options(properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS, false);
    }

    @Bean
    Retryer riotFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
