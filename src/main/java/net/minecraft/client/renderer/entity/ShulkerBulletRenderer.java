package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ShulkerBulletModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ShulkerBulletRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public class ShulkerBulletRenderer extends EntityRenderer<ShulkerBullet, ShulkerBulletRenderState> {
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/shulker/spark.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE_LOCATION);
	private static final int GLOW_TINT = 654311423;
	private final ShulkerBulletModel model;
	/**
	 * Semantic-only view over the exact animated ShulkerBulletModel root. The
	 * real model applies yaw/pitch first; this already-admitted Model.Simple
	 * wrapper then exposes the resulting copied geometry without widening the
	 * generic EntityModel admission boundary.
	 */
	private final Model.Simple rustSemanticModel;

	public ShulkerBulletRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ShulkerBulletModel(context.bakeLayer(ModelLayers.SHULKER_BULLET));
		this.rustSemanticModel = new Model.Simple(this.model.root(), this.model::renderType);
	}

	protected int getBlockLightLevel(ShulkerBullet shulkerBullet, BlockPos blockPos) {
		return 15;
	}

	public void submit(
		ShulkerBulletRenderState shulkerBulletRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		float f = shulkerBulletRenderState.ageInTicks;
		poseStack.translate(0.0F, 0.15F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(f * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(f * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(f * 0.15F) * 360.0F));
		poseStack.scale(-0.5F, -0.5F, 0.5F);

		RenderType baseRenderType = this.model.renderType(TEXTURE_LOCATION);
		ResourceLocation entityIdentity = RustGalWorldPrimitiveRenderer.entityIdentity(shulkerBulletRenderState);
		boolean rustShulkerBulletEligible = entityIdentity != null
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.rustSemanticModel,
				baseRenderType,
				TEXTURE_LOCATION,
				OverlayTexture.NO_OVERLAY,
				shulkerBulletRenderState.outlineColor,
				null
			)
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.rustSemanticModel,
				RENDER_TYPE,
				TEXTURE_LOCATION,
				OverlayTexture.NO_OVERLAY,
				shulkerBulletRenderState.outlineColor,
				null
			);
		WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(), rustShulkerBulletEligible, ownership
		);

		if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
			// Vanilla ModelFeatureRenderer invokes setupAnim before each pass. Both
			// passes share one pose, so apply it once to the shared root before the
			// no-op semantic wrapper is copied by Rust.
			this.model.setupAnim(shulkerBulletRenderState);
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				baseRenderType,
				TEXTURE_LOCATION,
				entityIdentity,
				shulkerBulletRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame Shulker Bullet base pass was admitted but did not enqueue a copied indexed mesh");
			}

			poseStack.scale(1.5F, 1.5F, 1.5F);
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				RENDER_TYPE,
				TEXTURE_LOCATION,
				entityIdentity,
				shulkerBulletRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				GLOW_TINT
			)) {
				throw new IllegalStateException("Rust whole-frame Shulker Bullet glow pass was admitted but did not enqueue a copied indexed mesh");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", TEXTURE_LOCATION, true, true, false
			);
		} else if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", TEXTURE_LOCATION, false, false, false
			);
		} else {
			submitNodeCollector.submitModel(
				this.model,
				shulkerBulletRenderState,
				poseStack,
				baseRenderType,
				shulkerBulletRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				shulkerBulletRenderState.outlineColor,
				null
			);
			poseStack.scale(1.5F, 1.5F, 1.5F);
			submitNodeCollector.order(1)
				.submitModel(
					this.model,
					shulkerBulletRenderState,
					poseStack,
					RENDER_TYPE,
					shulkerBulletRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					GLOW_TINT,
					null,
					shulkerBulletRenderState.outlineColor,
					null
				);
			if (rustShulkerBulletEligible && !submitNodeCollector.isSemanticCoverageOnly()) {
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
					TEXTURE_LOCATION,
					false,
					false,
					ownership.usesJavaCompatibility()
				);
			}
		}

		poseStack.popPose();
		super.submit(shulkerBulletRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public ShulkerBulletRenderState createRenderState() {
		return new ShulkerBulletRenderState();
	}

	public void extractRenderState(ShulkerBullet shulkerBullet, ShulkerBulletRenderState shulkerBulletRenderState, float f) {
		super.extractRenderState(shulkerBullet, shulkerBulletRenderState, f);
		shulkerBulletRenderState.yRot = shulkerBullet.getYRot(f);
		shulkerBulletRenderState.xRot = shulkerBullet.getXRot(f);
	}
}
