package com.seibel.distanthorizons.core.dataObjects.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnRenderSourceSemanticMaterialTest {
	@ParameterizedTest(name = "height={0}, column={1}")
	@CsvSource({"1, 0", "1, 64", "1, 4095", "4, 0", "4, 64", "4, 4095",
		"128, 0", "128, 64", "128, 4095"})
	void clearingColumnRemovesAllSidecarsWithoutChangingOtherColumns(int height, int targetColumn) {
		try (ColumnRenderSource source = ColumnRenderSource.createEmpty(0L, height, -64)) {
			int stone = source.internSemanticMaterial("minecraft:stone", "minecraft:plains");
			List<ColumnRenderSource.SemanticMaterialSpan> spans = List.of(
				new ColumnRenderSource.SemanticMaterialSpan(0, 4, stone,
					ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 91L));
			ColumnRenderSource.SemanticHorizontalContributor[] contributors = {
				new ColumnRenderSource.SemanticHorizontalContributor(spans), null, null, null
			};
			LongArrayList[] rawContributors = {new LongArrayList(new long[] {11L}), null, null, null};
			int[] columns = java.util.stream.IntStream.of(0, targetColumn - 1, targetColumn,
				targetColumn + 1, ColumnRenderSource.WIDTH * ColumnRenderSource.WIDTH - 1)
				.filter(column -> column >= 0 && column < ColumnRenderSource.WIDTH * ColumnRenderSource.WIDTH)
				.distinct().toArray();
			for (int column : columns) {
				int x = column / ColumnRenderSource.WIDTH, z = column % ColumnRenderSource.WIDTH;
				source.setSemanticHorizontalContributors(x, z, rawContributors);
				source.setSemanticHorizontalUniformity(x, z, 0, true);
				for (int slot = 0; slot < height; slot++) {
					source.setSemanticMaterialId(x, z, slot, stone);
					source.setSemanticVariantProvenance(x, z, slot, ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 91L);
					source.setSemanticMaterialSpans(x, z, slot, spans);
					source.setSemanticHorizontalContributorSpans(x, z, slot, contributors);
				}
			}

			source.clearSemanticMaterialsForColumn(targetColumn / ColumnRenderSource.WIDTH,
				targetColumn % ColumnRenderSource.WIDTH);
			// Replacing an already empty column must also leave its neighbors intact.
			source.clearSemanticMaterialsForColumn(targetColumn / ColumnRenderSource.WIDTH,
				targetColumn % ColumnRenderSource.WIDTH);
			for (int column : columns) {
				int x = column / ColumnRenderSource.WIDTH, z = column % ColumnRenderSource.WIDTH;
				boolean cleared = column == targetColumn;
				assertEquals(!cleared, source.hasSemanticHorizontalUniformity(x, z, 0));
				if (cleared) assertNull(source.getSemanticHorizontalContributors(x, z));
				else assertArrayEquals(rawContributors, source.getSemanticHorizontalContributors(x, z));
				for (int slot = 0; slot < height; slot++) {
					assertEquals(cleared ? ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE : stone,
						source.getSemanticMaterialId(x, z, slot));
					assertEquals(cleared ? ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE : ColumnRenderSource.SEMANTIC_VARIANT_EXACT,
						source.getSemanticVariantState(x, z, slot));
					assertEquals(cleared ? List.of() : spans, source.getSemanticMaterialSpans(x, z, slot));
					if (cleared) assertNull(source.getSemanticHorizontalContributorSpans(x, z, slot));
					else assertArrayEquals(contributors, source.getSemanticHorizontalContributorSpans(x, z, slot));
				}
			}
			assertEquals(1, source.semanticMaterials().size());
		}
	}

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
