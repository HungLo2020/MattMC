package net.vulkanic.world;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.sodium.client.render.chunk.translucent_sorting.SortType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainCameraSortPolicyTest {
    @Test void translucentCameraOrderingDoesNotDependOnDepthWrites() {
        assertTrue(ChunkSectionLayer.TRANSLUCENT.pipeline().isWriteDepth());
        assertEquals(RustGalWorldPrimitiveRenderer.DEPTH_POLICY_TEST_WRITE,
            RustGalTerrainRenderer.terrainDepthPolicy(ChunkSectionLayer.TRANSLUCENT));
		assertTrue(RustGalTerrainRenderer.terrainCameraSortRequested(ChunkSectionLayer.TRANSLUCENT, SortType.DYNAMIC));
		assertFalse(RustGalTerrainRenderer.terrainCameraSortRequested(ChunkSectionLayer.TRANSLUCENT, SortType.NONE));
		assertFalse(RustGalTerrainRenderer.terrainCameraSortRequested(ChunkSectionLayer.TRANSLUCENT, SortType.STATIC_NORMAL_RELATIVE));
		assertFalse(RustGalTerrainRenderer.terrainCameraSortRequested(ChunkSectionLayer.TRANSLUCENT, SortType.STATIC_TOPO));
		for (var layer : new ChunkSectionLayer[]{ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT,
				ChunkSectionLayer.CUTOUT_MIPPED}) {
			assertFalse(RustGalTerrainRenderer.terrainCameraSortRequested(layer, SortType.DYNAMIC));
        }
    }
}
