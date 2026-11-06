package com.your.summoner.collector.infrastructure.config;

import com.mongodb.client.MongoClient;
import com.your.summoner.collector.application.port.out.RiotCredentials;
import com.your.summoner.collector.infrastructure.adapter.out.riot.RateLimitObserver;
import com.your.summoner.collector.infrastructure.adapter.out.vault.VaultRiotCredentials;
import com.your.summoner.collector.infrastructure.security.AesGcmCipher;
import com.your.summoner.collector.infrastructure.security.MasterKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RiotCredentialsConfig {

    /** Falha a subida (sem imprimir a chave) quando o valor existe mas é inválido. */
    @Bean
    MasterKey masterKey(@Value("${collector.master-key:}") String base64) {
        return MasterKey.parse(base64);
    }

    /** O MongoTemplate do cofre não é bean de propósito: um segundo template desligaria o principal. */
    @Bean
    RiotCredentials riotCredentials(MasterKey masterKey, MongoClient client, RiotApiProperties riot,
                                    @Value("${collector.vault.database:yoursummoner}") String vaultDatabase) {
        if (!masterKey.present()) {
            return new VaultRiotCredentials(null, null, riot::token, Clock.systemUTC());
        }
        return new VaultRiotCredentials(new MongoTemplate(client, vaultDatabase),
                new AesGcmCipher(masterKey.bytes()), riot::token, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitObserver.class)
    RateLimitObserver noopRateLimitObserver() {
        return (host, method, status, headers) -> { };
    }
}
