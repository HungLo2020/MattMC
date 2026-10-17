package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A model with its parents' properties resolved (Rust's
 * `assets/models/resolve.rs`): what item models read while baking.
 */
@Environment(EnvType.CLIENT)
public interface ResolvedModel extends ModelDebugName {
	TextureSlots getTopTextureSlots();

	boolean getTopAmbientOcclusion();

	UnbakedModel.GuiLight getTopGuiLight();

	ItemTransforms getTopTransforms();

	QuadCollection bakeTopGeometry(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState);

	TextureAtlasSprite resolveParticleSprite(TextureSlots textureSlots, ModelBaker modelBaker);
}
