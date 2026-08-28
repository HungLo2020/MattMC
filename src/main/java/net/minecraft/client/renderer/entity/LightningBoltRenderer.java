package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class LightningBoltRenderer extends EntityRenderer<LightningBolt, LightningBoltRenderState> {
	private static final int SEMANTIC_LIGHTNING_QUADS = 4 * (8 + 3 + 3) * 4;
	public LightningBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public void submit(
		LightningBoltRenderState lightningBoltRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		float[] fs = new float[8];
		float[] gs = new float[8];
		float f = 0.0F;
		float g = 0.0F;
		RandomSource randomSource = RandomSource.create(lightningBoltRenderState.seed);

		for (int i = 7; i >= 0; i--) {
			fs[i] = f;
			gs[i] = g;
			f += randomSource.nextInt(11) - 5;
			g += randomSource.nextInt(11) - 5;
		}

		float h = f;
		float j = g;
		boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute()
			.usesRustWholeFrameVulkan()) {
			// Vanilla emits four crossed quads for each of 14 segments in each
			// of four deterministic layers. Keep the semantic payload exactly
			// bounded to that complete route; the old 56-quad allocation covered
			// one layer and could overrun before Rust received the submission.
			float[] vertices = new float[SEMANTIC_LIGHTNING_QUADS * 12];
			float[] uvs = new float[SEMANTIC_LIGHTNING_QUADS * 8];
			int[] colors = new int[SEMANTIC_LIGHTNING_QUADS];
			int quadIndex = 0;
			for (int layer = 0; layer < 4; layer++) {
				RandomSource layerRandom = RandomSource.create(lightningBoltRenderState.seed);
				for (int branch = 0; branch < 3; branch++) {
					int start = branch > 0 ? 7 - branch : 7;
					int end = branch > 0 ? start - 2 : 0;
					float x = fs[start] - h, z = gs[start] - j;
					for (int segment = start; segment >= end; segment--) {
						float ox = x, oz = z;
						if (branch == 0) { x += layerRandom.nextInt(11) - 5; z += layerRandom.nextInt(11) - 5; }
						else { x += layerRandom.nextInt(31) - 15; z += layerRandom.nextInt(31) - 15; }
						float u0 = 0.1F + layer * 0.2F, u1 = u0, scale0 = 0.5F, scale1 = 0.45F;
						if (branch == 0) { u0 *= segment * 0.1F + 1.0F; u1 *= (segment - 1.0F) * 0.1F + 1.0F; }
						appendLightningQuad(vertices, uvs, colors, quadIndex++, x, z, segment, ox, oz, scale1, scale1, scale0, u0, u1, false, false, true, false);
						appendLightningQuad(vertices, uvs, colors, quadIndex++, x, z, segment, ox, oz, scale1, scale1, scale0, u0, u1, true, false, true, true);
						appendLightningQuad(vertices, uvs, colors, quadIndex++, x, z, segment, ox, oz, scale1, scale1, scale0, u0, u1, true, true, false, true);
						appendLightningQuad(vertices, uvs, colors, quadIndex++, x, z, segment, ox, oz, scale1, scale1, scale0, u0, u1, false, true, false, false);
					}
				}
			}
			if (quadIndex != SEMANTIC_LIGHTNING_QUADS) {
				throw new IllegalStateException("Rust whole-frame lightning semantic quad count drifted: " + quadIndex);
			}
			if (submitNodeCollector.submitColoredQuadsSemantic(poseStack, RenderType.lightning(), vertices, uvs, colors, lightningBoltRenderState.lightCoords)) return;
			throw new IllegalStateException("Rust whole-frame lightning route rejected semantic quads");
		}
		if (rustPresentation) {
			throw new IllegalStateException("Rust whole-frame lightning route is unavailable; Java custom geometry is not a fallback");
		}
		submitNodeCollector.submitCustomGeometrySemantic(poseStack, RenderType.lightning(), (pose, vertexConsumer) -> {
			Matrix4f matrix4f = pose.pose();

			for (int i = 0; i < 4; i++) {
				RandomSource randomSourcex = RandomSource.create(lightningBoltRenderState.seed);

				for (int jx = 0; jx < 3; jx++) {
					int k = 7;
					int l = 0;
					if (jx > 0) {
						k = 7 - jx;
					}

					if (jx > 0) {
						l = k - 2;
					}

					float hx = fs[k] - h;
					float m = gs[k] - j;

					for (int n = k; n >= l; n--) {
						float o = hx;
						float p = m;
						if (jx == 0) {
							hx += randomSourcex.nextInt(11) - 5;
							m += randomSourcex.nextInt(11) - 5;
						} else {
							hx += randomSourcex.nextInt(31) - 15;
							m += randomSourcex.nextInt(31) - 15;
						}

						float q = 0.5F;
						float r = 0.45F;
						float s = 0.45F;
						float t = 0.5F;
						float u = 0.1F + i * 0.2F;
						if (jx == 0) {
							u *= n * 0.1F + 1.0F;
						}

						float v = 0.1F + i * 0.2F;
						if (jx == 0) {
							v *= (n - 1.0F) * 0.1F + 1.0F;
						}

						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, false, false, true, false);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, true, false, true, true);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, true, true, false, true);
						quad(matrix4f, vertexConsumer, hx, m, n, o, p, 0.45F, 0.45F, 0.5F, u, v, false, true, false, false);
					}
				}
			}
		});
	}

	private static void appendLightningQuad(float[] vertices, float[] uvs, int[] colors, int quad, float x, float z, int segment, float ox, float oz, float sx, float sz, float sy, float u0, float u1, boolean bx0, boolean bz0, boolean bx1, boolean bz1) {
		int v = quad * 12;
		float ax = x + (bx0 ? sy : -sy), az = z + (bz0 ? sy : -sy);
		float bx = ox + (bx0 ? sx : -sx), bz = oz + (bz0 ? sx : -sx);
		float cx = ox + (bx1 ? sx : -sx), cz = oz + (bz1 ? sx : -sx);
		float dx = x + (bx1 ? sy : -sy), dz = z + (bz1 ? sy : -sy);
		float[] p = {ax, segment * 16.0F, az, bx, (segment + 1) * 16.0F, bz, cx, (segment + 1) * 16.0F, cz, dx, segment * 16.0F, dz};
		System.arraycopy(p, 0, vertices, v, 12);
		int t = quad * 8; float[] tex = {0, u0, 1, u1, 1, u1, 0, u0}; System.arraycopy(tex, 0, uvs, t, 8);
		colors[quad] = net.minecraft.util.ARGB.colorFromFloat(0.3F, 0.45F, 0.45F, 0.5F);
	}

	private static void quad(
		Matrix4f matrix4f,
		VertexConsumer vertexConsumer,
		float f,
		float g,
		int i,
		float h,
		float j,
		float k,
		float l,
		float m,
		float n,
		float o,
		boolean bl,
		boolean bl2,
		boolean bl3,
		boolean bl4
	) {
		vertexConsumer.addVertex(matrix4f, f + (bl ? o : -o), (float)(i * 16), g + (bl2 ? o : -o)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, h + (bl ? n : -n), (float)((i + 1) * 16), j + (bl2 ? n : -n)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, h + (bl3 ? n : -n), (float)((i + 1) * 16), j + (bl4 ? n : -n)).setColor(k, l, m, 0.3F);
		vertexConsumer.addVertex(matrix4f, f + (bl3 ? o : -o), (float)(i * 16), g + (bl4 ? o : -o)).setColor(k, l, m, 0.3F);
	}

	public LightningBoltRenderState createRenderState() {
		return new LightningBoltRenderState();
	}

	public void extractRenderState(LightningBolt lightningBolt, LightningBoltRenderState lightningBoltRenderState, float f) {
		super.extractRenderState(lightningBolt, lightningBoltRenderState, f);
		lightningBoltRenderState.seed = lightningBolt.seed;
	}

	protected boolean affectedByCulling(LightningBolt lightningBolt) {
		return false;
	}
}
