package net.minecraft.client.renderer.state;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SubmitNodeStorage;

@Environment(EnvType.CLIENT)
public class ParticlesRenderState implements net.irisshaders.iris.mixinterface.ParticleRenderStateExtension {
	public final List<ParticleGroupRenderState> particles = new ArrayList();

	public void reset() {
		this.particles.forEach(ParticleGroupRenderState::clear);
		this.particles.clear();
	}

	public void add(ParticleGroupRenderState particleGroupRenderState) {
		this.particles.add(particleGroupRenderState);
	}

	public void submit(SubmitNodeStorage submitNodeStorage, CameraRenderState cameraRenderState) {
		for (ParticleGroupRenderState particleGroupRenderState : this.particles) {
			particleGroupRenderState.submit(submitNodeStorage, cameraRenderState);
		}
	}

	/**
	 * Submits the copied particle state through semantic collectors.  This is
	 * deliberately separate from {@link #submit}, which remains the private
	 * OpenGL/Iris compatibility lowering.  A Rust-owned frame must never regain
	 * the legacy callback merely because a wrapper still owns a
	 * {@code ParticlesRenderState}.
	 */
	public void submitSemantic(SubmitNodeStorage submitNodeStorage, CameraRenderState cameraRenderState) {
		for (ParticleGroupRenderState particleGroupRenderState : this.particles) {
			particleGroupRenderState.submitSemantic(submitNodeStorage, cameraRenderState);
		}
	}
	
	// Iris: ParticleRenderStateExtension implementation
	@Override
	public void submitWithoutItems(SubmitNodeStorage submitNodeStorage, CameraRenderState cameraRenderState) {
		for (ParticleGroupRenderState particleGroupRenderState : this.particles) {
			if (!(particleGroupRenderState instanceof net.minecraft.client.particle.ItemPickupParticleGroup.State)) {
				particleGroupRenderState.submit(submitNodeStorage, cameraRenderState);
			}
		}
	}

}
