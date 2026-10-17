package net.minecraft.world.level.levelgen;

import java.lang.ref.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native source creation, not a cached-work approximation. */
@Isolated
final class NativeLegacyOwnerAllocationTest {
    @Test void delegatingWrapperCreatesItsSeparateOwnerOnlyForAGenuineInheritedUse() {
        var parent = new LegacyRandomSource(42);
        long created = NativeLegacySource.statistics()[1];
        var wrapper = new WorldgenRandom(parent);
        assertEquals(created, NativeLegacySource.statistics()[1]);
        wrapper.nextInt();
        assertEquals(1, wrapper.getCount());
        assertEquals(created, NativeLegacySource.statistics()[1]);
        assertEquals(0, wrapper.rawSeed());
        assertEquals(created + 1, NativeLegacySource.statistics()[1]);
        assertEquals(0, wrapper.rawSeed());
        assertEquals(created + 1, NativeLegacySource.statistics()[1]);
        Reference.reachabilityFence(parent); Reference.reachabilityFence(wrapper);
    }
}
