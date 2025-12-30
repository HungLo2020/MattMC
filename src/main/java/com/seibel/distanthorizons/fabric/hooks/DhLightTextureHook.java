package com.seibel.distanthorizons.fabric.hooks;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.fabric.mixins.client.LightTextureAccessor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.hooks.LightTextureHooks;

/**
 * Hook implementation for light texture updates.
 * Replaces MixinLightTexture.
 */
public class DhLightTextureHook implements LightTextureHooks {
    private MinecraftRenderWrapper renderWrapper = null;

    @Override
    public void onLightTextureUpdated(LightTexture lightTexture, float partialTicks) {
        IMinecraftClientWrapper mc = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
        if (mc == null) {
            return;
        }

        IClientLevelWrapper clientLevel = mc.getWrappedClientLevel();
        if (clientLevel == null) {
            return;
        }

        // lazy initialization to make sure we don't call this too early
        if (this.renderWrapper == null) {
            this.renderWrapper = (MinecraftRenderWrapper)SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
        }

        LightTextureAccessor accessor = (LightTextureAccessor) lightTexture;
        GlTexture glTexture = (GlTexture) accessor.getTexture();
        this.renderWrapper.setLightmapId(glTexture.glId(), clientLevel);
    }
}
