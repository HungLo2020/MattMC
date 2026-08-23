package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WingsLayer<S extends HumanoidRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private static final NamespacedId ELYTRA_CAPE_LOCATION = new NamespacedId("minecraft", "elytra_with_cape");
	
	private final ElytraModel elytraModel;
	private final ElytraModel elytraBabyModel;
	private final EquipmentLayerRenderer equipmentRenderer;

	public WingsLayer(RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, EquipmentLayerRenderer equipmentLayerRenderer) {
		super(renderLayerParent);
		this.elytraModel = new ElytraModel(entityModelSet.bakeLayer(ModelLayers.ELYTRA));
		this.elytraBabyModel = new ElytraModel(entityModelSet.bakeLayer(ModelLayers.ELYTRA_BABY));
		this.equipmentRenderer = equipmentLayerRenderer;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S humanoidRenderState, float f, float g) {
		ItemStack itemStack = humanoidRenderState.chestEquipment;
		Equippable equippable = (Equippable)itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty()) {
			ResourceLocation resourceLocation = getPlayerElytraTexture(humanoidRenderState);
			ElytraModel elytraModel = humanoidRenderState.isBaby ? this.elytraBabyModel : this.elytraModel;
			
			// Iris: Set elytra item context
			if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
				&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& WorldRenderingSettings.INSTANCE.getItemIds() != null) {
				if (humanoidRenderState instanceof AvatarRenderState state && state.skin.cape() != null && state.showCape) {
					CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(ELYTRA_CAPE_LOCATION));
				} else {
					ResourceLocation location = BuiltInRegistries.ITEM.getKey(Items.ELYTRA);
					CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId(location.getNamespace(), location.getPath())));
				}
			}
			
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.0F, 0.125F);
			this.equipmentRenderer
				.renderLayers(
					EquipmentClientInfo.LayerType.WINGS,
					(ResourceKey<EquipmentAsset>)equippable.assetId().get(),
					elytraModel,
					humanoidRenderState,
					itemStack,
					poseStack,
					submitNodeCollector,
					i,
					resourceLocation,
					humanoidRenderState.outlineColor,
					0
				);
			poseStack.popPose();
			
			// Iris: Clear elytra item context
			if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
				&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
			}
		}
	}

	@Nullable
	private static ResourceLocation getPlayerElytraTexture(HumanoidRenderState humanoidRenderState) {
		if (humanoidRenderState instanceof AvatarRenderState avatarRenderState) {
			PlayerSkin playerSkin = avatarRenderState.skin;
			if (playerSkin.elytra() != null) {
				return playerSkin.elytra().texturePath();
			}

			if (playerSkin.cape() != null && avatarRenderState.showCape) {
				return playerSkin.cape().texturePath();
			}
		}

		return null;
	}
}
