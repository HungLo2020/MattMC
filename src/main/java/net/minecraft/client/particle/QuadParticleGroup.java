package net.minecraft.client.particle;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.QuadParticleRenderState;

@Environment(EnvType.CLIENT)
public class QuadParticleGroup extends ParticleGroup<SingleQuadParticle> {
	private final ParticleRenderType particleType;
	final QuadParticleRenderState particleTypeRenderState = new QuadParticleRenderState();

	public QuadParticleGroup(ParticleEngine particleEngine, ParticleRenderType particleRenderType) {
		super(particleEngine);
		this.particleType = particleRenderType;
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f) {
		for (SingleQuadParticle singleQuadParticle : this.particles) {
			if (frustum.pointInFrustum(singleQuadParticle.x, singleQuadParticle.y, singleQuadParticle.z)) {
				try {
					singleQuadParticle.extract(this.particleTypeRenderState, camera, f);
				} catch (Throwable var9) {
					CrashReport crashReport = CrashReport.forThrowable(var9, "Rendering Particle");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being rendered");
					crashReportCategory.setDetail("Particle", singleQuadParticle::toString);
					crashReportCategory.setDetail("Particle Type", this.particleType::toString);
					throw new ReportedException(crashReport);
				}
			}
		}

		return this.particleTypeRenderState;
	}

	public int enqueueRustGalBlockMarkers(Camera camera, float f) {
		int enqueued = 0;
		for (SingleQuadParticle singleQuadParticle : this.particles) {
			if (singleQuadParticle instanceof BlockMarker blockMarker && blockMarker.enqueueRustGal(camera, f)) {
				enqueued++;
			}
		}
		return enqueued;
	}

	public int enqueueRustGalTerrainParticles(Camera camera, float f) {
		int enqueued = 0;
		for (SingleQuadParticle singleQuadParticle : this.particles) {
			if (singleQuadParticle instanceof TerrainParticle terrainParticle && terrainParticle.enqueueRustGal(camera, f)) {
				enqueued++;
			}
		}
		return enqueued;
	}

	public int enqueueRustGalParticles() {
		return this.particleTypeRenderState.enqueueRustGal();
	}

	/**
	 * Extracts the current visible quad particles before copying them into the
	 * Rust semantic stream. The whole-frame presenter bypasses vanilla's
	 * ParticlesRenderState extraction, so enqueueing the reusable state directly
	 * would otherwise replay a stale snapshot (or submit nothing on the first
	 * frame).
	 */
	public int enqueueRustGalParticles(Frustum frustum, Camera camera, float partialTick) {
		this.particleTypeRenderState.clear();
		if (System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank()) {
			this.extractRenderState(frustum, camera, partialTick);
		} else {
			for (SingleQuadParticle particle : this.particles) {
				if (!(particle instanceof TerrainParticle)
					&& frustum.pointInFrustum(particle.x, particle.y, particle.z)) {
					particle.extract(this.particleTypeRenderState, camera, partialTick);
				}
			}
		}
		return this.particleTypeRenderState.enqueueRustGal();
	}
}
