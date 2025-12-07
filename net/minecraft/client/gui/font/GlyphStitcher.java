package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlyphStitcher implements AutoCloseable {
	private final TextureManager textureManager;
	private final ResourceLocation texturePrefix;
	private final List<FontTexture> textures = new ArrayList();

	public GlyphStitcher(TextureManager textureManager, ResourceLocation resourceLocation) {
		this.textureManager = textureManager;
		this.texturePrefix = resourceLocation;
	}

	public void reset() {
		int i = this.textures.size();
		this.textures.clear();

		for (int j = 0; j < i; j++) {
			this.textureManager.release(this.textureName(j));
		}
	}

	public void close() {
		this.reset();
	}

	@Nullable
	public BakedSheetGlyph stitch(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap) {
		for (FontTexture fontTexture : this.textures) {
			BakedSheetGlyph bakedSheetGlyph = fontTexture.add(glyphInfo, glyphBitmap);
			if (bakedSheetGlyph != null) {
				return bakedSheetGlyph;
			}
		}

		int i = this.textures.size();
		ResourceLocation resourceLocation = this.textureName(i);
		boolean bl = glyphBitmap.isColored();
		GlyphRenderTypes glyphRenderTypes = bl
			? GlyphRenderTypes.createForColorTexture(resourceLocation)
			: GlyphRenderTypes.createForIntensityTexture(resourceLocation);
		FontTexture fontTexture2 = new FontTexture(resourceLocation::toString, glyphRenderTypes, bl);
		this.textures.add(fontTexture2);
		this.textureManager.register(resourceLocation, fontTexture2);
		return fontTexture2.add(glyphInfo, glyphBitmap);
	}

	private ResourceLocation textureName(int i) {
		return this.texturePrefix.withSuffix("/" + i);
	}
}
