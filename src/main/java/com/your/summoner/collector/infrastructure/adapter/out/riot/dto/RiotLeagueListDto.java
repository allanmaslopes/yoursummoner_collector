package com.your.summoner.collector.infrastructure.adapter.out.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotLeagueListDto(List<Entry> entries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String puuid) {
        @Override
        public String toString() {
            return "Entry[***]";
        }
    }
}
