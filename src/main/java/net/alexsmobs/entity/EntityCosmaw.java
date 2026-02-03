package net.alexsmobs.entity;

import net.alexsmobs.config.AMConfig;
import net.alexsmobs.entity.ai.*;
import net.alexsmobs.entity.ai.*;
import net.alexsmobs.entity.util.Maths;
import net.alexsmobs.misc.AMBlockPos;
import net.alexsmobs.misc.AMSoundRegistry;
import net.alexsmobs.misc.AMTagRegistry;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

public class EntityCosmaw extends TamableAnimal implements ITargetsDroppedItems, FlyingAnimal, IFollower {

    private static final EntityDataAccessor<Float> COSMAW_PITCH = SynchedEntityData.defineId(EntityCosmaw.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(EntityCosmaw.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(EntityCosmaw.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COMMAND = SynchedEntityData.defineId(EntityCosmaw.class, EntityDataSerializers.INT);
    public float clutchProgress;
    public float prevClutchProgress;
    public float openProgress;
    public float prevOpenProgress;
    public float prevCosmawPitch;
    public float biteProgress;
    public float prevBiteProgress;
    private float stuckRot = random.nextInt(3) * 90;
    private UUID fishThrowerID;
    private int heldItemTime;
    private BlockPos lastSafeTpPosition;

    public EntityCosmaw(EntityType<? extends TamableAnimal> type, Level lvl) {
        super(type, lvl);
        this.moveControl = new FlightMoveController(this, 1F, false, true);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.MOVEMENT_SPEED, 0.3F);
    }

    public boolean checkSpawnRules(LevelAccessor worldIn, EntitySpawnReason spawnReasonIn) {
        return AMEntityRegistry.rollSpawn(AMConfig.cosmawSpawnRolls, this.getRandom(), spawnReasonIn);
    }

    public static boolean canCosmawSpawn(EntityType<? extends Animal> animal, LevelAccessor worldIn, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return !worldIn.getBlockState(pos.below()).isAir();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COSMAW_PITCH, 0.0F);
        builder.define(ATTACK_TICK, 0);
        builder.define(COMMAND, Integer.valueOf(0));
        builder.define(SITTING, false);

    }

    @Override
    protected void onBelowWorld() {
    }

    public boolean doHurtTarget(Entity entityIn) {
        if (this.entityData.get(ATTACK_TICK) == 0 && this.biteProgress == 0) {
            this.entityData.set(ATTACK_TICK, 5);
        }
        return true;
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AIAttack());
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FlyingAIFollowOwner(this, 1.3D, 8.0F, 4.0F, false));
        this.goalSelector.addGoal(4, new AIPickupOwner());
        this.goalSelector.addGoal(5, new BreedGoal(this, 1.2D));
        this.goalSelector.addGoal(6, new AnimalAITemptDistance(this, 1.1D, itemStack -> itemStack.is(AMTagRegistry.COSMAW_FOODSTUFFS), false, 25) {
            public boolean canUse() {
                return super.canUse() && EntityCosmaw.this.getMainHandItem().isEmpty();
            }

            public boolean canContinueToUse() {
                return super.canContinueToUse() && EntityCosmaw.this.getMainHandItem().isEmpty();
            }
        });
        this.goalSelector.addGoal(7, new RandomFlyGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new CreatureAITargetItems<>(this, true));
        this.targetSelector.addGoal(2, (new HurtByTargetGoal(this) {
            public boolean canUse() {
                LivingEntity livingentity = this.mob.getLastHurtByMob();
                if (livingentity != null && EntityCosmaw.this.isOwnedBy(livingentity)) {
                    return false;
                }
                return super.canUse();
            }
        }));
        this.targetSelector.addGoal(3, new EntityAINearestTarget3D(this, EntityCosmicCod.class, 80, true, false, null));

    }

    protected SoundEvent getAmbientSound() {
        return AMSoundRegistry.COSMAW_IDLE.get();
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return AMSoundRegistry.COSMAW_HURT.get();
    }

    protected SoundEvent getDeathSound() {
        return AMSoundRegistry.COSMAW_HURT.get();
    }

    public boolean isFood(ItemStack stack) {
        return this.isTame() && stack.is(AMTagRegistry.COSMAW_BREEDABLES);
    }

    public boolean isNoGravity() {
        return true;
    }

    public boolean isLeftHanded() {
        return false;
    }

    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    public float getClampedCosmawPitch(float partialTick) {
        float f = prevCosmawPitch + (this.getCosmawPitch() - prevCosmawPitch) * partialTick;
        return Mth.clamp(f, -90, 90);
    }

    public float getCosmawPitch() {
        return this.entityData.get(COSMAW_PITCH);
    }

    public void setCosmawPitch(float pitch) {
        this.entityData.set(COSMAW_PITCH, pitch);
    }

    public int getCommand() {
        return this.entityData.get(COMMAND);
    }

    public void setCommand(int command) {
        this.entityData.set(COMMAND, command);
    }

    public boolean isSitting() {
        return this.entityData.get(SITTING);
    }

    public void setOrderedToSit(boolean sit) {
        this.entityData.set(SITTING, sit);
    }


    public void positionRider(Entity passenger, Entity.MoveFunction moveFunc) {
        if (this.hasPassenger(passenger)) {
            final float f = this.walkAnimation.position();
            final float f1 = this.walkAnimation.speed();
            final float bob = (float) (Math.sin(f * 0.7F) * (double) f1 * 0.0625F * 1.6F - (f1 * 0.0625F * 1.6F));
            passenger.setPos(this.getX(), this.getY() - bob + 0.3F - (this.getBbHeight() * 0.75D), this.getZ());
        }
    }


    public void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putBoolean("CosmawSitting", this.isSitting());
        valueOutput.putInt("Command", this.getCommand());
    }

    public void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        this.setOrderedToSit(valueInput.getBooleanOr("CosmawSitting", false));
        this.setCommand(valueInput.getIntOr("Command", 0));
    }

    public void tick() {
        super.tick();
        prevOpenProgress = openProgress;
        prevClutchProgress = clutchProgress;
        prevBiteProgress = biteProgress;
        prevCosmawPitch = this.getCosmawPitch();
        if (!this.level().isClientSide()) {
            final float f2 = (float) -((float) this.getDeltaMovement().y * Mth.RAD_TO_DEG);
            this.setCosmawPitch(this.getCosmawPitch() + 0.6F * (this.getCosmawPitch() + f2) - this.getCosmawPitch());
        }

        if (isMouthOpen()) {
            if (openProgress < 5F)
                openProgress++;
        } else {
            if (openProgress > 0F)
                openProgress--;
        }

        if (isVehicle()) {
            if (clutchProgress < 5F)
                clutchProgress++;
        } else {
            if (clutchProgress > 0F)
                clutchProgress--;
        }

        if (this.entityData.get(ATTACK_TICK) > 0) {
            if (biteProgress < 5F) {
                biteProgress = Math.min(5F, biteProgress + 2F);
            } else {
                if (this.getTarget() != null && this.distanceTo(this.getTarget()) < 3.3D) {
                    this.getTarget().hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
                this.entityData.set(ATTACK_TICK, this.entityData.get(ATTACK_TICK) - 1);
            }
        } else {
            if (biteProgress > 0F) {
                biteProgress -= 1F;
            }
        }
        if (!this.getMainHandItem().isEmpty()) {
            heldItemTime++;
            if (heldItemTime > 30 && canTargetItem(this.getMainHandItem())) {
                heldItemTime = 0;
                this.heal(4);
                this.gameEvent(GameEvent.EAT);
                this.playSound(SoundEvents.DOLPHIN_EAT, this.getSoundVolume(), this.getVoicePitch());
                if (this.getMainHandItem().is(AMTagRegistry.COSMAW_TAMEABLES) && fishThrowerID != null && !this.isTame()) {
                    if (getRandom().nextFloat() < 0.3F) {
                        this.setTame(true, true);
                        this.setCommand(1);
                        this.setOwner(level().getPlayerByUUID(this.fishThrowerID));
                        Player player = level().getPlayerByUUID(fishThrowerID);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) player, this);
                        }
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                this.getMainHandItem().shrink(1);
            }
        } else {
            heldItemTime = 0;
        }
        if (!this.level().isClientSide()) {
            if (tickCount % 100 == 0 || lastSafeTpPosition == null) {
                BlockPos pos = getCosmawGround(this.blockPosition());
                if (pos.getY() > 1) {
                    lastSafeTpPosition = pos;
                }
            }

            if (this.isVehicle()) {
                if (lastSafeTpPosition != null) {
                    final double dist = this.distanceToSqr(Vec3.atCenterOf(lastSafeTpPosition));
                    float speed = 0.8F;
                    if(this.getY() < -40){
                        speed = 3F;
                    }
                    if (verticalCollision && dist > 14) {
                        this.setYRot(this.stuckRot);
                        if (random.nextInt(50) == 0) {
                            this.stuckRot = Mth.wrapDegrees(this.stuckRot + 90);

                        }
                        final float angle = (Maths.STARTING_ANGLE * stuckRot);
                        final double extraX = -2 * Mth.sin(Mth.PI + angle);
                        final double extraZ = -2 * Mth.cos(angle);
                        this.getMoveControl().setWantedPosition(this.getX() + extraX, this.getY() + 2, this.getZ() + extraZ, speed);
                    } else if (lastSafeTpPosition.getY() > this.getY() + 2.3F) {
                        this.getMoveControl().setWantedPosition(this.getX(), this.getY() + 2, this.getZ(), speed);
                    } else {
                        this.getMoveControl().setWantedPosition(lastSafeTpPosition.getX(), lastSafeTpPosition.getY() + 2, lastSafeTpPosition.getZ(), speed);
                    }
                    if (dist < 7 && getCosmawGround(this.blockPosition()).getY() > 1) {
                        this.ejectPassengers();
                    }
                } else {
                    if (this.getY() < 0F) {
                        this.setDeltaMovement(this.getDeltaMovement().add(0, 0.75F, 0));
                    } else if (this.getY() < 80F) {
                        this.setDeltaMovement(this.getDeltaMovement().add(0, 0.1F, 0));
                    }
                }

            }
        }

    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        final InteractionResult type = super.mobInteract(player, hand);
        final InteractionResult interactionresult = stack.interactLivingEntity(player, this, hand);
        if (canTargetItem(stack) && this.getMainHandItem().isEmpty()) {
            final ItemStack rippedStack = stack.copy();
            rippedStack.setCount(1);
            stack.shrink(1);
            this.setItemInHand(InteractionHand.MAIN_HAND, rippedStack);
            if (rippedStack.is(AMTagRegistry.COSMAW_TAMEABLES)) {
                fishThrowerID = player.getUUID();
            }
            return InteractionResult.SUCCESS;
        } else if ((this.isTame() && isOwnedBy(player)) && !this.isBaby() && interactionresult != InteractionResult.SUCCESS && type != InteractionResult.SUCCESS) {
            this.setCommand(this.getCommand() + 1);
            if (this.getCommand() == 3) {
                this.setCommand(0);
            }
            player.displayClientMessage(Component.translatable("entity.alexsmobs.all.command_" + this.getCommand(), this.getName()), true);
            final boolean sit = this.getCommand() == 2;
            if (sit) {
                this.setOrderedToSit(true);
                return InteractionResult.SUCCESS;
            } else {
                this.setOrderedToSit(false);
                return InteractionResult.SUCCESS;
            }
        }
        return type;
    }


    public boolean isMouthOpen() {
        return !this.getMainHandItem().isEmpty();
    }

    public boolean causeFallDamage(float distance, float damageMultiplier) {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new DirectPathNavigator(this, level, 0.5F);
    }



    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        Entity entity = AMEntityRegistry.COSMAW.get().create(level, EntitySpawnReason.BREEDING);
        if (entity instanceof EntityCosmaw cosmaw) {
            return cosmaw;
        }
        return null;
    }

    private BlockPos getCosmawGround(BlockPos in) {
        BlockPos position = new BlockPos(in.getX(), (int) this.getY(), in.getZ());
        while (position.getY() < 256 && !level().getFluidState(position).isEmpty()) {
            position = position.above();
        }
        while (position.getY() > 1 && level().isEmptyBlock(position)) {
            position = position.below();
        }
        return position;
    }

    @Override
    public boolean canTargetItem(ItemStack stack) {
        return stack.is(AMTagRegistry.COSMAW_FOODSTUFFS);
    }

    @Override
    public void onGetItem(ItemEntity e) {
        ItemStack duplicate = e.getItem().copy();
        duplicate.setCount(1);
        if (!this.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() && level() instanceof ServerLevel serverLevel) {
            this.spawnAtLocation(serverLevel, this.getItemInHand(InteractionHand.MAIN_HAND));
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
        Entity itemThrower = e.getOwner();
        if (e.getItem().is(AMTagRegistry.COSMAW_TAMEABLES) && !this.isTame() && itemThrower != null) {
            fishThrowerID = itemThrower.getUUID();
        } else {
            fishThrowerID = null;
        }
    }

    public boolean isTargetBlocked(Vec3 target) {
        final Vec3 Vector3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        return this.level().clip(new ClipContext(Vector3d, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    @Override
    public boolean isFlying() {
        return true;
    }

    @Override
    public boolean shouldFollow() {
        return this.getCommand() == 1 && !this.isVehicle();
    }

    private boolean shouldWander() {
        if (this.isVehicle()) {
            return false;
        }
        if (this.isTame()) {
            final int command = this.getCommand();
            if (command == 2 || this.isSitting()) {
                return false;
            }
            if (command == 1 && this.getOwner() != null && this.distanceTo(this.getOwner()) < 10) {
                return true;
            }
            return command == 0;
        } else {
            return true;
        }
    }

    public void push(Entity entity) {
        if (!this.isTame() || !(entity instanceof LivingEntity) || !isOwnedBy((LivingEntity) entity)) {
            super.push(entity);
        }
    }

    public boolean canRiderInteract() {
        return true;
    }

    public boolean shouldRiderSit() {
        return false;
    }

    static class RandomFlyGoal extends Goal {
        private final EntityCosmaw parentEntity;
        private BlockPos target = null;

        public RandomFlyGoal(EntityCosmaw mosquito) {
            this.parentEntity = mosquito;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse() {
            if (this.parentEntity.getNavigation().isDone() && this.parentEntity.shouldWander() && this.parentEntity.getTarget() == null && this.parentEntity.getRandom().nextInt(4) == 0) {
                target = getBlockInViewCosmaw();
                if (target != null) {
                    this.parentEntity.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1.0D);
                    return true;
                }
            }
            return false;
        }

        public boolean canContinueToUse() {
            return target != null && this.parentEntity.shouldWander() && parentEntity.getTarget() == null;
        }

        public void stop() {
            target = null;
        }

        public void tick() {
            if (target != null) {
                this.parentEntity.getMoveControl().setWantedPosition(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1.0D);
                if (parentEntity.distanceToSqr(Vec3.atCenterOf(target)) < 4D || this.parentEntity.horizontalCollision) {
                    target = null;
                }
            }
        }

        public BlockPos getBlockInViewCosmaw() {
            final float radius = 5 + parentEntity.getRandom().nextInt(10);
            final float neg = parentEntity.getRandom().nextBoolean() ? 1 : -1;
            final float renderYawOffset = parentEntity.getYRot();
            final float angle = (Maths.STARTING_ANGLE * renderYawOffset) + 3.15F * (parentEntity.getRandom().nextFloat() * neg);
            final double extraX = radius * Mth.sin(Mth.PI + angle);
            final double extraZ = radius * Mth.cos(angle);
            final BlockPos radialPos = AMBlockPos.fromCoords(parentEntity.getX() + extraX, parentEntity.getY(), parentEntity.getZ() + extraZ);
            BlockPos ground = parentEntity.getCosmawGround(radialPos);
            if (ground.getY() <= 1) {
                ground = ground.above(70 + parentEntity.random.nextInt(4));
            } else {
                ground = ground.above(2 + parentEntity.random.nextInt(2));
            }
            if (!parentEntity.isTargetBlocked(Vec3.atCenterOf(ground.above()))) {
                return ground;
            }
            return null;
        }

    }

    private class AIPickupOwner extends Goal {
        private LivingEntity owner;

        @Override
        public boolean canUse() {
            if (EntityCosmaw.this.isTame() && EntityCosmaw.this.getOwner() != null && !EntityCosmaw.this.isSitting() && !EntityCosmaw.this.getOwner().isPassenger()) {
                if (!EntityCosmaw.this.getOwner().onGround() && EntityCosmaw.this.getOwner().fallDistance > 4F) {
                    owner = EntityCosmaw.this.getOwner();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void tick() {
            if (owner != null) {
                if(!owner.isFallFlying() || owner.getY() < -30F) {
                    final double dist = EntityCosmaw.this.distanceTo(owner);
                    if (dist < 3F || owner.getY() <= -50F) {
                        owner.fallDistance = 0.0F;
                        owner.startRiding(EntityCosmaw.this, true, false);
                    } else if (dist > 100F || owner.getY() <= -20F) {
                        EntityCosmaw.this.teleportTo(owner.getX(), owner.getY() - 1F, owner.getZ());
                    } else {
                        EntityCosmaw.this.getNavigation().moveTo(owner, 1F + Math.min(dist * 0.3F, 3));
                    }
                }
            }
        }
    }

    private class AIAttack extends Goal {

        public AIAttack() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return EntityCosmaw.this.getTarget() != null && EntityCosmaw.this.getTarget().isAlive();
        }

        public void tick() {
            if (EntityCosmaw.this.distanceTo(EntityCosmaw.this.getTarget()) < 3D * (EntityCosmaw.this.isBaby() ? 0.5F : 1)) {
                EntityCosmaw.this.doHurtTarget(EntityCosmaw.this.getTarget());
            } else {
                EntityCosmaw.this.getNavigation().moveTo(EntityCosmaw.this.getTarget(), 1);
            }
        }
    }
}
