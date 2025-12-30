package com.seibel.distanthorizons.fabric.hooks;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.fabric.mixins.client.LightTextureAccessor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.hooks.LightTextureHooks;
import org.apache.logging.log4j.Logger;

/**
 * Hook implementation for light texture updates.
 * Replaces MixinLightTexture.
 */
public class DhLightTextureHook implements LightTextureHooks {
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();
    private MinecraftRenderWrapper renderWrapper = null;

    @Override
    public void onLightTextureUpdated(LightTexture lightTexture, float partialTicks) {
        LOGGER.info("[DH-LIGHT-TEX] Hook called");
        
        IMinecraftClientWrapper mc = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
        if (mc == null) {
            LOGGER.warn("[DH-LIGHT-TEX] MC is null");
            return;
        }

        IClientLevelWrapper clientLevel = mc.getWrappedClientLevel();
        if (clientLevel == null) {
            LOGGER.warn("[DH-LIGHT-TEX] Client level is null");
            return;
        }

        // lazy initialization to make sure we don't call this too early
        if (this.renderWrapper == null) {
            this.renderWrapper = (MinecraftRenderWrapper)SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
            LOGGER.info("[DH-LIGHT-TEX] Initialized renderWrapper");
        }

        LOGGER.info("[DH-LIGHT-TEX] Accessor cast, getting texture");
        LightTextureAccessor accessor = (LightTextureAccessor) lightTexture;
        GlTexture glTexture = (GlTexture) accessor.getTexture();
        LOGGER.info("[DH-LIGHT-TEX] GlTexture obtained: ID=" + glTexture.glId());
        
        LOGGER.info("[DH-LIGHT-TEX] Calling setLightmapId");
        this.renderWrapper.setLightmapId(glTexture.glId(), clientLevel);
        LOGGER.info("[DH-LIGHT-TEX] setLightmapId completed");
    }
}
