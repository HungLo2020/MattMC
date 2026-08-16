package net.vulkanic.gui;

import net.minecraft.client.gui.font.TextGlyphQuad;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RustGalGuiRendererTest {
	@Test
	void textQuadUsesMinecraftCornerOrderToBuildTheAffineBasis() {
		TextGlyphQuad quad = new TextGlyphQuad(
			"minecraft:font/default/0", false,
			10.0F, 20.0F,
			10.0F, 30.0F,
			18.0F, 30.0F,
			18.0F, 20.0F,
			0.0F, 0.1F, 0.2F, 0.4F, 0.7F, 0xFFFFFFFF
		);

		var request = RustGalGuiRenderer.transformTextQuad(quad, new Matrix3x2f(), 41L, 320, 180, null);

		assertEquals(10.0F, request.x0());
		assertEquals(20.0F, request.y0());
		assertEquals(18.0F, request.x1(), "U axis must end at the top-right glyph corner");
		assertEquals(20.0F, request.y1());
		assertEquals(10.0F, request.x3(), "V axis must end at the bottom-left glyph corner");
		assertEquals(30.0F, request.y3());
	}
}
