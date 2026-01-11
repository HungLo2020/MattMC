package com.github.alexmodguy.alexscaves.server.item;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForgeMod;

import javax.annotation.Nullable;

public class DivingArmorItem extends ArmorItem {
    private final ACArmorMaterial acMaterial;

    public DivingArmorItem(ACArmorMaterial armorMaterial, Type slot) {
        super(armorMaterial.getHolder(), slot, new Properties().durability(armorMaterial.getDurabilityForType(slot)).attributes(createDivingAttributes(armorMaterial, slot)));
        this.acMaterial = armorMaterial;
    }

    private static ItemAttributeModifiers createDivingAttributes(ACArmorMaterial armorMaterial, Type slot) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "armor_diving_" + slot.getName());
        EquipmentSlotGroup slotGroup = EquipmentSlotGroup.bySlot(slot.getSlot());
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        builder.add(Attributes.ARMOR, new AttributeModifier(id, armorMaterial.getDefenseForType(slot), AttributeModifier.Operation.ADD_VALUE), slotGroup);
        if (slot == Type.LEGGINGS) {
            builder.add(NeoForgeMod.SWIM_SPEED, new AttributeModifier(id, 0.5D, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        } else if (slot == Type.CHESTPLATE) {
            builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, armorMaterial.getToughness(), AttributeModifier.Operation.ADD_VALUE), slotGroup);
        }
        float knockbackResist = armorMaterial.getKnockbackResistance();
        if (knockbackResist > 0) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, knockbackResist, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        }
        return builder.build();
    }

    @Override
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept((IClientItemExtensions) AlexsCaves.PROXY.getArmorProperties());
    }

    @Override
    @Nullable
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        if (slot == EquipmentSlot.LEGS) {
            return ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "textures/armor/diving_suit_1.png");
        } else {
            return ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "textures/armor/diving_suit_0.png");
        }
    }
}
