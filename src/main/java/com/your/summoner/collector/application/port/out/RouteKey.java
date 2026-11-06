package com.your.summoner.collector.application.port.out;

/** Rota de uma chamada à Riot: host ("br1" ou "americas") e método ("league", "match-ids", "match", "timeline"). */
public record RouteKey(String host, String method) {}
