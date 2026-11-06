package com.your.summoner.collector.infrastructure.ratelimit;

import java.util.ArrayList;
import java.util.List;

public final class RateLimitHeaders {

    public record Limit(int limit, int windowSec) {}

    private RateLimitHeaders() {}

    /** "20:1,100:120" vira [(20, 1s), (100, 120s)]; partes inválidas são ignoradas. */
    public static List<Limit> parse(String header) {
        List<Limit> out = new ArrayList<>();
        if (header == null) {
            return out;
        }
        for (String part : header.split(",")) {
            String[] p = part.trim().split(":");
            if (p.length != 2) {
                continue;
            }
            try {
                int limit = Integer.parseInt(p[0].trim());
                int window = Integer.parseInt(p[1].trim());
                if (limit > 0 && window > 0) {
                    out.add(new Limit(limit, window));
                }
            } catch (NumberFormatException ignored) {
                // parte malformada: ignora
            }
        }
        return out;
    }
}
