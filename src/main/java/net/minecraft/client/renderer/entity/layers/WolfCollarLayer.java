package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class WolfCollarLayer extends RenderLayer<WolfRenderState, WolfModel> {
	private static final ResourceLocation WOLF_COLLAR_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_collar.png");
	private static final ResourceLocation WOLF_COLLAR_IDENTITY = ResourceLocation.withDefaultNamespace("wolf_collar");

	public WolfCollarLayer(RenderLayerParent<WolfRenderState, WolfModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, WolfRenderState wolfRenderState, float f, float g) {
		DyeColor dyeColor = wolfRenderState.collarColor;
		if (dyeColor != null && !wolfRenderState.isInvisible) {
			int j = dyeColor.getTextureDiffuseColor();
			WolfModel model = this.getParentModel(wolfRenderState);
			RenderType renderType = RenderType.entityCutoutNoCull(WOLF_COLLAR_LOCATION);
			if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
				boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
					model, wolfRenderState, poseStack.last(), renderType, WOLF_COLLAR_LOCATION,
					WOLF_COLLAR_IDENTITY, i, OverlayTexture.NO_OVERLAY, j, wolfRenderState.outlineColor
				);
				if (queued) {
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-whole-frame", WOLF_COLLAR_LOCATION, model.getClass().getName(),
						wolfRenderState.entityId, true, true, false
					);
					return;
				}
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-unavailable", WOLF_COLLAR_LOCATION, model.getClass().getName(),
					wolfRenderState.entityId, false, false, false
				);
				throw new IllegalStateException("Rust whole-frame wolf-collar route has no copied semantic mesh");
			}
			submitNodeCollector.order(1)
				.submitModelSemanticTexture(
					model,
					wolfRenderState,
					poseStack,
					renderType,
					i,
					OverlayTexture.NO_OVERLAY,
					j,
					WOLF_COLLAR_LOCATION,
					wolfRenderState.outlineColor,
					null
				);
		}
	}
}
