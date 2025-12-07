package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EmptyGlyph implements UnbakedGlyph {
	final GlyphInfo info;

	public EmptyGlyph(float f) {
		this.info = GlyphInfo.simple(f);
	}

	@Override
	public GlyphInfo info() {
		return this.info;
	}

	@Override
	public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
		return new BakedGlyph() {
			@Override
			public GlyphInfo info() {
				return EmptyGlyph.this.info;
			}

			@Nullable
			@Override
			public TextRenderable createGlyph(float f, float g, int i, int j, Style style, float h, float k) {
				return null;
			}
		};
	}
}
