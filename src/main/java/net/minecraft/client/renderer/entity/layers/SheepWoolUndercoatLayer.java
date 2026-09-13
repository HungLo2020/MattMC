package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class SheepWoolUndercoatLayer extends RenderLayer<SheepRenderState, SheepModel> {
	private static final ResourceLocation SHEEP_WOOL_UNDERCOAT_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_wool_undercoat.png");
	private final EntityModel<SheepRenderState> adultModel;
	private final EntityModel<SheepRenderState> babyModel;

	public SheepWoolUndercoatLayer(RenderLayerParent<SheepRenderState, SheepModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.adultModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_WOOL_UNDERCOAT));
		this.babyModel = new SheepFurModel(entityModelSet.bakeLayer(ModelLayers.SHEEP_BABY_WOOL_UNDERCOAT));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, SheepRenderState sheepRenderState, float f, float g) {
		if (!sheepRenderState.isInvisible && (sheepRenderState.isJebSheep || sheepRenderState.woolColor != DyeColor.WHITE)) {
			EntityModel<SheepRenderState> entityModel = sheepRenderState.isBaby ? this.babyModel : this.adultModel;
			boolean rustWholeFrame = net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan();
			if (rustWholeFrame
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
					entityModel, sheepRenderState, poseStack.last(), RenderType.entityCutoutNoCull(SHEEP_WOOL_UNDERCOAT_LOCATION),
					SHEEP_WOOL_UNDERCOAT_LOCATION, ResourceLocation.withDefaultNamespace("sheep_wool_undercoat"), i,
					LivingEntityRenderer.getOverlayCoords(sheepRenderState, 0.0F), sheepRenderState.getWoolColor(),
					sheepRenderState.outlineColor)) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", SHEEP_WOOL_UNDERCOAT_LOCATION, entityModel.getClass().getName(),
					sheepRenderState.entityId, true, true, false
				);
					return;
				}
				if (rustWholeFrame) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-unavailable", SHEEP_WOOL_UNDERCOAT_LOCATION, entityModel.getClass().getName(),
					sheepRenderState.entityId, false, false, false
				);
					throw new IllegalStateException("Rust whole-frame sheep-wool undercoat route has no semantic mesh");
				}
			coloredCutoutModelCopyLayerRender(
				entityModel, SHEEP_WOOL_UNDERCOAT_LOCATION, poseStack, submitNodeCollector, i, sheepRenderState, sheepRenderState.getWoolColor(), 1
			);
		}
	}
}
