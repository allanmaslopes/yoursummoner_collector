package com.your.summoner.collector.infrastructure.adapter.out.vault;

import com.your.summoner.collector.application.port.out.RiotCredentials;
import com.your.summoner.collector.infrastructure.security.AesGcmCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/** Chave do cofre primeiro, depois o ambiente. Resultado em cache por 30 s; nunca loga o valor. */
public class VaultRiotCredentials implements RiotCredentials {
    private static final Logger LOG = LoggerFactory.getLogger(VaultRiotCredentials.class);
    static final String SECRET_NAME = "RIOT_API_KEY";
    static final Duration TTL = Duration.ofSeconds(30);

    private final MongoTemplate vault;
    private final AesGcmCipher cipher;
    private final Supplier<String> env;
    private final Clock clock;
    private Optional<String> cached = Optional.empty();
    private Instant expires = Instant.MIN;

    /** {@code vault} e {@code cipher} nulos = sem chave mestra: só o ambiente. */
    public VaultRiotCredentials(MongoTemplate vault, AesGcmCipher cipher, Supplier<String> env, Clock clock) {
        this.vault = vault;
        this.cipher = cipher;
        this.env = env;
        this.clock = clock;
    }

    @Override
    public synchronized Optional<String> riotApiKey() {
        Instant now = clock.instant();
        if (now.isBefore(expires)) {
            return cached;
        }
        cached = fromVault().or(() -> Optional.ofNullable(env.get()).filter(v -> !v.isBlank()));
        expires = now.plus(TTL);
        return cached;
    }

    private Optional<String> fromVault() {
        if (vault == null || cipher == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(vault.findById(SECRET_NAME, SecretDocument.class))
                    .map(doc -> cipher.decrypt(doc.cipherText())).filter(v -> !v.isBlank());
        } catch (RuntimeException e) {
            LOG.warn("Secret vault unavailable, using environment credentials ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
