package com.github.alexmodguy.alexscaves.server.item;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.enchantment.ACEnchantmentHelper;
import com.github.alexmodguy.alexscaves.server.enchantment.ACEnchantmentRegistry;
import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.entity.item.DesolateDaggerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class DesolateDaggerItem extends SwordItem {
    public DesolateDaggerItem() {
        super(Tiers.DIAMOND, (new Item.Properties()).rarity(ACItemRegistry.getRarityDemonic()).attributes(createDaggerAttributes()));
    }

    private static ItemAttributeModifiers createDaggerAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "desolate_dagger_damage"), 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(ResourceLocation.fromNamespaceAndPath(AlexsCaves.MODID, "desolate_dagger_speed"), -2F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public int getMaxDamage(ItemStack stack) {
        return 360;
    }

    public boolean hurtEnemy(ItemStack stack, LivingEntity hurt, LivingEntity player) {
        if (super.hurtEnemy(stack, hurt, player)) {
            int delayedLevel = ACEnchantmentHelper.getEnchantmentLevel(player.level(), ACEnchantmentRegistry.IMPENDING_STAB, stack);
            for(int i = 0; i < 1 + ACEnchantmentHelper.getEnchantmentLevel(player.level(), ACEnchantmentRegistry.DOUBLE_STAB, stack); i++){
                DesolateDaggerEntity daggerEntity = ACEntityRegistry.DESOLATE_DAGGER.get().create(player.level());
                daggerEntity.setTargetId(hurt.getId());
                daggerEntity.copyPosition(player);
                daggerEntity.setItemStack(stack);
                daggerEntity.orbitFor = (delayedLevel > 0 ? 40 : 20) + player.getRandom().nextInt(10);
                player.level().addFreshEntity(daggerEntity);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean isValidRepairItem(ItemStack itemStack, ItemStack repairWith) {
        return repairWith.is(ACItemRegistry.PURE_DARKNESS.get());
    }

}
