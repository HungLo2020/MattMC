package net.minecraft.client.dev;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiItemRasterDiagnosticsTest {
    @Test void backgroundStagesIncludeTheActualPresentedBackBufferOnly() {
        assertEquals(42, GuiItemRasterDiagnostics.backgroundObservationFrame(41, false));
        assertEquals(42, GuiItemRasterDiagnostics.backgroundObservationFrame(42, true));
        assertTrue(GuiItemRasterDiagnostics.observesBackgroundStage("main-after-post"));
        assertTrue(GuiItemRasterDiagnostics.observesBackgroundStage("main-after-gui"));
        assertTrue(GuiItemRasterDiagnostics.observesBackgroundStage("window-after-blit"));
        assertFalse(GuiItemRasterDiagnostics.observesBackgroundStage("terrain-after-solid"));
        assertFalse(GuiItemRasterDiagnostics.observesBackgroundStage("window-before-blit"));
        assertFalse(GuiItemRasterDiagnostics.observesBackgroundStage(null));
    }
    @Test void loadingAndSingleFrameFixturesCannotExhaustBackgroundProbeBudget() {
        assertFalse(GuiItemRasterDiagnostics.shouldObserveBackground(false,true,8,8));
        assertFalse(GuiItemRasterDiagnostics.shouldObserveBackground(true,false,2000,8));
        assertFalse(GuiItemRasterDiagnostics.shouldObserveBackground(true,true,0,1));
        assertFalse(GuiItemRasterDiagnostics.shouldObserveBackground(true,true,6,8));
        assertTrue(GuiItemRasterDiagnostics.shouldObserveBackground(true,true,7,8));
        assertTrue(GuiItemRasterDiagnostics.shouldObserveBackground(true,true,8,8));
    }
    @Test void backgroundProbesAreBoundedUniqueAndMatchAlphaInteriors() {
        int[][] points = GuiItemRasterDiagnostics.backgroundProbePoints();
        assertEquals(405, points.length);
        var unique = new java.util.HashSet<String>();
        for (int[] point : points) {
            assertTrue(point[0] >= 381 && point[0] <= 896);
            assertTrue(point[1] >= 674 && point[1] <= 676);
            assertTrue(unique.add(point[0] + "," + point[1]));
        }
        assertTrue(unique.contains("502,675"));
        assertTrue(unique.contains("826,675"));
        points[0][0] = -1;
        assertEquals(381, GuiItemRasterDiagnostics.backgroundProbePoints()[0][0]);
    }
    @Test void describesTopLeftRowsWithoutMutatingReadback() {
        ByteBuffer pixels = ByteBuffer.allocate(4*4*4);
        for (int row=0; row<4; row++) for (int column=0; column<4; column++) {
            int offset=(row*4+column)*4;
            pixels.put(offset,(byte)row).put(offset+1,(byte)column).put(offset+3,(byte)255);
        }
        byte[] before=pixels.array().clone();
        var lines=GuiItemRasterDiagnostics.describe(pixels,4,4,2,4);
        assertEquals("slot=0,0,2 atlas=4x4 centerColumn=0:3,1,0,255;1:2,1,0,255; centerRow=0:2,0,0,255;1:2,1,0,255;",lines.get(0));
        assertEquals("slot=2,2,2 atlas=4x4 centerColumn=0:1,3,0,255;1:0,3,0,255; centerRow=0:0,2,0,255;1:0,3,0,255;",lines.get(3));
        assertArrayEquals(before,pixels.array());
        assertEquals(0,pixels.position());
    }

    @Test void rejectsUnboundedOrIncompleteReadback() {
        assertFalse(GuiItemRasterDiagnostics.validLayout(4096,4096,48,9));
        assertFalse(GuiItemRasterDiagnostics.validLayout(512,512,0,9));
        assertFalse(GuiItemRasterDiagnostics.validLayout(512,512,48,33));
        assertFalse(GuiItemRasterDiagnostics.validLayout(4,4,4,2));
        assertThrows(IllegalArgumentException.class,
            () -> GuiItemRasterDiagnostics.describe(ByteBuffer.allocate(3),4,4,2,1));
    }
}
