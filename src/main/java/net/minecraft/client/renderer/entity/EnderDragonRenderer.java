package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableList.Builder;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.dragon.EnderDragonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class EnderDragonRenderer extends EntityRenderer<EnderDragon, EnderDragonRenderState> {
	private static final int SEMANTIC_CRYSTAL_BEAM_QUADS = 8;
	public static final ResourceLocation CRYSTAL_BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal_beam.png");
	private static final ResourceLocation DRAGON_EXPLODING_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_exploding.png");
	// VoxelMap: Made accessible
	public static final ResourceLocation DRAGON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
	private static final ResourceLocation DRAGON_EYES_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png");
	private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(DRAGON_LOCATION);
	private static final RenderType DECAL = RenderType.entityDecal(DRAGON_LOCATION);
	private static final RenderType EYES = RenderType.eyes(DRAGON_EYES_LOCATION);
	private static final RenderType BEAM = RenderType.entitySmoothCutout(CRYSTAL_BEAM_LOCATION);
	private static final float HALF_SQRT_3 = (float)(Math.sqrt(3.0) / 2.0);
	// VoxelMap: Made accessible
	public final EnderDragonModel model;

	public EnderDragonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		this.model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
	}

	public void submit(
		EnderDragonRenderState enderDragonRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		float f = enderDragonRenderState.getHistoricalPos(7).yRot();
		float g = (float)(enderDragonRenderState.getHistoricalPos(5).y() - enderDragonRenderState.getHistoricalPos(10).y());
		poseStack.mulPose(Axis.YP.rotationDegrees(-f));
		poseStack.mulPose(Axis.XP.rotationDegrees(g * 10.0F));
		poseStack.translate(0.0F, 0.0F, 1.0F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		int i = OverlayTexture.pack(0.0F, enderDragonRenderState.hasRedOverlay);
		if (enderDragonRenderState.deathTime > 0.0F) {
			int j = ARGB.white(enderDragonRenderState.deathTime / 200.0F);
			submitNodeCollector.order(0)
				.submitModelSemanticTexture(
					this.model,
					enderDragonRenderState,
					poseStack,
					RenderType.dragonExplosionAlpha(DRAGON_EXPLODING_LOCATION),
					enderDragonRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					j,
					DRAGON_EXPLODING_LOCATION,
					enderDragonRenderState.outlineColor,
					null
				);
			if (i == OverlayTexture.NO_OVERLAY) {
				submitNodeCollector.order(1)
					.submitModelSemanticTexture(
						this.model, enderDragonRenderState, poseStack, DECAL,
						enderDragonRenderState.lightCoords, i, -1, DRAGON_LOCATION,
						enderDragonRenderState.outlineColor, null
					);
			} else {
				// Overlay coordinates are copied into the Rust mesh instance's
				// explicit overlay-color metadata; retain the same direct-texture
				// semantic route for the hurt/red-overlay state.
				submitNodeCollector.order(1)
					.submitModelSemanticTexture(
						this.model, enderDragonRenderState, poseStack, DECAL,
						enderDragonRenderState.lightCoords, i, -1, DRAGON_LOCATION,
						enderDragonRenderState.outlineColor, null
					);
			}
		} else {
			submitNodeCollector.order(0)
				.submitModelSemanticTexture(
					this.model, enderDragonRenderState, poseStack, RENDER_TYPE, enderDragonRenderState.lightCoords, i, -1,
					DRAGON_LOCATION, enderDragonRenderState.outlineColor, null
				);
		}

		submitNodeCollector.submitModelSemanticTexture(
			this.model,
			enderDragonRenderState,
			poseStack,
			EYES,
			enderDragonRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			-1,
			DRAGON_EYES_LOCATION,
			enderDragonRenderState.outlineColor,
			null
		);
		if (enderDragonRenderState.deathTime > 0.0F) {
			float h = enderDragonRenderState.deathTime / 200.0F;
			poseStack.pushPose();
			poseStack.translate(0.0F, -1.0F, -2.0F);
			submitRays(poseStack, h, submitNodeCollector, RenderType.dragonRays());
			submitRays(poseStack, h, submitNodeCollector, RenderType.dragonRaysDepth());
			poseStack.popPose();
		}

		poseStack.popPose();
		if (enderDragonRenderState.beamOffset != null) {
			submitCrystalBeams(
				(float)enderDragonRenderState.beamOffset.x,
				(float)enderDragonRenderState.beamOffset.y,
				(float)enderDragonRenderState.beamOffset.z,
				enderDragonRenderState.ageInTicks,
				poseStack,
				submitNodeCollector,
				enderDragonRenderState.lightCoords
			);
		}

		super.submit(enderDragonRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	private static void submitRays(PoseStack poseStack, float f, SubmitNodeCollector submitNodeCollector, RenderType renderType) {
		boolean rustProcedural = net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute()
			.usesRustWholeFrameVulkan();
		boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
		if (rustPresentation && (!Float.isFinite(f) || f < 0.0F || f > 1.0F)) {
			throw new IllegalStateException("Rust whole-frame End Dragon ray route rejected non-finite or out-of-range death progress");
		}
		if (rustProcedural) {
			float g = Math.min(f > 0.8F ? (f - 0.8F) / 0.2F : 0.0F, 1.0F);
			int headColor = ARGB.colorFromFloat(1.0F - g, 1.0F, 1.0F, 1.0F);
			RandomSource randomSource = RandomSource.create(432L);
			Vector3f origin = new Vector3f();
			Vector3f first = new Vector3f();
			Vector3f second = new Vector3f();
			Vector3f third = new Vector3f();
			Quaternionf quaternion = new Quaternionf();
			int rayCount = Mth.floor((f + f * f) / 2.0F * 60.0F);
			for (int ray = 0; ray < rayCount; ray++) {
				quaternion.rotationXYZ(randomSource.nextFloat() * (float)(Math.PI * 2), randomSource.nextFloat() * (float)(Math.PI * 2), randomSource.nextFloat() * (float)(Math.PI * 2))
					.rotateXYZ(randomSource.nextFloat() * (float)(Math.PI * 2), randomSource.nextFloat() * (float)(Math.PI * 2), randomSource.nextFloat() * (float)(Math.PI * 2) + f * (float)(Math.PI / 2));
				poseStack.mulPose(quaternion);
				float length = randomSource.nextFloat() * 20.0F + 5.0F + g * 10.0F;
				float radius = randomSource.nextFloat() * 2.0F + 1.0F + g * 2.0F;
				first.set(-HALF_SQRT_3 * radius, length, -0.5F * radius);
				second.set(HALF_SQRT_3 * radius, length, -0.5F * radius);
				third.set(0.0F, length, radius);
				float[] vertices = {
					origin.x(), origin.y(), origin.z(), first.x(), first.y(), first.z(), second.x(), second.y(), second.z(), second.x(), second.y(), second.z(),
					origin.x(), origin.y(), origin.z(), second.x(), second.y(), second.z(), third.x(), third.y(), third.z(), third.x(), third.y(), third.z(),
					origin.x(), origin.y(), origin.z(), third.x(), third.y(), third.z(), first.x(), first.y(), first.z(), first.x(), first.y(), first.z()
				};
				float[] uvs = {0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1};
				int[] colors = {headColor, 0xFFFF00FF, 0xFFFF00FF};
				if (!submitNodeCollector.submitColoredQuadsSemantic(poseStack, renderType, vertices, uvs, colors, 15728880)) {
					throw new IllegalStateException("Rust procedural ray route rejected semantic quads");
				}
			}
			return;
		}
		if (rustPresentation) {
			throw new IllegalStateException("Rust whole-frame End Dragon ray route is unavailable; Java custom geometry is not a fallback");
		}
		submitNodeCollector.submitCustomGeometrySemantic(
			// Rust receives the same deterministic rays through semantic quads below;
			// this Java producer is retained only for compatibility routes.
			poseStack,
			renderType,
			(pose, vertexConsumer) -> {
				float g = Math.min(f > 0.8F ? (f - 0.8F) / 0.2F : 0.0F, 1.0F);
				int i = ARGB.colorFromFloat(1.0F - g, 1.0F, 1.0F, 1.0F);
				int j = 16711935;
				RandomSource randomSource = RandomSource.create(432L);
				Vector3f vector3f = new Vector3f();
				Vector3f vector3f2 = new Vector3f();
				Vector3f vector3f3 = new Vector3f();
				Vector3f vector3f4 = new Vector3f();
				Quaternionf quaternionf = new Quaternionf();
				int k = Mth.floor((f + f * f) / 2.0F * 60.0F);

				for (int l = 0; l < k; l++) {
					quaternionf.rotationXYZ(
							randomSource.nextFloat() * (float) (Math.PI * 2), randomSource.nextFloat() * (float) (Math.PI * 2), randomSource.nextFloat() * (float) (Math.PI * 2)
						)
						.rotateXYZ(
							randomSource.nextFloat() * (float) (Math.PI * 2),
							randomSource.nextFloat() * (float) (Math.PI * 2),
							randomSource.nextFloat() * (float) (Math.PI * 2) + f * (float) (Math.PI / 2)
						);
					pose.rotate(quaternionf);
					float h = randomSource.nextFloat() * 20.0F + 5.0F + g * 10.0F;
					float m = randomSource.nextFloat() * 2.0F + 1.0F + g * 2.0F;
					vector3f2.set(-HALF_SQRT_3 * m, h, -0.5F * m);
					vector3f3.set(HALF_SQRT_3 * m, h, -0.5F * m);
					vector3f4.set(0.0F, h, m);
					vertexConsumer.addVertex(pose, vector3f).setColor(i);
					vertexConsumer.addVertex(pose, vector3f2).setColor(16711935);
					vertexConsumer.addVertex(pose, vector3f3).setColor(16711935);
					vertexConsumer.addVertex(pose, vector3f).setColor(i);
					vertexConsumer.addVertex(pose, vector3f3).setColor(16711935);
					vertexConsumer.addVertex(pose, vector3f4).setColor(16711935);
					vertexConsumer.addVertex(pose, vector3f).setColor(i);
					vertexConsumer.addVertex(pose, vector3f4).setColor(16711935);
					vertexConsumer.addVertex(pose, vector3f2).setColor(16711935);
				}
			}
		);
	}

	public static void submitCrystalBeams(float f, float g, float h, float i, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int j) {
		float k = Mth.sqrt(f * f + h * h);
		float l = Mth.sqrt(f * f + g * g + h * h);
		
		boolean rustCrystalBeam = net.vulkanic.world.WorldRenderRoutePolicy.currentCrystalBeamRoute()
			.usesRustWholeFrameVulkan();
		boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
		// Iris state is consulted only by the Java compatibility route.
		int iris$previousEntity = 0;
		if (!rustPresentation && !rustCrystalBeam
			&& net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getEntityIds() != null) {
			iris$previousEntity = net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
			net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentEntity(
				net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getEntityIds().applyAsInt(
					new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft", "end_crystal_beam")
				)
			);
		}
		
		poseStack.pushPose();
		poseStack.translate(0.0F, 2.0F, 0.0F);
		poseStack.mulPose(Axis.YP.rotation((float)(-Math.atan2(h, f)) - (float) (Math.PI / 2)));
		poseStack.mulPose(Axis.XP.rotation((float)(-Math.atan2(k, g)) - (float) (Math.PI / 2)));
		float m = 0.0F - i * 0.01F;
		float n = l / 32.0F - i * 0.01F;
		float[] beamVertices = new float[SEMANTIC_CRYSTAL_BEAM_QUADS * 12];
		float[] beamUvs = new float[SEMANTIC_CRYSTAL_BEAM_QUADS * 8];
		int[] beamColors = new int[SEMANTIC_CRYSTAL_BEAM_QUADS * 4];
		int vertexIndex = 0;
		int uvIndex = 0;
		float bx = 0.0F, by = 0.75F, bu = 0.0F;
		for (int nx = 1; nx <= 8; nx++) {
			float o = Mth.sin(nx * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float p = Mth.cos(nx * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float q = nx / 8.0F;
			float[][] quad = {{bx * 0.2F, by * 0.2F, 0.0F}, {bx, by, l}, {o, p, l}, {o * 0.2F, p * 0.2F, 0.0F}};
			float[][] tex = {{bu, m}, {bu, n}, {q, n}, {q, m}};
			for (int v = 0; v < 4; v++) { beamVertices[vertexIndex++] = quad[v][0]; beamVertices[vertexIndex++] = quad[v][1]; beamVertices[vertexIndex++] = quad[v][2]; beamUvs[uvIndex++] = tex[v][0]; beamUvs[uvIndex++] = tex[v][1]; }
			beamColors[(nx - 1) * 4] = -16777216; beamColors[(nx - 1) * 4 + 1] = -1; beamColors[(nx - 1) * 4 + 2] = -1; beamColors[(nx - 1) * 4 + 3] = -16777216;
			bx = o; by = p; bu = q;
		}
		if (vertexIndex != SEMANTIC_CRYSTAL_BEAM_QUADS * 12
			|| uvIndex != SEMANTIC_CRYSTAL_BEAM_QUADS * 8
			|| beamColors.length != SEMANTIC_CRYSTAL_BEAM_QUADS * 4) {
			throw new IllegalStateException("Rust whole-frame crystal-beam semantic payload count drifted");
		}
				if (rustCrystalBeam && submitNodeCollector.submitCrystalBeamSemantic(poseStack, BEAM, CRYSTAL_BEAM_LOCATION, beamVertices, beamUvs, beamColors, j)) {
			poseStack.popPose();
			return;
		}
		if (rustCrystalBeam) {
			throw new IllegalStateException("Rust whole-frame End Crystal beam route rejected semantic quads");
		}
		if (rustPresentation) {
			throw new IllegalStateException("Rust whole-frame End Crystal beam route is unavailable; Java custom geometry is not a fallback");
		}
		submitNodeCollector.submitCustomGeometrySemantic(
			poseStack,
			BEAM,
			(pose, vertexConsumer) -> {
				int jx = 8;
				float kx = 0.0F;
				float lx = 0.75F;
				float mx = 0.0F;

				for (int nx = 1; nx <= 8; nx++) {
					float o = Mth.sin(nx * (float) (Math.PI * 2) / 8.0F) * 0.75F;
					float p = Mth.cos(nx * (float) (Math.PI * 2) / 8.0F) * 0.75F;
					float q = nx / 8.0F;
					vertexConsumer.addVertex(pose, kx * 0.2F, lx * 0.2F, 0.0F)
						.setColor(-16777216)
						.setUv(mx, m)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(j)
						.setNormal(pose, 0.0F, -1.0F, 0.0F);
					vertexConsumer.addVertex(pose, kx, lx, l).setColor(-1).setUv(mx, n).setOverlay(OverlayTexture.NO_OVERLAY).setLight(j).setNormal(pose, 0.0F, -1.0F, 0.0F);
					vertexConsumer.addVertex(pose, o, p, l).setColor(-1).setUv(q, n).setOverlay(OverlayTexture.NO_OVERLAY).setLight(j).setNormal(pose, 0.0F, -1.0F, 0.0F);
					vertexConsumer.addVertex(pose, o * 0.2F, p * 0.2F, 0.0F)
						.setColor(-16777216)
						.setUv(q, m)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(j)
						.setNormal(pose, 0.0F, -1.0F, 0.0F);
					kx = o;
					lx = p;
					mx = q;
				}
			}
		);
		poseStack.popPose();
		
		// Iris: Restore previous entity ID (from MixinEnderDragonRenderer)
		if (iris$previousEntity != 0) {
			net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentEntity(iris$previousEntity);
		}
	}

	public EnderDragonRenderState createRenderState() {
		return new EnderDragonRenderState();
	}

	public void extractRenderState(EnderDragon enderDragon, EnderDragonRenderState enderDragonRenderState, float f) {
		super.extractRenderState(enderDragon, enderDragonRenderState, f);
		enderDragonRenderState.flapTime = Mth.lerp(f, enderDragon.oFlapTime, enderDragon.flapTime);
		enderDragonRenderState.deathTime = enderDragon.dragonDeathTime > 0 ? enderDragon.dragonDeathTime + f : 0.0F;
		enderDragonRenderState.hasRedOverlay = enderDragon.hurtTime > 0;
		EndCrystal endCrystal = enderDragon.nearestCrystal;
		if (endCrystal != null) {
			Vec3 vec3 = endCrystal.getPosition(f).add(0.0, EndCrystalRenderer.getY(endCrystal.time + f), 0.0);
			enderDragonRenderState.beamOffset = vec3.subtract(enderDragon.getPosition(f));
		} else {
			enderDragonRenderState.beamOffset = null;
		}

		DragonPhaseInstance dragonPhaseInstance = enderDragon.getPhaseManager().getCurrentPhase();
		enderDragonRenderState.isLandingOrTakingOff = dragonPhaseInstance == EnderDragonPhase.LANDING || dragonPhaseInstance == EnderDragonPhase.TAKEOFF;
		enderDragonRenderState.isSitting = dragonPhaseInstance.isSitting();
		BlockPos blockPos = enderDragon.level().getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(enderDragon.getFightOrigin()));
		enderDragonRenderState.distanceToEgg = blockPos.distToCenterSqr(enderDragon.position());
		enderDragonRenderState.partialTicks = enderDragon.isDeadOrDying() ? 0.0F : f;
		enderDragonRenderState.flightHistory.copyFrom(enderDragon.flightHistory);
	}

	protected void extractAdditionalHitboxes(EnderDragon enderDragon, Builder<HitboxRenderState> builder, float f) {
		super.extractAdditionalHitboxes(enderDragon, builder, f);
		double d = -Mth.lerp(f, enderDragon.xOld, enderDragon.getX());
		double e = -Mth.lerp(f, enderDragon.yOld, enderDragon.getY());
		double g = -Mth.lerp(f, enderDragon.zOld, enderDragon.getZ());

		for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
			AABB aABB = enderDragonPart.getBoundingBox();
			HitboxRenderState hitboxRenderState = new HitboxRenderState(
				aABB.minX - enderDragonPart.getX(),
				aABB.minY - enderDragonPart.getY(),
				aABB.minZ - enderDragonPart.getZ(),
				aABB.maxX - enderDragonPart.getX(),
				aABB.maxY - enderDragonPart.getY(),
				aABB.maxZ - enderDragonPart.getZ(),
				(float)(d + Mth.lerp(f, enderDragonPart.xOld, enderDragonPart.getX())),
				(float)(e + Mth.lerp(f, enderDragonPart.yOld, enderDragonPart.getY())),
				(float)(g + Mth.lerp(f, enderDragonPart.zOld, enderDragonPart.getZ())),
				0.25F,
				1.0F,
				0.0F
			);
			builder.add(hitboxRenderState);
		}
	}

	protected boolean affectedByCulling(EnderDragon enderDragon) {
		return false;
	}
}
