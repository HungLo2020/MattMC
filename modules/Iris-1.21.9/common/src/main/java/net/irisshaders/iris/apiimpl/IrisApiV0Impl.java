package net.irisshaders.iris.apiimpl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.iris.Iris;
import net.iris.api.v0.IrisApi;
import net.iris.api.v0.IrisApiConfig;
import net.iris.api.v0.IrisProgram;
import net.iris.api.v0.IrisTextVertexSink;
import net.iris.gui.screen.ShaderPackScreen;
import net.iris.pipeline.IrisPipelines;
import net.iris.pipeline.VanillaRenderingPipeline;
import net.iris.pipeline.WorldRenderingPipeline;
import net.iris.pipeline.programs.ShaderKey;
import net.iris.shaderpack.loading.ProgramId;
import net.iris.shadows.ShadowRenderingState;
import net.iris.vertices.IrisTextVertexSinkImpl;
import net.minecraft.client.gui.screens.Screen;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

public class IrisApiV0Impl implements IrisApi {
	public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();
	private static final IrisApiV0ConfigImpl CONFIG = new IrisApiV0ConfigImpl();

	@Override
	public int getMinorApiRevision() {
		return 3;
	}

	@Override
	public boolean isShaderPackInUse() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline == null) {
			return false;
		}

		return !(pipeline instanceof VanillaRenderingPipeline);
	}

	@Override
	public boolean isRenderingShadowPass() {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	@Override
	public Object openMainIrisScreenObj(Object parent) {
		return new ShaderPackScreen((Screen) parent);
	}

	@Override
	public String getMainScreenLanguageKey() {
		return "options.iris.shaderPackSelection";
	}

	@Override
	public IrisApiConfig getConfig() {
		return CONFIG;
	}

	@Override
	public IrisTextVertexSink createTextVertexSink(int maxQuadCount, IntFunction<ByteBuffer> bufferProvider) {
		return new IrisTextVertexSinkImpl(maxQuadCount, bufferProvider);
	}

	@Override
	public float getSunPathRotation() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline == null) {
			return 0;
		}

		return pipeline.getSunPathRotation();
	}

	@Override
	public void assignPipeline(RenderPipeline pipeline, IrisProgram program) {
		IrisPipelines.assignPipeline(pipeline, ShaderKey.findBestMatch(pipeline, ProgramId.fromAPI(program)));
	}
}
