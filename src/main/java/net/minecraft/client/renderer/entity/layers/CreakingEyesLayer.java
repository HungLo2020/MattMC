package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.CreakingModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public final class CreakingEyesLayer extends RenderLayer<CreakingRenderState, CreakingModel> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/creaking/creaking_eyes.png");
	private static final ResourceLocation IDENTITY = ResourceLocation.withDefaultNamespace("creaking_eyes");
	private final CreakingModel model;

	public CreakingEyesLayer(RenderLayerParent<CreakingRenderState, CreakingModel> parent, EntityModelSet modelSet) {
		super(parent);
		this.model = new CreakingModel(modelSet.bakeLayer(ModelLayers.CREAKING_EYES));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
		CreakingRenderState state, float limbAngle, float limbDistance) {
		if (!state.eyesGlowing) return;
		RenderType renderType = RenderType.eyes(TEXTURE);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneTranslucentModelMesh(
				this.model, state, poseStack.last(), renderType, TEXTURE, IDENTITY,
				light, overlay, ARGB.white(1.0F)
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", TEXTURE, this.model.getClass().getName(),
					state.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", TEXTURE, this.model.getClass().getName(),
				state.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame creaking-eyes route has no copied semantic mesh");
		}
		collector.order(1).submitModelSemanticTexture(
			this.model, state, poseStack, renderType, light, overlay, ARGB.white(1.0F),
			TEXTURE, state.outlineColor, null
		);
	}
}
