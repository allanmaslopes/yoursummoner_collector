package com.your.summoner.collector.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {

    @Test
    void patchFromGameVersion() {
        Optional<PatchVersion> p = PatchVersion.fromGameVersion("16.22.712.1234");
        assertEquals(Optional.of(new PatchVersion(16, 22)), p);
        assertEquals("16.22", p.orElseThrow().toString());
    }

    @Test
    void patchParseAcceptsShortAndLong() {
        assertEquals(Optional.of(new PatchVersion(16, 22)), PatchVersion.parse("16.22"));
        assertEquals(Optional.of(new PatchVersion(16, 22)), PatchVersion.parse("16.22.1"));
    }

    @Test
    void patchCompare() {
        assertTrue(new PatchVersion(16, 9).compareTo(new PatchVersion(16, 22)) < 0);
        assertTrue(new PatchVersion(15, 24).compareTo(new PatchVersion(16, 1)) < 0);
    }

    @Test
    void invalidPatchIsEmpty() {
        for (String s : new String[]{null, "", "abc", "16", "lolpatch_3.7"}) {
            assertEquals(Optional.empty(), PatchVersion.parse(s), String.valueOf(s));
        }
    }

    @Test
    void listsAreImmutable() {
        List<Integer> items = new ArrayList<>(List.of(1, 2));
        var p = new CollectedMatch.Participant(1, 100, 412, "UTILITY", true, items, null,
                new CollectedMatch.Runes(8000, 8100, List.of(1)), 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
        items.add(3);
        assertEquals(List.of(1, 2), p.items());
        assertThrows(UnsupportedOperationException.class, () -> p.items().add(3));
    }

    @Test
    void nullListsBecomeEmpty() {
        assertTrue(new CollectedMatch.Runes(8000, 8100, null).perks().isEmpty());
    }

    @Test
    void frameSlotsAreImmutable() {
        List<CollectedMatch.SlotState> slots = new ArrayList<>(List.of(
                new CollectedMatch.SlotState(1, 500, 500, 1, 0, 0)));
        var frame = new CollectedMatch.Frame(0, slots, 0, 0);

        slots.add(new CollectedMatch.SlotState(2, 500, 500, 1, 0, 0));

        assertEquals(1, frame.slots().size());
        assertThrows(UnsupportedOperationException.class, () -> frame.slots().clear());
    }

    @Test
    void tierOrderIsAscending() {
        assertTrue(Tier.MASTER.compareTo(Tier.CHALLENGER) < 0);
    }

    @Test
    void everyPlatformHasARegion() {
        for (Platform p : Platform.values()) {
            assertTrue(p.matchRegion() != null, p.name());
        }
    }
}
