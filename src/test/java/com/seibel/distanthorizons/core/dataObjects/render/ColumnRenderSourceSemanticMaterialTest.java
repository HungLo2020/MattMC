package com.seibel.distanthorizons.core.dataObjects.render;

import org.junit.jupiter.api.Test;

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
}
