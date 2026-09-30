package net.irisshaders.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.mixinterface.ExtendedBiome;
import net.irisshaders.iris.parsing.BiomeCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_TICK;

public class BiomeUniforms {
	private static final Object2IntMap<ResourceKey<Biome>> biomeMap = new Object2IntOpenHashMap<>();

	public static Object2IntMap<ResourceKey<Biome>> getBiomeMap() {
		return biomeMap;
	}

	private static BiomeCategories getBiomeCategory(Holder<Biome> holder) {
		if (holder.is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
			// Literally only the void has this...
			return BiomeCategories.NONE;
		} else if (holder.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
			return BiomeCategories.ICY;
		} else if (holder.is(BiomeTags.IS_HILL)) {
			return BiomeCategories.EXTREME_HILLS;
		} else if (holder.is(BiomeTags.IS_TAIGA)) {
			return BiomeCategories.TAIGA;
		} else if (holder.is(BiomeTags.IS_OCEAN)) {
			return BiomeCategories.OCEAN;
		} else if (holder.is(BiomeTags.IS_JUNGLE)) {
			return BiomeCategories.JUNGLE;
		} else if (holder.is(BiomeTags.IS_FOREST)) {
			return BiomeCategories.FOREST;
		} else if (holder.is(BiomeTags.IS_BADLANDS)) {
			return BiomeCategories.MESA;
		} else if (holder.is(BiomeTags.IS_NETHER)) {
			return BiomeCategories.NETHER;
		} else if (holder.is(BiomeTags.IS_END)) {
			return BiomeCategories.THE_END;
		} else if (holder.is(BiomeTags.IS_BEACH)) {
			return BiomeCategories.BEACH;
		} else if (holder.is(BiomeTags.HAS_DESERT_PYRAMID)) {
			return BiomeCategories.DESERT;
		} else if (holder.is(BiomeTags.IS_RIVER)) {
			return BiomeCategories.RIVER;
		} else if (holder.is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
			return BiomeCategories.SWAMP;
		} else if (holder.is(BiomeTags.PLAYS_UNDERWATER_MUSIC)) {
			return BiomeCategories.UNDERGROUND;
		} else if (holder.is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
			return BiomeCategories.MUSHROOM;
		} else if (holder.is(BiomeTags.IS_MOUNTAIN)) {
			return BiomeCategories.MOUNTAIN;
		} else {
			return BiomeCategories.PLAINS;
		}
	}

	@FunctionalInterface
	public interface ToFloatFunction<T> {
		/**
		 * Applies this function to the given argument.
		 *
		 * @param value the function argument
		 * @return the function result
		 */
		float applyAsFloat(T value);
	}
}
