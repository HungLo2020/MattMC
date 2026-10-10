package net.vulkanic.world;

import java.lang.foreign.*;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native graph publication and its bounded CPU identity handoff. */
class NativeTerrainFrameTest {
    @Test void populatedSelectionPublishesIncreasingIdsWithoutJavaPointStorage() throws Throwable {
        try (var graph = new RustSectionGraph(0, 0); var arena = Arena.ofConfined()) {
            assertTrue(graph.addColumn(0, 0, 1));
            graph.buildStarted(0, 0, 0);
            graph.acceptBuild(0, 0, 0, 1, -1L, false, new int[0], 0);
            var field = RustSectionGraph.class.getDeclaredField("graph");
            field.setAccessible(true);
            var handle = (MemorySegment)field.get(graph);
            var meshes = arena.allocate(64, 8);
            meshes.fill((byte)0);
            meshes.set(ValueLayout.JAVA_LONG, 16, 77);
            meshes.set(ValueLayout.JAVA_LONG, 32, 88);
            meshes.set(ValueLayout.JAVA_LONG, 40, 1);
            meshes.set(ValueLayout.JAVA_LONG, 56, 2);
            var setMeshes = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_sodium_section_graph_set_meshes",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            assertEquals(0, (int)setMeshes.invokeExact(handle, 0, meshes, 1));
            float[] matrix = {1f/64,0,0,0, 0,1f/64,0,0, 0,0,1f/64,0, 0,0,0,1};
            graph.select(8, 8, 8, matrix, 128, false, false);
            var first = graph.selectTerrain(8, 8, 8, new int[]{1,1,2}, new int[]{0,1,2},
                false, 0, true, 40, 32);
            assertEquals(2, first.sectionCount());
            assertTrue(first.frameOwner() > 0);
            assertTrue(first.frameId() > 0);
            var reference = new VulkanicGalBridge.StaticTerrainFrameReference(first.frameOwner(), first.frameId(),
                first.sectionCount(), first.casterCount());
            var sections = VulkanicGalBridge.StaticTerrainSections.nativeFrame(reference);
            var casters = VulkanicGalBridge.StaticTerrainShadowCasters.nativeFrame(reference);
            assertEquals(2, sections.count());
            assertEquals(0, sections.packed().byteSize());
            assertEquals(0, casters.packed().byteSize());
            assertSame(reference, sections.nativeReference());
            graph.reloadResources(true);
            var second = graph.selectTerrain(8, 8, 8, new int[]{1,1,2}, new int[]{0,1,2},
                false, 0, true, 40, 32);
            assertEquals(first.frameOwner(), second.frameOwner());
            assertTrue(second.frameId() > first.frameId());
            assertEquals(reference.id(), sections.nativeReference().id());
        }
    }
}
