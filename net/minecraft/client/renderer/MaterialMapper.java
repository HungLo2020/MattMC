package net.minecraft.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public record MaterialMapper(ResourceLocation sheet, String prefix) {
	public Material apply(ResourceLocation resourceLocation) {
		return new Material(this.sheet, resourceLocation.withPrefix(this.prefix + "/"));
	}

	public Material defaultNamespaceApply(String string) {
		return this.apply(ResourceLocation.withDefaultNamespace(string));
	}
}
