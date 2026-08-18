package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.ArrowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;
import net.minecraft.world.entity.projectile.AbstractArrow;

@Environment(EnvType.CLIENT)
public abstract class ArrowRenderer<T extends AbstractArrow, S extends ArrowRenderState> extends EntityRenderer<T, S> {
	private final ArrowModel model;

	public ItemRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	public void submit(S arrowRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(arrowRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(arrowRenderState.xRot));
		ResourceLocation textureLocation = this.getTextureLocation(arrowRenderState);
		boolean eligible = RustGalWorldPrimitiveRenderer.isArrowMeshEligible(textureLocation, arrowRenderState);
		WorldRenderRoutePolicy.Route ownership = WorldRenderRoutePolicy.currentArrowOwnershipRoute();
		ArrowSubmitDisposition disposition = classifyArrowSubmit(
			submitNodeCollector.isSemanticCoverageOnly(), eligible, ownership
		);

		if (disposition == ArrowSubmitDisposition.RUST_AVAILABLE) {
			// Mirrors ModelFeatureRenderer's normal producer-side animation step
			// before copying the transient ArrowModel pose into semantic geometry.
			this.model.setupAnim(arrowRenderState);
			if (!RustGalWorldPrimitiveRenderer.enqueueArrowModel(
				this.model.root(),
				arrowRenderState,
				poseStack.last(),
				textureLocation,
				arrowRenderState.lightCoords
			)) {
				throw new IllegalStateException("Rust Arrow route selected without an indexed mesh request");
			}
			RustGalWorldPrimitiveRenderer.recordArrowRouteDecision(
				"rust-vulkan-whole-frame", textureLocation, true, true, false
			);
		} else if (disposition == ArrowSubmitDisposition.RUST_UNAVAILABLE) {
			// Rust owns this callsite, but this Arrow state is not representable yet.
			// Fail closed for the frame: never authorize a hidden Java entity submit.
			RustGalWorldPrimitiveRenderer.recordArrowRouteDecision(
				"rust-vulkan-unavailable", textureLocation, false, false, false
			);
		} else if (disposition == ArrowSubmitDisposition.JAVA_COMPATIBILITY) {
			submitNodeCollector.submitModel(
				this.model,
				arrowRenderState,
				poseStack,
				RenderType.entityCutout(textureLocation),
				arrowRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				arrowRenderState.outlineColor,
				null
			);
			RustGalWorldPrimitiveRenderer.recordArrowRouteDecision(
				"java-legacy", textureLocation, false, false, !submitNodeCollector.isSemanticCoverageOnly()
			);
		} else {
			RustGalWorldPrimitiveRenderer.recordArrowRouteDecision("disabled", textureLocation, false, false, false);
		}
		poseStack.popPose();
		if (disposition == ArrowSubmitDisposition.JAVA_COMPATIBILITY
			|| disposition == ArrowSubmitDisposition.DISABLED) {
			super.submit(arrowRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	static ArrowSubmitDisposition classifyArrowSubmit(
		boolean semanticCoverageOnly,
		boolean eligible,
		WorldRenderRoutePolicy.Route ownership
	) {
		if (ownership == WorldRenderRoutePolicy.Route.DISABLED) {
			return ArrowSubmitDisposition.DISABLED;
		}
		if (semanticCoverageOnly) {
			return ArrowSubmitDisposition.JAVA_COMPATIBILITY;
		}
		if (ownership.usesRustWholeFrameVulkan()) {
			return eligible ? ArrowSubmitDisposition.RUST_AVAILABLE : ArrowSubmitDisposition.RUST_UNAVAILABLE;
		}
		return ArrowSubmitDisposition.JAVA_COMPATIBILITY;
	}

	static enum ArrowSubmitDisposition {
		DISABLED,
		JAVA_COMPATIBILITY,
		RUST_AVAILABLE,
		RUST_UNAVAILABLE
	}

	protected abstract ResourceLocation getTextureLocation(S arrowRenderState);

	public void extractRenderState(T abstractArrow, S arrowRenderState, float f) {
		super.extractRenderState(abstractArrow, arrowRenderState, f);
		arrowRenderState.xRot = abstractArrow.getXRot(f);
		arrowRenderState.yRot = abstractArrow.getYRot(f);
		arrowRenderState.shake = abstractArrow.shakeTime - f;
	}
}
