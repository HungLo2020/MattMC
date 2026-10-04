package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LodQuadBuilderLayerTest {

	@ParameterizedTest
	@EnumSource(EDhDirection.class)
	void opaqueLeafColorKeepsItsTerrainProgramAndSemanticPayload(EDhDirection direction) {
		LodQuadBuilder builder = new LodQuadBuilder(true, null);
		emit(builder, direction, 0xff336699, EDhApiBlockMaterial.LEAVES);

		assertEquals(1, builder.getCurrentOpaqueQuadsCount(),
			"an opaque reduced leaf color must use dh_terrain, as in Frozen");
		assertEquals(0, builder.getCurrentTransparentQuadsCount());
		assertPayload(builder.makeOpaqueRustSemanticBuffers(), direction, 255, EDhApiBlockMaterial.LEAVES);
		assertTrue(builder.makeTransparentRustSemanticBuffers().packedVertexBuffers().isEmpty());
		assertTrue(builder.makeTransparentUpRustSemanticBuffers().packedVertexBuffers().isEmpty());
		assertTrue(builder.makeTransparentWaterUpRustSemanticBuffers().packedVertexBuffers().isEmpty());
	}

	@ParameterizedTest
	@EnumSource(EDhDirection.class)
	void translucentLeafColorRetainsItsNonWaterLayer(EDhDirection direction) {
		LodQuadBuilder builder = new LodQuadBuilder(true, null);
		emit(builder, direction, 0x80336699, EDhApiBlockMaterial.LEAVES);

		assertEquals(0, builder.getCurrentOpaqueQuadsCount());
		assertEquals(1, builder.getCurrentTransparentQuadsCount());
		assertTrue(builder.makeOpaqueRustSemanticBuffers().packedVertexBuffers().isEmpty());
		assertPayload(direction == EDhDirection.UP
			? builder.makeTransparentUpRustSemanticBuffers()
			: builder.makeTransparentRustSemanticBuffers(), direction, 128, EDhApiBlockMaterial.LEAVES);
		assertTrue(builder.makeTransparentWaterUpRustSemanticBuffers().packedVertexBuffers().isEmpty());
	}

	@ParameterizedTest
	@EnumSource(EDhDirection.class)
	void waterKeepsItsTransparentLayerEvenWithOpaquePackedColor(EDhDirection direction) {
		LodQuadBuilder builder = new LodQuadBuilder(true, null);
		emit(builder, direction, 0xff336699, EDhApiBlockMaterial.WATER);

		assertEquals(0, builder.getCurrentOpaqueQuadsCount());
		assertEquals(1, builder.getCurrentTransparentQuadsCount());
		assertPayload(direction == EDhDirection.UP
			? builder.makeTransparentWaterUpRustSemanticBuffers()
			: builder.makeTransparentRustSemanticBuffers(), direction, 255, EDhApiBlockMaterial.WATER);
		assertTrue(builder.makeTransparentUpRustSemanticBuffers().packedVertexBuffers().isEmpty());
	}

	@Test
	void disabledTransparencyKeepsLeafAndWaterInOpaqueBuffers() {
		for (EDhApiBlockMaterial material : new EDhApiBlockMaterial[] {
			EDhApiBlockMaterial.LEAVES, EDhApiBlockMaterial.WATER
		}) {
			LodQuadBuilder builder = new LodQuadBuilder(false, null);
			emit(builder, EDhDirection.UP, 0x80336699, material);
			assertEquals(1, builder.getCurrentOpaqueQuadsCount());
			assertEquals(0, builder.getCurrentTransparentQuadsCount());
			assertPayload(builder.makeOpaqueRustSemanticBuffers(), EDhDirection.UP, 255, material);
		}
	}

	private static void emit(LodQuadBuilder builder, EDhDirection direction, int color, EDhApiBlockMaterial material) {
		switch (direction) {
			case UP -> builder.addQuadUp((short) 2, (short) 8, (short) 3,
				(short) 4, (short) 5, color, material.index, (byte) 10, (byte) 3, 17);
			case DOWN -> builder.addQuadDown((short) 2, (short) 8, (short) 3,
				(short) 4, (short) 5, color, material.index, (byte) 10, (byte) 3, 17);
			default -> builder.addQuadAdj(direction, (short) 2, (short) 8, (short) 3,
				(short) 4, (short) 5, color, material.index, (byte) 10, (byte) 3, 17);
		}
	}

	private static void assertPayload(LodQuadBuilder.SemanticVertexBufferBuild build,
		EDhDirection direction, int alpha, EDhApiBlockMaterial material) {
		assertEquals(1, build.packedVertexBuffers().size());
		assertArrayEquals(new int[] {17}, build.semanticMaterialIds().getFirst());
		byte[] vertices = build.packedVertexBuffers().getFirst();
		assertEquals(4 * 16, vertices.length);
		for (int vertex = 0; vertex < 4; vertex++) {
			int offset = vertex * 16;
			assertEquals(0x33, Byte.toUnsignedInt(vertices[offset + 8]));
			assertEquals(0x66, Byte.toUnsignedInt(vertices[offset + 9]));
			assertEquals(0x99, Byte.toUnsignedInt(vertices[offset + 10]));
			assertEquals(alpha, Byte.toUnsignedInt(vertices[offset + 11]));
			assertEquals(material.index, vertices[offset + 12]);
			assertEquals(direction.ordinal(), Byte.toUnsignedInt(vertices[offset + 13]));
		}
	}
}
