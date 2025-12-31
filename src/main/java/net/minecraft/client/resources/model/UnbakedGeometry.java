package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.TextureSlots;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface UnbakedGeometry {
	UnbakedGeometry EMPTY = (textureSlots, modelBaker, modelState, modelDebugName) -> QuadCollection.EMPTY;

	QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName modelDebugName);
}
