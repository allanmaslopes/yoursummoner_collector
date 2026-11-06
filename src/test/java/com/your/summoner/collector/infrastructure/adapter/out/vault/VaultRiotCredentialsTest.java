package com.your.summoner.collector.infrastructure.adapter.out.vault;

import com.mongodb.client.MongoClient;
import com.your.summoner.collector.infrastructure.security.AesGcmCipher;
import com.your.summoner.collector.support.RiotApiWireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Cofre real (Mongo embarcado, banco à parte) com uma chave mestra gerada no teste. */
class VaultRiotCredentialsTest extends RiotApiWireMock {
    @Autowired MongoClient client;

    private MongoTemplate vault;
    private AesGcmCipher cipher;
    private final Now clock = new Now();

    static class Now extends Clock {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void freshVault() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new AesGcmCipher(key);
        vault = new MongoTemplate(client, "vault_test");
        vault.dropCollection(SecretDocument.class);
    }

    private void store(String value) {
        vault.save(new SecretDocument("RIOT_API_KEY", cipher.encrypt(value), "", clock.now, "test"));
    }

    private VaultRiotCredentials credentials(String env) {
        return new VaultRiotCredentials(vault, cipher, () -> env, clock);
    }

    @Test
    void vaultKeyWinsOverEnv() {
        store("vault-key");

        assertThat(credentials("env-key").riotApiKey()).contains("vault-key");
    }

    @Test
    void fallsBackToEnvWhenVaultEmpty() {
        assertThat(credentials("env-key").riotApiKey()).contains("env-key");
        assertThat(credentials("").riotApiKey()).isEmpty();
    }

    @Test
    void withoutMasterKeyOnlyEnvIsUsed() {
        store("vault-key");

        assertThat(new VaultRiotCredentials(null, null, () -> "env-key", clock).riotApiKey())
                .isEqualTo(Optional.of("env-key"));
    }

    @Test
    void vaultChangeVisibleAfterCache() {
        store("old-key");
        var credentials = credentials("env-key");
        assertThat(credentials.riotApiKey()).contains("old-key");

        store("new-key");
        clock.now = clock.now.plus(Duration.ofSeconds(29));
        assertThat(credentials.riotApiKey()).contains("old-key");

        clock.now = clock.now.plus(Duration.ofSeconds(2));
        assertThat(credentials.riotApiKey()).contains("new-key");
    }
}
