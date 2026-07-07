package net.minecraft.world.entity.animal.nautilus;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractNautilus extends TamableAnimal {
	private static final int BREATH_EFFECT_DURATION = 60;
	private static final int BREATH_EFFECT_REFRESH = 40;
	private int breathEffectCooldown;

	protected AbstractNautilus(EntityType<? extends AbstractNautilus> entityType, Level level) {
		super(entityType, level);
		this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.011F, 0.0F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 10);
		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 15.0)
			.add(Attributes.MOVEMENT_SPEED, 1.0)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(2, new NautilusFollowOwnerGoal(this, 1.2, 10.0F, 2.0F));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.1, itemStack -> itemStack.is(ItemTags.NAUTILUS_TAMING_ITEMS), false));
		this.goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 10));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isFood(ItemStack itemStack) {
		return itemStack.is(this.isTame() || this.isBaby() ? ItemTags.NAUTILUS_FOOD : ItemTags.NAUTILUS_TAMING_ITEMS);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (!itemStack.isEmpty()) {
			InteractionResult interactionResult = itemStack.interactLivingEntity(player, this, interactionHand);
			if (interactionResult.consumesAction()) {
				return interactionResult;
			}
		}

		if (this.canBeTamedByPlayer() && !this.isTame() && itemStack.is(ItemTags.NAUTILUS_TAMING_ITEMS)) {
			if (!player.getAbilities().instabuild) {
				itemStack.shrink(1);
			}

			this.playEatingSound();
			if (!this.level().isClientSide()) {
				if (this.random.nextInt(3) == 0) {
					this.tame(player);
					this.level().broadcastEntityEvent(this, (byte)7);
				} else {
					this.level().broadcastEntityEvent(this, (byte)6);
				}
			}

			return InteractionResult.SUCCESS;
		}

		if (this.isTame() && this.isOwnedBy(player) && this.isFood(itemStack) && this.getHealth() < this.getMaxHealth()) {
			if (!player.getAbilities().instabuild) {
				itemStack.shrink(1);
			}

			this.heal(4.0F);
			this.playEatingSound();
			return InteractionResult.SUCCESS;
		}

		if (this.canPlayerRide(player) && !player.isSecondaryUseActive()) {
			this.doPlayerRide(player);
			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, interactionHand);
	}

	protected boolean canBeTamedByPlayer() {
		return true;
	}

	protected boolean canPlayerRide(Player player) {
		return this.isTame() && this.isOwnedBy(player) && this.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
	}

	protected void doPlayerRide(Player player) {
		if (!this.level().isClientSide()) {
			player.setYRot(this.getYRot());
			player.setXRot(this.getXRot());
			player.startRiding(this);
		}
	}

	@Override
	public boolean canUseSlot(EquipmentSlot equipmentSlot) {
		return equipmentSlot != EquipmentSlot.BODY && equipmentSlot != EquipmentSlot.SADDLE
			? super.canUseSlot(equipmentSlot)
			: this.isAlive() && !this.isBaby() && this.isTame();
	}

	@Override
	protected boolean canDispenserEquipIntoSlot(EquipmentSlot equipmentSlot) {
		return equipmentSlot == EquipmentSlot.BODY || equipmentSlot == EquipmentSlot.SADDLE;
	}

	@Override
	public Holder<SoundEvent> getEquipSound(EquipmentSlot equipmentSlot, ItemStack itemStack, Equippable equippable) {
		if (equipmentSlot == EquipmentSlot.SADDLE) {
			return this.isUnderWater() ? SoundEvents.NAUTILUS_SADDLE_UNDERWATER_EQUIP : SoundEvents.NAUTILUS_SADDLE_EQUIP;
		}

		return super.getEquipSound(equipmentSlot, itemStack, equippable);
	}

	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return this.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE) && this.getFirstPassenger() instanceof Player player
			? player
			: super.getControllingPassenger();
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 vec3) {
		float y = player.isJumping() ? 0.4F : (player.isShiftKeyDown() ? -0.4F : 0.0F);
		return new Vec3(player.xxa, y, player.zza).scale(1.4 * this.getAttributeValue(Attributes.MOVEMENT_SPEED));
	}

	protected Vec2 getRiddenRotation(LivingEntity livingEntity) {
		return new Vec2(livingEntity.getXRot() * 0.5F, livingEntity.getYRot());
	}

	@Override
	protected void tickRidden(Player player, Vec3 vec3) {
		super.tickRidden(player, vec3);
		Vec2 vec2 = this.getRiddenRotation(player);
		this.setRot(vec2.y, vec2.x);
		this.yRotO = this.yBodyRot = this.yHeadRot = vec2.y;
	}

	@Override
	public void travel(Vec3 vec3) {
		if (this.isInWater()) {
			this.moveRelative(0.02F, vec3);
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
		} else {
			super.travel(vec3);
		}
	}

	@Override
	public void aiStep() {
		if (!this.isInWater() && this.onGround() && this.verticalCollision) {
			this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.05F, 0.35F, (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F));
			this.setOnGround(false);
			this.hasImpulse = true;
			this.playSound(this.getFlopSound(), 1.0F, 1.0F);
		}

		if (!this.level().isClientSide() && this.getFirstPassenger() instanceof Player player) {
			if (this.breathEffectCooldown-- <= 0) {
				player.addEffect(new MobEffectInstance(MobEffects.BREATH_OF_THE_NAUTILUS, BREATH_EFFECT_DURATION, 0, false, true, true), this);
				this.breathEffectCooldown = BREATH_EFFECT_REFRESH;
			}
		}

		super.aiStep();
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("BreathEffectCooldown", this.breathEffectCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.breathEffectCooldown = valueInput.getIntOr("BreathEffectCooldown", 0);
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		boolean bl = super.hurtServer(serverLevel, damageSource, f);
		Entity entity = damageSource.getEntity();
		if (bl && entity instanceof LivingEntity livingEntity && livingEntity != this.getOwner()) {
			this.setTarget(livingEntity);
		}

		return bl;
	}

	@Override
	public float getWalkTargetValue(BlockPos blockPos, LevelReader levelReader) {
		return 0.0F;
	}

	@Override
	public boolean checkSpawnObstruction(LevelReader levelReader) {
		return levelReader.isUnobstructed(this);
	}

	@Override
	public int getMaxAirSupply() {
		return 300;
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.NAUTILUS_SWIM;
	}

	protected SoundEvent getDashSound() {
		return null;
	}

	protected SoundEvent getDashReadySound() {
		return null;
	}

	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance mobEffectInstance) {
		return mobEffectInstance.is(MobEffects.POISON) ? false : super.canBeAffected(mobEffectInstance);
	}

	public static boolean checkNautilusSpawnRules(
		EntityType<? extends AbstractNautilus> entityType, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random
	) {
		int seaLevel = level.getSeaLevel();
		return pos.getY() >= seaLevel - 25
			&& pos.getY() <= seaLevel - 5
			&& level.getFluidState(pos.below()).is(FluidTags.WATER)
			&& level.getBlockState(pos.above()).is(Blocks.WATER);
	}

	private static class NautilusFollowOwnerGoal extends Goal {
		private final AbstractNautilus nautilus;
		private final double speedModifier;
		private final float startDistance;
		private final float stopDistance;
		@Nullable
		private LivingEntity owner;
		private int timeToRecalcPath;

		NautilusFollowOwnerGoal(AbstractNautilus nautilus, double speedModifier, float startDistance, float stopDistance) {
			this.nautilus = nautilus;
			this.speedModifier = speedModifier;
			this.startDistance = startDistance;
			this.stopDistance = stopDistance;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity livingEntity = this.nautilus.getOwner();
			if (livingEntity == null || this.nautilus.unableToMoveToOwner()) {
				return false;
			}
			if (this.nautilus.distanceToSqr(livingEntity) < this.startDistance * this.startDistance) {
				return false;
			}

			this.owner = livingEntity;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.owner != null
				&& !this.nautilus.getNavigation().isDone()
				&& !this.nautilus.unableToMoveToOwner()
				&& this.nautilus.distanceToSqr(this.owner) > this.stopDistance * this.stopDistance;
		}

		@Override
		public void start() {
			this.timeToRecalcPath = 0;
		}

		@Override
		public void stop() {
			this.owner = null;
			this.nautilus.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.owner == null) {
				return;
			}

			this.nautilus.getLookControl().setLookAt(this.owner, 10.0F, this.nautilus.getMaxHeadXRot());
			if (--this.timeToRecalcPath <= 0) {
				this.timeToRecalcPath = this.adjustedTickDelay(10);
				this.nautilus.getNavigation().moveTo(this.owner, this.speedModifier);
			}
		}
	}
}
