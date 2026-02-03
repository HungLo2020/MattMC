package net.irisshaders.iris.mixinterface;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;

public interface RenderTypeInterface {
	default RenderTarget iris$getRenderTarget() {
		throw new AssertionError("No accessible");
	}

	default RenderPipeline iris$getPipeline() {
		throw new AssertionError("No accessible");
	}
}
