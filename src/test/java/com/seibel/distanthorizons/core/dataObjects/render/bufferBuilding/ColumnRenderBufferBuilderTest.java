package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import org.junit.jupiter.api.Test;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnRenderBufferBuilderTest {
	@Test
	void legacyUnuploadedContainersStillClose() {
		assertTrue(ColumnRenderBufferBuilder.shouldCloseUnuploadedContainer(false));
	}

	@Test
	void selectedRustWholeFrameSemanticBuildDoesNotCloseItsCopiedAsset() {
		assertFalse(ColumnRenderBufferBuilder.shouldCloseUnuploadedContainer(true));
	}

	@Test
	void blockResolutionRustGeometryRetainsExactAtlasProvenance() {
		ColumnRenderBufferBuilder.SemanticMaterialProvenance provenance =
			ColumnRenderBufferBuilder.semanticMaterialProvenanceForDetailLevel(true, (byte) 0,
				42, (byte) 1, 123L);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}

	@Test
	void coarseRustGeometryRejectsSingleSpriteAtlasProvenance() {
		ColumnRenderBufferBuilder.SemanticMaterialProvenance provenance =
			ColumnRenderBufferBuilder.semanticMaterialProvenanceForDetailLevel(true, (byte) 1,
				42, (byte) 1, 123L);

		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE, provenance.materialId());
		assertEquals(ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, provenance.variantState());
		assertEquals(0L, provenance.variantPosition());
	}

	@Test
	void coarseRustGeometryAcceptsOnlyAnExplicitUniformContributorProof() {
		ColumnRenderBufferBuilder.SemanticMaterialProvenance provenance =
			ColumnRenderBufferBuilder.semanticMaterialProvenanceForDetailLevel(
				true, (byte) 1, 42, (byte) 1, 123L, true
			);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}

	@Test
	void legacyGeometryKeepsItsExistingSemanticSidecars() {
		ColumnRenderBufferBuilder.SemanticMaterialProvenance provenance =
			ColumnRenderBufferBuilder.semanticMaterialProvenanceForDetailLevel(false, (byte) 3,
				42, (byte) 1, 123L);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}
}
