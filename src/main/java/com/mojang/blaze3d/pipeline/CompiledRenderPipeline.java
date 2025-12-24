package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface CompiledRenderPipeline {
	boolean isValid();
}
