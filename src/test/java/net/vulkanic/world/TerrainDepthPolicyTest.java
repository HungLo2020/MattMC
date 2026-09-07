package net.vulkanic.world;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainDepthPolicyTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void semanticDepthPolicyPreservesTheActualTerrainPipelineIncludingTranslucency() {
        for (var layer : ChunkSectionLayer.values()) {
            assertEquals(layer.pipeline().isWriteDepth()
                    ? RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE
                    : RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_NO_WRITE,
                RustGalTerrainRenderer.terrainDepthPolicy(layer), layer.name());
        }
        assertTrue(ChunkSectionLayer.TRANSLUCENT.pipeline().isWriteDepth());
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE,
            RustGalTerrainRenderer.terrainDepthPolicy(ChunkSectionLayer.TRANSLUCENT));
    }
}
