package net.minecraft.world.item.equipment.trim;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ProvidesTrimMaterial;

public class TrimMaterials {
	public static final ResourceKey<TrimMaterial> QUARTZ = registryKey("quartz");
	public static final ResourceKey<TrimMaterial> IRON = registryKey("iron");
	public static final ResourceKey<TrimMaterial> NETHERITE = registryKey("netherite");
	public static final ResourceKey<TrimMaterial> REDSTONE = registryKey("redstone");
	public static final ResourceKey<TrimMaterial> COPPER = registryKey("copper");
	public static final ResourceKey<TrimMaterial> GOLD = registryKey("gold");
	public static final ResourceKey<TrimMaterial> EMERALD = registryKey("emerald");
	public static final ResourceKey<TrimMaterial> DIAMOND = registryKey("diamond");
	public static final ResourceKey<TrimMaterial> LAPIS = registryKey("lapis");
	public static final ResourceKey<TrimMaterial> AMETHYST = registryKey("amethyst");
	public static final ResourceKey<TrimMaterial> RESIN = registryKey("resin");

	public static Optional<Holder<TrimMaterial>> getFromIngredient(HolderLookup.Provider provider, ItemStack itemStack) {
		ProvidesTrimMaterial providesTrimMaterial = itemStack.get(DataComponents.PROVIDES_TRIM_MATERIAL);
		return providesTrimMaterial != null ? providesTrimMaterial.unwrap(provider) : Optional.empty();
	}

	private static ResourceKey<TrimMaterial> registryKey(String string) {
		return ResourceKey.create(Registries.TRIM_MATERIAL, ResourceLocation.withDefaultNamespace(string));
	}
}
