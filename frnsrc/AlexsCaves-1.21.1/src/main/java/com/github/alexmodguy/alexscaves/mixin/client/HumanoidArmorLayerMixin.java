package com.github.alexmodguy.alexscaves.mixin.client;


import com.github.alexmodguy.alexscaves.client.render.item.ACArmorRenderProperties;
import com.github.alexmodguy.alexscaves.server.item.CustomArmorPostRender;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin extends RenderLayer {

    private static final Map<String, ResourceLocation> AC_ARMOR_LOCATION_CACHE = Maps.newHashMap();
    private ItemStack lastArmorItemStackRendered = ItemStack.EMPTY;

    @Shadow
    protected abstract void setPartVisibility(HumanoidModel humanoidModel, EquipmentSlot equipmentSlot);

    public HumanoidArmorLayerMixin(RenderLayerParent renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(
            method = {"Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V"},
            at = @At(value = "HEAD"),
            remap = true,
            cancellable = true
    )
    private void ac_renderArmorPiece(PoseStack poseStack, MultiBufferSource multiBufferSource, LivingEntity livingEntity, EquipmentSlot equipmentSlot, int light, HumanoidModel humanoidModel, CallbackInfo ci) {
        ItemStack itemstack = livingEntity.getItemBySlot(equipmentSlot);
        if (itemstack.getItem() instanceof CustomArmorPostRender) {
            ci.cancel();
            lastArmorItemStackRendered = livingEntity.getItemBySlot(equipmentSlot);
            Item item = itemstack.getItem();
            if (item instanceof ArmorItem armorItem) {
                if (armorItem.getEquipmentSlot() == equipmentSlot) {
                    boolean legs = equipmentSlot == EquipmentSlot.LEGS;
                    HumanoidModel model = this.getParentModel() instanceof HumanoidModel humanoidModel1 ? humanoidModel1 : humanoidModel;
                    Model armorModel = ClientHooks.getArmorModel(livingEntity, itemstack, equipmentSlot, model);
                    setPartVisibility((HumanoidModel) armorModel, equipmentSlot);
                    ResourceLocation texture = getACArmorResource(livingEntity, itemstack, equipmentSlot, null);
                    ACArmorRenderProperties.renderCustomArmor(poseStack, multiBufferSource, light, lastArmorItemStackRendered, armorItem, armorModel, legs, texture);
                }
            }
        }
    }


    /* Updated for 1.21 ArmorMaterial.Layer system */
    private ResourceLocation getACArmorResource(LivingEntity entity, ItemStack stack, EquipmentSlot slot, @Nullable String type) {
        ArmorItem item = (ArmorItem) stack.getItem();
        // In 1.21, armor textures are handled via the ArmorMaterial.Layer system
        net.minecraft.world.item.ArmorMaterial material = item.getMaterial().value();
        boolean innerModel = slot == EquipmentSlot.LEGS;
        
        // Get the first layer (most armor materials have only one layer, leather has two for dyeing)
        if (!material.layers().isEmpty()) {
            net.minecraft.world.item.ArmorMaterial.Layer layer = material.layers().get(0);
            // Use the new ClientHooks.getArmorTexture with ArmorMaterial.Layer
            return ClientHooks.getArmorTexture(entity, stack, layer, innerModel, slot);
        }
        
        // Fallback: construct texture path manually if no layers defined
        ResourceLocation materialName = item.getMaterial().unwrapKey().get().location();
        String domain = materialName.getNamespace();
        String texture = materialName.getPath();
        String s1 = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, texture, (innerModel ? 2 : 1), type == null ? "" : String.format(java.util.Locale.ROOT, "_%s", type));
        
        ResourceLocation resourcelocation = AC_ARMOR_LOCATION_CACHE.get(s1);
        if (resourcelocation == null) {
            resourcelocation = ResourceLocation.parse(s1);
            AC_ARMOR_LOCATION_CACHE.put(s1, resourcelocation);
        }
        return resourcelocation;
    }
}
