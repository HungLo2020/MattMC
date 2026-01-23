package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.config.AMConfig;
import com.github.alexthe666.alexsmobs.entity.ai.AdvancedPathNavigateNoTeleport;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIHurtByTargetNotBaby;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIPanicBaby;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIWanderRanged;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class EntityBison extends Animal implements IAnimatedEntity, Shearable {

    public static final Animation ANIMATION_PREPARE_CHARGE = Animation.create(40);
    public static final Animation ANIMATION_EAT = Animation.create(35);
    public static final Animation ANIMATION_ATTACK = Animation.create(15);
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(EntityBison.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SNOWY = SynchedEntityData.defineId(EntityBison.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(EntityBison.class, EntityDataSerializers.BOOLEAN);
    public float prevChargeProgress;
    public float chargeProgress;
    private int animationTick;
    private Animation currentAnimation;
    private int snowTimer = 0;
    private boolean permSnow = false;
    private int blockBreakCounter;
    private int chargeCooldown = random.nextInt(2000);
    private EntityBison chargePartner;
    private boolean hasChargedSpeed = false;
    private int feedingsSinceLastShear = 0;

    public EntityBison(EntityType<? extends Animal> animal, Level lvl) {
        super(animal, lvl);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.ATTACK_DAMAGE, 8.0D).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.MOVEMENT_SPEED, 0.25F).add(Attributes.ATTACK_KNOCKBACK, 2.0D).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    public boolean checkSpawnRules(LevelAccessor worldIn, EntitySpawnReason spawnReasonIn) {
        return AMConfig.bisonSpawnWeight > 0;
    }

    public SpawnGroupData finalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, EntitySpawnReason reason, @javax.annotation.Nullable SpawnGroupData spawnDataIn) {
        if (spawnDataIn == null) {
            spawnDataIn = new AgeableMob.AgeableMobGroupData(0.25F);
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn);
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.BISON_IDLE;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.BISON_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.BISON_HURT;
    }

    protected void playStepSound(BlockPos p_28301_, BlockState p_28302_) {
        this.playSound(SoundEvents.COW_STEP, 0.1F, 1.0F);
    }

    public boolean isSnowy() {
        return this.entityData.get(SNOWY);
    }

    public void setSnowy(boolean honeyed) {
        this.entityData.set(SNOWY, Boolean.valueOf(honeyed));
    }

    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1D, true));
        this.goalSelector.addGoal(3, new AnimalAIPanicBaby(this, 1.25D));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.0D, Ingredient.of(Items.WHEAT), false));
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(6, new AIChargeFurthest());
        this.goalSelector.addGoal(7, new AnimalAIWanderRanged(this, 70, 1.0D, 18, 7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 15.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, (new AIAttackNearPlayers()));
        this.targetSelector.addGoal(2, (new AnimalAIHurtByTargetNotBaby(this)));

    }

    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT);
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHEARED, false);
        builder.define(SNOWY, false);
        builder.define(CHARGING, false);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mob) {
        return new EntityBison(EntityType.BISON, level);
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setSnowy(compound.getBooleanOr("Snowy", false));
        this.setSheared(compound.getBooleanOr("Sheared", false));
        this.permSnow = compound.getBooleanOr("SnowPerm", false);
        this.chargeCooldown = compound.getIntOr("ChargeCooldown", 0);
        this.feedingsSinceLastShear = compound.getIntOr("Feedings", 0);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Snowy", this.isSnowy());
        compound.putBoolean("Sheared", this.isSheared());
        compound.putBoolean("SnowPerm", this.permSnow);
        compound.putInt("ChargeCooldown", this.chargeCooldown);
        compound.putInt("Feedings", this.feedingsSinceLastShear);
    }

    protected PathNavigation createNavigation(Level worldIn) {
        return new AdvancedPathNavigateNoTeleport(this, worldIn, true);
    }

    public void tick() {
        super.tick();
        this.prevChargeProgress = this.chargeProgress;
        if (this.isCharging() && chargeProgress < 5F) {
            chargeProgress++;
        }
        if (!this.isCharging() && chargeProgress > 0F) {
            chargeProgress--;
        }
        if (!this.level().isClientSide()) {
            if (snowTimer == 0) {
                snowTimer = 200 + random.nextInt(400);
                if (this.isSnowy()) {
                    if (!permSnow) {
                        if (this.getRemainingFireTicks() > 0 || this.isInWaterOrBubble() || !isSnowingAt(level(), this.blockPosition().above())) {
                            this.setSnowy(false);
                        }
                    }
                } else {
                    if (isSnowingAt(level(), this.blockPosition())) {
                        this.setSnowy(true);
                    }
                }
            }

            LivingEntity attackTarget = this.getTarget();
            if (this.getDeltaMovement().lengthSqr() < 0.05D && this.getAnimation() == NO_ANIMATION && (attackTarget == null || !attackTarget.isAlive())) {
                if ((getRandom().nextInt(600) == 0 && level().getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK))) {
                    this.setAnimation(ANIMATION_EAT);
                }
            }
            if(this.getAnimation() == ANIMATION_EAT && this.getAnimationTick() == 30 && level().getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)){
                this.feedingsSinceLastShear++;
                BlockPos down = this.blockPosition().below();
                this.level().levelEvent(2001, down, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
                this.level().setBlock(down, Blocks.DIRT.defaultBlockState(), 2);
            }

            if (isCharging()) {
                if (!hasChargedSpeed) {
                    this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.65F);
                    hasChargedSpeed = true;
                }
            } else {
                if (hasChargedSpeed) {
                    this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25F);
                    hasChargedSpeed = false;
                }
            }

            if (attackTarget != null && attackTarget.isAlive() && this.isAlive()) {
                final double dist = this.distanceTo(attackTarget);
                if (hasLineOfSight(attackTarget)) {
                    this.lookAt(attackTarget, 30, 30);
                    this.yBodyRot = this.getYRot();
                }
                if (dist < this.getBbWidth() + 3.0F) {
                    if (this.getAnimation() == ANIMATION_ATTACK && this.getAnimationTick() > 8 && dist < this.getBbWidth() + 1.0F && this.hasLineOfSight(attackTarget)) {
                        float dmg = (float) this.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
                        if (attackTarget instanceof Wolf) {
                            dmg = 2;
                        }
                        launch(attackTarget, isCharging());
                        if (isCharging()) {
                            dmg += 3;
                            this.setCharging(false);
                        }
                        attackTarget.hurt(this.damageSources().mobAttack(this), dmg);
                    }
                } else if (!this.isCharging()) {
                    final Animation animation = this.getAnimation();
                    if(animation == NO_ANIMATION){
                        this.setAnimation(ANIMATION_PREPARE_CHARGE);
                    }
                    else if (animation == ANIMATION_PREPARE_CHARGE) {
                        this.getNavigation().stop();
                        if (this.getAnimationTick() > 30) {
                            this.setCharging(true);
                        }
                    }
                }
            }
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if(feedingsSinceLastShear >= 5 && this.isSheared()){
            feedingsSinceLastShear = 0;
            this.setSheared(false);
        }
        if (!this.level().isClientSide() && this.isCharging() && (this.getTarget() == null && this.chargePartner == null || this.isInWaterOrBubble())) {
            this.setCharging(false);
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }

    public static boolean isSnowingAt(Level world, BlockPos position) {
        if (!world.isRaining()) {
            return false;
        } else if (!world.canSeeSky(position)) {
            return false;
        } else if (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, position).getY() > position.getY()) {
            return false;
        } else {
            return world.getBiome(position).value().getPrecipitationAt(position, world.getSeaLevel()) == Biome.Precipitation.SNOW;
        }
    }

    public boolean doHurtTarget(Entity entityIn) {
        if (this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_ATTACK);
        }
        return true;
    }

    public boolean isSheared() {
        return this.entityData.get(SHEARED);
    }

    public void setSheared(boolean b) {
        this.entityData.set(SHEARED, b);
    }

    private void launch(Entity launch, boolean huge) {
        final float rot = 180F + this.getYRot();
        final float hugeScale = huge ? 4F : 0.6F;
        final float strength = (float) (hugeScale *  (1.0D - ((LivingEntity) launch).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        final float rotRad = rot * Mth.DEG_TO_RAD;
        final float x = Mth.sin(rotRad);
        final float z = -Mth.cos(rotRad);
        launch.hasImpulse = true;
        final Vec3 vec3 = this.getDeltaMovement();
        final Vec3 vec31 = vec3.add((new Vec3(x, 0.0D, z)).normalize().scale(strength));
        launch.setDeltaMovement(vec31.x, huge ? 1F : 0.5F, vec31.z);
        launch.setOnGround(false);
    }


    private void knockbackTarget(LivingEntity entity, float strength, float angle) {
        float rot = getYRot() + angle;
        if(entity != null){
            entity.knockback(strength, Mth.sin(rot * Mth.DEG_TO_RAD), -Mth.cos(rot * Mth.DEG_TO_RAD));
        }
    }

    public InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        final ItemStack itemstack = player.getItemInHand(hand);
        final Item item = itemstack.getItem();
        final InteractionResult type = super.mobInteract(player, hand);
        if (!this.level().isClientSide()) {
            if (item == Items.SNOW && !this.isSnowy()) {
                this.usePlayerItem(player, hand, itemstack);
                this.permSnow = true;
                this.setSnowy(true);
                this.playSound(SoundEvents.SNOW_PLACE, this.getSoundVolume(), this.getVoicePitch());
                this.gameEvent(GameEvent.ENTITY_INTERACT);
                return InteractionResult.SUCCESS;
            }

            if (item instanceof ShovelItem && this.isSnowy()) {
                this.permSnow = false;
                if (!player.isCreative()) {
                    if (itemstack.isDamageableItem()) itemstack.setDamageValue(itemstack.getDamageValue() + 1);
                }
                this.setSnowy(false);
                this.playSound(SoundEvents.SNOW_BREAK, this.getSoundVolume(), this.getVoicePitch());
                this.gameEvent(GameEvent.ENTITY_INTERACT);
                return InteractionResult.SUCCESS;
            }
        }
        return type;
    }

    protected void customServerAiStep() {
        super.customServerAiStep();
        breakBlock();
    }

    public void breakBlock() {
        if (this.blockBreakCounter > 0) {
            --this.blockBreakCounter;
            return;
        }
        boolean flag = false;
        if (!this.level().isClientSide() && this.blockBreakCounter == 0) {
            for (int a = (int) Math.round(this.getBoundingBox().minX); a <= (int) Math.round(this.getBoundingBox().maxX); a++) {
                for (int b = (int) Math.round(this.getBoundingBox().minY) - 1; (b <= (int) Math.round(this.getBoundingBox().maxY) + 1) && (b <= 127); b++) {
                    for (int c = (int) Math.round(this.getBoundingBox().minZ); c <= (int) Math.round(this.getBoundingBox().maxZ); c++) {
                        final BlockPos pos = new BlockPos(a, b, c);
                        final BlockState state = level().getBlockState(pos);
                        final Block block = state.getBlock();
                        if (block == Blocks.SNOW && state.getValue(SnowLayerBlock.LAYERS) <= 1) {
                            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6F, 1, 0.6F));
                            flag = true;
                            level().destroyBlock(pos, true);
                        }
                    }
                }
            }
        }
        if (flag) {
            blockBreakCounter = this.isCharging() && this.getTarget() != null ? 2 : 20;
        }
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int i) {
        animationTick = i;
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
        return new Animation[]{ANIMATION_PREPARE_CHARGE, ANIMATION_ATTACK, ANIMATION_EAT};
    }

    @Override
    public void shear(ServerLevel serverLevel, SoundSource category, ItemStack itemStack) {
        serverLevel.playSound(null, this, SoundEvents.SHEEP_SHEAR, category, 1.0F, 1.0F);
        this.gameEvent(GameEvent.ENTITY_INTERACT);
        this.setSheared(true);
        this.feedingsSinceLastShear = 0;
        for (int i = 0; i < 2 + random.nextInt(2); i++) {
            this.spawnAtLocation(new ItemStack(Items.BISON_FUR));
        }
    }

    @Override
    public boolean readyForShearing() {
        return !isSheared() && !isBaby();
    }

    public boolean isCharging() {
        return this.entityData.get(CHARGING);
    }

    public void setCharging(boolean charging) {
        this.entityData.set(CHARGING, charging);
    }

    public boolean isValidCharging() {
        return !this.isBaby() && this.isAlive() && chargeCooldown == 0 && !this.isInWaterOrBubble();
    }


    public void pushBackJostling(EntityBison bison, float strength) {
        applyKnockbackFromBuffalo(strength, bison.getX() - this.getX(), bison.getZ() - this.getZ());
    }

    private void applyKnockbackFromBuffalo(float strength, double ratioX, double ratioZ) {
        if (!(strength <= 0.0F)) {
            this.hasImpulse = true;
            Vec3 vector3d = this.getDeltaMovement();
            Vec3 vector3d1 = (new Vec3(ratioX, 0.0D, ratioZ)).normalize().scale(strength);
            this.setDeltaMovement(vector3d.x / 2.0D - vector3d1.x, 0.3F, vector3d.z / 2.0D - vector3d1.z);
        }
    }

    private void resetChargeCooldown() {
        this.setCharging(false);
        this.chargePartner = null;
        this.chargeCooldown = 1000 + random.nextInt(2000);
    }

    private class AIChargeFurthest extends Goal {


        public AIChargeFurthest() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (EntityBison.this.isValidCharging()) {
                if (EntityBison.this.chargePartner != null && EntityBison.this.chargePartner.isValidCharging() && EntityBison.this.chargePartner != EntityBison.this) {
                    EntityBison.this.chargePartner.chargePartner = EntityBison.this;
                    return true;
                } else if (random.nextInt(100) == 0) {
                    EntityBison furthest = null;
                    for (final EntityBison bison : EntityBison.this.level().getEntitiesOfClass(EntityBison.class, EntityBison.this.getBoundingBox().inflate(15F))) {
                        if (bison.chargeCooldown == 0 && !bison.isBaby() && !bison.is(EntityBison.this)) {
                            if (furthest == null || EntityBison.this.distanceTo(furthest) < EntityBison.this.distanceTo(bison)) {
                                furthest = bison;
                            }
                        }
                    }
                    if (furthest != null && furthest != EntityBison.this) {
                        EntityBison.this.chargePartner = furthest;
                        furthest.chargePartner = EntityBison.this;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return EntityBison.this.isValidCharging() && EntityBison.this.chargePartner != null && EntityBison.this.chargePartner.isValidCharging() && !EntityBison.this.chargePartner.is(EntityBison.this);
        }

        public void tick() {
            EntityBison.this.lookAt(EntityBison.this.chargePartner, 30, 30);
            EntityBison.this.yBodyRot = EntityBison.this.getYRot();
            if (!EntityBison.this.isCharging()) {
                final Animation bisonAnimation = EntityBison.this.getAnimation();
                if (bisonAnimation == NO_ANIMATION || bisonAnimation == ANIMATION_PREPARE_CHARGE && EntityBison.this.getAnimationTick() > 35) {
                    EntityBison.this.setCharging(true);
                }
            } else {
                final float dist = EntityBison.this.distanceTo(EntityBison.this.chargePartner);
                EntityBison.this.getNavigation().moveTo(EntityBison.this.chargePartner, 1.0F);
                if (EntityBison.this.hasLineOfSight(EntityBison.this.chargePartner)) {
                    final float flingAnimAt = EntityBison.this.getBbWidth() + 1.0F;
                    if (dist < flingAnimAt && EntityBison.this.getAnimation() == ANIMATION_ATTACK) {
                        if (EntityBison.this.getAnimationTick() > 8) {
                            boolean flag = false;
                            if (EntityBison.this.onGround()) {
                                EntityBison.this.pushBackJostling(EntityBison.this.chargePartner, 0.2F);
                                flag = true;
                            }
                            if (EntityBison.this.chargePartner.onGround()) {
                                EntityBison.this.chargePartner.pushBackJostling(EntityBison.this, 0.9F);
                                flag = true;
                            }
                            if (flag) {
                                EntityBison.this.resetChargeCooldown();
                            }
                        }
                    } else {
                        final float startFlingAnimAt = EntityBison.this.getBbWidth() + 3.0F;
                        if (dist < startFlingAnimAt && EntityBison.this.getAnimation() != ANIMATION_ATTACK) {
                            EntityBison.this.setAnimation(ANIMATION_ATTACK);
                        }
                    }
                }
            }
        }
    }

    class AIAttackNearPlayers extends NearestAttackableTargetGoal<net.minecraft.world.entity.player.Player> {
        public AIAttackNearPlayers() {
            super(EntityBison.this, net.minecraft.world.entity.player.Player.class, 80, true, true, null);
        }

        public boolean canUse() {
            if (EntityBison.this.isBaby() || EntityBison.this.isInLove()) {
                return false;
            } else {
                return super.canUse();
            }
        }

        protected double getFollowDistance() {
            return 3.0D;
        }
    }
}
