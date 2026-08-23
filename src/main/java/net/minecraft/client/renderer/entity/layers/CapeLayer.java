package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerCapeModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public class CapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final NamespacedId CAPE_LOCATION = new NamespacedId("minecraft", "player_cape");
	
	private final HumanoidModel<AvatarRenderState> model;
	private final EquipmentAssetManager equipmentAssets;

	public CapeLayer(
		RenderLayerParent<AvatarRenderState, PlayerModel> renderLayerParent, EntityModelSet entityModelSet, EquipmentAssetManager equipmentAssetManager
	) {
		super(renderLayerParent);
		this.model = new PlayerCapeModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_CAPE));
		this.equipmentAssets = equipmentAssetManager;
	}

	private boolean hasLayer(ItemStack itemStack, EquipmentClientInfo.LayerType layerType) {
		Equippable equippable = (Equippable)itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty()) {
			EquipmentClientInfo equipmentClientInfo = this.equipmentAssets.get((ResourceKey<EquipmentAsset>)equippable.assetId().get());
			return !equipmentClientInfo.getLayers(layerType).isEmpty();
		} else {
			return false;
		}
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		if (!avatarRenderState.isInvisible && avatarRenderState.showCape) {
			PlayerSkin playerSkin = avatarRenderState.skin;
			if (playerSkin.cape() != null) {
				if (!this.hasLayer(avatarRenderState.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
					// Iris: Set cape item context
					if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
						&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
						&& WorldRenderingSettings.INSTANCE.getItemIds() != null) {
						CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(CAPE_LOCATION));
					}
					
					poseStack.pushPose();
					if (this.hasLayer(avatarRenderState.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
						poseStack.translate(0.0F, -0.053125F, 0.06875F);
					}
					var texture = playerSkin.cape().texturePath();
					var renderType = RenderType.entitySolid(texture);
					if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
						&& net.vulkanic.world.WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
						boolean eligible = !avatarRenderState.isInvisible
							&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
								this.model, renderType, texture, OverlayTexture.NO_OVERLAY, avatarRenderState.outlineColor, null);
						if (eligible && net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueStandaloneModelMesh(
							this.model, avatarRenderState, poseStack.last(), renderType, texture,
							net.vulkanic.world.RustGalWorldPrimitiveRenderer.entityIdentity(avatarRenderState),
							i, OverlayTexture.NO_OVERLAY, -1, avatarRenderState.outlineColor)) {
							net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
								"rust-vulkan-whole-frame", texture, this.model.getClass().getName(), avatarRenderState.entityId, true, true, false);
						} else {
							 net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordModelMeshRouteDecision(
								"rust-vulkan-unavailable", texture, this.model.getClass().getName(), avatarRenderState.entityId, false, false, false);
							throw new IllegalStateException(
								"Rust whole-frame cape route has no semantic mesh for " + texture
							);
						}
					} else {
						submitNodeCollector.submitModelSemanticTexture(
							this.model, avatarRenderState, poseStack, renderType, i,
							OverlayTexture.NO_OVERLAY, -1, texture,
							avatarRenderState.outlineColor, null);
					}
					poseStack.popPose();
					
					// Iris: Clear cape item context
					if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
						&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
						CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
					}
				}
			}
		}
	}
}
