package net.blaze3d.pipeline;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface CompiledRenderPipeline {
	boolean isValid();
}
