package net.minecraft.client.renderer.feature;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.RenderPass;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ParticleFeatureRenderer implements AutoCloseable, net.irisshaders.iris.fantastic.PhasedParticleEngine {
	private final Queue<ParticleFeatureRenderer.ParticleBufferCache> availableBuffers = new ArrayDeque();
	private final List<ParticleFeatureRenderer.ParticleBufferCache> usedBuffers = new ArrayList();
	// Iris: Track particle rendering phase (from MixinParticleEngine)
	private net.irisshaders.iris.pipeline.WorldRenderingPhase lastPhase = net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE;
	// Iris: Phased particle rendering (merged from MixinParticleFeatureRenderer)
	private net.irisshaders.iris.fantastic.ParticleRenderingPhase phase = net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING;

	@Override
	public void setParticleRenderingPhase(net.irisshaders.iris.fantastic.ParticleRenderingPhase phase) {
		this.phase = phase;
	}

	public void render(SubmitNodeCollection submitNodeCollection) {
		// Iris: Set particles rendering phase (from MixinParticleEngine)
		net.irisshaders.iris.Iris.getPipelineManager().getPipeline().ifPresent(pipeline -> {
			lastPhase = pipeline.getPhase();
			pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.PARTICLES);
		});

		try {
			if (!submitNodeCollection.getParticleGroupRenderers().isEmpty()) {
				Minecraft minecraft = Minecraft.getInstance();
				TextureManager textureManager = minecraft.getTextureManager();
				RenderTarget renderTarget = minecraft.getMainRenderTarget();
				// Iris: Prevent fabulous crash (merged from MixinParticleFeatureRenderer)
				RenderTarget renderTarget2 = phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.OPAQUE ? null : minecraft.levelRenderer.getParticlesTarget();
				minecraft.gameRenderer.lightTexture().turnOnLightLayer();

				try {
					for (SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer : submitNodeCollection.getParticleGroupRenderers()) {
						ParticleFeatureRenderer.ParticleBufferCache particleBufferCache = (ParticleFeatureRenderer.ParticleBufferCache)this.availableBuffers.poll();
						if (particleBufferCache == null) {
							particleBufferCache = new ParticleFeatureRenderer.ParticleBufferCache();
						}

						this.usedBuffers.add(particleBufferCache);
						// Iris: Override particle rendering code (merged from MixinParticleFeatureRenderer)
						QuadParticleRenderState.PreparedBuffers preparedBuffers = particleGroupRenderer.prepare(particleBufferCache);
						if (preparedBuffers != null) {
							try (RenderPass renderPass = net.vulkanic.VulkanicAPI.createRenderPass(() -> "Particles - Main", renderTarget.getColorTextureView(), OptionalInt.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
								this.prepareRenderPass(renderPass);
								if (phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING || phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.OPAQUE) {
									particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, false);
								}
								if (renderTarget2 == null && (phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING || phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.TRANSLUCENT)) {
									particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, true);
								}
							}

							if (renderTarget2 != null && (phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING || phase == net.irisshaders.iris.fantastic.ParticleRenderingPhase.TRANSLUCENT)) {
								try (RenderPass renderPass = net.vulkanic.VulkanicAPI.createRenderPass(() -> "Particles - Transparent", renderTarget2.getColorTextureView(), OptionalInt.empty(), renderTarget2.getDepthTextureView(), OptionalDouble.empty())) {
									this.prepareRenderPass(renderPass);
									particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, true);
								}
							}
						}
					}
				} finally {
					minecraft.gameRenderer.lightTexture().turnOffLightLayer();
				}
			}
		} finally {
			// Iris: Restore previous rendering phase (from MixinParticleEngine)
			net.irisshaders.iris.Iris.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.setPhase(lastPhase));
		}
	}

	public void endFrame() {
		for (ParticleFeatureRenderer.ParticleBufferCache particleBufferCache : this.usedBuffers) {
			particleBufferCache.rotate();
		}

		this.availableBuffers.addAll(this.usedBuffers);
		this.usedBuffers.clear();
	}

	private void prepareRenderPass(RenderPass renderPass) {
		renderPass.setUniform("Projection", VulkanicAPI.getProjectionMatrixBuffer());
		renderPass.setUniform("Fog", VulkanicAPI.getShaderFog());
		renderPass.bindSampler("Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
	}

	public void close() {
		this.availableBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
	}

	@Environment(EnvType.CLIENT)
	public static class ParticleBufferCache implements AutoCloseable {
		@Nullable
		private MappableRingBuffer ringBuffer;

		public void write(ByteBuffer byteBuffer) {
			if (this.ringBuffer == null || this.ringBuffer.size() < byteBuffer.remaining()) {
				if (this.ringBuffer != null) {
					this.ringBuffer.close();
				}

				this.ringBuffer = new MappableRingBuffer(() -> "Particle Vertices", 34, byteBuffer.remaining());
			}

			try (GpuBuffer.MappedView mappedView = net.vulkanic.VulkanicAPI.createCommandEncoder().mapBuffer(this.ringBuffer.currentBuffer().slice(), false, true)) {
				mappedView.data().put(byteBuffer);
			}
		}

		public GpuBuffer get() {
			if (this.ringBuffer == null) {
				throw new IllegalStateException("Can't get buffer before it's made");
			} else {
				return this.ringBuffer.currentBuffer();
			}
		}

		void rotate() {
			if (this.ringBuffer != null) {
				this.ringBuffer.rotate();
			}
		}

		public void close() {
			if (this.ringBuffer != null) {
				this.ringBuffer.close();
			}
		}
	}
}
