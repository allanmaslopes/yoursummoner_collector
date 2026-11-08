package com.your.summoner.collector.application.service;

import com.your.summoner.collector.application.port.out.riot.RiotMatch;
import com.your.summoner.collector.application.port.out.riot.RiotTimeline;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.CollectedMatch.EventType;
import com.your.summoner.collector.domain.model.CollectedMatch.Frame;
import com.your.summoner.collector.domain.model.CollectedMatch.Participant;
import com.your.summoner.collector.domain.model.CollectedMatch.Runes;
import com.your.summoner.collector.domain.model.CollectedMatch.SlotState;
import com.your.summoner.collector.domain.model.CollectedMatch.TimelineEvent;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Tier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Reduz match + timeline da Riot para {@link CollectedMatch}. Função pura; não lê nenhum identificador de jogador. */
@Component
public class MatchReducer {

    private static final int SLOTS = 10;

    public CollectedMatch reduce(RiotMatch m, RiotTimeline tl, Platform p, Tier tier, Instant collectedAt) {
        RiotMatch.Info info = m.info();
        PatchVersion patch = PatchVersion.fromGameVersion(info.gameVersion())
                .orElseThrow(() -> new IllegalArgumentException("invalid gameVersion: " + info.gameVersion()));
        int winning = info.teams().stream().filter(RiotMatch.Team::win).mapToInt(RiotMatch.Team::teamId)
                .findFirst().orElse(0);
        Map<Integer, Integer> teamBySlot = new HashMap<>();
        info.participants().forEach(x -> teamBySlot.put(x.participantId(), x.teamId()));
        List<RiotTimeline.Event> timelineEvents = tl.info().frames().stream()
                .flatMap(f -> f.events().stream()).toList();
        return new CollectedMatch(CollectedMatch.SCHEMA_VERSION, m.metadata().matchId(), p, p.matchRegion(),
                patch.toString(), info.gameVersion(), info.queueId(), (int) info.gameDuration(),
                Instant.ofEpochMilli(info.gameStartTimestamp()).truncatedTo(ChronoUnit.HOURS), winning, tier,
                info.participants().stream().map(this::participant).toList(),
                frames(tl.info(), timelineEvents, teamBySlot), events(tl.info(), timelineEvents), collectedAt);
    }

    private Participant participant(RiotMatch.Participant x) {
        String pos = x.teamPosition() == null || x.teamPosition().isBlank() ? "NONE" : x.teamPosition();
        List<Integer> items = Stream.of(x.item0(), x.item1(), x.item2(), x.item3(), x.item4(), x.item5(), x.item6())
                .filter(i -> i != 0).toList();
        return new Participant(x.participantId(), x.teamId(), x.championId(), pos, x.win(), items,
                List.of(x.summoner1Id(), x.summoner2Id()), runes(x.perks()), x.kills(), x.deaths(), x.assists(),
                x.physicalDamageDealtToChampions(), x.magicDamageDealtToChampions(), x.trueDamageDealtToChampions(),
                x.totalDamageTaken(), x.goldEarned(), x.totalMinionsKilled() + x.neutralMinionsKilled(),
                x.champLevel(), x.visionScore(), x.timeCCingOthers(), x.totalHeal());
    }

    private Runes runes(RiotMatch.Perks perks) {
        List<RiotMatch.Style> st = perks == null ? List.of() : perks.styles();
        RiotMatch.StatPerks statPerks = perks == null ? null : perks.statPerks();
        return new Runes(st.size() > 0 ? st.get(0).style() : 0, st.size() > 1 ? st.get(1).style() : 0,
                st.stream().flatMap(s -> s.selections().stream()).map(RiotMatch.Selection::perk).toList(),
                statPerks == null ? null : new CollectedMatch.StatPerks(statPerks.offense(), statPerks.flex(),
                        statPerks.defense()));
    }

    private List<TimelineEvent> events(RiotTimeline.Info tl, List<RiotTimeline.Event> all) {
        List<TimelineEvent> out = new ArrayList<>();
        for (RiotTimeline.Event e : all) {
            EventType type = switch (e.type() == null ? "" : e.type()) {
                case "ITEM_PURCHASED" -> EventType.BUY;
                case "ITEM_SOLD" -> EventType.SELL;
                case "ITEM_UNDO" -> EventType.UNDO;
                case "SKILL_LEVEL_UP" -> EventType.SKILL;
                default -> null;
            };
            if (type == null || e.participantId() < 1 || e.participantId() > SLOTS) {
                continue;
            }
            boolean skill = type == EventType.SKILL;
            out.add(new TimelineEvent((int) (e.timestamp() / 1000), e.participantId(), type,
                    skill ? null : e.itemId(), skill ? e.skillSlot() : null,
                    type == EventType.BUY || skill ? frameIndex(tl, e.timestamp()) : null));
        }
        out.sort(Comparator.comparingInt(TimelineEvent::t)); // estável: empate mantém a ordem original
        return out;
    }

    private List<Frame> frames(RiotTimeline.Info tl, List<RiotTimeline.Event> all,
                               Map<Integer, Integer> teamBySlot) {
        List<RiotTimeline.Event> kills = all.stream().filter(e -> "CHAMPION_KILL".equals(e.type())).toList();
        return IntStream.range(0, tl.frames().size())
                .mapToObj(index -> frame(index, tl.frames().get(index), kills, teamBySlot))
                .toList();
    }

    private Frame frame(int index, RiotTimeline.Frame frame, List<RiotTimeline.Event> kills,
                        Map<Integer, Integer> teamBySlot) {
        List<SlotState> slots = IntStream.rangeClosed(1, SLOTS).mapToObj(slot -> {
            RiotTimeline.ParticipantFrame f = frame.participantFrames().get(slot);
            return f == null ? new SlotState(slot, 0, 0, 0, 0, 0)
                    : new SlotState(slot, f.currentGold(), f.totalGold(), f.level(), f.xp(),
                    f.minionsKilled() + f.jungleMinionsKilled());
        }).toList();
        int blue = 0;
        int red = 0;
        for (RiotTimeline.Event k : kills) {
            if (k.timestamp() > frame.timestamp() || k.killerId() < 1 || k.killerId() > SLOTS) {
                continue;
            }
            Integer team = teamBySlot.get(k.killerId());
            if (team != null && team == 100) {
                blue++;
            } else if (team != null && team == 200) {
                red++;
            }
        }
        return new Frame(index, slots, blue, red);
    }

    private Integer frameIndex(RiotTimeline.Info tl, long timestamp) {
        if (tl.frames().isEmpty()) {
            return null;
        }
        long index = tl.frameInterval() > 0 ? Math.max(0, timestamp) / tl.frameInterval() : 0;
        return (int) Math.min(index, tl.frames().size() - 1);
    }
}
