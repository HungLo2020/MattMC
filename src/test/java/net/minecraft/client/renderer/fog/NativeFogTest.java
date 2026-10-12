package net.minecraft.client.renderer.fog;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeFogTest {
    @Test void atmosphericOwnersHaveIndependentRetainedHistories() {
        var first = new NativeFog.RainState();
        var second = new NativeFog.RainState();
        assertEquals(.2F, first.update(1, 15, true, 1));
        assertEquals(0, second.update(0, 15, true, 1));
        assertEquals(.36F, first.update(1, 15, true, 1));
        assertEquals(.1F, second.update(1, 15, false, 1));
    }

    @Test void borrowedScratchDoesNotAliasEarlierColorResultsOrFogData() {
        var first = NativeFog.finishColor(0xff336699, 4, 0, 0, 0);
        var before = new org.joml.Vector4f(first);
        NativeFog.finishColor(-1, 4, 1, 0, 1);
        assertEquals(before, first);
        var data = new FogData();
        data.renderDistanceStart = 123;
        data.renderDistanceEnd = 456;
        NativeFog.ranges(data, 0, 256, 0, 0, 0);
        assertEquals(123, data.renderDistanceStart);
        assertEquals(456, data.renderDistanceEnd);
        NativeFog.ranges(new FogData(), 4, 256, 0, 0, 1);
        assertEquals(.25F, data.environmentalStart);
        assertEquals(1, data.environmentalEnd);
    }
}
