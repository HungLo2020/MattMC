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
public class BreezeWindLayer extends RenderLayer<BreezeRenderState, BreezeModel> {
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
	private static final ResourceLocation BREEZE_WIND_IDENTITY = ResourceLocation.withDefaultNamespace("breeze_wind");
	private final BreezeModel model;

	public BreezeWindLayer(RenderLayerParent<BreezeRenderState, BreezeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new BreezeModel(entityModelSet.bakeLayer(ModelLayers.BREEZE_WIND));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, BreezeRenderState breezeRenderState, float f, float g) {
		float offsetU = this.xOffset(breezeRenderState.ageInTicks) % 1.0F;
		RenderType renderType = RenderType.breezeWind(TEXTURE_LOCATION, offsetU, 0.0F);
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneScrollingTranslucentModelMesh(
				this.model, breezeRenderState, poseStack.last(), renderType, TEXTURE_LOCATION,
				BREEZE_WIND_IDENTITY, i, OverlayTexture.NO_OVERLAY, -1, offsetU
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", TEXTURE_LOCATION, this.model.getClass().getName(),
					breezeRenderState.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", TEXTURE_LOCATION, this.model.getClass().getName(),
				breezeRenderState.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame breeze-wind route has no copied semantic mesh");
		}
		submitNodeCollector.order(1)
			.submitModelSemanticTexture(this.model, breezeRenderState, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, -1, TEXTURE_LOCATION, breezeRenderState.outlineColor, null);
	}

	private float xOffset(float f) {
		return f * 0.02F;
	}
}
