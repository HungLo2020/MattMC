package net.irisshaders.iris.gl.sampler;

import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;

public class GlSampler extends GlResource {
	public static final GlSampler MIPPED_LINEAR_HW = new GlSampler(true, true, true, true);
	public static final GlSampler LINEAR_HW = new GlSampler(true, false, true, true);
	public static final GlSampler MIPPED_NEAREST_HW = new GlSampler(false, true, true, true);
	public static final GlSampler NEAREST_HW = new GlSampler(false, false, true, true);
	public static final GlSampler MIPPED_LINEAR = new GlSampler(true, true, false, false);
	public static final GlSampler LINEAR = new GlSampler(true, false, false, false);
	public static final GlSampler MIPPED_NEAREST = new GlSampler(false, true, false, false);
	public static final GlSampler NEAREST = new GlSampler(false, false, false, false);
	
	public GlSampler(boolean linear, boolean mipmapped, boolean shadow, boolean hardwareShadow) {
		super(requireJavaSamplerAllocation());

		VulkanicTextureParameterValue baseFilter = linear
			? VulkanicTextureParameterValue.LINEAR
			: VulkanicTextureParameterValue.NEAREST;
		IrisRenderSystem.samplerParameteri(getId(), VulkanicTextureParameterName.MIN_FILTER, baseFilter);
		IrisRenderSystem.samplerParameteri(getId(), VulkanicTextureParameterName.MAG_FILTER, baseFilter);
		IrisRenderSystem.samplerParameteri(getId(), VulkanicTextureParameterName.WRAP_S, VulkanicTextureParameterValue.CLAMP_TO_EDGE);
		IrisRenderSystem.samplerParameteri(getId(), VulkanicTextureParameterName.WRAP_T, VulkanicTextureParameterValue.CLAMP_TO_EDGE);

		if (mipmapped) {
			VulkanicTextureParameterValue mipFilter = linear
				? VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR
				: VulkanicTextureParameterValue.NEAREST_MIPMAP_NEAREST;
			IrisRenderSystem.samplerParameteri(getId(), VulkanicTextureParameterName.MIN_FILTER, mipFilter);
		}

		if (hardwareShadow) {
			IrisRenderSystem.samplerParameteri(
				getId(),
				VulkanicTextureParameterName.COMPARE_MODE,
				VulkanicTextureParameterValue.COMPARE_REF_TO_TEXTURE
			);
		}
	}

	private static int requireJavaSamplerAllocation() {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris sampler allocation is unavailable on the Rust Vulkan route");
		}
		return IrisRenderSystem.genSampler();
	}

	@Override
	protected void destroyInternal() {
		IrisRenderSystem.destroySampler(getGlId());
	}

	public int getId() {
		return getGlId();
	}
}
