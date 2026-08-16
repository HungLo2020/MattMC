package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferQuadSemanticMaterialTest {
	@Test
	void greedyMergeMarksDistinctExactMaterialIdentitiesMixed() {
		BufferQuad left = quad((short) 0, 11);
		BufferQuad right = quad((short) 1, 12);

		assertTrue(left.tryMerge(right, BufferMergeDirectionEnum.EastWest));
		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, left.semanticMaterialId);
	}

	@Test
	void greedyMergePreservesOneExactMaterialIdentity() {
		BufferQuad left = quad((short) 0, 11);
		BufferQuad right = quad((short) 1, 11);

		assertTrue(left.tryMerge(right, BufferMergeDirectionEnum.EastWest));
		assertEquals(11, left.semanticMaterialId);
	}

	@Test
	void exactAtlasRouteKeepsDifferentSourceMaterialsInSeparateQuads() {
		assertTrue(!LodQuadBuilder.canMergeSemanticMaterials(true, 11, 12));
		assertTrue(LodQuadBuilder.canMergeSemanticMaterials(true, 11, 11));
		assertTrue(LodQuadBuilder.canMergeSemanticMaterials(false, 11, 12));
	}

	private static BufferQuad quad(short x, int semanticMaterialId) {
		return new BufferQuad(
			x, (short) 0, (short) 0, (short) 1, (short) 1,
			0xFF336699, (byte) 1, (byte) 15, (byte) 0,
			EDhDirection.UP, semanticMaterialId
		);
	}
}
