package net.minecraft.world.entity.animal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class PigVariants {
	public static final ResourceKey<PigVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	public static final ResourceKey<PigVariant> WARM = createKey(TemperatureVariants.WARM);
	public static final ResourceKey<PigVariant> COLD = createKey(TemperatureVariants.COLD);
	public static final ResourceKey<PigVariant> DEFAULT = TEMPERATE;

	private static ResourceKey<PigVariant> createKey(ResourceLocation resourceLocation) {
		return ResourceKey.create(Registries.PIG_VARIANT, resourceLocation);
	}
}
