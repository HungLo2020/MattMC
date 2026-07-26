package net.vulkanic.gui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GuiSpriteAtlas {
	public static final int MAX_EXTENT = 4096;
	private static final Map<RustGalGuiRenderer.TextureGroup, TextureAtlas> ATLASES = new EnumMap<>(RustGalGuiRenderer.TextureGroup.class);

	private GuiSpriteAtlas() {
	}

	static TextureAtlas atlasFor(RustGalGuiRenderer.TextureGroup group) {
		TextureAtlas atlas = ATLASES.get(group);
		if (atlas != null) {
			return atlas;
		}
		TextureAtlas created = buildAtlas(group);
		ATLASES.put(group, created);
		return created;
	}

	static void clear() {
		ATLASES.clear();
	}

	private static TextureAtlas buildAtlas(RustGalGuiRenderer.TextureGroup group) {
		List<RustGalGuiRenderer.GuiSprite> sprites = new ArrayList<>();
		for (RustGalGuiRenderer.GuiSprite sprite : RustGalGuiRenderer.GuiSprite.values()) {
			if (sprite.textureGroup == group) {
				sprites.add(sprite);
			}
		}
		if (sprites.isEmpty()) {
			throw new IllegalStateException("no GUI sprites are assigned to texture group " + group.semanticId);
		}
		int width = 1;
		int height = 0;
		int rowWidth = 0;
		int rowHeight = 0;
		for (RustGalGuiRenderer.GuiSprite sprite : sprites) {
			if (sprite.width > MAX_EXTENT || sprite.height > MAX_EXTENT) {
				throw new IllegalStateException("GUI sprite " + sprite.name() + " exceeds atlas extent " + MAX_EXTENT);
			}
			if (rowWidth > 0 && rowWidth + sprite.width > MAX_EXTENT) {
				width = Math.max(width, rowWidth);
				height += rowHeight;
				rowWidth = 0;
				rowHeight = 0;
			}
			rowWidth += sprite.width;
			rowHeight = Math.max(rowHeight, sprite.height);
		}
		width = Math.max(width, rowWidth);
		height += rowHeight;
		if (height > MAX_EXTENT) {
			throw new IllegalStateException("GUI sprite atlas " + group.semanticId + " exceeds atlas extent " + MAX_EXTENT);
		}
		byte[] bytes = new byte[width * height * 4];
		Map<RustGalGuiRenderer.GuiSprite, AtlasRegion> regions = new EnumMap<>(RustGalGuiRenderer.GuiSprite.class);
		int xOffset = 0;
		int yOffset = 0;
		rowHeight = 0;
		for (RustGalGuiRenderer.GuiSprite sprite : sprites) {
			if (xOffset > 0 && xOffset + sprite.width > MAX_EXTENT) {
				xOffset = 0;
				yOffset += rowHeight;
				rowHeight = 0;
			}
			byte[] spriteBytes = spriteTextureBytes(sprite);
			for (int y = 0; y < sprite.height; y++) {
				int src = y * sprite.width * 4;
				int dst = ((yOffset + y) * width + xOffset) * 4;
				System.arraycopy(spriteBytes, src, bytes, dst, sprite.width * 4);
			}
			regions.put(sprite, new AtlasRegion(xOffset, yOffset, sprite.width, sprite.height));
			xOffset += sprite.width;
			rowHeight = Math.max(rowHeight, sprite.height);
		}
		return new TextureAtlas(group, width, height, bytes, regions);
	}

	private static byte[] spriteTextureBytes(RustGalGuiRenderer.GuiSprite sprite) {
		try (InputStream input = RustGalGuiRenderer.class.getResourceAsStream(sprite.textureResource)) {
			if (input == null) {
				throw new IllegalStateException("missing GUI texture resource: " + sprite.textureResource);
			}
			BufferedImage image = ImageIO.read(input);
			if (image == null || image.getWidth() != sprite.width || image.getHeight() != sprite.height) {
				throw new IllegalStateException("unexpected GUI texture dimensions for " + sprite.textureResource);
			}
			byte[] bytes = new byte[sprite.textureBytes()];
			int offset = 0;
			for (int y = 0; y < sprite.height; y++) {
				for (int x = 0; x < sprite.width; x++) {
					int argb = image.getRGB(x, y);
					bytes[offset++] = (byte)((argb >>> 16) & 0xFF);
					bytes[offset++] = (byte)((argb >>> 8) & 0xFF);
					bytes[offset++] = (byte)(argb & 0xFF);
					bytes[offset++] = (byte)((argb >>> 24) & 0xFF);
				}
			}
			return bytes;
		} catch (IOException error) {
			throw new IllegalStateException("failed to load GUI texture " + sprite.textureResource, error);
		}
	}

	record AtlasRegion(int x, int y, int width, int height) {
	}

	record TextureAtlas(
		RustGalGuiRenderer.TextureGroup group,
		int width,
		int height,
		byte[] bytes,
		Map<RustGalGuiRenderer.GuiSprite, AtlasRegion> regions
	) {
		AtlasRegion region(RustGalGuiRenderer.GuiSprite sprite) {
			AtlasRegion region = this.regions.get(sprite);
			if (region == null) {
				throw new IllegalArgumentException("sprite " + sprite.name() + " is not in atlas " + this.group.semanticId);
			}
			return region;
		}
	}
}
