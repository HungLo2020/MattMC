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
import net.logging.LogUtils;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ParticleFeatureRenderer implements AutoCloseable, net.irisshaders.iris.fantastic.PhasedParticleEngine {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static int debugParticleFeatureLogCount;
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

	public void endFrame() {
		return;
	}

	public void close() {
		this.availableBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
		this.usedBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
		this.availableBuffers.clear();
		this.usedBuffers.clear();
	}

	/** Releases cached Java particle buffers when Rust Vulkan ownership begins late. */
	public void ensureRustSemanticRoute() {
		this.close();
	}

	@Environment(EnvType.CLIENT)
	public static class ParticleBufferCache implements AutoCloseable {
		@Nullable
		private MappableRingBuffer ringBuffer;

		public void close() {
			if (this.ringBuffer != null) {
				this.ringBuffer.close();
			}
		}
	}
}
