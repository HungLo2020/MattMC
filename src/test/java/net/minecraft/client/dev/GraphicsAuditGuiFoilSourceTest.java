package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditGuiFoilSourceTest {
    @Test void selectedFrameSnapshotDoesNotRetainEarlierCompassFrames() {
        String property = "mattmc.dev.guiItemRasterTrace";
        String old = System.getProperty(property);
        System.setProperty(property, "true");
        GraphicsAuditGuiFoilTiming.resetForTest();
        GraphicsAuditGuiFoilSource.resetForTest();
        try {
            GraphicsAuditGuiFoilTiming.beginFrame();
            GraphicsAuditGuiFoilSource.observe(new GraphicsAuditGuiFoilSource.Sample(
                "minecraft:item/compass_08",new float[12],new float[8]));
            String earlier = GraphicsAuditGuiFoilTiming.snapshot();
            GraphicsAuditGuiFoilTiming.beginFrame();
            GraphicsAuditGuiFoilSource.observe(new GraphicsAuditGuiFoilSource.Sample(
                "minecraft:item/compass_07",new float[12],new float[8]));
            var current = com.google.gson.JsonParser.parseString(GraphicsAuditGuiFoilTiming.snapshot()).getAsJsonObject();
            var source = current.getAsJsonObject("sourceEvidence");
            assertEquals(current.get("frameSequence"),source.get("frameSequence"));
            assertEquals(1,source.getAsJsonArray("sources").size());
            assertTrue(source.toString().contains("compass_07"));
            assertFalse(source.toString().contains("compass_08"));
            assertTrue(earlier.contains("compass_08"));
            assertFalse(earlier.contains("compass_07"));
        } finally {
            if (old == null) System.clearProperty(property); else System.setProperty(property,old);
            GraphicsAuditGuiFoilTiming.resetForTest();
            GraphicsAuditGuiFoilSource.resetForTest();
        }
    }

    @Test void observesImmutableFiniteSourceDataAndRejectsAmbiguousOrUnboundedEvidence() {
        GraphicsAuditGuiFoilSource.resetForTest();
        try {
            float[] positions = new float[12], uv = new float[8];
            uv[0] = 0.25F;
            var sample = new GraphicsAuditGuiFoilSource.Sample("minecraft:item/feather", positions, uv);
            uv[0] = 0.5F;
            GraphicsAuditGuiFoilSource.observe(sample);
            assertTrue(GraphicsAuditGuiFoilSource.json().contains("0.25"));
            assertTrue(GraphicsAuditGuiFoilSource.json().contains("\"complete\":true"));
            GraphicsAuditGuiFoilSource.observe(new GraphicsAuditGuiFoilSource.Sample("minecraft:item/feather",positions,uv));
            assertTrue(GraphicsAuditGuiFoilSource.json().contains("\"complete\":false"));
            GraphicsAuditGuiFoilSource.resetForTest();
            for (int i=0; i<65; i++) GraphicsAuditGuiFoilSource.observe(
                new GraphicsAuditGuiFoilSource.Sample("minecraft:item/test"+i,positions,uv));
            assertTrue(GraphicsAuditGuiFoilSource.json().contains("\"complete\":false"));
            assertFalse(GraphicsAuditGuiFoilSource.json().contains("test64"));
            uv[0] = Float.NaN;
            assertThrows(IllegalArgumentException.class, () -> new GraphicsAuditGuiFoilSource.Sample("minecraft:item/test",positions,uv));
        } finally { GraphicsAuditGuiFoilSource.resetForTest(); }
    }
}
