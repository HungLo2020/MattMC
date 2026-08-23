package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;

@Environment(EnvType.CLIENT)
public class SlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
	// VoxelMap: Made accessible
	public final SlimeModel model;

	public SlimeOuterLayer(RenderLayerParent<SlimeRenderState, SlimeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new SlimeModel(entityModelSet.bakeLayer(ModelLayers.SLIME_OUTER));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, SlimeRenderState slimeRenderState, float f, float g) {
		boolean bl = slimeRenderState.appearsGlowing() && slimeRenderState.isInvisible;
		if (!slimeRenderState.isInvisible || bl) {
			int j = LivingEntityRenderer.getOverlayCoords(slimeRenderState, 0.0F);
			if (bl) {
				if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
					&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()
					&& j == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
					&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
						this.model, slimeRenderState, poseStack.last(), RenderType.outline(SlimeRenderer.SLIME_LOCATION),
						SlimeRenderer.SLIME_LOCATION, net.minecraft.resources.ResourceLocation.withDefaultNamespace("slime_outer_outline"),
						i, j, -1, slimeRenderState.outlineColor)) {
					return;
				}
				if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
					&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
					throw new IllegalStateException("Rust whole-frame slime-outline route has no semantic mesh");
				}
				submitNodeCollector.order(1)
					.submitModel(
						this.model, slimeRenderState, poseStack, RenderType.outline(SlimeRenderer.SLIME_LOCATION), i, j, -1, null, slimeRenderState.outlineColor, null
					);
			} else {
				if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()
					&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
						this.model, slimeRenderState, poseStack.last(), RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION),
						SlimeRenderer.SLIME_LOCATION, net.minecraft.resources.ResourceLocation.withDefaultNamespace("slime_outer"), i,
						-1, 0, slimeRenderState.outlineColor)) {
					return;
				}
				if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
					throw new IllegalStateException("Rust whole-frame slime route has no semantic mesh");
				}
				submitNodeCollector.order(1)
					.submitModelSemanticTexture(
						this.model, slimeRenderState, poseStack, RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION),
						i, j, -1, SlimeRenderer.SLIME_LOCATION, slimeRenderState.outlineColor, null
					);
			}
		}
	}
}
