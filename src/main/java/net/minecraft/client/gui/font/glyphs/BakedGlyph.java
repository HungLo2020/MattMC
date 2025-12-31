package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface BakedGlyph {
	GlyphInfo info();

	@Nullable
	TextRenderable createGlyph(float f, float g, int i, int j, Style style, float h, float k);
}
