package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnBoxSemanticMaterialSpanTest {
	@Test
	void verticalFaceRetainsEachReducedSourceMaterialInterval() {
		List<ColumnBox.SemanticFaceSegment> segments = ColumnBox.semanticVerticalFaceSegments(
			(short) 1, (short) 5,
			ColumnRenderSource.SEMANTIC_MATERIAL_MIXED,
			ColumnRenderSource.SEMANTIC_VARIANT_MIXED, 0L,
			List.of(
				new ColumnRenderSource.SemanticMaterialSpan(
					0, 3, 7, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 101L
				),
				new ColumnRenderSource.SemanticMaterialSpan(
					3, 5, 8, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 202L
				)
			)
		);

		assertEquals(List.of(
			new ColumnBox.SemanticFaceSegment(1, 2, 7, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 101L),
			new ColumnBox.SemanticFaceSegment(3, 2, 8, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 202L),
			new ColumnBox.SemanticFaceSegment(5, 1, ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, ColumnRenderSource.SEMANTIC_VARIANT_MIXED, 0L)
		), segments);
	}

	@Test
	void completeSpanCoverageDoesNotInventFallbackTextureIdentity() {
		List<ColumnBox.SemanticFaceSegment> segments = ColumnBox.semanticVerticalFaceSegments(
			(short) 4, (short) 4,
			99, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 99L,
			List.of(
				new ColumnRenderSource.SemanticMaterialSpan(
					4, 6, 11, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 111L
				),
				new ColumnRenderSource.SemanticMaterialSpan(
					6, 8, 12, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 222L
				)
			)
		);

		assertEquals(List.of(
			new ColumnBox.SemanticFaceSegment(4, 2, 11, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 111L),
			new ColumnBox.SemanticFaceSegment(6, 2, 12, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 222L)
		), segments);
	}
}
