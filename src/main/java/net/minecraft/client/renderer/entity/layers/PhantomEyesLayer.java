package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.PhantomRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class PhantomEyesLayer extends EyesLayer<PhantomRenderState, PhantomModel> {
	private static final RenderType PHANTOM_EYES = RenderType.eyes(ResourceLocation.withDefaultNamespace("textures/entity/phantom_eyes.png"));
	private static final ResourceLocation PHANTOM_EYES_IDENTITY = ResourceLocation.withDefaultNamespace("phantom_eyes");

	public PhantomEyesLayer(RenderLayerParent<PhantomRenderState, PhantomModel> renderLayerParent) {
		super(renderLayerParent);
	}

	@Override
	public RenderType renderType() {
		return PHANTOM_EYES;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light,
		PhantomRenderState state, float limbAngle, float limbDistance) {
		if (net.vulkanic.world.WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
			boolean queued = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneTranslucentModelMesh(
				this.getParentModel(), state, poseStack.last(), PHANTOM_EYES,
				semanticTexture(), PHANTOM_EYES_IDENTITY, light, OverlayTexture.NO_OVERLAY, -1
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
			throw new IllegalStateException("Rust whole-frame phantom-eyes route has no copied semantic mesh");
		}
		super.submit(poseStack, submitNodeCollector, light, state, limbAngle, limbDistance);
	}

	@Override
	protected ResourceLocation semanticTexture() {
		return ResourceLocation.withDefaultNamespace("textures/entity/phantom_eyes.png");
	}
}
