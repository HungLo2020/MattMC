package com.seibel.distanthorizons.fabric.mixins.client;


import com.mojang.blaze3d.platform.TextureUtil;
import com.seibel.distanthorizons.core.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


/**
 * Sets Minecraft's LOD Bias (looks similar to mipmaps)
 *
 * @author coolGi
 */
@Mixin(TextureUtil.class)
public class MixinTextureUtil
{
	// TODO fix for MC 1.21.5+
	
		
}
