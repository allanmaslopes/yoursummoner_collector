package com.your.summoner.collector.application.port.out.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** match-v5 timeline só com o que o redutor usa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotTimeline(Info info) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(long frameInterval, List<Frame> frames) {
        public Info {
            frames = frames == null ? List.of() : List.copyOf(frames);
        }
    }

    /** {@code participantFrames} é indexado pelo participantId (1..10). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frame(long timestamp, Map<Integer, ParticipantFrame> participantFrames, List<Event> events) {
        public Frame {
            participantFrames = participantFrames == null ? Map.of() : Map.copyOf(participantFrames);
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantFrame(int currentGold, int totalGold, int level, int xp,
                                   int minionsKilled, int jungleMinionsKilled) {}

    /** Campos ausentes no tipo do evento ficam 0 (ex.: {@code skillSlot} fora de SKILL_LEVEL_UP). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String type, long timestamp, int participantId, int itemId, int skillSlot, int killerId) {}
}
