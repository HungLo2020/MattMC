package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EvokerFangsModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EvokerFangsRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.StandaloneModelRenderOwnershipPolicy;
import net.vulkanic.world.WorldRenderRoutePolicy;
import net.minecraft.world.entity.projectile.EvokerFangs;

@Environment(EnvType.CLIENT)
public class EvokerFangsRenderer extends EntityRenderer<EvokerFangs, EvokerFangsRenderState> {
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/illager/evoker_fangs.png");
	private static final ResourceLocation EVOKER_FANGS_ENTITY_ID = ResourceLocation.withDefaultNamespace("evoker_fangs");
	private final EvokerFangsModel model;

	public EvokerFangsRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new EvokerFangsModel(context.bakeLayer(ModelLayers.EVOKER_FANGS));
	}

	public void submit(
		EvokerFangsRenderState evokerFangsRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		float f = evokerFangsRenderState.biteProgress;
		if (f != 0.0F) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - evokerFangsRenderState.yRot));
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			var renderType = this.model.renderType(TEXTURE_LOCATION);
			boolean eligible = RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
				this.model, renderType, TEXTURE_LOCATION, OverlayTexture.NO_OVERLAY, evokerFangsRenderState.outlineColor, null
			);
			WorldRenderRoutePolicy.Route ownership = StandaloneModelRenderOwnershipPolicy.currentOwnershipRoute();
			StandaloneModelRenderOwnershipPolicy.Disposition disposition = StandaloneModelRenderOwnershipPolicy.classify(
				submitNodeCollector.isSemanticCoverageOnly(), eligible, ownership
			);
			if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_AVAILABLE) {
				if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
					this.model, evokerFangsRenderState, poseStack.last(), renderType, TEXTURE_LOCATION,
					EVOKER_FANGS_ENTITY_ID,
					evokerFangsRenderState.lightCoords, OverlayTexture.NO_OVERLAY, -1,
					evokerFangsRenderState.outlineColor
				)) {
					throw new IllegalStateException("Rust whole-frame EvokerFangs route selected without a copied indexed mesh request");
				}
				net.minecraft.client.dev.DeterministicCameraCapture.noteModelMeshClientSemanticSubmission(
					evokerFangsRenderState.entityId);
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", TEXTURE_LOCATION, true, true, false
				);
			} else if (disposition == StandaloneModelRenderOwnershipPolicy.Disposition.RUST_UNAVAILABLE) {
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-unavailable", TEXTURE_LOCATION, false, false, false
				);
				throw new IllegalStateException("Rust whole-frame EvokerFangs route has no semantic mesh");
			} else {
				if (eligible) {
					RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						ownership == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy", TEXTURE_LOCATION,
						false, false, !submitNodeCollector.isSemanticCoverageOnly() && ownership.usesJavaCompatibility()
					);
				}
				submitNodeCollector.submitModelSemanticTexture(
					this.model,
					evokerFangsRenderState,
					poseStack,
					renderType,
					evokerFangsRenderState.lightCoords,
					OverlayTexture.NO_OVERLAY,
					-1,
					TEXTURE_LOCATION,
					evokerFangsRenderState.outlineColor,
					null
				);
			}
			poseStack.popPose();
			super.submit(evokerFangsRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public EvokerFangsRenderState createRenderState() {
		return new EvokerFangsRenderState();
	}

	public void extractRenderState(EvokerFangs evokerFangs, EvokerFangsRenderState evokerFangsRenderState, float f) {
		super.extractRenderState(evokerFangs, evokerFangsRenderState, f);
		evokerFangsRenderState.yRot = evokerFangs.getYRot();
		evokerFangsRenderState.biteProgress = evokerFangs.getAnimationProgress(f);
	}
}
