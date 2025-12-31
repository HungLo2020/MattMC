package net.minecraft.hooks;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Hook interface for GuiGraphics sprite blitting events.
 * Allows mods to track sprite usage during GUI rendering.
 */
public interface GuiGraphicsHooks {
    /**
     * Called before a sprite is blitted to the GUI.
     * 
     * @param renderPipeline The render pipeline
     * @param sprite The sprite being blitted
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     * @param blitOffset Blit offset
     */
    default void onSpriteBlitSimple(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int blitOffset) {}

    /**
     * Called before a sprite is blitted to the GUI with UV coordinates.
     * 
     * @param renderPipeline The render pipeline
     * @param sprite The sprite being blitted
     * @param textureWidth Texture width
     * @param textureHeight Texture height
     * @param uPosition U position
     * @param vPosition V position
     * @param x X position
     * @param y Y position
     * @param uWidth U width
     * @param vHeight V height
     * @param blitOffset Blit offset
     */
    default void onSpriteBlitUV(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int textureWidth, int textureHeight, int uPosition, int vPosition, int x, int y, int uWidth, int vHeight, int blitOffset) {}
}
