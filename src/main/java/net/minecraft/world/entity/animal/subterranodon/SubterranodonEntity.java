package net.minecraft.world.entity.animal.subterranodon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Subterranodon - A tameable flying prehistoric creature from Alex's Caves.
 * This is a simplified implementation integrated into vanilla Minecraft.
 * 
 * Note: Many advanced features from the mod are simplified or not yet implemented.
 * See AC-TODO.md for a complete list of missing features.
 */
public class SubterranodonEntity extends TamableAnimal implements FlyingAnimal, PackAnimal, FlyingMount, KeybindUsingMount, RidingMeterMount, LaysEggs {
    
    // Entity data
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(SubterranodonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HOVERING = SynchedEntityData.defineId(SubterranodonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> METER_AMOUNT = SynchedEntityData.defineId(SubterranodonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.defineId(SubterranodonEntity.class, EntityDataSerializers.BOOLEAN);
    
    // Navigation
    private boolean isLandNavigator;
    
    // Pack behavior
    private SubterranodonEntity priorPackMember;
    private SubterranodonEntity afterPackMember;
    
    // Flight mechanics
    public int timeFlying;
    public Vec3 lastFlightTargetPos;
    public boolean resetFlightAIFlag = false;
    public boolean landingFlag;
    public boolean slowRidden;
    
    // Client-side interpolation for smooth movement
    private int lSteps;
    private double lx, ly, lz, lyr, lxr;
    
    // Animation state
    private float flyProgress, prevFlyProgress;
    private float flapAmount, prevFlapAmount;
    private float hoverProgress, prevHoverProgress;
    
    // Mount controls
    private int controlUpTicks = 0;
    private int controlDownTicks = 0;
    private int timeVehicle;
    
    public SubterranodonEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        switchNavigator(true);
    }
    
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.ATTACK_DAMAGE, 2.0D)
            .add(Attributes.FLYING_SPEED, 1.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.2D)
            .add(Attributes.FOLLOW_RANGE, 32.0D)
            .add(Attributes.MAX_HEALTH, 20.0D);
    }
    
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, false));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2D, 10.0F, 2.0F));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.1D, stack -> stack.is(Items.COD) || stack.is(Items.COOKED_COD), false));
        // TODO: Add SubterranodonFlightGoal for proper flying behavior
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }
    
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLYING, false);
        builder.define(HOVERING, false);
        builder.define(METER_AMOUNT, 1.0F);
        builder.define(HAS_EGG, false);
    }
    
    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        this.setFlying(valueInput.getBooleanOr("Flying", false));
        this.timeFlying = valueInput.getIntOr("TimeFlying", 0);
        this.setHasEgg(valueInput.getBooleanOr("HasEgg", false));
    }
    
    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putBoolean("Flying", this.isFlying());
        valueOutput.putInt("TimeFlying", this.timeFlying);
        valueOutput.putBoolean("HasEgg", this.hasEgg());
    }
    
    private void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveControl = new MoveControl(this);
            this.navigation = new GroundPathNavigation(this, level());
            this.isLandNavigator = true;
        } else {
            this.moveControl = new FlightMoveHelper(this);
            this.navigation = new FlyingPathNavigation(this, level());
            this.isLandNavigator = false;
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Update animation progress
        prevFlyProgress = flyProgress;
        prevHoverProgress = hoverProgress;
        prevFlapAmount = flapAmount;
        
        // Flight progress animation
        if (isFlying() && flyProgress < 5F) {
            flyProgress++;
        }
        if (!isFlying() && flyProgress > 0F) {
            flyProgress--;
        }
        
        // Hover progress animation
        if (isHovering() && hoverProgress < 5F) {
            hoverProgress++;
        }
        if (!isHovering() && hoverProgress > 0F) {
            hoverProgress--;
        }
        
        // Flap animation
        float yMov = (float) this.getDeltaMovement().y;
        if (yMov > 0 || this.isHovering()) {
            if (flapAmount < 5F) {
                flapAmount += 1F;
            }
        } else if (yMov <= 0.05F) {
            if (flapAmount > 0) {
                flapAmount -= 0.5F;
            }
        }
        
        // Flying behavior
        if (isFlying()) {
            timeFlying++;
            if (this.isLandNavigator) {
                switchNavigator(false);
            }
            // Slow fall when flying
            if (this.getDeltaMovement().y < 0 && this.isAlive()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0.6D, 1));
            }
            // Land if needed
            if (this.onGround()) {
                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()) {
                    this.setHovering(false);
                    this.setFlying(false);
                }
            }
        } else {
            timeFlying = 0;
            if (!this.isLandNavigator) {
                switchNavigator(true);
            }
        }
        
        // Mount behavior
        if (this.isVehicle() && !this.isBaby()) {
            this.setFlying(true);
        }
        
        // Update hovering (server-side only)
        if (!this.level().isClientSide()) {
            this.setHovering(isHoveringFromServer() && isFlying());
            if (this.isHovering() && isFlying() && this.isAlive() && !this.isVehicle()) {
                if (timeFlying < 30) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0, 0.075D, 0));
                }
                if (landingFlag) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0, -0.3D, 0));
                }
            }
            if (!this.isHovering() && this.isFlying() && timeFlying > 40 && this.onGround()) {
                this.setFlying(false);
            }
        }
        
        // Stamina regeneration
        if (this.getMeterAmount() < 1.0F && controlUpTicks == 0) {
            this.setMeterAmount(this.getMeterAmount() + (slowRidden ? 0.002F : 0.001F));
        }
        
        // Control ticks
        if (controlDownTicks > 0) {
            controlDownTicks--;
        } else if (controlUpTicks > 0) {
            controlUpTicks--;
        }
        
        if (isVehicle()) {
            timeVehicle++;
        } else {
            timeVehicle = 0;
        }
    }
    
    private boolean isHoveringFromServer() {
        if (this.isVehicle()) {
            return slowRidden;
        } else {
            return landingFlag || timeFlying < 30;
        }
    }
    
    // Flying Animal implementation
    @Override
    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }
    
    public void setFlying(boolean flying) {
        if (flying && this.isBaby()) {
            flying = false;
        }
        this.entityData.set(FLYING, flying);
    }
    
    public boolean isHovering() {
        return this.entityData.get(HOVERING);
    }
    
    public void setHovering(boolean hovering) {
        if (hovering && this.isBaby()) {
            hovering = false;
        }
        this.entityData.set(HOVERING, hovering);
    }
    
    // Pack Animal implementation
    @Override
    public PackAnimal getPriorPackMember() {
        return this.priorPackMember;
    }
    
    @Override
    public PackAnimal getAfterPackMember() {
        return afterPackMember;
    }
    
    @Override
    public void setPriorPackMember(PackAnimal animal) {
        this.priorPackMember = (SubterranodonEntity) animal;
    }
    
    @Override
    public void setAfterPackMember(PackAnimal animal) {
        this.afterPackMember = (SubterranodonEntity) animal;
    }
    
    @Override
    public void resetPackFlags() {
        resetFlightAIFlag = true;
    }
    
    // RidingMeterMount implementation
    @Override
    public boolean hasRidingMeter() {
        return true;
    }
    
    @Override
    public float getMeterAmount() {
        return this.entityData.get(METER_AMOUNT);
    }
    
    @Override
    public void setMeterAmount(float amount) {
        this.entityData.set(METER_AMOUNT, amount);
    }
    
    // KeybindUsingMount implementation
    @Override
    public void onKeyPacket(Entity keyPresser, int type) {
        if (keyPresser.isPassengerOfSameVehicle(this)) {
            if (type == 0) { // Up key
                if (controlUpTicks != 10) {
                    this.setMeterAmount(Math.max(this.getMeterAmount() - 0.075F, 0F));
                }
                controlUpTicks = 10;
            }
            if (type == 1) { // Down key
                controlDownTicks = 10;
            }
        }
    }
    
    // LaysEggs implementation
    @Override
    public boolean hasEgg() {
        return this.entityData.get(HAS_EGG);
    }
    
    @Override
    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(HAS_EGG, hasEgg);
    }
    
    @Override
    public BlockState createEggBlockState() {
        // TODO: Implement custom egg block
        return null;
    }
    
    // Breeding and babies
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {
        return EntityType.SUBTERRANODON.create(serverLevel, EntitySpawnReason.BREEDING);
    }
    
    // Taming
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult prev = super.mobInteract(player, hand);
        if (prev != InteractionResult.SUCCESS) {
            ItemStack itemStack = player.getItemInHand(hand);
            // Tame with cod (simplified - originally used Trilocaris)
            if (!this.isTame() && (itemStack.is(Items.COD) || itemStack.is(Items.COOKED_COD))) {
                itemStack.shrink(1);
                if (getRandom().nextInt(3) == 0) {
                    this.tame(player);
                    this.setOrderedToSit(false);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return prev;
    }
    
    public boolean canOwnerMount(Player player) {
        return !this.isBaby();
    }
    
    @Override
    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
        // Flying creatures don't take fall damage
    }
    
    // Sounds (using vanilla sounds as placeholders)
    // TODO: Register custom Subterranodon sounds
    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }
    
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.COOKED_COD);
    }
    
    // Riding
    protected Vec3 getRiddenInput(Player player, Vec3 deltaIn) {
        float f = player.zza < 0.0F ? 0.5F : 1.0F;
        return new Vec3(player.xxa * 0.25F, controlUpTicks > 0 ? 1 : controlDownTicks > 0 ? -1 : 0.0D, player.zza * 0.5F * f);
    }
    
    protected void tickRidden(Player player, Vec3 vec3) {
        super.tickRidden(player, vec3);
        slowRidden = player.zza < 0.3F || timeVehicle < 10 || this.onGround();
        if (player.zza != 0 || player.xxa != 0) {
            this.setYRot(player.getYRot());
            this.setXRot(player.getXRot() * 0.25F);
            this.setTarget(null);
        }
    }
    
    protected float getRiddenSpeed(Player rider) {
        return (float) (this.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }
    
    public boolean shouldRiderSit() {
        return false;
    }
    
    public void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (this.isPassengerOfSameVehicle(passenger) && passenger instanceof LivingEntity living && !this.touchingUnloadedChunk()) {
            float flight = (flyProgress / 5.0F) - (hoverProgress / 5.0F);
            Vec3 seatOffset = new Vec3(0F, 0.0F, 0.2F - 1.5F * flight).xRot((float) Math.toRadians(this.getXRot())).yRot((float) Math.toRadians(-this.yBodyRot));
            double targetY = this.getY() - passenger.getBbHeight() - 0.5F + 0.25F * flight;
            passenger.setYBodyRot(this.yBodyRot);
            passenger.fallDistance = 0.0F;
            moveFunction.accept(passenger, this.getX() + seatOffset.x, targetY, this.getZ() + seatOffset.z);
        } else {
            super.positionRider(passenger, moveFunction);
        }
    }
    
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().minY, this.getZ());
    }
    
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        if (entity instanceof Player) {
            return (Player) entity;
        }
        return null;
    }
    
    /**
     * Custom move controller for flying behavior.
     */
    class FlightMoveHelper extends MoveControl {
        private final SubterranodonEntity parentEntity;
        
        public FlightMoveHelper(SubterranodonEntity bird) {
            super(bird);
            this.parentEntity = bird;
        }
        
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                final Vec3 vector3d = new Vec3(this.wantedX - parentEntity.getX(), this.wantedY - parentEntity.getY(), this.wantedZ - parentEntity.getZ());
                final double d5 = vector3d.length();
                if (d5 < parentEntity.getBoundingBox().getSize()) {
                    this.operation = MoveControl.Operation.WAIT;
                    parentEntity.setDeltaMovement(parentEntity.getDeltaMovement().scale(0.5D));
                } else {
                    float hoverSlow = parentEntity.isHoveringFromServer() && !parentEntity.landingFlag ? 0.2F : 1F;
                    parentEntity.setDeltaMovement(parentEntity.getDeltaMovement().add(vector3d.scale(this.speedModifier * 0.1D / d5).multiply(hoverSlow, 1, hoverSlow)));
                    final Vec3 vector3d1 = parentEntity.getDeltaMovement();
                    float f = -((float) Mth.atan2(vector3d1.x, vector3d1.z)) * 180.0F / (float) Math.PI;
                    parentEntity.setYRot(Mth.approachDegrees(parentEntity.getYRot(), f, 20));
                    parentEntity.yBodyRot = parentEntity.getYRot();
                }
            }
        }
    }
}
