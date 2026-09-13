package net.minecraft.client.renderer.entity.layers;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SpiderEyesLayer<M extends SpiderModel> extends EyesLayer<LivingEntityRenderState, M> {
	private static final RenderType SPIDER_EYES = RenderType.eyes(ResourceLocation.withDefaultNamespace("textures/entity/spider_eyes.png"));
	private static final ResourceLocation SPIDER_EYES_IDENTITY = ResourceLocation.withDefaultNamespace("spider_eyes");

	public SpiderEyesLayer(RenderLayerParent<LivingEntityRenderState, M> renderLayerParent) {
		super(renderLayerParent);
	}

	@Override
	public RenderType renderType() {
		return SPIDER_EYES;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light,
		LivingEntityRenderState state, float limbAngle, float limbDistance) {
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneTranslucentModelMesh(
				this.getParentModel(), state, poseStack.last(), SPIDER_EYES,
				semanticTexture(), SPIDER_EYES_IDENTITY, light, OverlayTexture.NO_OVERLAY, -1
			);
			if (queued) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
					"rust-vulkan-whole-frame", semanticTexture(), this.getParentModel().getClass().getName(),
					state.entityId, true, true, false
				);
				return;
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
				"rust-vulkan-unavailable", semanticTexture(), this.getParentModel().getClass().getName(),
				state.entityId, false, false, false
			);
			throw new IllegalStateException("Rust whole-frame spider-eyes route has no copied semantic mesh");
		}
		super.submit(poseStack, submitNodeCollector, light, state, limbAngle, limbDistance);
	}

	@Override
	protected ResourceLocation semanticTexture() {
		return ResourceLocation.withDefaultNamespace("textures/entity/spider_eyes.png");
	}
}
