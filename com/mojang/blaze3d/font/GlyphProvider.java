package com.mojang.blaze3d.font;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.FontOption;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GlyphProvider extends AutoCloseable {
	float BASELINE = 7.0F;

	default void close() {
	}

	@Nullable
	default UnbakedGlyph getGlyph(int i) {
		return null;
	}

	IntSet getSupportedGlyphs();

	@Environment(EnvType.CLIENT)
	public record Conditional(GlyphProvider provider, FontOption.Filter filter) implements AutoCloseable {
		public void close() {
			this.provider.close();
		}
	}
}
