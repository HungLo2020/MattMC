package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public interface CatVariants {
	ResourceKey<CatVariant> TABBY = createKey("tabby");
	ResourceKey<CatVariant> BLACK = createKey("black");
	ResourceKey<CatVariant> RED = createKey("red");
	ResourceKey<CatVariant> SIAMESE = createKey("siamese");
	ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
	ResourceKey<CatVariant> CALICO = createKey("calico");
	ResourceKey<CatVariant> PERSIAN = createKey("persian");
	ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
	ResourceKey<CatVariant> WHITE = createKey("white");
	ResourceKey<CatVariant> JELLIE = createKey("jellie");
	ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

	private static ResourceKey<CatVariant> createKey(String string) {
		return ResourceKey.create(Registries.CAT_VARIANT, ResourceLocation.withDefaultNamespace(string));
	}
}
