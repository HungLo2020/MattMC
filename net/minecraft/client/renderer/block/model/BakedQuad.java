package net.minecraft.client.renderer.block.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public record BakedQuad(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, int lightEmission) {
	public boolean isTinted() {
		return this.tintIndex != -1;
	}
}
