package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.entity.ai.AnimalAIFleeLight;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntityCentipedeHead extends Monster {

    private static final EntityDataAccessor<String> CHILD_UUID = SynchedEntityData.defineId(EntityCentipedeHead.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CHILD_ID = SynchedEntityData.defineId(EntityCentipedeHead.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEGMENT_COUNT = SynchedEntityData.defineId(EntityCentipedeHead.class, EntityDataSerializers.INT);
    public final float[] ringBuffer = new float[64];
    public int ringBufferIndex = -1;
    private EntityCentipedeBody[] parts;

    public EntityCentipedeHead(EntityType type, Level worldIn) {
        super(type, worldIn);
        this.xpReward = 13;
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 35.0D).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ARMOR, 6.0D).add(Attributes.ATTACK_DAMAGE, 8.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.5F).add(Attributes.MOVEMENT_SPEED, 0.22F).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    public static <T extends Mob> boolean canCentipedeSpawn(EntityType<EntityCentipedeHead> entityType, ServerLevelAccessor iServerWorld, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return reason == EntitySpawnReason.SPAWNER || !iServerWorld.canSeeSky(pos) && pos.getY() <= 0 && checkMonsterSpawnRules(entityType, iServerWorld, reason, pos, random);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4D, false));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D, 13, false));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new AnimalAIFleeLight(this, 1.0D, 75, 5));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this)));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, true, null));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 20, true, true, null));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, EntityCockroach.class, 45, true, true, null));
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.CENTIPEDE_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.CENTIPEDE_HURT;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.CENTIPEDE_WALK, 1F, 1.0F);
    }

    public int getMaxHeadXRot() {
        return 1;
    }

    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return false;
    }

    public int getHeadRotSpeed() {
        return 1;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHILD_UUID, "");
        builder.define(CHILD_ID, -1);
        builder.define(SEGMENT_COUNT, 5);
    }

    public boolean doHurtTarget(ServerLevel level, Entity entityIn) {
        if (super.doHurtTarget(level, entityIn)) {
            if (entityIn instanceof LivingEntity) {
                final int i;
                final Difficulty difficulty = this.level().getDifficulty();
                if (difficulty == Difficulty.NORMAL) {
                    i = 10;
                } else if (difficulty == Difficulty.HARD) {
                    i = 20;
                } else {
                    i = 3;
                }
                ((LivingEntity) entityIn).addEffect(new MobEffectInstance(MobEffects.POISON, i * 20, 1));
            }
            this.playSound(SoundEvents.CENTIPEDE_ATTACK, this.getSoundVolume(), this.getVoicePitch());
            this.gameEvent(GameEvent.ENTITY_INTERACT);
            return true;
        } else {
            return false;
        }
    }

    public int getSegmentCount() {
        return Math.max(this.entityData.get(SEGMENT_COUNT), 1);
    }

    public void setSegmentCount(int segments) {
        this.entityData.set(SEGMENT_COUNT, segments);
    }

    @Nullable
    public UUID getChildId() {
        String uuidStr = this.entityData.get(CHILD_UUID);
        if (uuidStr == null || uuidStr.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setChildId(@Nullable UUID uniqueId) {
        this.entityData.set(CHILD_UUID, uniqueId == null ? "" : uniqueId.toString());
    }

    public Entity getChild() {
        final UUID id = getChildId();
        if (id != null && !this.level().isClientSide()) {
            return ((ServerLevel) level()).getEntity(id);
        }
        return null;
    }

    public void pushEntities() {
        final List<Entity> entities = this.level().getEntities(this, this.getBoundingBox().expandTowards(0.2D, 0.0D, 0.2D));
        entities.stream().filter(entity -> !(entity instanceof EntityCentipedeBody) && entity.isPushable()).forEach(entity -> entity.push(this));
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, EntitySpawnReason reason, @Nullable SpawnGroupData spawnDataIn) {
        this.setSegmentCount(random.nextInt(4) + 5);
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn);
    }

    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        if (this.getChildId() != null) {
            valueOutput.putString("ChildUUID", this.getChildId().toString());
        }
        valueOutput.putInt("SegCount", getSegmentCount());

    }

    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        Optional<String> childUUID = valueInput.getString("ChildUUID");
        if (childUUID.isPresent()) {
            try {
                this.setChildId(UUID.fromString(childUUID.get()));
            } catch (IllegalArgumentException e) {
                // Invalid UUID string
            }
        }
        this.setSegmentCount(valueInput.getIntOr("SegCount", 3));
    }

    private boolean shouldReplaceParts() {
        if (parts == null || parts[0] == null || parts.length != this.getSegmentCount()) {
            return true;
        }
        for (int i = 0; i < this.getSegmentCount(); i++) {
            if (parts[i] == null) {
                return true;
            }
        }
        return false;
    }

    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(DamageTypes.IN_WALL)  || super.isInvulnerableTo(level, source);
    }


    public void tick() {
        super.tick();
        this.yBodyRot = Mth.clamp(this.getYRot(), this.yBodyRot - 2, this.yBodyRot + 2);
        this.yHeadRot = this.yBodyRot;
        if (this.ringBufferIndex < 0) {
            Arrays.fill(this.ringBuffer, this.yBodyRot);
        }
        if (updateRingBuffer() || ringBufferIndex < 0) {
            this.ringBufferIndex++;
        }
        if (this.ringBufferIndex == this.ringBuffer.length) {
            this.ringBufferIndex = 0;
        }
        this.ringBuffer[this.ringBufferIndex] = this.getYRot();

        if (!this.level().isClientSide()) {
            final Entity child = getChild();
            if (child == null) {
                LivingEntity partParent = this;
                parts = new EntityCentipedeBody[this.getSegmentCount()];
                Vec3 prevPos = this.position();
                float backOffset = 0.45F;
                for (int i = 0; i < this.getSegmentCount(); i++) {
                    final EntityCentipedeBody part = createBody(partParent, i == this.getSegmentCount() - 1);
                    part.setParent(partParent);
                    part.setBodyIndex(i);
                    if (partParent == this) {
                        this.setChildId(part.getUUID());
                        this.entityData.set(CHILD_ID, part.getId());
                    }
                    if (partParent instanceof final EntityCentipedeBody body) {
                        body.setChildId(part.getUUID());
                    }
                    part.setPos(part.tickMultipartPosition(this.getId(), backOffset, prevPos, this.getXRot(), getYawForPart(i), false));
                    level().addFreshEntity(part);
                    parts[i] = part;
                    partParent = part;
                    backOffset = part.getBackOffset();
                    prevPos = part.position();
                }
            }
            if (tickCount > 1) {
                if (shouldReplaceParts() && this.getChild() instanceof EntityCentipedeBody) {
                    parts = new EntityCentipedeBody[this.getSegmentCount()];
                    parts[0] = (EntityCentipedeBody) this.getChild();
                    this.entityData.set(CHILD_ID, parts[0].getId());
                    int i = 1;
                    while (i < parts.length && parts[i - 1].getChild() instanceof EntityCentipedeBody) {
                        parts[i] = (EntityCentipedeBody) parts[i - 1].getChild();
                        i++;
                    }
                }
                Vec3 prev = this.position();
                float xRot = this.getXRot();
                float backOffset = 0.45F;
                for (int i = 0; i < this.getSegmentCount(); i++) {
                    if (this.parts[i] != null) {
                        final float reqRot = getYawForPart(i);
                        prev = parts[i].tickMultipartPosition(this.getId(), backOffset, prev, xRot, reqRot, true);
                        xRot = parts[i].getXRot();
                        backOffset = parts[i].getBackOffset();
                    }
                }
            }
        }
    }

    private boolean updateRingBuffer() {
        return this.getDeltaMovement().lengthSqr() >= 0.005D;
    }


    public EntityCentipedeBody createBody(LivingEntity parent, boolean tail) {
        EntityCentipedeBody body = tail ? new EntityCentipedeTail(EntityType.CAVE_CENTIPEDE_TAIL, parent.level()) : new EntityCentipedeBody(EntityType.CAVE_CENTIPEDE_BODY, parent.level());
        body.setParent(parent);
        return body;
    }

    public boolean canBeLeashed(Player player) {
        return true;
    }

    private float getYawForPart(int i) {
        return this.getRingBuffer(4 + i * 4, 1.0F);
    }

    public float getRingBuffer(int bufferOffset, float partialTicks) {
        if (this.isDeadOrDying()) {
            partialTicks = 0.0F;
        }

        partialTicks = 1.0F - partialTicks;
        final int i = this.ringBufferIndex - bufferOffset & 63;
        final int j = this.ringBufferIndex - bufferOffset - 1 & 63;
        final float d0 = this.ringBuffer[i];
        final float d1 = this.ringBuffer[j] - d0;
        return Mth.wrapDegrees(d0 + d1 * partialTicks);
    }
}
