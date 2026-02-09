package net.irisshaders.iris.gl.sampler;

import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;

public class GlSampler extends GlResource {
	public static final GlSampler MIPPED_LINEAR_HW = new GlSampler(true, true, true, true);
	public static final GlSampler LINEAR_HW = new GlSampler(true, false, true, true);
	public static final GlSampler MIPPED_NEAREST_HW = new GlSampler(false, true, true, true);
	public static final GlSampler NEAREST_HW = new GlSampler(false, false, true, true);
	public static final GlSampler MIPPED_LINEAR = new GlSampler(true, true, false, false);
	public static final GlSampler LINEAR = new GlSampler(true, false, false, false);
	public static final GlSampler MIPPED_NEAREST = new GlSampler(false, true, false, false);
	public static final GlSampler NEAREST = new GlSampler(false, false, false, false);
	
	// GL constants for texture parameters (from GL11C, GL13C, GL20C, GL30C)
	private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
	private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
	private static final int GL_TEXTURE_WRAP_S = 0x2802;
	private static final int GL_TEXTURE_WRAP_T = 0x2803;
	private static final int GL_LINEAR = 0x2601;
	private static final int GL_NEAREST = 0x2600;
	private static final int GL_CLAMP_TO_EDGE = 0x812F;
	private static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
	private static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
	private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
	private static final int GL_COMPARE_REF_TO_TEXTURE = 0x884E;

	public GlSampler(boolean linear, boolean mipmapped, boolean shadow, boolean hardwareShadow) {
		super(IrisRenderSystem.genSampler());

		IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
		IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
		IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		if (mipmapped) {
			IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST);
		}

		if (hardwareShadow) {
			IrisRenderSystem.samplerParameteri(getId(), GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
		}
	}

	@Override
	protected void destroyInternal() {
		IrisRenderSystem.destroySampler(getGlId());
	}

	public int getId() {
		return getGlId();
	}
}
