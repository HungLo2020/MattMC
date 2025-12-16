package net.minecraft.client.renderer.sodium.mixin;

import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.List;

/**
 * Temporary accessor stub for TextureAtlas.
 * This will be replaced when mixins are inlined in Phase 4.
 * 
 * @see net.minecraft.client.renderer.advanced.AdvancedRenderingConfig
 */
public interface TextureAtlasAccessor {
    List<SpriteContents> sodium$getAllSprites();
    int getWidth();
    int getHeight();
}
