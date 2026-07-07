package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class ZombieNautilus extends AbstractNautilus {
	private static final EntityDataAccessor<Holder<ZombieNautilusVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(
		ZombieNautilus.class, EntityDataSerializers.ZOMBIE_NAUTILUS_VARIANT
	);

	public ZombieNautilus(EntityType<? extends ZombieNautilus> entityType, Level level) {
		super(entityType, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractNautilus.createAttributes().add(Attributes.MOVEMENT_SPEED, 1.1);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected boolean canBeTamedByPlayer() {
		return false;
	}

	@Override
	protected boolean canPlayerRide(Player player) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		return InteractionResult.PASS;
	}

	@Nullable
	@Override
	public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		return null;
	}

	@Override
	public boolean canBreed() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ZombieNautilusVariants.TEMPERATE));
	}

	public void setVariant(Holder<ZombieNautilusVariant> variant) {
		this.entityData.set(DATA_VARIANT_ID, variant);
	}

	public Holder<ZombieNautilusVariant> getVariant() {
		return this.entityData.get(DATA_VARIANT_ID);
	}

	public boolean isCoralVariant() {
		return this.getVariant().is(ZombieNautilusVariants.WARM);
	}

	@Nullable
	@Override
	public <T> T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.ZOMBIE_NAUTILUS_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, this.getVariant())
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.ZOMBIE_NAUTILUS_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.ZOMBIE_NAUTILUS_VARIANT) {
			this.setVariant(castComponentValue(DataComponents.ZOMBIE_NAUTILUS_VARIANT, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		VariantUtils.writeVariant(valueOutput, this.getVariant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		VariantUtils.readVariant(valueInput, Registries.ZOMBIE_NAUTILUS_VARIANT).ifPresent(this::setVariant);
	}

	@Override
	public SpawnGroupData finalizeSpawn(
		ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData spawnGroupData
	) {
		VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.ZOMBIE_NAUTILUS_VARIANT).ifPresent(this::setVariant);
		spawnGroupData = super.finalizeSpawn(level, difficulty, reason, spawnGroupData);
		if (level instanceof ServerLevel serverLevel && reason != EntitySpawnReason.SPAWN_ITEM_USE && level.getRandom().nextFloat() < 0.15F) {
			Drowned drowned = EntityType.DROWNED.create(serverLevel, EntitySpawnReason.JOCKEY);
			if (drowned != null) {
				drowned.setPos(this.getX(), this.getY(), this.getZ());
				drowned.setYRot(this.getYRot());
				drowned.setXRot(0.0F);
				drowned.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, null);
				drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
				drowned.setGuaranteedDrop(EquipmentSlot.MAINHAND);
				drowned.startRiding(this);
				serverLevel.addFreshEntityWithPassengers(drowned);
			}
		}

		return spawnGroupData;
	}

	public static boolean checkZombieNautilusSpawnRules(
		EntityType<? extends ZombieNautilus> entityType, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random
	) {
		return level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
			&& Monster.isDarkEnoughToSpawn(level, pos, random)
			&& AbstractNautilus.checkNautilusSpawnRules(entityType, level, reason, pos, random);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_NAUTILUS_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
		return SoundEvents.ZOMBIE_NAUTILUS_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_NAUTILUS_DEATH;
	}
}
