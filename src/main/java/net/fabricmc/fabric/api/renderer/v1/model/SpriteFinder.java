package net.fabricmc.fabric.api.renderer.v1.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for finding sprites from UV coordinates.
 */
public interface SpriteFinder {
    
    /**
     * Gets a SpriteFinder for the given atlas.
     */
    static SpriteFinder get(TextureAtlas atlas) {
        // Return a no-op implementation for now
        return new SpriteFinder() {
            @Override
            public @Nullable TextureAtlasSprite find(float u, float v) {
                return null;
            }
            
            @Override
            public @Nullable TextureAtlasSprite find(QuadView quad) {
                return null;
            }
        };
    }
    
    /**
     * Finds a sprite at the given UV coordinates.
     */
    @Nullable
    TextureAtlasSprite find(float u, float v);
    
    /**
     * Finds a sprite for the given quad.
     */
    @Nullable
    TextureAtlasSprite find(QuadView quad);
}
