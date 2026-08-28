package net.minecraft.client.particle;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;

@Environment(EnvType.CLIENT)
public class NoRenderParticleGroup extends ParticleGroup<NoRenderParticle> {
	private static final ParticleGroupRenderState EMPTY_RENDER_STATE = new ParticleGroupRenderState() {
		@Override
		public void submit(net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
			CameraRenderState cameraRenderState) {
			// Intentionally empty: this particle group owns no visible work.
		}

		@Override
		public void submitSemantic(net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
			CameraRenderState cameraRenderState) {
			// Preserve the no-render contract on the Rust semantic route too.
		}
	};

	public NoRenderParticleGroup(ParticleEngine particleEngine) {
		super(particleEngine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f) {
		return EMPTY_RENDER_STATE;
	}
}
