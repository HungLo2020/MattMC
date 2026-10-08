package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditGroundFoilTimingTest {
    private static JsonObject snapshot() {
        return JsonParser.parseString(GraphicsAuditGroundFoilTiming.snapshot()).getAsJsonObject();
    }
    private static void enabled(Runnable test) {
        String property = "mattmc.dev.groundItemFoilTiming", old = System.getProperty(property);
        System.setProperty(property,"true"); GraphicsAuditGroundFoilTiming.resetForTest();
        try { test.run(); } finally {
            GraphicsAuditGroundFoilTiming.resetForTest();
            if (old == null) System.clearProperty(property); else System.setProperty(property,old);
        }
    }
    @Test void frozenObservesOnlyInsideWorldAndDoesNotRetainEarlierFrames() {
        enabled(() -> {
            GraphicsAuditGroundFoilTiming.beginFrame(7);
            GraphicsAuditGroundFoilTiming.observeScaledTicks(123,8);
            assertEquals(0,snapshot().getAsJsonArray("samples").size());
            GraphicsAuditGroundFoilTiming.beginWorld();
            GraphicsAuditGroundFoilTiming.observeScaledTicks(340012,8);
            assertFalse(snapshot().get("complete").getAsBoolean());
            GraphicsAuditGroundFoilTiming.endWorld();
            var first=snapshot(); assertTrue(first.get("complete").getAsBoolean());
            assertEquals(7,first.get("renderedFrameIndex").getAsLong());
            assertEquals(340012,first.getAsJsonArray("samples").get(0).getAsJsonObject().get("scaledTicks").getAsLong());
            GraphicsAuditGroundFoilTiming.observeScaledTicks(8888,8);
            assertEquals(1,snapshot().getAsJsonArray("samples").size());
            GraphicsAuditGroundFoilTiming.beginFrame(8);
            assertEquals(0,snapshot().getAsJsonArray("samples").size());
            assertEquals(1,first.getAsJsonArray("samples").size());
        });
    }
    @Test void semanticSamplesCopyActualClocksUnsignedMeshIdentityAndRejectInvalidValues() {
        enabled(() -> {
            GraphicsAuditGroundFoilTiming.beginFrame(12);
            GraphicsAuditGroundFoilTiming.observeSemanticClock(-1L,85003,.5,.5F);
            var sample=snapshot().getAsJsonArray("samples").get(0).getAsJsonObject();
            assertEquals("18446744073709551615",sample.get("meshKey").getAsString());
            assertEquals(340012,sample.get("scaledTicks").getAsLong());
            assertEquals(85003,sample.get("clockMillis").getAsLong());
            GraphicsAuditGroundFoilTiming.observeSemanticClock(1,10,Double.NaN,.5F);
            assertFalse(snapshot().get("complete").getAsBoolean());
            assertEquals(1,snapshot().getAsJsonArray("samples").size());
        });
    }
    @Test void movingSelectionRequiresBothClocksAndMatchesLaterNaturalCycles() {
        enabled(() -> {
            String property="mattmc.dev.graphicsAuditGroundFoilPhaseTargets",old=System.getProperty(property);
            try {
                System.clearProperty(property);
                GraphicsAuditGroundFoilTiming.beginFrame(1);
                GraphicsAuditGroundFoilTiming.observeSemanticClock(1,85003,.5,.5F);
                assertFalse(GraphicsAuditGroundFoilTiming.readyForCapture(10000));
                GraphicsAuditGroundFoilTiming.observeSemanticClock(2,85004,.5,.5F);
                assertTrue(GraphicsAuditGroundFoilTiming.readyForCapture(10000));
                assertFalse(GraphicsAuditGroundFoilTiming.readyForCapture(40000));
                System.setProperty(property,"10012,10016");
                assertTrue(GraphicsAuditGroundFoilTiming.pairedPhaseMatches(340000));
                assertFalse(GraphicsAuditGroundFoilTiming.pairedPhaseMatches(340032));
                assertTrue(GraphicsAuditGroundFoilTiming.pairedPhaseMatches(670016));
                GraphicsAuditGroundFoilTiming.beginFrame(2); GraphicsAuditGroundFoilTiming.beginWorld();
                GraphicsAuditGroundFoilTiming.observeScaledTicks(670016,8); GraphicsAuditGroundFoilTiming.endWorld();
                assertTrue(GraphicsAuditGroundFoilTiming.readyForCapture(10000));
                assertEquals(200_000_000_000L,GraphicsAuditGuiFoilTiming.captureWaitBudgetNanos());
                System.setProperty(property,"10012");
                assertThrows(IllegalArgumentException.class,() -> GraphicsAuditGroundFoilTiming.pairedPhaseMatches(340012));
            } finally {
                if (old==null) System.clearProperty(property); else System.setProperty(property,old);
            }
        });
    }

    @Test void scopeErrorsAndOverflowCannotBecomeCompleteEvidence() {
        enabled(() -> {
            GraphicsAuditGroundFoilTiming.beginFrame(1); GraphicsAuditGroundFoilTiming.beginWorld();
            GraphicsAuditGroundFoilTiming.beginWorld(); GraphicsAuditGroundFoilTiming.endWorld();
            assertFalse(snapshot().get("complete").getAsBoolean());
            GraphicsAuditGroundFoilTiming.beginFrame(2); GraphicsAuditGroundFoilTiming.beginWorld();
            for (int i=0;i<17;i++) GraphicsAuditGroundFoilTiming.observeScaledTicks(i,8);
            GraphicsAuditGroundFoilTiming.endWorld();
            assertFalse(snapshot().get("complete").getAsBoolean());
            assertEquals(16,snapshot().getAsJsonArray("samples").size());
            GraphicsAuditGroundFoilTiming.beginFrame(0);
            assertFalse(snapshot().get("complete").getAsBoolean());
        });
    }
}
