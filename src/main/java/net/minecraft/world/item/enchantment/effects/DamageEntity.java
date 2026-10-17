package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record DamageEntity(LevelBasedValue minDamage, LevelBasedValue maxDamage, Holder<DamageType> damageType) implements EnchantmentEntityEffect {
	/** The native codec's `damage_entity` case (`content/data/enchant.rs`). */
	@SuppressWarnings("unchecked")
	public static final MapCodec<DamageEntity> CODEC = (MapCodec<DamageEntity>)(MapCodec<?>)EnchantmentEntityEffect.CODEC.dispatchCase("minecraft:damage_entity");

	@Override
	public void apply(ServerLevel serverLevel, int i, EnchantedItemInUse enchantedItemInUse, Entity entity, Vec3 vec3) {
		float f = Mth.randomBetween(entity.getRandom(), this.minDamage.calculate(i), this.maxDamage.calculate(i));
		entity.hurtServer(serverLevel, new DamageSource(this.damageType, enchantedItemInUse.owner()), f);
	}

	@Override
	public MapCodec<DamageEntity> codec() {
		return CODEC;
	}
}
