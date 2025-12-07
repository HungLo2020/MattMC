package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

@Environment(EnvType.CLIENT)
public interface SpriteGetter {
	TextureAtlasSprite get(Material material, ModelDebugName modelDebugName);

	TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName);

	default TextureAtlasSprite resolveSlot(TextureSlots textureSlots, String string, ModelDebugName modelDebugName) {
		Material material = textureSlots.getMaterial(string);
		return material != null ? this.get(material, modelDebugName) : this.reportMissingReference(string, modelDebugName);
	}
}
