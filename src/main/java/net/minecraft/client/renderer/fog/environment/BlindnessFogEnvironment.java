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
public class BlindnessFogEnvironment extends MobEffectFogEnvironment {
	@Override
	public Holder<MobEffect> getMobEffect() {
		return MobEffects.BLINDNESS;
	}

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		if (entity instanceof LivingEntity livingEntity) {
			MobEffectInstance mobEffectInstance = livingEntity.getEffect(this.getMobEffect());
			if (mobEffectInstance != null) {
                boolean infinite = mobEffectInstance.isInfiniteDuration();
                float duration = infinite ? 0.0F : mobEffectInstance.getDuration();
                NativeFog.ranges(fogData, 4, f, duration, 0.0F, infinite ? 1 : 0);
			}
		}
	}

	@Override
	public float getModifiedDarkness(LivingEntity livingEntity, float f, float g) {
		MobEffectInstance mobEffectInstance = livingEntity.getEffect(this.getMobEffect());
		if (mobEffectInstance != null) {
            boolean ending = mobEffectInstance.endsWithin(19);
            f = NativeFog.effectDarkness(0, f, ending ? mobEffectInstance.getDuration() : 0.0F, ending);
		}

		return f;
	}
}
