package frnsrc.Iris;

import net.irisshaders.iris.pipeline.programs.ShaderMap;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;

public interface ShaderRenderingPipeline extends WorldRenderingPipeline {
	ShaderMap getShaderMap();

	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldOverrideShaders();
}
