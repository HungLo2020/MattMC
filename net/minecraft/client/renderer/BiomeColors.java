package net.minecraft.client.renderer;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.advanced.AdvancedRenderingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

@Environment(EnvType.CLIENT)
public class BiomeColors {
	public static final ColorResolver GRASS_COLOR_RESOLVER = Biome::getGrassColor;
	public static final ColorResolver FOLIAGE_COLOR_RESOLVER = (biome, d, e) -> biome.getFoliageColor();
	public static final ColorResolver DRY_FOLIAGE_COLOR_RESOLVER = (biome, d, e) -> biome.getDryFoliageColor();
	public static final ColorResolver WATER_COLOR_RESOLVER = (biome, d, e) -> biome.getWaterColor();

	// ===== BEGIN SODIUM BIOME COLOR OPTIMIZATION =====
	// Originally from: sodium.mixin.core.world.biome.BiomeColorsMixin
	// Step 12: Inline Biome Color Mixins
	
	/**
	 * Calculates average biome color with Sodium optimization support.
	 * Routes to either Sodium's optimized color blending (using cached color maps)
	 * or vanilla color blending based on configuration.
	 * 
	 * @param blockAndTintGetter The level to query colors from
	 * @param blockPos Position to sample color at
	 * @param colorResolver The color resolver function
	 * @return The blended color value
	 */
	private static int getAverageColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, ColorResolver colorResolver) {
		if (AdvancedRenderingConfig.isEnabled()) {
			return getAverageColorSodium(blockAndTintGetter, blockPos, colorResolver);
		}
		return getAverageColorVanilla(blockAndTintGetter, blockPos, colorResolver);
	}
	
	/**
	 * Sodium's optimized color blending path using cached color maps.
	 * Placeholder for future Sodium implementation (Phase 3).
	 * Currently delegates to vanilla path until Sodium biome color caching is migrated.
	 * 
	 * Sodium's implementation caches biome color samples in spatial hash maps
	 * to avoid redundant BlockAndTintGetter.getBlockTint() calls.
	 * 
	 * @param blockAndTintGetter The level to query colors from
	 * @param blockPos Position to sample color at
	 * @param colorResolver The color resolver function
	 * @return The blended color value
	 */
	private static int getAverageColorSodium(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, ColorResolver colorResolver) {
		// Placeholder - actual Sodium implementation will be added in Phase 3
		// For now, delegate to vanilla path to maintain functionality
		return getAverageColorVanilla(blockAndTintGetter, blockPos, colorResolver);
	}
	
	/**
	 * Vanilla biome color blending path (original Minecraft implementation).
	 * This method contains the original color sampling logic.
	 * 
	 * @param blockAndTintGetter The level to query colors from
	 * @param blockPos Position to sample color at
	 * @param colorResolver The color resolver function
	 * @return The blended color value
	 */
	private static int getAverageColorVanilla(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos, ColorResolver colorResolver) {
		return blockAndTintGetter.getBlockTint(blockPos, colorResolver);
	}
	// ===== END SODIUM BIOME COLOR OPTIMIZATION =====

	public static int getAverageGrassColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return getAverageColor(blockAndTintGetter, blockPos, GRASS_COLOR_RESOLVER);
	}

	public static int getAverageFoliageColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return getAverageColor(blockAndTintGetter, blockPos, FOLIAGE_COLOR_RESOLVER);
	}

	public static int getAverageDryFoliageColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return getAverageColor(blockAndTintGetter, blockPos, DRY_FOLIAGE_COLOR_RESOLVER);
	}

	public static int getAverageWaterColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return getAverageColor(blockAndTintGetter, blockPos, WATER_COLOR_RESOLVER);
	}
}
