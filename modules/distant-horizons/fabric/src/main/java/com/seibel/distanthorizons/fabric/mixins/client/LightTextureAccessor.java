package com.seibel.distanthorizons.fabric.mixins.client;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for LightTexture internal fields.
 * Allows hook implementations to access texture data.
 */
@Mixin(LightTexture.class)
public interface LightTextureAccessor {
    @Accessor("texture")
    GpuTexture getTexture();
}
