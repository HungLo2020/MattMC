package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class CowVariants {
	public static final ResourceKey<CowVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	public static final ResourceKey<CowVariant> WARM = createKey(TemperatureVariants.WARM);
	public static final ResourceKey<CowVariant> COLD = createKey(TemperatureVariants.COLD);
	public static final ResourceKey<CowVariant> DEFAULT = TEMPERATE;

	private static ResourceKey<CowVariant> createKey(ResourceLocation resourceLocation) {
		return ResourceKey.create(Registries.COW_VARIANT, resourceLocation);
	}
}
