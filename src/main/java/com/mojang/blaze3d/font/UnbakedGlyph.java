package com.mojang.blaze3d.font;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

@Environment(EnvType.CLIENT)
public interface UnbakedGlyph {
	GlyphInfo info();

	BakedGlyph bake(UnbakedGlyph.Stitcher stitcher);

	@Environment(EnvType.CLIENT)
	public interface Stitcher {
		BakedGlyph stitch(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap);

		BakedGlyph getMissing();
	}
}
