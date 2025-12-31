package net.minecraft.world.entity;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public interface NeutralMob {
	String TAG_ANGER_TIME = "AngerTime";
	String TAG_ANGRY_AT = "AngryAt";

	int getRemainingPersistentAngerTime();

	void setRemainingPersistentAngerTime(int i);

	@Nullable
	UUID getPersistentAngerTarget();

	void setPersistentAngerTarget(@Nullable UUID uUID);

	void startPersistentAngerTimer();

	default void addPersistentAngerSaveData(ValueOutput valueOutput) {
		valueOutput.putInt("AngerTime", this.getRemainingPersistentAngerTime());
		valueOutput.storeNullable("AngryAt", UUIDUtil.CODEC, this.getPersistentAngerTarget());
	}

	default void readPersistentAngerSaveData(Level level, ValueInput valueInput) {
		this.setRemainingPersistentAngerTime(valueInput.getIntOr("AngerTime", 0));
		if (level instanceof ServerLevel serverLevel) {
			UUID uUID = (UUID)valueInput.read("AngryAt", UUIDUtil.CODEC).orElse(null);
			this.setPersistentAngerTarget(uUID);
			if ((uUID != null ? serverLevel.getEntity(uUID) : null) instanceof LivingEntity livingEntity) {
				this.setTarget(livingEntity);
			}
		}
	}

	default void updatePersistentAnger(ServerLevel serverLevel, boolean bl) {
		LivingEntity livingEntity = this.getTarget();
		UUID uUID = this.getPersistentAngerTarget();
		if ((livingEntity == null || livingEntity.isDeadOrDying()) && uUID != null && serverLevel.getEntity(uUID) instanceof Mob) {
			this.stopBeingAngry();
		} else {
			if (livingEntity != null && !Objects.equals(uUID, livingEntity.getUUID())) {
				this.setPersistentAngerTarget(livingEntity.getUUID());
				this.startPersistentAngerTimer();
			}

			if (this.getRemainingPersistentAngerTime() > 0 && (livingEntity == null || livingEntity.getType() != EntityType.PLAYER || !bl)) {
				this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
				if (this.getRemainingPersistentAngerTime() == 0) {
					this.stopBeingAngry();
				}
			}
		}
	}

	default boolean isAngryAt(LivingEntity livingEntity, ServerLevel serverLevel) {
		if (!this.canAttack(livingEntity)) {
			return false;
		} else {
			return livingEntity.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(serverLevel)
				? true
				: livingEntity.getUUID().equals(this.getPersistentAngerTarget());
		}
	}

	default boolean isAngryAtAllPlayers(ServerLevel serverLevel) {
		return serverLevel.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
	}

	default boolean isAngry() {
		return this.getRemainingPersistentAngerTime() > 0;
	}

	default void playerDied(ServerLevel serverLevel, Player player) {
		if (serverLevel.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
			if (player.getUUID().equals(this.getPersistentAngerTarget())) {
				this.stopBeingAngry();
			}
		}
	}

	default void forgetCurrentTargetAndRefreshUniversalAnger() {
		this.stopBeingAngry();
		this.startPersistentAngerTimer();
	}

	default void stopBeingAngry() {
		this.setLastHurtByMob(null);
		this.setPersistentAngerTarget(null);
		this.setTarget(null);
		this.setRemainingPersistentAngerTime(0);
	}

	@Nullable
	LivingEntity getLastHurtByMob();

	void setLastHurtByMob(@Nullable LivingEntity livingEntity);

	void setTarget(@Nullable LivingEntity livingEntity);

	boolean canAttack(LivingEntity livingEntity);

	@Nullable
	LivingEntity getTarget();
}
