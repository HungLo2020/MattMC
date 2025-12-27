package com.seibel.distanthorizons.fabric.mixins.client;

#if MC_VER < MC_1_21_3
import com.mojang.blaze3d.platform.NativeImage;
#elif MC_VER < MC_1_21_5
import com.mojang.blaze3d.pipeline.TextureTarget;
#else
import com.mojang.blaze3d.textures.GpuTexture;
#endif

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for LightTexture internal fields.
 * Allows DhLightTextureHook to access texture data without direct field access.
 */
@Mixin(LightTexture.class)
public interface LightTextureAccessor {
    #if MC_VER < MC_1_21_3
    @Accessor("lightPixels")
    NativeImage getLightPixels();
    #elif MC_VER < MC_1_21_5
    @Accessor("target")
    TextureTarget getTarget();
    #else
    @Accessor("texture")
    GpuTexture getTexture();
    #endif
}
