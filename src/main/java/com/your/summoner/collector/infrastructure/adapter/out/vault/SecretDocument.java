package com.your.summoner.collector.infrastructure.adapter.out.vault;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Linha do cofre da YourSummoner-API (só leitura aqui). Guarda só o texto cifrado. */
@Document("secrets")
public record SecretDocument(@Id String name, String cipherText, String last4, Instant updatedAt, String updatedBy) {
    @Override
    public String toString() {
        return "SecretDocument[name=" + name + "]";
    }
}
