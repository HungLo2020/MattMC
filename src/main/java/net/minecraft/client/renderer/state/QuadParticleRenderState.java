package net.minecraft.client.renderer.state;

import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.logging.LogUtils;
import net.sodium.client.render.vertex.VertexConsumerUtils;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;

@Environment(EnvType.CLIENT)
public class QuadParticleRenderState implements SubmitNodeCollector.ParticleGroupRenderer, ParticleGroupRenderState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static int debugParticlePrepareLogCount;
	private static int debugParticleDrawLogCount;
	private static int debugParticleSampleLogCount;
	/**
	 * The Rust whole-frame particle stream is a bounded semantic queue.  Keep
	 * extraction bounded as well so a pathological particle emitter cannot build
	 * an unbounded Java-side snapshot before the Rust queue gets a chance to
	 * reject it.
	 */
	private static final int MAX_RUST_SEMANTIC_PARTICLES = 65_536;
	private final Map<SingleQuadParticle.Layer, QuadParticleRenderState.Storage> particles = new HashMap();
	private int particleCount;

	public void add(
		SingleQuadParticle.Layer layer, float f, float g, float h, float i, float j, float k, float l, float m, float n, float o, float p, float q, int r, int s
	) {
		if ((layer == null || !Float.isFinite(f) || !Float.isFinite(g) || !Float.isFinite(h)
			|| !Float.isFinite(i) || !Float.isFinite(j) || !Float.isFinite(k) || !Float.isFinite(l)
			|| !Float.isFinite(m) || !Float.isFinite(n) || !Float.isFinite(o) || !Float.isFinite(p)
			|| !Float.isFinite(q)
			|| !Float.isFinite(i * i + j * j + k * k + l * l)
			|| i * i + j * j + k * k + l * l <= 1.0e-8F)) {
			throw new IllegalArgumentException("Rust whole-frame particle admission requires finite copied quad semantics");
		}
		if (this.particleCount >= MAX_RUST_SEMANTIC_PARTICLES) {
			throw new IllegalStateException(
				"Rust whole-frame particle route exceeded bounded semantic particle capacity "
					+ MAX_RUST_SEMANTIC_PARTICLES
			);
		}
		((QuadParticleRenderState.Storage)this.particles.computeIfAbsent(layer, layerx -> new QuadParticleRenderState.Storage()))
			.add(f, g, h, i, j, k, l, m, n, o, p, q, r, s);
		this.particleCount++;
		net.minecraft.client.dev.GraphicsAuditLavaFixture.observeParticle(f, g, h, i, j, k, l, m, r);
	}

	/** Copies the extracted particle semantics into the explicit Rust world stream. */
	public int enqueueRustGal() {
		int unsupportedLayers = this.rustGalUnsupportedLayerCount();
		if (unsupportedLayers > 0) {
			RustGalWorldPrimitiveRenderer.recordUnsupportedParticleGroup();
			throw new IllegalStateException(
				"Rust whole-frame particle route encountered " + unsupportedLayers
					+ " atlas layer(s) without a bounded copied texture snapshot"
			);
		}
		if (!RustGalWorldPrimitiveRenderer.hasMaterialQuadCapacity(this.particleCount)) {
			RustGalWorldPrimitiveRenderer.recordUnsupportedParticleGroup();
			throw new IllegalStateException("Rust whole-frame particle route exceeded bounded material-quad capacity");
		}
		Set<net.minecraft.resources.ResourceLocation> atlasIdentities = new HashSet<>();
		for (SingleQuadParticle.Layer layer : this.particles.keySet()) {
			if (!atlasIdentities.add(layer.textureAtlasLocation())) continue;
			if (!RustGalWorldPrimitiveRenderer.ensureParticleAtlasAvailable(layer.textureAtlasLocation())) {
				RustGalWorldPrimitiveRenderer.recordUnsupportedParticleGroup();
				throw new IllegalStateException("Rust whole-frame particle atlas preflight rejected " + layer.textureAtlasLocation());
			}
		}
		int checkpoint = RustGalWorldPrimitiveRenderer.markMaterialQuadBatch();
		try {
			int submitted = 0;
			for (Entry<SingleQuadParticle.Layer, QuadParticleRenderState.Storage> entry : this.particles.entrySet()) {
				SingleQuadParticle.Layer layer = entry.getKey();
				QuadParticleRenderState.Storage storage = entry.getValue();
				if (net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES.equals(layer.textureAtlasLocation())) {
                    submitted += RustGalWorldPrimitiveRenderer.enqueueParticleLayer(layer.translucent(), storage.nativeValues);
				} else {
					int[] admitted = {0};
					storage.forEachParticle((x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light) ->
						admitted[0] += RustGalWorldPrimitiveRenderer.enqueueParticleQuadForAtlas(
							layer.textureAtlasLocation(), layer.translucent(), x, y, z, qx, qy, qz, qw,
							size, u0, u1, v0, v1, color, light) ? 1 : 0);
					if (admitted[0] != storage.count()) {
						// Atlas eligibility is only a preflight. A bounded snapshot can
						// still fail while it is being staged; never let the successful
						// prefix masquerade as a complete Rust particle group.
						RustGalWorldPrimitiveRenderer.recordUnsupportedParticleGroup();
						throw new IllegalStateException("Rust whole-frame particle atlas admission changed while staging");
					}
					submitted += admitted[0];
				}
			}
			return submitted;
		} catch (RuntimeException failure) {
			RustGalWorldPrimitiveRenderer.rollbackMaterialQuadBatch(checkpoint);
			throw failure;
		}
	}

	/**
	 * Counts extracted layers that the Rust particle material contract cannot
	 * consume. Block-atlas layers are handled by dedicated semantic producers;
	 * other atlas layers are admitted only when a copied semantic snapshot is
	 * available, otherwise they remain an explicit ownership gap.
	 */
	public int rustGalUnsupportedLayerCount() {
		int unsupported = 0;
		for (Entry<SingleQuadParticle.Layer, QuadParticleRenderState.Storage> entry : this.particles.entrySet()) {
			if (entry.getValue().count() > 0
				&& !net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES.equals(entry.getKey().textureAtlasLocation())
				&& !RustGalWorldPrimitiveRenderer.canUseParticleAtlas(entry.getKey().textureAtlasLocation())) {
				unsupported++;
			}
		}
		return unsupported;
	}

	@Override
	public void clear() {
		this.particles.values().forEach(QuadParticleRenderState.Storage::clear);
		this.particleCount = 0;
	}

	@Nullable
	@Override
	public QuadParticleRenderState.PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache particleBufferCache) {
		throw new IllegalStateException("Java particle buffer preparation is unavailable on Rust Vulkan");
	}

	@Override
	public void render(
		QuadParticleRenderState.PreparedBuffers preparedBuffers,
		ParticleFeatureRenderer.ParticleBufferCache particleBufferCache,
		RenderPass renderPass,
		TextureManager textureManager,
		boolean bl
	) {
		throw new IllegalStateException("Java particle rendering is unavailable on Rust Vulkan");
	}

	// Sodium: Optimized particle rendering (merged from QuadParticleRenderStateMixin)
	private static final Quaternionf TEMP_QUAT = new Quaternionf();
	private static final Vector3f TEMP_VECTOR = new Vector3f();

	protected void renderRotatedQuad(
		VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, float k, float l, float m, float n, float o, float p, float q, int r, int s
	) {
		Quaternionf quaternionf = new Quaternionf(i, j, k, l);
		this.renderVertex(vertexConsumer, quaternionf, f, g, h, 1.0F, -1.0F, m, o, q, r, s);
		this.renderVertex(vertexConsumer, quaternionf, f, g, h, 1.0F, 1.0F, m, o, p, r, s);
		this.renderVertex(vertexConsumer, quaternionf, f, g, h, -1.0F, 1.0F, m, n, p, r, s);
		this.renderVertex(vertexConsumer, quaternionf, f, g, h, -1.0F, -1.0F, m, n, q, r, s);
	}

	private void renderVertex(
		VertexConsumer vertexConsumer, Quaternionf quaternionf, float f, float g, float h, float i, float j, float k, float l, float m, int n, int o
	) {
		Vector3f vector3f = new Vector3f(i, j, 0.0F).rotate(quaternionf).mul(k).add(f, g, h);
		vertexConsumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z()).setUv(l, m).setColor(n).setLight(o);
	}

	@Override
	public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (this.particleCount > 0) {
			submitNodeCollector.submitParticleGroupSemantic(this);
		}
	}

	@Override
	public void submitSemantic(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (this.particleCount > 0) {
			submitNodeCollector.submitParticleGroupSemantic(this);
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface ParticleConsumer {
		void consume(float f, float g, float h, float i, float j, float k, float l, float m, float n, float o, float p, float q, int r, int s);
	}

	@Environment(EnvType.CLIENT)
	public record PreparedBuffers(int indexCount, GpuBufferSlice dynamicTransforms, Map<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer> layers) {
	}

	@Environment(EnvType.CLIENT)
	public record PreparedLayer(int vertexOffset, int indexCount) {
	}

	@Environment(EnvType.CLIENT)
    static class Storage {
        private final net.vulkanic.bridge.NativeParticleBuffer nativeValues = new net.vulkanic.bridge.NativeParticleBuffer();
        public void add(float f,float g,float h,float i,float j,float k,float l,float m,float n,float o,float p,float q,int r,int s) {
            nativeValues.append(1,0,0,f,g,h,i,j,k,l,m,n,o,p,q,r,s);
        }
        public void forEachParticle(ParticleConsumer consumer) { nativeValues.visit(consumer::consume); }
        public void clear() { nativeValues.clear(); }
        public int count() { return nativeValues.size(); }
    }
}
