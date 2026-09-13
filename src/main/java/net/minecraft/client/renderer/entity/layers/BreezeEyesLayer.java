package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class BreezeEyesLayer extends RenderLayer<BreezeRenderState, BreezeModel> {
	private static final RenderType BREEZE_EYES = RenderType.breezeEyes(ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_eyes.png"));
	private static final ResourceLocation BREEZE_EYES_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_eyes.png");
	private static final ResourceLocation BREEZE_EYES_IDENTITY = ResourceLocation.withDefaultNamespace("breeze_eyes");
	private final BreezeModel model;

	public BreezeEyesLayer(RenderLayerParent<BreezeRenderState, BreezeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new BreezeModel(entityModelSet.bakeLayer(ModelLayers.BREEZE_EYES));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, BreezeRenderState breezeRenderState, float f, float g) {
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneTranslucentModelMesh(
				this.model, breezeRenderState, poseStack.last(), BREEZE_EYES,
				BREEZE_EYES_TEXTURE, BREEZE_EYES_IDENTITY, i, OverlayTexture.NO_OVERLAY, -1
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", BREEZE_EYES_TEXTURE, this.model.getClass().getName(),
					breezeRenderState.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", BREEZE_EYES_TEXTURE, this.model.getClass().getName(),
				breezeRenderState.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame breeze-eyes route has no copied semantic mesh");
		}
		submitNodeCollector.order(1)
			.submitModelSemanticTexture(this.model, breezeRenderState, poseStack, BREEZE_EYES, i, OverlayTexture.NO_OVERLAY, -1,
				BREEZE_EYES_TEXTURE, breezeRenderState.outlineColor, null);
	}
}
