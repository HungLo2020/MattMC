package net.minecraft.world.entity.animal.frog;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.TemperatureVariants;

public interface FrogVariants {
	ResourceKey<FrogVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	ResourceKey<FrogVariant> WARM = createKey(TemperatureVariants.WARM);
	ResourceKey<FrogVariant> COLD = createKey(TemperatureVariants.COLD);

	private static ResourceKey<FrogVariant> createKey(ResourceLocation resourceLocation) {
		return ResourceKey.create(Registries.FROG_VARIANT, resourceLocation);
	}
}
