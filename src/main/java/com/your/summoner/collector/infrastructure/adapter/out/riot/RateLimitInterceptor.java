package com.your.summoner.collector.infrastructure.adapter.out.riot;

import feign.InvocationContext;
import feign.Response;
import feign.ResponseInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Repassa ao {@link RateLimitObserver} toda resposta da Riot, inclusive 4xx/5xx, antes do ErrorDecoder. */
public class RateLimitInterceptor implements ResponseInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitInterceptor.class);
    static final List<String> HEADERS = List.of("X-App-Rate-Limit", "X-App-Rate-Limit-Count",
            "X-Method-Rate-Limit", "X-Method-Rate-Limit-Count", "Retry-After");

    private final RateLimitObserver observer;
    private final Pattern hostPattern;

    public RateLimitInterceptor(RateLimitObserver observer, String baseUrlTemplate) {
        this.observer = observer;
        int at = baseUrlTemplate.indexOf("{host}");
        this.hostPattern = Pattern.compile("^" + Pattern.quote(baseUrlTemplate.substring(0, at)) + "([a-z0-9]+)");
    }

    @Override
    public Object intercept(InvocationContext context, Chain chain) throws Exception {
        try {
            Response response = context.response();
            String url = response.request().url();
            var host = hostPattern.matcher(url);
            if (host.find()) {
                observer.onResponse(host.group(1), method(url), response.status(), headers(response));
            }
        } catch (RuntimeException e) {
            // Observar nunca derruba a chamada.
            LOG.warn("Rate limit observer failed ({})", e.getClass().getSimpleName());
        }
        return chain.next(context);
    }

    static String method(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        if (path.contains("/lol/league/")) return "league";
        if (path.endsWith("/timeline")) return "timeline";
        if (path.endsWith("/ids")) return "match-ids";
        return "match";
    }

    private static Map<String, String> headers(Response response) {
        Map<String, String> found = new LinkedHashMap<>();
        for (String name : HEADERS) {
            response.headers().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty())
                    .findFirst().ifPresent(entry -> found.put(name, entry.getValue().iterator().next()));
        }
        return found;
    }
}
