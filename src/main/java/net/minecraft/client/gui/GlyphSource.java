package net.minecraft.client.gui;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public interface GlyphSource {
	BakedGlyph getGlyph(int i);

	BakedGlyph getRandomGlyph(RandomSource randomSource, int i);
}
