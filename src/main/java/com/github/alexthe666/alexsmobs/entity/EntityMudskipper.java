package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.config.AMConfig;
import com.github.alexthe666.alexsmobs.entity.ai.*;
import com.github.alexthe666.alexsmobs.item.AMItemRegistry;
import com.github.alexthe666.alexsmobs.misc.AMBlockPos;
import com.github.alexthe666.alexsmobs.misc.AMSoundRegistry;
import com.github.alexthe666.alexsmobs.misc.AMTagRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Optional;

public class EntityMudskipper extends TamableAnimal implements IFollower, ISemiAquatic, Bucketable {

    public float prevSitProgress;
    public float sitProgress;
    public float prevSwimProgress;
    public float swimProgress;
    public float prevDisplayProgress;
    public float displayProgress;
    public float prevMudProgress;
    public float mudProgress;
    public float nextDisplayAngleFromServer;
    public float prevDisplayAngle;
    public boolean displayDirection;
    public int displayTimer = 0;
    public boolean instantlyTriggerDisplayAI = false;
    public int displayCooldown = 100 + random.nextInt(100);
    private static final EntityDataAccessor<Boolean> DISPLAYING = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DISPLAY_ANGLE = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.FLOAT);
    private EntityReference<Entity> displayerRef;
    private static final EntityDataAccessor<Integer> MOUTH_TICKS = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COMMAND = SynchedEntityData.defineId(EntityMudskipper.class, EntityDataSerializers.INT);
    private boolean isLandNavigator;
    private int swimTimer = -1000;

    public EntityMudskipper(EntityType type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
        switchNavigator(true);
    }

    public void travel(Vec3 travelVector) {
        if (this.isOrderedToSit()) {
            if (this.getNavigation().getPath() != null) {
                this.getNavigation().stop();
            }
            travelVector = Vec3.ZERO;
            super.travel(travelVector);
            return;
        }
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        } else {
            super.travel(travelVector);
        }
    }

    public static <T extends Mob> boolean canMudskipperSpawn(EntityType type, LevelAccessor worldIn, EntitySpawnReason reason, BlockPos p_223317_3_, RandomSource random) {
        BlockState blockstate = worldIn.getBlockState(p_223317_3_.below());
        return blockstate.is(Blocks.MUD) || blockstate.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    public boolean checkSpawnRules(LevelAccessor worldIn, EntitySpawnReason spawnReasonIn) {
        return AMEntityRegistry.rollSpawn(AMConfig.mudskipperSpawnRolls, this.getRandom(), spawnReasonIn);
    }

    public boolean checkSpawnObstruction(LevelReader worldIn) {
        BlockPos pos = AMBlockPos.fromCoords(this.getX(), this.getEyeY(), this.getZ());
        return !worldIn.getBlockState(pos).isSuffocating(worldIn, pos);
    }

    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(1, new TameableAIFollowOwnerWater(this, 1.3D, 4.0F, 2.0F, false));
        this.goalSelector.addGoal(2, new MudskipperAIAttack(this));
        this.goalSelector.addGoal(3, new com.github.alexthe666.alexsmobs.entity.ai.AnimalAIFindWater(this));
        this.goalSelector.addGoal(3, new com.github.alexthe666.alexsmobs.entity.ai.AnimalAILeaveWater(this));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, Ingredient.of(this.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).getOrThrow(AMTagRegistry.MUDSKIPPER_TAMEABLES)), false));
        this.goalSelector.addGoal(5, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new PanicGoal(this, 1D));
        this.goalSelector.addGoal(7, new MudskipperAIDisplay(this));
        this.goalSelector.addGoal(8, new SemiAquaticAIRandomSwimming(this, 1.0D, 80));
        this.goalSelector.addGoal(9, new RandomStrollGoal(this, 1.0D, 120));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return EntityMudskipper.this.isTame() && super.canUse();
            }
        });
    }

    private void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveControl = new MoveControl(this);
            this.navigation = new GroundPathNavigatorWide(this, level());
            this.isLandNavigator = true;
        } else {
            this.moveControl = new AnimalSwimMoveControllerSink(this, 1.3F, 1);
            this.navigation = new SemiAquaticPathNavigator(this, level());
            this.isLandNavigator = false;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISPLAYING, false);
        builder.define(FROM_BUCKET, false);
        builder.define(DISPLAY_ANGLE, 0F);
        builder.define(MOUTH_TICKS, 0);
        builder.define(COMMAND, 0);
        builder.define(SITTING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 12.0D)
            .add(Attributes.ATTACK_DAMAGE, 2.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.2F)
            .add(Attributes.TEMPT_RANGE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putBoolean("FromBucket", this.fromBucket());
        valueOutput.putInt("DisplayCooldown", this.displayCooldown);
        valueOutput.putInt("MudskipperCommand", this.getCommand());
        valueOutput.putBoolean("MudskipperSitting", this.isOrderedToSit());
        EntityReference.store(this.displayerRef, valueOutput, "DisplayingPartner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        this.setFromBucket(valueInput.getBooleanOr("FromBucket", false));
        this.displayCooldown = valueInput.getIntOr("DisplayCooldown", 100 + random.nextInt(100));
        this.setCommand(valueInput.getIntOr("MudskipperCommand", 0));
        this.setOrderedToSit(valueInput.getBooleanOr("MudskipperSitting", false));
        this.displayerRef = EntityReference.read(valueInput, "DisplayingPartner");
    }

    public void tick(){
        super.tick();
        prevSwimProgress = swimProgress;
        prevSitProgress = sitProgress;
        prevDisplayProgress = displayProgress;
        prevMudProgress = mudProgress;
        if(displayProgress < 5F && this.isDisplaying()){
            displayProgress++;
        }
        if(displayProgress > 0F && !this.isDisplaying()){
            displayProgress--;
        }
        if(sitProgress < 5F && this.isOrderedToSit()){
            sitProgress++;
        }
        if(sitProgress > 0F && !this.isOrderedToSit()){
            sitProgress--;
        }
        //so the model does not sink in mud
        boolean mud = onMud();
        if (mud) {
            if (mudProgress < 1F)
                mudProgress += 0.5f;
        } else {
            if (mudProgress > 0)
                mudProgress -= 0.5f;
        }

        boolean swim = !this.onGround() && this.isInWater();
        if(swimProgress < 5F && swim){
            swimProgress++;
        }
        if(swimProgress > 0 && !swim){
            swimProgress--;
        }
        if (!this.level().isClientSide()) {
            if (this.isInWater()) {
                swimTimer++;
            } else {
                swimTimer--;
            }
        }
        if (displayCooldown > 0) {
            displayCooldown--;
        }
        if(!this.level().isClientSide()){
            if(this.getDisplayAngle() < nextDisplayAngleFromServer){
                this.setDisplayAngle(this.getDisplayAngle() + 1);

            }
            if(this.getDisplayAngle() > nextDisplayAngleFromServer) {
                this.setDisplayAngle(this.getDisplayAngle() - 1);
            }
        }
        if(this.isMouthOpen()){
            this.openMouth(this.getMouthTicks() - 1);
        }
        if (this.isInWater() && this.isLandNavigator) {
            switchNavigator(false);
        }
        if (!this.isInWater() && !this.isLandNavigator) {
            switchNavigator(true);
        }
    }

    @Override
    public void actuallyHurt(ServerLevel serverLevel, DamageSource source, float amount) {
        super.actuallyHurt(serverLevel, source, amount);
        if (source.getDirectEntity() instanceof LivingEntity) {
            this.openMouth(10);
        }
    }

    public boolean isDisplaying() {
        return this.entityData.get(DISPLAYING);
    }

    public void setDisplaying(boolean display) {
        this.entityData.set(DISPLAYING, display);
    }

    public float getDisplayAngle() {
        return this.entityData.get(DISPLAY_ANGLE);
    }

    public void setDisplayAngle(float scale) {
        this.entityData.set(DISPLAY_ANGLE, scale);
    }

    public int getMouthTicks() {
        return this.entityData.get(MOUTH_TICKS);
    }

    public void openMouth(int time) {
        this.entityData.set(MOUTH_TICKS, time);
    }

    @javax.annotation.Nullable
    public EntityReference<Entity> getDisplayingPartnerRef() {
        return this.displayerRef;
    }

    public void setDisplayingPartnerRef(@javax.annotation.Nullable EntityReference<Entity> ref) {
        this.displayerRef = ref;
    }

    @javax.annotation.Nullable
    public Entity getDisplayingPartner() {
        if (this.displayerRef != null) {
            return this.displayerRef.getEntity(this.level(), Entity.class);
        }
        return null;
    }

    public void setDisplayingPartner(@javax.annotation.Nullable Entity jostlingPartner) {
        if (jostlingPartner == null) {
            this.displayerRef = null;
        } else {
            this.displayerRef = EntityReference.of(jostlingPartner);
        }
    }

    public boolean canDisplayWith(EntityMudskipper mudskipper) {
        return !mudskipper.isBaby() && !mudskipper.isOrderedToSit() && !mudskipper.shouldFollow() && mudskipper.onGround() && mudskipper.getDisplayingPartnerRef() == null && mudskipper.displayCooldown == 0;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        EntityMudskipper mudskipper = (EntityMudskipper) AMEntityRegistry.MUDSKIPPER.get().create((Level) serverLevel, EntitySpawnReason.BREEDING);
        return mudskipper;
    }

    public boolean isMouthOpen() {
        return this.getMouthTicks() > 0;
    }

    public boolean onMud() {
        BlockState below = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
        return below.is(Blocks.MUD);
    }

    public void calculateEntityAnimation(boolean flying) {
        float f1 = (float) Mth.length(this.getX() - this.xo, 0, this.getZ() - this.zo);
        float f2 = Math.min(f1 * 8.0F, 1.0F);
        this.walkAnimation.update(f2, 0.4F, 1.0F);
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(AMSoundRegistry.MUDSKIPPER_WALK.get(), 1F, 1.0F);
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return AMSoundRegistry.MUDSKIPPER_HURT.get();
    }

    protected SoundEvent getDeathSound() {
        return AMSoundRegistry.MUDSKIPPER_HURT.get();
    }

    public int getCommand() {
        return this.entityData.get(COMMAND);
    }

    public void setCommand(int command) {
        this.entityData.set(COMMAND, Integer.valueOf(command));
    }

    public boolean isOrderedToSit() {
        return this.entityData.get(SITTING);
    }

    public void setOrderedToSit(boolean sit) {
        this.entityData.set(SITTING, Boolean.valueOf(sit));
    }

    @Override
    public boolean shouldEnterWater() {
        return (this.getLastHurtByMob() != null || swimTimer <= -1000) && !this.isDisplaying();
    }

    @Override
    public boolean shouldLeaveWater() {
        return swimTimer > 200 || this.isDisplaying();
    }

    @Override
    public boolean shouldStopMoving() {
        return this.isOrderedToSit();
    }

    @Override
    public int getWaterSearchRange() {
        return 10;
    }


    @Override
    public boolean fromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean bucket) {
        this.entityData.set(FROM_BUCKET, bucket);
    }

    @Override
    @Nonnull
    public ItemStack getBucketItemStack() {
        ItemStack stack = new ItemStack(AMItemRegistry.MUDSKIPPER_BUCKET.get());
        if (this.hasCustomName()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return stack;
    }

    @Override
    public void saveToBucketTag(@Nonnull ItemStack bucket) {
        Bucketable.saveDefaultDataToBucketTag(this, bucket);
        if (this.hasCustomName()) {
            bucket.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        // Store simple entity data in a custom tag
        bucket.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putBoolean("FromBucket", this.fromBucket());
            tag.putInt("DisplayCooldown", this.displayCooldown);
            tag.putInt("MudskipperCommand", this.getCommand());
            tag.putBoolean("MudskipperSitting", this.isOrderedToSit());
        }));
    }

    @Override
    public void loadFromBucketTag(@Nonnull CompoundTag compound) {
        Bucketable.loadDefaultDataFromBucketTag(this, compound);
        this.setFromBucket(compound.getBooleanOr("FromBucket", false));
        this.displayCooldown = compound.getIntOr("DisplayCooldown", 100 + random.nextInt(100));
        this.setCommand(compound.getIntOr("MudskipperCommand", 0));
        this.setOrderedToSit(compound.getBooleanOr("MudskipperSitting", false));
    }

    @Override
    @Nonnull
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_FISH;
    }

    @Override
    public boolean shouldFollow() {
        return this.getCommand() == 1;
    }

    public boolean isFood(ItemStack stack) {
        return stack.is(AMTagRegistry.MUDSKIPPER_BREEDABLES);
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();
        InteractionResult type = super.mobInteract(player, hand);
        if (!isTame() && itemstack.is(AMTagRegistry.MUDSKIPPER_TAMEABLES)) {
            this.usePlayerItem(player, hand, itemstack);
            this.openMouth(10);
            this.gameEvent(GameEvent.EAT);
            this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
            if (getRandom().nextInt(2) == 0) {
                this.tame(player);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS;
        }
        if (isTame() && itemstack.is(AMTagRegistry.MUDSKIPPER_FOODSTUFFS)) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemstack);
                this.openMouth(10);
                this.gameEvent(GameEvent.EAT);
                this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
                this.heal(5);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);
        if (item != Items.WATER_BUCKET && interactionresult != InteractionResult.SUCCESS && type != InteractionResult.SUCCESS && isTame() && isOwnedBy(player) && !isFood(itemstack)) {
            this.setCommand(this.getCommand() + 1);
            if (this.getCommand() == 3) {
                this.setCommand(0);
            }
            player.displayClientMessage(Component.translatable("entity.alexsmobs.all.command_" + this.getCommand(), this.getName()), true);
            boolean sit = this.getCommand() == 2;
            if (sit) {
                this.setOrderedToSit(true);
                return InteractionResult.SUCCESS;
            } else {
                this.setOrderedToSit(false);
                return InteractionResult.SUCCESS;
            }
        }
        return Bucketable.bucketMobPickup(player, hand, this).orElse(type);
    }
}
