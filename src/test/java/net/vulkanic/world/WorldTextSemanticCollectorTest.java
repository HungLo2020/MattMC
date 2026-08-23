package net.vulkanic.world;

import net.minecraft.client.gui.font.TextGlyphQuad;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldTextSemanticCollectorTest {
	@Test
	void worldTextQuadCopiesTheModelViewMatrix() {
		float[] matrix = identityMatrix();
		WorldTextSemanticCollector.WorldTextQuad quad = quad(matrix);
		matrix[0] = 7.0F;
		assertArrayEquals(identityMatrix(), quad.modelViewMatrix());
		float[] returned = quad.modelViewMatrix();
		returned[5] = 9.0F;
		assertArrayEquals(identityMatrix(), quad.modelViewMatrix());
	}

	@Test
	void bridgeRecordKeepsGlyphCornerAndAtlasSemantics() {
		WorldTextSemanticCollector.WorldTextQuad quad = new WorldTextSemanticCollector.WorldTextQuad(
			"minecraft:font/ascii", 1L, 1L, false, WorldTextSemanticCollector.DEPTH_POLYGON_OFFSET,
			0, 0xFFFFFFFF, 0.0, identityMatrix(), glyph()
		);
		var record = quad.toBridgeRecord();
		assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 0.0F, 8.0F, 0.0F, 8.0F, 8.0F, 0.0F, 8.0F, 0.0F, 0.0F}, record.positions());
		assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F}, record.uvs());
		assertEquals(WorldTextSemanticCollector.semanticAssetId("minecraft:font/ascii", false), record.assetId());
		assertEquals(WorldTextSemanticCollector.DEPTH_POLYGON_OFFSET, record.depthPolicy());
		assertEquals(0xFFFFFFFF, record.colorArgb());
	}

	@Test
	void worldTextQuadRejectsInvalidTransportState() {
		assertThrows(IllegalArgumentException.class, () -> quad(new float[15]));
		float[] nonFinite = identityMatrix();
		nonFinite[7] = Float.NaN;
		assertThrows(IllegalArgumentException.class, () -> quad(nonFinite));
		assertThrows(IllegalArgumentException.class, () -> new WorldTextSemanticCollector.WorldTextQuad(
			"minecraft:font/ascii", 1L, 1L, false, 99, 0, 0xFFFFFFFF, 0.0, identityMatrix(), glyph()
		));
	}

	@Test
	void ordinaryTextAdmissionPreservesExplicitDepthModesIncludingOutline() {
		assertEquals(WorldTextSemanticCollector.DEPTH_NORMAL,
			WorldTextSemanticCollector.textSubmitDepthPolicy(textSubmit(Font.DisplayMode.NORMAL, 0)));
		assertEquals(WorldTextSemanticCollector.DEPTH_SEE_THROUGH,
			WorldTextSemanticCollector.textSubmitDepthPolicy(textSubmit(Font.DisplayMode.SEE_THROUGH, 0)));
		assertEquals(WorldTextSemanticCollector.DEPTH_POLYGON_OFFSET,
			WorldTextSemanticCollector.textSubmitDepthPolicy(textSubmit(Font.DisplayMode.POLYGON_OFFSET, 0)));
		assertEquals(WorldTextSemanticCollector.DEPTH_NORMAL,
			WorldTextSemanticCollector.textSubmitDepthPolicy(textSubmit(Font.DisplayMode.NORMAL, 0xFF000000)));
	}

	@Test
	void rawTextureAtlasGenerationSuppliesAStablePositiveRevision() {
		assertEquals(7L, WorldTextSemanticCollector.rawAtlasRevision(7L));
		assertThrows(IllegalArgumentException.class,
			() -> WorldTextSemanticCollector.rawAtlasRevision(0L));
	}

	private static SubmitNodeStorage.TextSubmit textSubmit(Font.DisplayMode mode, int outlineColor) {
		return new SubmitNodeStorage.TextSubmit(new Matrix4f(), 0.0F, 0.0F, null, false, mode, 0, -1, 0, outlineColor);
	}

	private static WorldTextSemanticCollector.WorldTextQuad quad(float[] matrix) {
		return new WorldTextSemanticCollector.WorldTextQuad(
			"minecraft:font/ascii", 1L, 1L, false, WorldTextSemanticCollector.DEPTH_NORMAL,
			0, 0xFFFFFFFF, 0.0, matrix, glyph()
		);
	}

	private static TextGlyphQuad glyph() {
		return new TextGlyphQuad("minecraft:font/ascii", false, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8.0F,
			8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
	}

	private static float[] identityMatrix() {
		return new float[] {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F
		};
	}
}
