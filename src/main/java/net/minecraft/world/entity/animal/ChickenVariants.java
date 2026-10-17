package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class ChickenVariants {
	public static final ResourceKey<ChickenVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	public static final ResourceKey<ChickenVariant> WARM = createKey(TemperatureVariants.WARM);
	public static final ResourceKey<ChickenVariant> COLD = createKey(TemperatureVariants.COLD);
	public static final ResourceKey<ChickenVariant> DEFAULT = TEMPERATE;

	private static ResourceKey<ChickenVariant> createKey(ResourceLocation resourceLocation) {
		return ResourceKey.create(Registries.CHICKEN_VARIANT, resourceLocation);
	}
}
