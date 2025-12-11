package net.minecraft.client.renderer.shaders.gl.sampler;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL20C;

/**
 * Queries and caches OpenGL sampler-related limits.
 * IRIS 1.21.9 VERBATIM - from net.irisshaders.iris.gl.sampler.SamplerLimits
 */
public class SamplerLimits {
	private static SamplerLimits instance;
	private final int maxTextureUnits;
	private final int maxDrawBuffers;

	private SamplerLimits() {
		this.maxTextureUnits = GlStateManager._getInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
		this.maxDrawBuffers = GlStateManager._getInteger(GL20C.GL_MAX_DRAW_BUFFERS);
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
}
