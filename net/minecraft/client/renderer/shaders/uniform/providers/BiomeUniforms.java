// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.shaders.parsing.BiomeCategories;
import net.minecraft.client.renderer.shaders.uniform.FloatSupplier;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_TICK;

/**
 * Implements uniforms relating to biomes.
 * 
 * Based on IRIS's BiomeUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/BiomeUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (5 uniforms):
 * - biome: Current biome ID
 * - biome_category: Current biome category (enum ordinal)
 * - biome_precipitation: Precipitation type (0=none, 1=rain, 2=snow)
 * - rainfall: Rainfall/downfall value
 * - temperature: Biome base temperature
 */
public class BiomeUniforms {
	private static final ConcurrentHashMap<ResourceKey<Biome>, Integer> biomeMap = new ConcurrentHashMap<>();
	private static int nextBiomeId = 0;

	public static ConcurrentHashMap<ResourceKey<Biome>, Integer> getBiomeMap() {
		return biomeMap;
	}

	public static void addBiomeUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1i(PER_TICK, "biome", playerI(player -> {
				ResourceKey<Biome> biomeKey = player.level().getBiome(player.blockPosition()).unwrapKey().orElse(null);
				if (biomeKey == null) return 0;
				return biomeMap.computeIfAbsent(biomeKey, k -> nextBiomeId++);
			}))
			.uniform1i(PER_TICK, "biome_category", playerI(player -> {
				Holder<Biome> holder = player.level().getBiome(player.blockPosition());
				return getBiomeCategory(holder).ordinal();
			}))
			.uniform1i(PER_TICK, "biome_precipitation", playerI(player -> {
				Biome.Precipitation precipitation = player.level().getBiome(player.blockPosition()).value()
					.getPrecipitationAt(player.blockPosition(), player.level().getSeaLevel());
				return switch (precipitation) {
					case NONE -> 0;
					case RAIN -> 1;
					case SNOW -> 2;
				};
			}))
			.uniform1f(PER_TICK, "rainfall", playerF(player -> {
				// Note: IRIS uses ExtendedBiome mixin to access downfall
				// For MattMC, we use a default value of 0.5 (moderate rainfall)
				// This could be enhanced with a mixin in the future
				return 0.5f;
			}))
			.uniform1f(PER_TICK, "temperature", playerF(player ->
				player.level().getBiome(player.blockPosition()).value().getBaseTemperature()));
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

	static IntSupplier playerI(ToIntFunction<LocalPlayer> function) {
		return () -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null) {
				return 0;
			} else {
				return function.applyAsInt(player);
			}
		};
	}

	static FloatSupplier playerF(ToFloatFunction<LocalPlayer> function) {
		return () -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null) {
				return 0.0f;
			} else {
				return function.applyAsFloat(player);
			}
		};
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
