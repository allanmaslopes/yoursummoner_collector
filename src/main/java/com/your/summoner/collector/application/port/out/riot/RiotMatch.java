package com.your.summoner.collector.application.port.out.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** match-v5 só com o que o redutor usa. Sem puuid, Riot ID, summonerName ou summonerId de propósito. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotMatch(Metadata metadata, Info info) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(String matchId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(String gameVersion, int queueId, long gameDuration, long gameStartTimestamp,
                       String platformId, List<Team> teams, List<Participant> participants) {
        public Info {
            teams = teams == null ? List.of() : List.copyOf(teams);
            participants = participants == null ? List.of() : List.copyOf(participants);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(int teamId, boolean win) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(int participantId, int teamId, int championId, String teamPosition, boolean win,
                              int item0, int item1, int item2, int item3, int item4, int item5, int item6,
                              int summoner1Id, int summoner2Id, Perks perks,
                              int kills, int deaths, int assists,
                              int physicalDamageDealtToChampions, int magicDamageDealtToChampions,
                              int trueDamageDealtToChampions, int totalDamageTaken, int goldEarned,
                              int totalMinionsKilled, int neutralMinionsKilled, int champLevel, int visionScore,
                              int timeCCingOthers, int totalHeal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perks(StatPerks statPerks, List<Style> styles) {
        public Perks {
            styles = styles == null ? List.of() : List.copyOf(styles);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatPerks(int defense, int flex, int offense) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Style(int style, List<Selection> selections) {
        public Style {
            selections = selections == null ? List.of() : List.copyOf(selections);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Selection(int perk) {}
}
