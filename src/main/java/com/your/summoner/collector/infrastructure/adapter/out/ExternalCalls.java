package com.your.summoner.collector.infrastructure.adapter.out;

import com.your.summoner.collector.domain.exception.DomainException;
import com.your.summoner.collector.domain.exception.ExternalBadResponseException;
import com.your.summoner.collector.domain.exception.ExternalServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;

import java.util.function.Supplier;

/** Traduz falhas técnicas do Feign (conexão, timeout, status não mapeado, corpo ilegível) para o domínio. */
public final class ExternalCalls {

    private ExternalCalls() {
    }

    public static <T> T call(String service, Supplier<T> request) {
        try {
            return request.get();
        } catch (DomainException | UpstreamNotFoundException e) {
            throw e;
        } catch (RetryableException e) {
            throw new ExternalServiceUnavailableException(service,
                    "Could not reach %s: %s".formatted(service, e.getMessage()), e);
        } catch (DecodeException e) {
            throw new ExternalBadResponseException(service, "%s sent an unreadable body".formatted(service), e);
        } catch (FeignException e) {
            var message = "%s answered %d".formatted(service, e.status());
            throw e.status() >= 500 || e.status() < 0
                    ? new ExternalServiceUnavailableException(service, message, e)
                    : new ExternalBadResponseException(service, message, e);
        }
    }
}
