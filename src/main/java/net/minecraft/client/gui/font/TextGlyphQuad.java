package net.minecraft.client.gui.font;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * One fully resolved font quad expressed without a Java GPU object. Corners
 * retain Minecraft's glyph vertex order: top-left, bottom-left, bottom-right,
 * top-right. Consumers must map that order to their own affine basis
 * explicitly rather than assuming the second corner is the U-axis endpoint.
 *
 * <p>This is intentionally a semantic extraction type. The atlas identifier
 * names a resource generation owned by the font stitcher; it is not a texture
 * handle or a Java renderer object.</p>
 */
@Environment(EnvType.CLIENT)
public record TextGlyphQuad(
	String atlasIdentity,
	boolean colored,
	float x0,
	float y0,
	float x1,
	float y1,
	float x2,
	float y2,
	float x3,
	float y3,
	float z,
	float u0,
	float v0,
	float u1,
	float v1,
	int colorArgb
) {
}
