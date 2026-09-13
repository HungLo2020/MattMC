package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.DrownedModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class DrownedOuterLayer extends RenderLayer<ZombieRenderState, DrownedModel> {
	private static final ResourceLocation DROWNED_OUTER_LAYER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/zombie/drowned_outer_layer.png");
	private static final ResourceLocation DROWNED_OUTER_IDENTITY = ResourceLocation.withDefaultNamespace("drowned_outer");
	private final DrownedModel model;
	private final DrownedModel babyModel;

	public DrownedOuterLayer(RenderLayerParent<ZombieRenderState, DrownedModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new DrownedModel(entityModelSet.bakeLayer(ModelLayers.DROWNED_OUTER_LAYER));
		this.babyModel = new DrownedModel(entityModelSet.bakeLayer(ModelLayers.DROWNED_BABY_OUTER_LAYER));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ZombieRenderState zombieRenderState, float f, float g) {
		DrownedModel drownedModel = zombieRenderState.isBaby ? this.babyModel : this.model;
		RenderType renderType = RenderType.entityCutoutNoCull(DROWNED_OUTER_LAYER_LOCATION);
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				drownedModel, zombieRenderState, poseStack.last(), renderType, DROWNED_OUTER_LAYER_LOCATION,
				DROWNED_OUTER_IDENTITY, i, LivingEntityRenderer.getOverlayCoords(zombieRenderState, 0.0F), -1
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", DROWNED_OUTER_LAYER_LOCATION, drownedModel.getClass().getName(),
					zombieRenderState.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", DROWNED_OUTER_LAYER_LOCATION, drownedModel.getClass().getName(),
				zombieRenderState.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame drowned-outer route has no copied semantic mesh");
		}
			submitNodeCollector.order(1)
				.submitModelSemanticTexture(
					drownedModel,
					zombieRenderState,
					poseStack,
					renderType,
					i,
					LivingEntityRenderer.getOverlayCoords(zombieRenderState, 0.0F),
					-1,
					DROWNED_OUTER_LAYER_LOCATION,
					zombieRenderState.outlineColor,
					null
				);
	}
}
