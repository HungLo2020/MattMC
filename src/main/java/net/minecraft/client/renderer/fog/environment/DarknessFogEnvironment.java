package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.client.renderer.fog.NativeFog;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public class DarknessFogEnvironment extends MobEffectFogEnvironment {
	@Override
	public Holder<MobEffect> getMobEffect() {
		return MobEffects.DARKNESS;
	}

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		if (entity instanceof LivingEntity livingEntity) {
			MobEffectInstance mobEffectInstance = livingEntity.getEffect(this.getMobEffect());
			if (mobEffectInstance != null) {
                float blend = mobEffectInstance.getBlendFactor(livingEntity, deltaTracker.getGameTimeDeltaPartialTick(false));
                NativeFog.ranges(fogData, 5, f, blend, 0.0F, 0);
			}
		}
	}

	@Override
	public float getModifiedDarkness(LivingEntity livingEntity, float f, float g) {
		MobEffectInstance mobEffectInstance = livingEntity.getEffect(this.getMobEffect());
		return mobEffectInstance != null ? NativeFog.effectDarkness(1, f, mobEffectInstance.getBlendFactor(livingEntity, g), false) : f;
	}
}
