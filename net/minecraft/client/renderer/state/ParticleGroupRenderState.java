package net.minecraft.client.renderer.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;

@Environment(EnvType.CLIENT)
public interface ParticleGroupRenderState {
	void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState);

	default void clear() {
	}
}
