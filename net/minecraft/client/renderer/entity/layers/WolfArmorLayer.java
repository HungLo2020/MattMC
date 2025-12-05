package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.Crackiness.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public class WolfArmorLayer extends RenderLayer<WolfRenderState, WolfModel> {
	private final WolfModel adultModel;
	private final WolfModel babyModel;
	private final EquipmentLayerRenderer equipmentRenderer;
	private static final Map<Level, ResourceLocation> ARMOR_CRACK_LOCATIONS = Map.of(
		Level.LOW,
		ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_low.png"),
		Level.MEDIUM,
		ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_medium.png"),
		Level.HIGH,
		ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_high.png")
	);

	public WolfArmorLayer(
		RenderLayerParent<WolfRenderState, WolfModel> renderLayerParent, EntityModelSet entityModelSet, EquipmentLayerRenderer equipmentLayerRenderer
	) {
		super(renderLayerParent);
		this.adultModel = new WolfModel(entityModelSet.bakeLayer(ModelLayers.WOLF_ARMOR));
		this.babyModel = new WolfModel(entityModelSet.bakeLayer(ModelLayers.WOLF_BABY_ARMOR));
		this.equipmentRenderer = equipmentLayerRenderer;
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, WolfRenderState wolfRenderState, float f, float g) {
		ItemStack itemStack = wolfRenderState.bodyArmorItem;
		Equippable equippable = (Equippable)itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty()) {
			WolfModel wolfModel = wolfRenderState.isBaby ? this.babyModel : this.adultModel;
			this.equipmentRenderer
				.renderLayers(
					EquipmentClientInfo.LayerType.WOLF_BODY,
					(ResourceKey<EquipmentAsset>)equippable.assetId().get(),
					wolfModel,
					wolfRenderState,
					itemStack,
					poseStack,
					submitNodeCollector,
					i,
					wolfRenderState.outlineColor
				);
			this.maybeRenderCracks(poseStack, submitNodeCollector, i, itemStack, wolfModel, wolfRenderState);
		}
	}

	private void maybeRenderCracks(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ItemStack itemStack, Model<WolfRenderState> model, WolfRenderState wolfRenderState
	) {
		Level level = Crackiness.WOLF_ARMOR.byDamage(itemStack);
		if (level != Level.NONE) {
			ResourceLocation resourceLocation = (ResourceLocation)ARMOR_CRACK_LOCATIONS.get(level);
			submitNodeCollector.submitModel(
				model, wolfRenderState, poseStack, RenderType.armorTranslucent(resourceLocation), i, OverlayTexture.NO_OVERLAY, wolfRenderState.outlineColor, null
			);
		}
	}
}
