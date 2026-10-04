package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import net.minecraft.core.SectionPos;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainAirFrontierTest {
    @Test
    void knownAirBecomesTraversableImmediatelyWithoutAWorkerOrGeometry() throws Exception {
        var source = new RustGalWholeFrameTerrainSource();
        var section = SectionPos.of(1, 5, 2);
        Long2IntOpenHashMap incoming = field(source, "incomingDirections");
        incoming.put(section.asLong(), 1);
        assertTrue(admit(source, section));
        Long2ObjectOpenHashMap<RenderSection> resident = field(source, "sections");
        var air = resident.get(section.asLong());
        assertTrue(air.isBuilt());
        assertEquals(0, air.getFlags());
        assertEquals(1, air.getIncomingDirections());
        assertEquals(GraphDirectionSet.ALL,
            RustGalWholeFrameTerrainSource.rootVisibilityConnections(air.getVisibilityData(), true));
        LongArrayFIFOQueue frontier = field(source, "propagationPending");
        assertEquals(section.asLong(), frontier.dequeueLong());
        LongOpenHashSet running = field(source, "inFlight");
        assertTrue(running.isEmpty());
    }

    @Test
    void airAdmissionCancelsAnUndispatchedQueueEntryAndDoesNotLeaveDuplicateWork() throws Exception {
        var source = new RustGalWholeFrameTerrainSource();
        var section = SectionPos.of(1, 5, 2);
        ArrayDeque<SectionPos> pending = field(source, "pending");
        LongSet queued = field(source, "queued");
        pending.add(section);
        queued.add(section.asLong());
        assertTrue(admit(source, section));
        assertTrue(pending.isEmpty());
        assertTrue(queued.isEmpty());
    }

    @Test
    void changingToAirDoesNotDiscardARunningNonemptyBuildOrItsInvalidation() throws Exception {
        var source = new RustGalWholeFrameTerrainSource();
        var section = SectionPos.of(1, 5, 2);
        LongOpenHashSet running = field(source, "inFlight");
        LongOpenHashSet invalidated = field(source, "invalidatedInFlight");
        running.add(section.asLong());
        invalidated.add(section.asLong());
        assertFalse(admit(source, section));
        assertTrue(running.contains(section.asLong()));
        assertTrue(invalidated.contains(section.asLong()));
        Long2ObjectOpenHashMap<RenderSection> resident = field(source, "sections");
        assertFalse(resident.containsKey(section.asLong()));
        LongArrayFIFOQueue frontier = field(source, "propagationPending");
        assertTrue(frontier.isEmpty());
    }

    private static boolean admit(RustGalWholeFrameTerrainSource source, SectionPos section) throws Exception {
        var method = RustGalWholeFrameTerrainSource.class.getDeclaredMethod("admitLoadedEmptySection", SectionPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(source, section);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(RustGalWholeFrameTerrainSource source, String name) throws Exception {
        var field = RustGalWholeFrameTerrainSource.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(source);
    }
}
