package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class Nautilus extends AbstractNautilus {
	private static final int NAUTILUS_TOTAL_AIR_SUPPLY = 300;

	public Nautilus(EntityType<? extends Nautilus> entityType, Level level) {
		super(entityType, level);
	}

	@Nullable
	@Override
	public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		Nautilus nautilus = EntityType.NAUTILUS.create(serverLevel, EntitySpawnReason.BREEDING);
		if (nautilus != null && this.isTame()) {
			nautilus.setOwnerReference(this.getOwnerReference());
			nautilus.setTame(true, true);
		}

		return nautilus;
	}

	@Override
	public float getAgeScale() {
		return this.isBaby() ? 0.5F : 1.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isBaby() ? SoundEvents.BABY_NAUTILUS_AMBIENT : SoundEvents.NAUTILUS_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
		return this.isBaby() ? SoundEvents.BABY_NAUTILUS_HURT : SoundEvents.NAUTILUS_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isBaby() ? SoundEvents.BABY_NAUTILUS_DEATH : SoundEvents.NAUTILUS_DEATH;
	}

	@Override
	public int getMaxAirSupply() {
		return NAUTILUS_TOTAL_AIR_SUPPLY;
	}

	protected void handleAirSupply(ServerLevel serverLevel, int airSupply) {
		if (this.isAlive() && !this.isInWater()) {
			this.setAirSupply(airSupply - 1);
			if (this.getAirSupply() <= -20) {
				this.setAirSupply(0);
				this.hurtServer(serverLevel, this.damageSources().dryOut(), 2.0F);
			}
		} else {
			this.setAirSupply(NAUTILUS_TOTAL_AIR_SUPPLY);
		}
	}

	@Override
	public void baseTick() {
		int airSupply = this.getAirSupply();
		super.baseTick();
		if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
			this.handleAirSupply(serverLevel, airSupply);
		}
	}
}
