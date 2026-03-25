package net.blaze3d.opengl;

import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.PipelineDescriptor;

@Environment(EnvType.CLIENT)
public record GlRenderPipeline(RenderPipeline info, GlProgram program, PipelineDescriptor descriptor) implements CompiledRenderPipeline {
	@Override
	public boolean isValid() {
		return this.program != GlProgram.INVALID_PROGRAM;
	}
}
