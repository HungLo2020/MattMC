package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SheepWoolLayer extends RenderLayer<SheepRenderState, SheepModel> {
	private static final ResourceLocation SHEEP_WOOL_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_wool.png");
	private final EntityModel<SheepRenderState> adultModel;
	private final EntityModel<SheepRenderState> babyModel;

	public SheepWoolLayer(RenderLayerParent<SheepRenderState, SheepModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.adultModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_WOOL));
		this.babyModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_BABY_WOOL));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, SheepRenderState sheepRenderState, float f, float g) {
		if (!sheepRenderState.isSheared) {
			EntityModel<SheepRenderState> entityModel = sheepRenderState.isBaby ? this.babyModel : this.adultModel;
			int j = LivingEntityRenderer.getOverlayCoords(sheepRenderState, 0.0F);
			if (sheepRenderState.isInvisible) {
				if (sheepRenderState.appearsGlowing()) {
					if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
						&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()
						&& j == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
						&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMeshOutlineOnly(
							entityModel, sheepRenderState, poseStack.last(), RenderType.entityCutoutNoCull(SHEEP_WOOL_LOCATION),
							SHEEP_WOOL_LOCATION, ResourceLocation.withDefaultNamespace("sheep_wool"), i,
							sheepRenderState.outlineColor)) {
						return;
					}
					if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
						&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
						throw new IllegalStateException("Rust whole-frame sheep-wool outline route has no semantic mesh");
					}
					if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
						throw new IllegalStateException("Rust whole-frame sheep-wool outline route is unavailable; Java model geometry is not a fallback");
					}
					submitNodeCollector.submitModelSemantic(
						entityModel,
						sheepRenderState,
						poseStack,
						RenderType.outline(SHEEP_WOOL_LOCATION),
						i,
						LivingEntityRenderer.getOverlayCoords(sheepRenderState, 0.0F),
						-16777216,
						null,
						sheepRenderState.outlineColor,
						null
					);
				}
			} else {
				if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()
					&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
						entityModel, sheepRenderState, poseStack.last(), RenderType.entityCutoutNoCull(SHEEP_WOOL_LOCATION),
						SHEEP_WOOL_LOCATION, ResourceLocation.withDefaultNamespace("sheep_wool"), i,
						sheepRenderState.getWoolColor(), 0, sheepRenderState.outlineColor)) {
					return;
				}
				if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
					throw new IllegalStateException("Rust whole-frame sheep-wool route has no semantic mesh");
				}
				coloredCutoutModelCopyLayerRender(entityModel, SHEEP_WOOL_LOCATION, poseStack, submitNodeCollector, i, sheepRenderState, sheepRenderState.getWoolColor(), 0);
			}
		}
	}
}
