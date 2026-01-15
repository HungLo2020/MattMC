package net.caffeinemc.mods.sodium.fabric;

import net.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.GuiGraphicsHooks;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of GuiGraphicsHooks.
 * Marks sprites as active when blitted to the GUI.
 */
public class SodiumGuiGraphicsHook implements GuiGraphicsHooks {
    @Override
    public void onSpriteBlitSimple(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int blitOffset) {
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }

    @Override
    public void onSpriteBlitUV(RenderPipeline renderPipeline, TextureAtlasSprite sprite, int textureWidth, int textureHeight, int uPosition, int vPosition, int x, int y, int uWidth, int vHeight, int blitOffset) {
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }
}
