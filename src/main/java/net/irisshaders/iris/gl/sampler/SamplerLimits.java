package net.irisshaders.iris.gl.sampler;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicAPI;

public class SamplerLimits {
	private static SamplerLimits instance;
	private final int maxTextureUnits;
	private final int maxDrawBuffers;
	private final int maxShaderStorageUnits;

	private SamplerLimits() {
		this.maxTextureUnits = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_TEXTURE_IMAGE_UNITS);
		this.maxDrawBuffers = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_DRAW_BUFFERS);
		this.maxShaderStorageUnits = IrisRenderSystem.supportsSSBO()
			? VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS)
			: 0;
	}

	public static SamplerLimits get() {
		if (instance == null) {
			instance = new SamplerLimits();
		}

		return instance;
	}

	public int getMaxTextureUnits() {
		return maxTextureUnits;
	}

	public int getMaxDrawBuffers() {
		return maxDrawBuffers;
	}

	public int getMaxShaderStorageUnits() {
		return maxShaderStorageUnits;
	}
}
