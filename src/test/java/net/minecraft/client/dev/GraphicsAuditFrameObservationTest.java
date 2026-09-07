package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditFrameObservationTest {
    @Test
    void onlyTheExactCapturedFrameMatches() {
        var observation = new GraphicsAuditFrameObservation();
        assertNull(observation.matching(1977));
        observation.record(8, "startup");
        assertNull(observation.matching(1977));
        observation.record(1977, "captured");
        assertEquals("captured", observation.matching(1977));
        assertNull(observation.matching(1976));
        assertNull(observation.matching(1978));
    }

    @Test
    void retainsOnlyTheLatestObservation() {
        var observation = new GraphicsAuditFrameObservation();
        observation.record(1, "first");
        observation.record(2, "second");
        assertNull(observation.matching(1));
        assertEquals("second", observation.matching(2));
    }

    @Test
    void rejectsInvalidFramesAndMissingPayloads() {
        var observation = new GraphicsAuditFrameObservation();
        assertThrows(IllegalArgumentException.class, () -> observation.record(0, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> observation.record(-1, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> observation.record(1, null));
        assertNull(observation.matching(0));
    }
}
