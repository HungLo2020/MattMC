package net.fabricmc.fabric.api.blockview.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric extension interface for BlockAndTintGetter.
 */
public interface FabricBlockView extends BlockAndTintGetter {
    
    /**
     * Gets the render data associated with a block entity at the given position.
     */
    @Nullable
    default Object getBlockEntityRenderData(BlockPos pos) {
        return null;
    }
    
    /**
     * Returns whether this block view has biome information.
     */
    default boolean hasBiomes() {
        return false;
    }
    
    /**
     * Gets the biome at the given position using Fabric's extension method.
     */
    @Nullable
    default Holder<Biome> getBiomeFabric(BlockPos pos) {
        return null;
    }
}
