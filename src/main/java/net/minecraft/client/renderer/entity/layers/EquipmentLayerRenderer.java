package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.irisshaders.iris.helpers.EntityState;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EquipmentLayerRenderer {
	private static final int NO_LAYER_COLOR = 0;
	private final EquipmentAssetManager equipmentAssets;
	private final Function<EquipmentLayerRenderer.LayerTextureKey, ResourceLocation> layerTextureLookup;
	private final Function<EquipmentLayerRenderer.TrimSpriteKey, TextureAtlasSprite> trimSpriteLookup;

	public EquipmentLayerRenderer(EquipmentAssetManager equipmentAssetManager, TextureAtlas textureAtlas) {
		this.equipmentAssets = equipmentAssetManager;
		this.layerTextureLookup = Util.memoize(layerTextureKey -> layerTextureKey.layer.getTextureLocation(layerTextureKey.layerType));
		this.trimSpriteLookup = Util.memoize(trimSpriteKey -> textureAtlas.getSprite(trimSpriteKey.spriteId()));
	}

	public <S> void renderLayers(
		EquipmentClientInfo.LayerType layerType,
		ResourceKey<EquipmentAsset> resourceKey,
		Model<? super S> model,
		S object,
		ItemStack itemStack,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j
	) {
		this.renderLayers(layerType, resourceKey, model, object, itemStack, poseStack, submitNodeCollector, i, null, j, 1);
	}

	public <S> void renderLayers(
		EquipmentClientInfo.LayerType layerType,
		ResourceKey<EquipmentAsset> resourceKey,
		Model<? super S> model,
		S object,
		ItemStack itemStack,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		@Nullable ResourceLocation resourceLocation,
		int j,
		int k
	) {
		List<EquipmentClientInfo.Layer> list = this.equipmentAssets.get(resourceKey).getLayers(layerType);
		if (!list.isEmpty()) {
			int l = DyedItemColor.getOrDefault(itemStack, 0);
			boolean bl = itemStack.hasFoil();
			int m = k;

			for (EquipmentClientInfo.Layer layer : list) {
				int n = getColorForLayer(layer, l);
				if (n != 0) {
					// Iris: Set item context before rendering
					if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
						&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
						&& layer.usePlayerTexture() && WorldRenderingSettings.INSTANCE.getItemIds() != null) {
						ResourceLocation location = itemStack.get(DataComponents.ITEM_MODEL);
						if (location == null) {
							location = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
						}
						CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId(location.getNamespace(), location.getPath())));
					}
					
					ResourceLocation resourceLocation2 = layer.usePlayerTexture() && resourceLocation != null
						? resourceLocation
						: (ResourceLocation)this.layerTextureLookup.apply(new EquipmentLayerRenderer.LayerTextureKey(layerType, layer));
					if (!bl) {
						submitNodeCollector.order(m++)
							.submitModelSemanticTexture(
								model, object, poseStack, RenderType.armorCutoutNoCull(resourceLocation2),
								i, OverlayTexture.NO_OVERLAY, n, resourceLocation2, j, null);
					} else {
						submitNodeCollector.order(m++)
							.submitModelSemanticTexture(
								model, object, poseStack, RenderType.armorCutoutNoCull(resourceLocation2),
								i, OverlayTexture.NO_OVERLAY, n, resourceLocation2, j, null);
					}
					if (bl) {
						submitNodeCollector.order(m++).submitModel(model, object, poseStack, RenderType.armorEntityGlint(), i, OverlayTexture.NO_OVERLAY, n, null, j, null);
					}

					bl = false;
				}
			}

			ArmorTrim armorTrim = (ArmorTrim)itemStack.get(DataComponents.TRIM);
			if (armorTrim != null) {
				// Iris: Set trim item context
				if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
					&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					&& WorldRenderingSettings.INSTANCE.getItemIds() != null) {
					EntityState.interposeItemId(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId("minecraft", "trim_" + armorTrim.material().value().assets().base().suffix())));
				}
				
				TextureAtlasSprite textureAtlasSprite = (TextureAtlasSprite)this.trimSpriteLookup
					.apply(new EquipmentLayerRenderer.TrimSpriteKey(armorTrim, layerType, resourceKey));
				RenderType renderType = Sheets.armorTrimsSheet(((TrimPattern)armorTrim.pattern().value()).decal());
				submitNodeCollector.order(m++).submitModel(model, object, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, -1, textureAtlasSprite, j, null);
				
				// Iris: Restore item context after trim
				if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
					&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
					EntityState.restoreItemId();
				}
			}
			
			// Iris: Clear item context at end
			if (!net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()
				&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
			}
		}
	}

	private static int getColorForLayer(EquipmentClientInfo.Layer layer, int i) {
		Optional<EquipmentClientInfo.Dyeable> optional = layer.dyeable();
		if (optional.isPresent()) {
			int j = (Integer)((EquipmentClientInfo.Dyeable)optional.get()).colorWhenUndyed().map(ARGB::opaque).orElse(0);
			return i != 0 ? i : j;
		} else {
			return -1;
		}
	}

	@Environment(EnvType.CLIENT)
	record LayerTextureKey(EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer) {
	}

	@Environment(EnvType.CLIENT)
	record TrimSpriteKey(ArmorTrim trim, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId) {
		public ResourceLocation spriteId() {
			return this.trim.layerAssetId(this.layerType.trimAssetPrefix(), this.equipmentAssetId);
		}
	}
}
