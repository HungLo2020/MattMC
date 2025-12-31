package net.minecraft.hooks;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Hook interface for block entity type events.
 * Allows mods to interact with block entity types during initialization and usage.
 */
public interface BlockEntityTypeHooks {
    /**
     * Called when a block entity type is created/initialized.
     * This allows mods to register custom data or behavior for the block entity type.
     * 
     * @param blockEntityType The block entity type being initialized
     */
    default void onBlockEntityTypeInit(BlockEntityType<?> blockEntityType) {
    }
}
