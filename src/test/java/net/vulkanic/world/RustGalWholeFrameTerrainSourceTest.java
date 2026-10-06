package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection policy inputs of the whole-frame terrain source. The selection
 * itself (Frozen's occlusion culler and section tree) is Rust's and is tested
 * in {@code render/chunk/section_graph}.
 */
class RustGalWholeFrameTerrainSourceTest {
	@Test
	void terrainSelectionDistanceMatchesFrozensOpaqueFogRule() {
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 256.0f, true));
		assertEquals(48.5f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 0.5f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, false));
	}

	@Test
	void spectatorInsideSolidTerrainAndSmartCullOffBypassPortalOcclusion() {
		assertTrue(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(true, false));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(true, true));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(false, false));
		assertFalse(RustGalWholeFrameTerrainSource.terrainOcclusionCullingEnabled(false, true));
	}
}
