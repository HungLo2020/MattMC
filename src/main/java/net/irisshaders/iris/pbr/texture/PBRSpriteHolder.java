package net.irisshaders.iris.pbr.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public class PBRSpriteHolder {
	protected TextureAtlasSprite normalSprite;
	protected TextureAtlasSprite specularSprite;

	@Nullable
	public TextureAtlasSprite getNormalSprite() {
		return normalSprite;
	}

	@Nullable
	public TextureAtlasSprite getSpecularSprite() {
		return specularSprite;
	}

	public void close() {
		if (normalSprite != null) {
			normalSprite.contents().close();

		}
		if (specularSprite != null) {
			specularSprite.contents().close();
		}
	}
}
