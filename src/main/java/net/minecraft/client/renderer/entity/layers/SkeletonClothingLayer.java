package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SkeletonClothingLayer<S extends SkeletonRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private static final ResourceLocation BOGGED_OVERLAY = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/bogged_overlay.png");
	private static final ResourceLocation BOGGED_OVERLAY_IDENTITY = ResourceLocation.withDefaultNamespace("bogged_overlay");
	private static final ResourceLocation STRAY_OVERLAY = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/stray_overlay.png");
	private static final ResourceLocation STRAY_OVERLAY_IDENTITY = ResourceLocation.withDefaultNamespace("stray_overlay");
	private final SkeletonModel<S> layerModel;
	private final ResourceLocation clothesLocation;

	public SkeletonClothingLayer(
		RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, ModelLayerLocation modelLayerLocation, ResourceLocation resourceLocation
	) {
		super(renderLayerParent);
		this.clothesLocation = resourceLocation;
		this.layerModel = new SkeletonModel<>(entityModelSet.bakeLayer(modelLayerLocation));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S skeletonRenderState, float f, float g) {
		RenderType renderType = RenderType.entityCutoutNoCull(this.clothesLocation);
		if (skeletonRenderState instanceof net.minecraft.client.renderer.entity.state.BoggedRenderState boggedState
			&& !boggedState.isSheared && BOGGED_OVERLAY.equals(this.clothesLocation)
			&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.layerModel, skeletonRenderState, poseStack.last(), renderType, this.clothesLocation,
				BOGGED_OVERLAY_IDENTITY, i, LivingEntityRenderer.getOverlayCoords(skeletonRenderState, 0.0F), -1
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", this.clothesLocation, this.layerModel.getClass().getName(),
					boggedState.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", this.clothesLocation, this.layerModel.getClass().getName(),
				boggedState.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame bogged-overlay route has no copied semantic mesh");
		}
		if (skeletonRenderState.entityType == net.minecraft.world.entity.EntityType.STRAY
			&& STRAY_OVERLAY.equals(this.clothesLocation)
			&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
				this.layerModel, skeletonRenderState, poseStack.last(), renderType, this.clothesLocation,
				STRAY_OVERLAY_IDENTITY, i, LivingEntityRenderer.getOverlayCoords(skeletonRenderState, 0.0F), -1
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", this.clothesLocation, this.layerModel.getClass().getName(),
					skeletonRenderState.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", this.clothesLocation, this.layerModel.getClass().getName(),
				skeletonRenderState.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame stray-overlay route has no copied semantic mesh");
		}
		submitNodeCollector.order(1)
			.submitModelSemanticTexture(
				this.layerModel,
				skeletonRenderState,
				poseStack,
				renderType,
				i,
				LivingEntityRenderer.getOverlayCoords(skeletonRenderState, 0.0F),
				-1,
				this.clothesLocation,
				skeletonRenderState.outlineColor,
				null
			);
	}
}
