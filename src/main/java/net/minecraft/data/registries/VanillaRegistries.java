package net.minecraft.data.registries;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.DimensionTypes;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.data.worldgen.StructureSets;
import net.minecraft.data.worldgen.Structures;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.gametest.framework.GameTestEnvironments;
import net.minecraft.gametest.framework.GameTestInstances;
import net.minecraft.network.chat.ChatType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.Dialogs;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfigs;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public class VanillaRegistries {
	private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
		.add(Registries.DIMENSION_TYPE, DimensionTypes::bootstrap)
		.add(Registries.CONFIGURED_CARVER, Carvers::bootstrap)
		.add(Registries.CONFIGURED_FEATURE, FeatureUtils::bootstrap)
		.add(Registries.PLACED_FEATURE, PlacementUtils::bootstrap)
		.add(Registries.STRUCTURE, Structures::bootstrap)
		.add(Registries.STRUCTURE_SET, StructureSets::bootstrap)
		.add(Registries.PROCESSOR_LIST, ProcessorLists::bootstrap)
		.add(Registries.TEMPLATE_POOL, Pools::bootstrap)
		.add(Registries.BIOME, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.BIOME, net.minecraft.world.level.biome.Biome.DIRECT_CODEC))
		.add(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterLists::bootstrap)
		.add(Registries.NOISE, NoiseData::bootstrap)
		.add(Registries.DENSITY_FUNCTION, NoiseRouterData::bootstrap)
		.add(Registries.NOISE_SETTINGS, NoiseGeneratorSettings::bootstrap)
		.add(Registries.WORLD_PRESET, WorldPresets::bootstrap)
		.add(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPresets::bootstrap)
		.add(Registries.CHAT_TYPE, ChatType::bootstrap)
		.add(Registries.TRIM_PATTERN, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.TRIM_PATTERN, net.minecraft.world.item.equipment.trim.TrimPattern.DIRECT_CODEC))
		.add(Registries.TRIM_MATERIAL, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.TRIM_MATERIAL, net.minecraft.world.item.equipment.trim.TrimMaterial.DIRECT_CODEC))
		.add(Registries.TRIAL_SPAWNER_CONFIG, TrialSpawnerConfigs::bootstrap)
		.add(Registries.WOLF_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.WOLF_VARIANT, net.minecraft.world.entity.animal.wolf.WolfVariant.DIRECT_CODEC))
		.add(Registries.WOLF_SOUND_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.WOLF_SOUND_VARIANT, net.minecraft.world.entity.animal.wolf.WolfSoundVariant.DIRECT_CODEC))
		.add(Registries.PAINTING_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.PAINTING_VARIANT, net.minecraft.world.entity.decoration.PaintingVariant.DIRECT_CODEC))
		.add(Registries.DAMAGE_TYPE, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.DAMAGE_TYPE, net.minecraft.world.damagesource.DamageType.DIRECT_CODEC))
		.add(Registries.BANNER_PATTERN, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.BANNER_PATTERN, net.minecraft.world.level.block.entity.BannerPattern.DIRECT_CODEC))
		.add(Registries.ENCHANTMENT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.ENCHANTMENT, net.minecraft.world.item.enchantment.Enchantment.DIRECT_CODEC))
		.add(Registries.ENCHANTMENT_PROVIDER, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.ENCHANTMENT_PROVIDER, net.minecraft.world.item.enchantment.providers.EnchantmentProvider.DIRECT_CODEC))
		.add(Registries.JUKEBOX_SONG, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.JUKEBOX_SONG, net.minecraft.world.item.JukeboxSong.DIRECT_CODEC))
		.add(Registries.INSTRUMENT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.INSTRUMENT, net.minecraft.world.item.Instrument.DIRECT_CODEC))
		.add(Registries.PIG_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.PIG_VARIANT, net.minecraft.world.entity.animal.PigVariant.DIRECT_CODEC))
		.add(Registries.COW_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.COW_VARIANT, net.minecraft.world.entity.animal.CowVariant.DIRECT_CODEC))
		.add(Registries.CHICKEN_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.CHICKEN_VARIANT, net.minecraft.world.entity.animal.ChickenVariant.DIRECT_CODEC))
		.add(Registries.TEST_ENVIRONMENT, GameTestEnvironments::bootstrap)
		.add(Registries.TEST_INSTANCE, GameTestInstances::bootstrap)
		.add(Registries.FROG_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.FROG_VARIANT, net.minecraft.world.entity.animal.frog.FrogVariant.DIRECT_CODEC))
		.add(Registries.CAT_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.CAT_VARIANT, net.minecraft.world.entity.animal.CatVariant.DIRECT_CODEC))
		.add(Registries.ZOMBIE_NAUTILUS_VARIANT, net.minecraft.resources.NativeData.bootstrap(net.minecraft.resources.NativeData.Kind.ZOMBIE_NAUTILUS_VARIANT, net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant.DIRECT_CODEC))
		.add(Registries.DIALOG, Dialogs::bootstrap);

	private static void validateThatAllBiomeFeaturesHaveBiomeFilter(HolderLookup.Provider provider) {
		validateThatAllBiomeFeaturesHaveBiomeFilter(provider.lookupOrThrow(Registries.PLACED_FEATURE), provider.lookupOrThrow(Registries.BIOME));
	}

	public static void validateThatAllBiomeFeaturesHaveBiomeFilter(HolderGetter<PlacedFeature> holderGetter, HolderLookup<Biome> holderLookup) {
		holderLookup.listElements().forEach(reference -> {
			ResourceLocation resourceLocation = reference.key().location();
			List<HolderSet<PlacedFeature>> list = ((Biome)reference.value()).getGenerationSettings().features();
			list.stream().flatMap(HolderSet::stream).forEach(holder -> holder.unwrap().ifLeft(resourceKey -> {
				Holder.Reference<PlacedFeature> referencexx = holderGetter.getOrThrow(resourceKey);
				if (!validatePlacedFeature(referencexx.value())) {
					Util.logAndPauseIfInIde("Placed feature " + resourceKey.location() + " in biome " + resourceLocation + " is missing BiomeFilter.biome()");
				}
			}).ifRight(placedFeature -> {
				if (!validatePlacedFeature(placedFeature)) {
					Util.logAndPauseIfInIde("Placed inline feature in biome " + reference + " is missing BiomeFilter.biome()");
				}
			}));
		});
	}

	private static boolean validatePlacedFeature(PlacedFeature placedFeature) {
		return placedFeature.placement().contains(BiomeFilter.biome());
	}

	public static HolderLookup.Provider createLookup() {
		RegistryAccess.Frozen frozen = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		HolderLookup.Provider provider = BUILDER.build(frozen);
		validateThatAllBiomeFeaturesHaveBiomeFilter(provider);
		return provider;
	}
}
