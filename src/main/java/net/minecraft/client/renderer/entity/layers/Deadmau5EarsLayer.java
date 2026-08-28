package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerEarsModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

@Environment(EnvType.CLIENT)
public class Deadmau5EarsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final HumanoidModel<AvatarRenderState> model;

	public Deadmau5EarsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new PlayerEarsModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_EARS));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		if (avatarRenderState.showExtraEars && !avatarRenderState.isInvisible) {
			int j = LivingEntityRenderer.getOverlayCoords(avatarRenderState, 0.0F);
			var texture = avatarRenderState.skin.body().texturePath();
			var renderType = RenderType.entitySolid(texture);
			if ((net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
					|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
				&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
				boolean eligible = j == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
					&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
						this.model, renderType, texture, j, avatarRenderState.outlineColor, null);
				if (eligible && net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
					this.model, avatarRenderState, poseStack.last(), renderType, texture,
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.entityIdentity(avatarRenderState), i, j, -1,
					avatarRenderState.outlineColor)) {
					net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-whole-frame", texture, this.model.getClass().getName(), avatarRenderState.entityId, true, true, false);
				} else {
					 net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
						"rust-vulkan-unavailable", texture, this.model.getClass().getName(), avatarRenderState.entityId, false, false, false);
					throw new IllegalStateException(
						"Rust whole-frame avatar-ears route has no semantic mesh for " + texture
					);
				}
			} else {
				submitNodeCollector.submitModelSemanticTexture(
					this.model, avatarRenderState, poseStack, renderType, i, j, -1, texture,
					avatarRenderState.outlineColor, null
				);
			}
		}
	}
}
