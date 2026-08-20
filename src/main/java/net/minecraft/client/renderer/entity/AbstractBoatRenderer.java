package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public abstract class AbstractBoatRenderer extends EntityRenderer<AbstractBoat, BoatRenderState> {
	public AbstractBoatRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.8F;
	}

	public void submit(BoatRenderState boatRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.375F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - boatRenderState.yRot));
		float f = boatRenderState.hurtTime;
		if (f > 0.0F) {
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(f) * f * boatRenderState.damageTime / 10.0F * boatRenderState.hurtDir));
		}

		if (!boatRenderState.isUnderWater && !Mth.equal(boatRenderState.bubbleAngle, 0.0F)) {
			poseStack.mulPose(new Quaternionf().setAngleAxis(boatRenderState.bubbleAngle * (float) (Math.PI / 180.0), 1.0F, 0.0F, 1.0F));
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
		RenderType boatRenderType = this.renderType();
		ResourceLocation boatTexture = this.textureLocation();
		Model.Simple rustSemanticModel = this.rustSemanticModel();
		ResourceLocation entityIdentity = RustGalWorldPrimitiveRenderer.entityIdentity(boatRenderState);
		boolean rustBoatHullEligible = entityIdentity != null
			&& RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				rustSemanticModel,
				boatRenderType,
				boatTexture,
				OverlayTexture.NO_OVERLAY,
				boatRenderState.outlineColor,
				null
			);
		WorldRenderRoutePolicy.Route boatHullOwnership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
		StandaloneModelRenderOwnershipPolicy.Disposition boatHullDisposition = StandaloneModelRenderOwnershipPolicy.classify(
			submitNodeCollector.isSemanticCoverageOnly(),
			rustBoatHullEligible,
			boatHullOwnership
		);
		if (boatHullDisposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
			// Vanilla ModelFeatureRenderer normally invokes setupAnim immediately
			// before rendering. Do that at the semantic callsite so the shared baked
			// root contains the exact current paddle poses before Rust copies it.
			this.model().setupAnim(boatRenderState);
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				rustSemanticModel,
				Unit.INSTANCE,
				poseStack.last(),
				boatRenderType,
				boatTexture,
				entityIdentity,
				boatRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame boat hull was admitted but did not enqueue a copied indexed mesh");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", boatTexture, true, true, false
			);
		} else if (boatHullDisposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", boatTexture, false, false, false
			);
		} else {
			if (rustBoatHullEligible && !submitNodeCollector.isSemanticCoverageOnly()) {
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					boatHullOwnership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
					boatTexture,
					false,
					false,
					boatHullOwnership.usesJavaCompatibility()
				);
			}
			submitNodeCollector.submitModel(
				this.model(),
				boatRenderState,
				poseStack,
				boatRenderType,
				boatRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				boatRenderState.outlineColor,
				null
			);
		}
		this.submitTypeAdditions(boatRenderState, poseStack, submitNodeCollector, boatRenderState.lightCoords);
		poseStack.popPose();
		super.submit(boatRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected void submitTypeAdditions(BoatRenderState boatRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i) {
	}

	protected abstract EntityModel<BoatRenderState> model();

	/**
	 * Semantic-only view over the same baked root as {@link #model()}. Concrete
	 * renderers use an already-admitted {@link Model.Simple} wrapper whose own
	 * animation hook is intentionally a no-op; {@link #model()} applies the real
	 * current BoatRenderState animation before the shared root is copied.
	 */
	protected abstract Model.Simple rustSemanticModel();

	protected abstract RenderType renderType();

	/** Exact resource-pack identity copied into Rust-owned texture storage. */
	protected abstract ResourceLocation textureLocation();

	public BoatRenderState createRenderState() {
		return new BoatRenderState();
	}

	public void extractRenderState(AbstractBoat abstractBoat, BoatRenderState boatRenderState, float f) {
		super.extractRenderState(abstractBoat, boatRenderState, f);
		boatRenderState.yRot = abstractBoat.getYRot(f);
		boatRenderState.hurtTime = abstractBoat.getHurtTime() - f;
		boatRenderState.hurtDir = abstractBoat.getHurtDir();
		boatRenderState.damageTime = Math.max(abstractBoat.getDamage() - f, 0.0F);
		boatRenderState.bubbleAngle = abstractBoat.getBubbleAngle(f);
		boatRenderState.isUnderWater = abstractBoat.isUnderWater();
		boatRenderState.rowingTimeLeft = abstractBoat.getRowingTime(0, f);
		boatRenderState.rowingTimeRight = abstractBoat.getRowingTime(1, f);
	}
}
