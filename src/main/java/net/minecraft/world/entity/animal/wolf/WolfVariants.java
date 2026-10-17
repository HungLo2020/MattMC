package net.minecraft.world.entity.animal.wolf;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class WolfVariants {
	public static final ResourceKey<WolfVariant> PALE = createKey("pale");
	public static final ResourceKey<WolfVariant> SPOTTED = createKey("spotted");
	public static final ResourceKey<WolfVariant> SNOWY = createKey("snowy");
	public static final ResourceKey<WolfVariant> BLACK = createKey("black");
	public static final ResourceKey<WolfVariant> ASHEN = createKey("ashen");
	public static final ResourceKey<WolfVariant> RUSTY = createKey("rusty");
	public static final ResourceKey<WolfVariant> WOODS = createKey("woods");
	public static final ResourceKey<WolfVariant> CHESTNUT = createKey("chestnut");
	public static final ResourceKey<WolfVariant> STRIPED = createKey("striped");
	public static final ResourceKey<WolfVariant> DEFAULT = PALE;

	private static ResourceKey<WolfVariant> createKey(String string) {
		return ResourceKey.create(Registries.WOLF_VARIANT, ResourceLocation.withDefaultNamespace(string));
	}
}
