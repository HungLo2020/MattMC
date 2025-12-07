package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public interface ResolvableModel {
	void resolveDependencies(ResolvableModel.Resolver resolver);

	@Environment(EnvType.CLIENT)
	public interface Resolver {
		void markDependency(ResourceLocation resourceLocation);
	}
}
