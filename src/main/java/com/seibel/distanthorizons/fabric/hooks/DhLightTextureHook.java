package com.seibel.distanthorizons.fabric.hooks;

import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.hooks.LightTextureHooks;
import net.vulkanic.VulkanicAPI;

/**
 * Hook implementation for light texture updates.
 * Replaces MixinLightTexture.
 */
public class DhLightTextureHook implements LightTextureHooks {
    private MinecraftRenderWrapper renderWrapper = null;

    @Override
    public void onLightTextureUpdated(LightTexture lightTexture, float partialTicks) {
        // Rust owns the copied semantic lightmap inputs; DH must not turn
        // the Java lightmap object into a native handle for compatibility state.
        return;
    }
}
