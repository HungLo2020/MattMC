package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.TemperatureVariants;

public class ZombieNautilusVariants {
	public static final ResourceKey<ZombieNautilusVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
	public static final ResourceKey<ZombieNautilusVariant> WARM = createKey(TemperatureVariants.WARM);
	public static final ResourceKey<ZombieNautilusVariant> DEFAULT = TEMPERATE;

	private static ResourceKey<ZombieNautilusVariant> createKey(ResourceLocation resourceLocation) {
		return ResourceKey.create(Registries.ZOMBIE_NAUTILUS_VARIANT, resourceLocation);
	}
}
