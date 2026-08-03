package net.vulkanic.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RustGalTerrainRendererLightingContractTest {
	@Test
	public void compactTerrainColorConvertsSodiumAbgrToSemanticArgb() {
		int compactAbgr = 0x80402010;

		assertEquals(0x80102040, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, false));
	}

	@Test
	public void separateAoTerrainColorConsumesAlphaAsAmbientOcclusion() {
		int compactAbgr = 0x80402010;

		assertEquals(0xff081020, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, true));
	}

	@Test
	public void fullSeparateAoLeavesOpaqueWhiteVertexWhite() {
		int compactAbgr = 0xffffffff;

		assertEquals(0xffffffff, RustGalTerrainRenderer.decodeCompactTerrainColorForRust(compactAbgr, true));
	}
}
