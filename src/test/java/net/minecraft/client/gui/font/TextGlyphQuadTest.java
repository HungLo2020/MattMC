package net.minecraft.client.gui.font;

import java.util.ArrayList;
import java.util.List;
import net.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextGlyphQuadTest {
	private static BakedSheetGlyph glyph() {
		return new BakedSheetGlyph(
			GlyphInfo.simple(6.0F),
			null,
			null,
			"minecraft:font/default/0",
			false,
			0.1F,
			0.4F,
			0.2F,
			0.7F,
			1.0F,
			5.0F,
			-2.0F,
			6.0F
		);
	}

	@Test
	void glyphExtractionPreservesAtlasIdentityAndLayeredGlyphGeometry() {
		var renderable = glyph().createGlyph(10.0F, 20.0F, 0xFFABCDEF, 0x7F010203, Style.EMPTY.withBold(true).withItalic(true), 0.5F, 1.0F);
		List<TextGlyphQuad> quads = new ArrayList<>();
		assertEquals(4, renderable.collectSemanticQuads(quads::add));

		assertEquals(4, quads.size(), "shadow/main and their bold companions remain distinct semantic quads");
		assertTrue(quads.stream().allMatch(quad -> quad.atlasIdentity().equals("minecraft:font/default/0")));
		assertTrue(quads.stream().allMatch(quad -> !quad.colored()));
		assertEquals(0x7F010203, quads.getFirst().colorArgb());
		assertEquals(0.0F, quads.getFirst().z());
		assertEquals(0.001F, quads.get(1).z());
		assertEquals(0.03F, quads.get(2).z());
		assertEquals(0.031F, quads.get(3).z());
		assertTrue(quads.get(2).x0() != quads.get(2).x1(), "italic glyph keeps its affine shear instead of collapsing to a rectangle");
	}

	@Test
	void effectExtractionPreservesShadowAndDepthOrdering() {
		var renderable = glyph().createEffect(2.0F, 3.0F, 8.0F, 9.0F, 0.02F, 0xFF112233, 0xFF445566, 1.0F);
		List<TextGlyphQuad> quads = new ArrayList<>();
		assertEquals(2, renderable.collectSemanticQuads(quads::add));

		assertEquals(2, quads.size());
		assertEquals(0xFF445566, quads.getFirst().colorArgb());
		assertEquals(0.02F, quads.getFirst().z());
		assertEquals(0xFF112233, quads.get(1).colorArgb());
		assertEquals(0.05F, quads.get(1).z(), 0.00001F);
		assertEquals(9.0F, quads.get(1).y0());
		assertEquals(3.0F, quads.get(1).y2());
	}
}
