package net.vulkanic.world;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.server.Bootstrap;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerrainReadinessInvalidationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void offscreenEditsRemainDirtyWithoutResettingTheDrainedCameraQueue() throws Exception {
        checkInvalidation(10, false, true);
    }

    @Test void visibleEditsResetReadinessAndRequestAReplacement() throws Exception {
        checkInvalidation(0, true, true);
    }

    @Test void visibleEditsRetireTheCaptureReceiptEvenWhenTheQueueIsAlreadyBusy() throws Exception {
        checkInvalidation(0, true, false);
    }

    private static void checkInvalidation(int editedX, boolean visible, boolean initiallyDrained) throws Exception {
        Field surface = field("wholeFrameSurfaceQueueDrained");
        Field terrain = field("wholeFrameTerrainQueueDrained");
        boolean oldSurface = surface.getBoolean(null), oldTerrain = terrain.getBoolean(null);
        try (var graph = new RustSectionGraph(0, 0);
             var capture = mockStatic(DeterministicCameraCapture.class)) {
            graph.addColumn(0, 0, 1);
            graph.addColumn(10, 0, 1);
            for (int x : new int[] {0, 10}) {
                graph.acceptBuild(x, 0, 0, 1, -1L, false, new int[0], 0);
            }
            float[] frustum = new Matrix4f().scaling(1.0e-6F).get(new float[16]);
            graph.select(8, 8, 8, frustum, 32, false, false);
            assertEquals(visible, graph.boxVisible(editedX, 0, 0, editedX, 0, 0));
            assertEquals(0, graph.buildRequestCount());

            var source = new RustGalWholeFrameTerrainSource();
            var level = mock(ClientLevel.class);
            field("level").set(source, level);
            field("sectionCache").set(source, new ClonedChunkSectionCache(level));
            field("graph").set(source, graph);
            surface.setBoolean(null, initiallyDrained);
            terrain.setBoolean(null, initiallyDrained);
            source.invalidate(editedX, 0, 0);

            assertFalse(graph.sectionReady(editedX, 0, 0), "Every edit must retain its rebuild mark");
            assertEquals(!visible && initiallyDrained, surface.getBoolean(null), "Only camera work can reset the drained surface queue");
            assertEquals(!visible && initiallyDrained, terrain.getBoolean(null), "Offscreen updates must not prevent a settled camera capture");
            capture.verify(DeterministicCameraCapture::invalidateRustWholeFrameTerrainReadiness,
                visible ? times(1) : never());

            // Turning toward an offscreen edit must still request its rebuild.
            graph.select(editedX * 16 + 8, 8, 8, frustum, 32, false, false);
            assertEquals(1, graph.buildRequestCount());
            assertEquals(net.minecraft.core.SectionPos.asLong(editedX, 0, 0), graph.buildRequest(0));
        } finally {
            surface.setBoolean(null, oldSurface);
            terrain.setBoolean(null, oldTerrain);
        }
    }

    private static Field field(String name) throws Exception {
        Field field = RustGalWholeFrameTerrainSource.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
