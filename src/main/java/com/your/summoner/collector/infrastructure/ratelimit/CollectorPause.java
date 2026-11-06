package com.your.summoner.collector.infrastructure.ratelimit;

import com.your.summoner.collector.application.port.out.CollectorState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pausa global quando a chave da Riot é recusada (401/403); libera quando a chave do cofre muda. */
public class CollectorPause implements CollectorState {

    private static final Logger log = LoggerFactory.getLogger(CollectorPause.class);
    static final Duration RECHECK = Duration.ofMinutes(5);
    static final Duration PROBE = Duration.ofMinutes(30);

    private final Clock clock;
    private final Supplier<Optional<String>> currentKey;
    private boolean paused;
    private String rejectedHash;
    private long nextCheckMs;
    private long pausedAtMs;

    public CollectorPause(Clock clock, Supplier<Optional<String>> currentKey) {
        this.clock = clock;
        this.currentKey = currentKey;
    }

    /** Liga a pausa guardando o hash da chave vigente. Chamadas repetidas enquanto pausado não fazem nada. */
    public synchronized void pause() {
        if (paused) {
            return;
        }
        paused = true;
        pausedAtMs = clock.millis();
        rejectedHash = hashOfCurrent();
        nextCheckMs = clock.millis() + RECHECK.toMillis();
        log.warn("Riot recusou a chave (401/403): coleta pausada até a chave mudar no cofre");
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    @Override
    public boolean paused() {
        return isPaused();
    }

    /** Zero se não está pausado (ou se a chave mudou agora); senão, quanto falta para a próxima checagem. */
    public synchronized Duration remaining() {
        if (!paused) {
            return Duration.ZERO;
        }
        long now = clock.millis();
        if (now < nextCheckMs) {
            return Duration.ofMillis(nextCheckMs - now);
        }
        if (!Objects.equals(hashOfCurrent(), rejectedHash)) {
            paused = false;
            log.info("Chave da Riot mudou: coleta retomada");
            return Duration.ZERO;
        }
        // A chave pode ter mudado entre a requisição e o 401: tenta de novo de tempos em tempos; outro 401 repausa.
        if (now - pausedAtMs >= PROBE.toMillis()) {
            paused = false;
            log.info("Coleta pausada há {} min: nova tentativa com a chave atual", PROBE.toMinutes());
            return Duration.ZERO;
        }
        nextCheckMs = now + RECHECK.toMillis();
        return RECHECK;
    }

    private String hashOfCurrent() {
        return currentKey.get().filter(k -> !k.isBlank()).map(CollectorPause::sha256).orElse(null);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
