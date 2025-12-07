package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public interface ModelBaker {
	ResolvedModel getModel(ResourceLocation resourceLocation);

	SpriteGetter sprites();

	<T> T compute(ModelBaker.SharedOperationKey<T> sharedOperationKey);

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface SharedOperationKey<T> {
		T compute(ModelBaker modelBaker);
	}
}
