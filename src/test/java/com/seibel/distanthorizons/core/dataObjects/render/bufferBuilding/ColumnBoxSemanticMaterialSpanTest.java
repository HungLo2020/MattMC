package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnBoxSemanticMaterialSpanTest {
	@Test
	void verticalFaceRetainsEachReducedSourceMaterialInterval() {
		List<JavaColumnBoxReference.SemanticFaceSegment> segments = JavaColumnBoxReference.semanticVerticalFaceSegments(
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
			new JavaColumnBoxReference.SemanticFaceSegment(1, 2, 7, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 101L),
			new JavaColumnBoxReference.SemanticFaceSegment(3, 2, 8, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 202L),
			new JavaColumnBoxReference.SemanticFaceSegment(5, 1, ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, ColumnRenderSource.SEMANTIC_VARIANT_MIXED, 0L)
		), segments);
	}

	@Test
	void completeSpanCoverageDoesNotInventFallbackTextureIdentity() {
		List<JavaColumnBoxReference.SemanticFaceSegment> segments = JavaColumnBoxReference.semanticVerticalFaceSegments(
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
			new JavaColumnBoxReference.SemanticFaceSegment(4, 2, 11, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 111L),
			new JavaColumnBoxReference.SemanticFaceSegment(6, 2, 12, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 222L)
		), segments);
	}

	@Test
	void faceWithoutSpansIsOneLegacyQuadAndEmptyFaceIsNone() {
		// Ordinary play keeps no semantic spans: each vertical face must be the
		// single Frozen quad, not a list of material segments.
		for (var direction : new com.seibel.distanthorizons.core.enums.EDhDirection[] {
			com.seibel.distanthorizons.core.enums.EDhDirection.NORTH,
			com.seibel.distanthorizons.core.enums.EDhDirection.EAST}) {
			LodQuadBuilder builder = new LodQuadBuilder(false, null);
			JavaColumnBoxReference.addVerticalFaces(JavaQuadSink.of(builder), direction, (short) 1, (short) 4, (short) 2, (short) 16, (short) 12,
				0xFF336699, (byte) 1, (byte) 15, (byte) 0, ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE,
				ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L, List.of());
			assertEquals(1, builder.getCurrentOpaqueQuadsCount());
			LodQuadBuilder empty = new LodQuadBuilder(false, null);
			JavaColumnBoxReference.addVerticalFaces(JavaQuadSink.of(empty), direction, (short) 1, (short) 4, (short) 2, (short) 16, (short) 0,
				0xFF336699, (byte) 1, (byte) 15, (byte) 0, ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE,
				ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L, List.of());
			assertEquals(0, empty.getCurrentOpaqueQuadsCount());
		}
	}

	@Test
	void emptySpanSplitterAgreesWithTheOneQuadPath() {
		List<JavaColumnBoxReference.SemanticFaceSegment> segments = JavaColumnBoxReference.semanticVerticalFaceSegments(
			(short) 4, (short) 12, 7, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L, List.of());
		assertEquals(List.of(new JavaColumnBoxReference.SemanticFaceSegment(4, 12, 7, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L)), segments);
		assertEquals(List.of(), JavaColumnBoxReference.semanticVerticalFaceSegments(
			(short) 4, (short) 0, 7, ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L, List.of()));
	}
}
