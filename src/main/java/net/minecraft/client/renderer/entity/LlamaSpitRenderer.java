package net.minecraft.client.renderer.entity;

import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.LlamaSpitModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LlamaSpitRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;
import net.minecraft.world.entity.projectile.LlamaSpit;

@Environment(EnvType.CLIENT)
public class LlamaSpitRenderer extends EntityRenderer<LlamaSpit, LlamaSpitRenderState> {
	private static final ResourceLocation LLAMA_SPIT_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/llama/spit.png");
	private static final ResourceLocation LLAMA_SPIT_ENTITY_ID = ResourceLocation.withDefaultNamespace("llama_spit");
	private final LlamaSpitModel model;

	public LlamaSpitRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new LlamaSpitModel(context.bakeLayer(ModelLayers.LLAMA_SPIT));
	}

	public void submit(
		LlamaSpitRenderState llamaSpitRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.15F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(llamaSpitRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(llamaSpitRenderState.xRot));
		RenderType renderType = this.model.renderType(LLAMA_SPIT_LOCATION);
		boolean eligible = RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
			this.model,
			renderType,
			LLAMA_SPIT_LOCATION,
			OverlayTexture.NO_OVERLAY,
			llamaSpitRenderState.outlineColor,
			null
		);
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentModelMeshRoute(eligible);
		if (!submitNodeCollector.isSemanticCoverageOnly() && route.usesRustWholeFrameVulkan()) {
			if (!RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.model,
				llamaSpitRenderState,
				poseStack.last(),
				renderType,
				LLAMA_SPIT_LOCATION,
				LLAMA_SPIT_ENTITY_ID,
				llamaSpitRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1
			)) {
				throw new IllegalStateException("Rust whole-frame LlamaSpit route selected without a copied indexed mesh request");
			}
			RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-whole-frame", LLAMA_SPIT_LOCATION, true, true, false
			);
		} else {
			if (eligible) {
				RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					route == WorldRenderRoutePolicy.Route.DISABLED ? "disabled" : "java-legacy",
					LLAMA_SPIT_LOCATION,
					false,
					false,
					!submitNodeCollector.isSemanticCoverageOnly() && route.usesJavaCompatibility()
				);
			}
		submitNodeCollector.submitModel(
			this.model,
			llamaSpitRenderState,
			poseStack,
			renderType,
			llamaSpitRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			llamaSpitRenderState.outlineColor,
			null
		);
		}
		poseStack.popPose();
		super.submit(llamaSpitRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public LlamaSpitRenderState createRenderState() {
		return new LlamaSpitRenderState();
	}

	public void extractRenderState(LlamaSpit llamaSpit, LlamaSpitRenderState llamaSpitRenderState, float f) {
		super.extractRenderState(llamaSpit, llamaSpitRenderState, f);
		llamaSpitRenderState.xRot = llamaSpit.getXRot(f);
		llamaSpitRenderState.yRot = llamaSpit.getYRot(f);
	}
}
