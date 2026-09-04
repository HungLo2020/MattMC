package com.seibel.distanthorizons.core.dataObjects.render;

import org.junit.jupiter.api.Test;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnRenderSourceSemanticMaterialTest {
	@Test
	void semanticMaterialTableIsDeduplicatedAndColumnBound() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 4, -64);
		int grass = source.internSemanticMaterial("minecraft:grass_block", "minecraft:plains");
		int grassAgain = source.internSemanticMaterial("minecraft:grass_block", "minecraft:plains");
		int redstone = source.internSemanticMaterial("minecraft:redstone_ore", "minecraft:plains");

		assertEquals(1, grass);
		assertEquals(grass, grassAgain);
		assertEquals(2, redstone);

		source.setSemanticMaterialId(3, 5, 2, grass);
		assertEquals(grass, source.getSemanticMaterialId(3, 5, 2));
		assertEquals("minecraft:grass_block", source.getSemanticMaterialIdentity(grass).blockStateIdentity());
		assertEquals("minecraft:plains", source.getSemanticMaterialIdentity(grass).biomeIdentity());

		source.setSemanticMaterialId(3, 5, 2, ColumnRenderSource.SEMANTIC_MATERIAL_MIXED);
		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, source.getSemanticMaterialId(3, 5, 2));
		assertNull(source.getSemanticMaterialIdentity(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED));

		source.clearSemanticMaterialsForColumn(3, 5);
		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE, source.getSemanticMaterialId(3, 5, 2));
	}

	@Test
	void semanticMaterialTableRejectsUnknownIdsAndEmptyKeys() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 1, 0);
		assertThrows(IllegalArgumentException.class, () -> source.internSemanticMaterial("", "minecraft:plains"));
		assertThrows(IllegalArgumentException.class, () -> source.internSemanticMaterial("minecraft:stone", ""));
		assertThrows(IllegalArgumentException.class, () -> source.setSemanticMaterialId(0, 0, 0, 1));
		assertThrows(IndexOutOfBoundsException.class, () -> source.getSemanticMaterialId(64, 0, 0));
	}

	@Test
	void reducedEntriesRetainOrderedSourceMaterialIntervalsOutsideTheLegacyVertexAbi() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 1, 0);
		int grass = source.internSemanticMaterial("minecraft:grass_block", "minecraft:plains");
		int ore = source.internSemanticMaterial("minecraft:redstone_ore", "minecraft:plains");
		source.setSemanticMaterialId(0, 0, 0, ColumnRenderSource.SEMANTIC_MATERIAL_MIXED);
		source.setSemanticMaterialSpans(0, 0, 0, java.util.List.of(
			new ColumnRenderSource.SemanticMaterialSpan(0, 3, ore,
				ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 91L),
			new ColumnRenderSource.SemanticMaterialSpan(3, 4, grass,
				ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 92L)
		));

		assertEquals(2, source.getSemanticMaterialSpans(0, 0, 0).size());
		assertEquals(ore, source.getSemanticMaterialSpans(0, 0, 0).getFirst().materialId());
		assertEquals(grass, source.getSemanticMaterialSpans(0, 0, 0).getLast().materialId());

		source.clearSemanticMaterialsForColumn(0, 0);
		assertTrue(source.getSemanticMaterialSpans(0, 0, 0).isEmpty());
	}

	@Test
	void compactSemanticSidecarReconstructsExactSeedAndStoresUniformityPerColumn() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 2, -64);
		source.renderDataContainer.set(1, RenderDataPointUtil.createDataPoint(8, 7, 0xffffffff, (byte) 0, (byte) 0, (byte) 0));
		source.setSemanticVariantProvenance(0, 0, 1, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 123L);
		source.setSemanticHorizontalUniformity(0, 0, 1, true);

		assertEquals(ColumnRenderSource.packSemanticVariantPosition(0, -57, 0),
			source.getSemanticVariantPosition(0, 0, 1));
		assertTrue(source.hasSemanticHorizontalUniformity(0, 0, 0));
		source.clearSemanticMaterialsForColumn(0, 0);
		assertEquals(ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, source.getSemanticVariantState(0, 0, 1));
		assertTrue(!source.hasSemanticHorizontalUniformity(0, 0, 1));
	}

	@Test
	void horizontalContributorTransportIsDefensiveAndClearedWithColumn() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 1, 0);
		it.unimi.dsi.fastutil.longs.LongArrayList first = new it.unimi.dsi.fastutil.longs.LongArrayList(new long[] { 11L });
		it.unimi.dsi.fastutil.longs.LongArrayList[] contributors = new it.unimi.dsi.fastutil.longs.LongArrayList[] {
			first, null, new it.unimi.dsi.fastutil.longs.LongArrayList(new long[] { 22L }), null
		};
		source.setSemanticHorizontalContributors(2, 3, contributors);
		first.set(0, 99L);
		assertEquals(11L, source.getSemanticHorizontalContributors(2, 3)[0].getLong(0));
		source.clearSemanticMaterialsForColumn(2, 3);
		assertNull(source.getSemanticHorizontalContributors(2, 3));
	}

	@Test
	void horizontalContributorSpansReturnAClonedContributorArray() {
		ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, 1, 0);
		int stone = source.internSemanticMaterial("minecraft:stone", "minecraft:plains");
		ColumnRenderSource.SemanticHorizontalContributor[] contributors = new ColumnRenderSource.SemanticHorizontalContributor[4];
		contributors[0] = new ColumnRenderSource.SemanticHorizontalContributor(
			java.util.List.of(new ColumnRenderSource.SemanticMaterialSpan(0, 4, stone,
				ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE, 0L)));
		source.setSemanticHorizontalContributorSpans(1, 1, 0, contributors);
		ColumnRenderSource.SemanticHorizontalContributor[] returned = source.getSemanticHorizontalContributorSpans(1, 1, 0);
		assertEquals(4, returned.length);
		returned[0] = null;
		assertTrue(source.getSemanticHorizontalContributorSpans(1, 1, 0)[0] != null);
	}
}
