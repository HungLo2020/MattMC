package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import org.junit.jupiter.api.Test;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnRenderBufferBuilderTest {
	@Test
	void blockResolutionRustGeometryRetainsExactAtlasProvenance() {
		JavaSectionBuildReference.SemanticMaterialProvenance provenance =
			JavaSectionBuildReference.semanticMaterialProvenanceForDetailLevel(true, (byte) 0,
				42, (byte) 1, 123L);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}

	@Test
	void coarseRustGeometryRejectsSingleSpriteAtlasProvenance() {
		JavaSectionBuildReference.SemanticMaterialProvenance provenance =
			JavaSectionBuildReference.semanticMaterialProvenanceForDetailLevel(true, (byte) 1,
				42, (byte) 1, 123L);

		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE, provenance.materialId());
		assertEquals(ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, provenance.variantState());
		assertEquals(0L, provenance.variantPosition());
	}

	@Test
	void coarseRustGeometryAcceptsOnlyAnExplicitUniformContributorProof() {
		JavaSectionBuildReference.SemanticMaterialProvenance provenance =
			JavaSectionBuildReference.semanticMaterialProvenanceForDetailLevel(
				true, (byte) 1, 42, (byte) 1, 123L, true
			);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}

	@Test
	void legacyGeometryKeepsItsExistingSemanticSidecars() {
		JavaSectionBuildReference.SemanticMaterialProvenance provenance =
			JavaSectionBuildReference.semanticMaterialProvenanceForDetailLevel(false, (byte) 3,
				42, (byte) 1, 123L);

		assertEquals(42, provenance.materialId());
		assertEquals((byte) 1, provenance.variantState());
		assertEquals(123L, provenance.variantPosition());
	}

	@Test
	void coarseGeometryRecoversOnlyACommonCompleteContributor() {
		ColumnRenderSource.SemanticMaterialSpan span = new ColumnRenderSource.SemanticMaterialSpan(
			4, 12, 42, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 123L
		);
		ColumnRenderSource.SemanticHorizontalContributor contributor =
			new ColumnRenderSource.SemanticHorizontalContributor(List.of(span));
		var contributors = new ColumnRenderSource.SemanticHorizontalContributor[] {
			contributor, contributor, contributor, contributor
		};
		var fallback = new JavaSectionBuildReference.SemanticMaterialProvenance(
			ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE,
			ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L
		);
		var recovered = JavaSectionBuildReference.recoverCommonHorizontalProvenance(
			null, RenderDataPointUtil.createDataPoint(12, 4, 0xffffffff, 15, 0, 1), contributors, fallback
		);
		assertEquals(42, recovered.materialId());
		assertEquals(ColumnRenderSource.SEMANTIC_VARIANT_EXACT, recovered.variantState());
		assertEquals(123L, recovered.variantPosition());

		var mixed = new ColumnRenderSource.SemanticHorizontalContributor[] {
			contributor, contributor, new ColumnRenderSource.SemanticHorizontalContributor(List.of(
				new ColumnRenderSource.SemanticMaterialSpan(4, 12, 43,
					ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 123L)
			)), contributor
		};
		assertEquals(fallback, JavaSectionBuildReference.recoverCommonHorizontalProvenance(
				 null, RenderDataPointUtil.createDataPoint(12, 4, 0xffffffff, 15, 0, 1), mixed, fallback));
	}

	@Test
	void coarseGeometryRetainsAnIdenticalLayeredContributorSequence() {
		var layers = List.of(
			new ColumnRenderSource.SemanticMaterialSpan(4, 8, 42,
				ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 123L),
			new ColumnRenderSource.SemanticMaterialSpan(8, 12, 43,
				ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 456L)
		);
		var contributor = new ColumnRenderSource.SemanticHorizontalContributor(layers);
		var recovered = JavaSectionBuildReference.recoverCommonHorizontalSpans(
			RenderDataPointUtil.createDataPoint(12, 4, 0xffffffff, 15, 0, 1),
			new ColumnRenderSource.SemanticHorizontalContributor[] { contributor, contributor, contributor, contributor }
		);
		assertEquals(layers, recovered);
	}
}
