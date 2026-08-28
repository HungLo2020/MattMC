package net.minecraft.client.renderer.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;

@Environment(EnvType.CLIENT)
public interface ParticleGroupRenderState {
	void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState);

	/** Explicit semantic submission used by Rust-owned particle extraction. */
	default void submitSemantic(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (VulkanicAPI.isVulkanBackendSelected() || RustGalVulkanWholeFrameMode.enabled()) {
			RustGalWorldPrimitiveRenderer.recordUnsupportedParticleGroup();
			throw new IllegalStateException(
				"Rust whole-frame particle route has no semantic collector for " + getClass().getName()
			);
		}
		submit(submitNodeCollector, cameraRenderState);
	}

	default void clear() {
	}
}
