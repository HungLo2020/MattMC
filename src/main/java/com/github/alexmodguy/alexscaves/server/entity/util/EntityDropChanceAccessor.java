package com.github.alexmodguy.alexscaves.server.entity.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;

public interface EntityDropChanceAccessor {

    float ac_getEquipmentDropChance(EquipmentSlot equipmentSlot);

    void ac_setDropChance(EquipmentSlot equipmentSlot, float chance);

    void ac_dropCustomDeathLoot(ServerLevel serverLevel, DamageSource damageSource, boolean playerKill);
}
