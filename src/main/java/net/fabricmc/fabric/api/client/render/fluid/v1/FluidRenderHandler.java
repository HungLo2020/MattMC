package net.fabricmc.fabric.api.client.render.fluid.v1;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for custom fluid rendering.
 */
public interface FluidRenderHandler {
    
    /**
     * Gets the sprites for rendering this fluid.
     * Index 0 is still, index 1 is flowing, index 2 (optional) is overlay.
     */
    TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state);
    
    /**
     * Gets the tint color for this fluid.
     */
    default int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
        return -1; // White/no tint
    }
    
    /**
     * Called when textures are reloaded.
     * Override to reload any custom sprites from the texture atlas.
     */
    default void reloadTextures(TextureAtlas textureAtlas) {
        // Default implementation does nothing
    }
}
