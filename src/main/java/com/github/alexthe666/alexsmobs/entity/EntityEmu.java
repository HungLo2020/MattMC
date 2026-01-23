package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIHerdPanic;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIWanderRanged;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class EntityEmu extends Animal implements IAnimatedEntity, IHerdPanic {

    public static final Animation ANIMATION_DODGE_LEFT = Animation.create(10);
    public static final Animation ANIMATION_DODGE_RIGHT = Animation.create(10);
    public static final Animation ANIMATION_PECK_GROUND = Animation.create(25);
    public static final Animation ANIMATION_SCRATCH = Animation.create(20);
    public static final Animation ANIMATION_PUZZLED = Animation.create(30);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(EntityEmu.class, EntityDataSerializers.INT);
    private int animationTick;
    private Animation currentAnimation;
    private int revengeCooldown = 0;
    private boolean emuAttackedDirectly = false;
    public int timeUntilNextEgg = this.random.nextInt(6000) + 6000;

    public EntityEmu(EntityType<? extends Animal> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.35F)
            .add(Attributes.ATTACK_DAMAGE, 3F);
    }

    public static boolean canEmuSpawn(EntityType<? extends Animal> animal, LevelAccessor worldIn, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        // Emus spawn on grass blocks in plains-like biomes with good lighting
        boolean spawnBlock = worldIn.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON);
        return spawnBlock && worldIn.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EMU_IDLE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.EMU_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EMU_HURT;
    }
    
    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, Integer.valueOf(variant));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true){
            protected double getAttackReachSqr(LivingEntity attackTarget) {
                return EntityEmu.this.getBbWidth() * 2.0F * EntityEmu.this.getBbWidth() * 2.0F + attackTarget.getBbWidth() + 2.5;
            }

            public boolean canUse() {
                return super.canUse() && EntityEmu.this.revengeCooldown <= 0;
            }

            public boolean canContinueToUse() {
                return super.canContinueToUse() && EntityEmu.this.revengeCooldown <= 0;
            }
        });
        this.goalSelector.addGoal(2, new AnimalAIHerdPanic(this, 1.5D));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, itemStack -> itemStack.is(ItemTags.CHICKEN_FOOD), false));
        this.goalSelector.addGoal(5, new AnimalAIWanderRanged(this, 110, 1.0D, 10, 7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 15.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new EntityEmu.HurtByTargetGoal());
        // Emus can attack skeletons and pillagers
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Pillager.class, false));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource source, float amount) {
        super.actuallyHurt(serverLevel, source, amount);
        double range = 15;
        int fleeTime = 100 + getRandom().nextInt(5);
        this.revengeCooldown = fleeTime;
        List<? extends EntityEmu> list = this.level().getEntitiesOfClass(this.getClass(), this.getBoundingBox().inflate(range, range / 2, range));
        for (EntityEmu emu : list) {
            emu.revengeCooldown = fleeTime;
            if(emu.isBaby() && random.nextInt(2) == 0){
                emu.emuAttackedDirectly = this.getLastHurtByMob() != null;
                emu.revengeCooldown = emu.emuAttackedDirectly ? 10 + getRandom().nextInt(30) : fleeTime;
            }
        }
        emuAttackedDirectly = this.getLastHurtByMob() != null;
        this.revengeCooldown = emuAttackedDirectly ? 10 + getRandom().nextInt(30) : revengeCooldown;
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (this.getAnimation() == ANIMATION_PECK_GROUND || this.getAnimation() == ANIMATION_PUZZLED ? 0.15F : 1F) * (isInLava() ? 0.2F : 1F));
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.getLastHurtByMob() == null && this.getTarget() == null) {
                if (this.getDeltaMovement().lengthSqr() < 0.03D && this.getRandom().nextInt(190) == 0 && this.getAnimation() == NO_ANIMATION) {
                    if (getRandom().nextInt(3) == 0) {
                        this.setAnimation(ANIMATION_PUZZLED);
                    } else if (this.onGround()) {
                        this.setAnimation(ANIMATION_PECK_GROUND);
                    }
                }
            }
            if (revengeCooldown > 0) {
                revengeCooldown--;
            }
            if (revengeCooldown <= 0 && this.getLastHurtByMob() != null && !emuAttackedDirectly) {
                this.setLastHurtByMob(null);
                revengeCooldown = 0;
            }
            LivingEntity target = getTarget();
            if (this.isAlive() && target != null && this.getAnimation() == ANIMATION_SCRATCH && this.distanceTo(target) < 4F && (this.getAnimationTick() == 8 || this.getAnimationTick() == 15)) {
                float f1 = this.getYRot() * Mth.DEG_TO_RAD;
                this.setDeltaMovement(this.getDeltaMovement().add(-Mth.sin(f1) * 0.02F, 0.0D, Mth.cos(f1) * 0.02F));
                target.knockback(0.4F, target.getX() - this.getX(), target.getZ() - this.getZ());
                target.hurt(this.damageSources().mobAttack(this), (float) this.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue());
            }
        }
        if (!this.level().isClientSide() && this.isAlive() && !this.isBaby() && --this.timeUntilNextEgg <= 0) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            if (this.level() instanceof ServerLevel serverLevel) {
                this.spawnAtLocation(serverLevel, Items.EMU_EGG);
            }
            this.timeUntilNextEgg = this.random.nextInt(6000) + 6000;
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_DODGE_LEFT, ANIMATION_DODGE_RIGHT, ANIMATION_PECK_GROUND, ANIMATION_SCRATCH, ANIMATION_PUZZLED};
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverWorld, AgeableMob ageableEntity) {
        EntityEmu emu = EntityType.EMU.create(serverWorld, EntitySpawnReason.BREEDING);
        if (emu != null) {
            emu.setVariant(this.getVariant());
        }
        return emu;
    }

    public boolean doHurtTarget(Entity entityIn) {
        if (this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_SCRATCH);
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        this.setVariant(valueInput.getIntOr("Variant", 0));
        valueInput.getInt("EggLayTime").ifPresent(integer -> this.timeUntilNextEgg = integer);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putInt("Variant", this.getVariant());
        valueOutput.putInt("EggLayTime", this.timeUntilNextEgg);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, EntitySpawnReason reason, @Nullable SpawnGroupData spawnDataIn) {
        if(this.random.nextInt(200) == 0){
            this.setVariant(2);
        }else if(random.nextInt(3) == 0){
            this.setVariant(1);
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn);
    }

    @Override
    public void onPanic() {

    }

    @Override
    public boolean canPanic() {
        return true;
    }

    class HurtByTargetGoal extends net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal {
        public HurtByTargetGoal() {
            super(EntityEmu.this);
        }

        @Override
        public void start() {
            if (EntityEmu.this.isBaby() || !emuAttackedDirectly) {
                this.alertOthers();
                this.stop();
            } else {
                super.start();
            }
        }

        @Override
        protected void alertOther(Mob mobIn, LivingEntity targetIn) {
            if (mobIn instanceof EntityEmu && !mobIn.isBaby() && !emuAttackedDirectly && ((EntityEmu) mobIn).revengeCooldown <= 0) {
                super.alertOther(mobIn, targetIn);
            }

        }
    }
}
