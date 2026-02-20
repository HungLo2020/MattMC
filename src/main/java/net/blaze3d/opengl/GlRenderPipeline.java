package net.blaze3d.opengl;

import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.pipeline.VulkanicCompiledPipeline;

@Environment(EnvType.CLIENT)
public record GlRenderPipeline(RenderPipeline info, GlProgram program)
        implements CompiledRenderPipeline, VulkanicCompiledPipeline {
	@Override
	public boolean isValid() {
		return this.program != GlProgram.INVALID_PROGRAM;
	}

	/**
	 * Implements {@link VulkanicCompiledPipeline#getNativePipelineHandle()}.
	 *
	 * <p>Returns the GL program object name as a {@code long}.
	 * For a Vulkan backend this will be the {@code VkPipeline} handle.
	 */
	@Override
	public long getNativePipelineHandle() {
		return this.program.getProgramId();
	}
}
