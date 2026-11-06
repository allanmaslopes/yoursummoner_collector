package com.your.summoner.collector.domain.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Patch no formato do jogo (major.minor), ex.: 16.22. */
public record PatchVersion(int major, int minor) implements Comparable<PatchVersion> {

    private static final Pattern P = Pattern.compile("^(\\d+)\\.(\\d+)(\\..*)?$");

    public PatchVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("patch must be non-negative");
        }
    }

    /** "16.22.712.1234" → 16.22. */
    public static Optional<PatchVersion> fromGameVersion(String gameVersion) {
        return parse(gameVersion);
    }

    /** Aceita "16.22" e "16.22.1". */
    public static Optional<PatchVersion> parse(String patch) {
        if (patch == null) {
            return Optional.empty();
        }
        Matcher m = P.matcher(patch.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PatchVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(PatchVersion o) {
        return major != o.major ? Integer.compare(major, o.major) : Integer.compare(minor, o.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
