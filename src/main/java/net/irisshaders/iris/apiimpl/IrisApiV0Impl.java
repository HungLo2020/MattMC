package net.irisshaders.iris.apiimpl;

import net.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.Iris;
import net.iris.api.v0.IrisApi;
import net.iris.api.v0.IrisApiConfig;
import net.iris.api.v0.IrisProgram;
import net.iris.api.v0.IrisTextVertexSink;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.vertices.IrisTextVertexSinkImpl;
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
		return Iris.isPackInUseQuick();
	}

	@Override
	public boolean isRenderingShadowPass() {
		// Rust renders the pack's shadow pass internally; Java never runs one.
		return false;
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
		// Rust reads the pack's sunPathRotation constant for its own sky; it is
		// not mirrored back to Java.
		return 0;
	}

	@Override
	public void assignPipeline(RenderPipeline pipeline, IrisProgram program) {
		// Rust selects pack programs from copied semantics; there is no Java
		// pipeline-to-program table to update.
	}
}
