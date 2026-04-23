package net.irisshaders.iris.mixinterface;

import net.blaze3d.systems.RenderPass;
import net.irisshaders.iris.gl.program.Program;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import org.jetbrains.annotations.Nullable;

public interface CustomPass {
	void setupState();

	default void bindRenderPassResources(RenderPass renderPass) {
	}

	@Nullable
	default Program program() {
		return null;
	}

	@Nullable
	default PipelineDescriptor pipelineDescriptor() {
		return null;
	}

	@Nullable
	default PipelineHandle pipelineHandle() {
		return null;
	}

	@Nullable
	default PipelineHandle pipelineHandle(@Nullable PipelineDescriptor descriptor) {
		return this.pipelineHandle();
	}
}
