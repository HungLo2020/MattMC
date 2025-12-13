package net.irisshaders.iris.mixin.entity_render_context;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.helpers.EntityState;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EquipmentLayerRenderer.class)
public abstract class MixinEquipmentLayerRenderer {
	private static final String V = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/ResourceLocation;II)V";

	/**
	 * The itemStack is passed as an argument (argsOnly = true works), but we'll use ModifyVariable for consistency.
	 * Capture the itemStack at the first STORE point in the loop.
	 */
	@Inject(method = V, at = @At("HEAD"))
	private void iris$captureItemStack(net.minecraft.client.resources.model.EquipmentClientInfo.LayerType layerType, net.minecraft.resources.ResourceKey<?> resourceKey, Model model, Object state, ItemStack itemStack, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int light, ResourceLocation texture, int overlayU, int overlayV, CallbackInfo ci) {
		if (WorldRenderingSettings.INSTANCE.getItemIds() == null) return;

		ResourceLocation location = itemStack.get(DataComponents.ITEM_MODEL);
		if (location == null)
			location = BuiltInRegistries.ITEM.getKey(itemStack.getItem());


		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId(location.getNamespace(), location.getPath())));
	}

	/**
	 * Capture ArmorTrim when it's stored to set the trim ID.
	 * This avoids @Local capture issues on MC 1.21.10.
	 */
	@ModifyVariable(method = V, at = @At(value = "STORE"), ordinal = 0)
	private ArmorTrim iris$captureArmorTrim(ArmorTrim armorTrim) {
		if (armorTrim != null && WorldRenderingSettings.INSTANCE.getItemIds() != null) {
			// TODO 1.21.5 check
			EntityState.interposeItemId(WorldRenderingSettings.INSTANCE.getItemIds().applyAsInt(new NamespacedId("minecraft", "trim_" + armorTrim.material().value().assets().base().suffix())));
		}
		return armorTrim;
	}

	@Inject(method = V, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", ordinal = 2, shift = At.Shift.AFTER))
	private void changeTrimTemp2(CallbackInfo ci) {
		EntityState.restoreItemId();
	}

	@Inject(method = V, at = @At(value = "TAIL"))
	private void changeId2(CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
	}
}
