package net.voxelmap.util;

import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Helper to access EnderDragonRenderer fields that were made accessible for VoxelMap.
 * Replaces the AccessorEnderDragonRenderer mixin.
 */
public class EnderDragonRendererAccessor {
    
    /**
     * Gets the dragon texture location.
     * This field was made public in EnderDragonRenderer for VoxelMap compatibility.
     */
    public static ResourceLocation getTextureLocation() {
        return EnderDragonRenderer.DRAGON_LOCATION;
    }
    
    private EnderDragonRendererAccessor() {}
}
