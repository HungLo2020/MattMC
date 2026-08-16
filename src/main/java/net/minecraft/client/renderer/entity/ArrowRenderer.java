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

	public ArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	public void submit(S arrowRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(arrowRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(arrowRenderState.xRot));
		ResourceLocation textureLocation = this.getTextureLocation(arrowRenderState);
		boolean eligible = RustGalWorldPrimitiveRenderer.isArrowMeshEligible(textureLocation, arrowRenderState);
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentArrowRoute(eligible);
		// A whole-frame Rust session has no Java entity presenter to recover an
		// unsupported Arrow after extraction. Fail before route selection rather
		// than silently retaining an unexecuted Java model submit.
		if (!submitNodeCollector.isSemanticCoverageOnly()
			&& !eligible
			&& WorldRenderRoutePolicy.currentArrowRoute(true).usesRustWholeFrameVulkan()) {
			throw new IllegalStateException("Rust whole-frame Arrow encountered unsupported semantic state before route selection");
		}
		if (!submitNodeCollector.isSemanticCoverageOnly() && route.usesRustWholeFrameVulkan()) {
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
		} else if (route != WorldRenderRoutePolicy.Route.DISABLED) {
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
		if (submitNodeCollector.isSemanticCoverageOnly() || !route.usesRustWholeFrameVulkan()) {
			super.submit(arrowRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	protected abstract ResourceLocation getTextureLocation(S arrowRenderState);

	public void extractRenderState(T abstractArrow, S arrowRenderState, float f) {
		super.extractRenderState(abstractArrow, arrowRenderState, f);
		arrowRenderState.xRot = abstractArrow.getXRot(f);
		arrowRenderState.yRot = abstractArrow.getYRot(f);
		arrowRenderState.shake = abstractArrow.shakeTime - f;
	}
}
