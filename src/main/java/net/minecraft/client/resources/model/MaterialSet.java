package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

@Environment(EnvType.CLIENT)
public interface MaterialSet {
	TextureAtlasSprite get(Material material);
}
