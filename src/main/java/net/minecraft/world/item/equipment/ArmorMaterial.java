package net.minecraft.world.item.equipment;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public record ArmorMaterial(
	int durability,
	Map<ArmorType, Integer> defense,
	int enchantmentValue,
	Holder<SoundEvent> equipSound,
	float toughness,
	float knockbackResistance,
	TagKey<Item> repairIngredient,
	ResourceKey<EquipmentAsset> assetId
) {
	public ItemAttributeModifiers createAttributes(ArmorType armorType) {
		int i = (Integer)this.defense.getOrDefault(armorType, 0);
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		EquipmentSlotGroup equipmentSlotGroup = EquipmentSlotGroup.bySlot(armorType.getSlot());
		ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("armor." + armorType.getName());
		builder.add(Attributes.ARMOR, new AttributeModifier(resourceLocation, i, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup);
		builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(resourceLocation, this.toughness, AttributeModifier.Operation.ADD_VALUE), equipmentSlotGroup);
		if (this.knockbackResistance > 0.0F) {
			builder.add(
				Attributes.KNOCKBACK_RESISTANCE,
				new AttributeModifier(resourceLocation, this.knockbackResistance, AttributeModifier.Operation.ADD_VALUE),
				equipmentSlotGroup
			);
		}

		return builder.build();
	}
}
