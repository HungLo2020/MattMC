package com.github.alexmodguy.alexscaves.server.entity.living;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class SauropodPartEntity extends Entity {
    public final SauropodBaseEntity parentMob;
    private final Entity connectedTo;
    private final EntityDimensions size;
    public float scale = 1;

    public SauropodPartEntity(SauropodBaseEntity parent, Entity connectedTo, float sizeXZ, float sizeY) {
        super(parent.getType(), parent.level());
        this.parentMob = parent;
        this.blocksBuilding = true;
        this.connectedTo = connectedTo;
        this.size = EntityDimensions.scalable(sizeXZ, sizeY);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
    }

    public EntityDimensions getDimensions(Pose pose) {
        return parentMob == null ? size : size.scale(parentMob.getScale());
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (parentMob == null) {
            return InteractionResult.PASS;
        } else {
            this.playSound(net.minecraft.sounds.SoundEvents.ITEM_BREAK.value());
            return parentMob.interact(player, hand);
        }
    }

    // canBeCollidedWith removed in 1.21 - collision handled differently

    @Override
    public boolean isPickable() {
        return parentMob != null && parentMob.isPickable();
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return parentMob != null ? parentMob.getPickResult() : null;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        }
        if (parentMob != null && !parentMob.isDeadOrDying()) {
            damageSource = parentMob.level().damageSources().mobAttack(parentMob);
            if (!damageSource.is(DamageTypeTags.NO_KNOCKBACK)) {
                double entityX = damageSource.getEntity() != null ? damageSource.getEntity().getX() : this.getX();
                double entityZ = damageSource.getEntity() != null ? damageSource.getEntity().getZ() : this.getZ();
                double d0 = entityX - parentMob.getX();
                double d1;
                for (d1 = entityZ - parentMob.getZ(); d0 * d0 + d1 * d1 < 1.0E-4; d1 = (Math.random() - Math.random()) * 0.01) {
                    d0 = (Math.random() - Math.random()) * 0.01;
                }
                parentMob.knockback(0.4F, d0, d1);
            }
            return parentMob.hurtServer(serverLevel, damageSource, f);
        }
        return false;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.parentMob == entity;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public void setPosition(double x, double y, double z) {
        this.setPosRaw(x, y, z);
    }

    public void setPosCenteredY(Vec3 vec3) {
        this.setPosition(vec3.x, vec3.y - this.getBbHeight() / 2.0, vec3.z);
        float sizeXZ = size.width() / 2.0F;
        float sizeY = size.height();
        // Can't call setBoundingBox directly - use setPos which updates BB
        this.setPos(vec3.x, vec3.y, vec3.z);
    }

    public Vec3 centeredPosition() {
        return new Vec3(this.getX(), this.getY() + this.getBbHeight() / 2.0, this.getZ());
    }

    public float calculateAnimationAngle(float partialTicks, boolean pitch) {
        float parentRot = 0;
        Vec3 connection = connectedTo.getPosition(partialTicks).add(0, connectedTo.getBbHeight() * 0.5F, 0);
        if (connectedTo == parentMob && parentMob != null) {
            connection = connection.add(0, -parentMob.getLegSolverBodyOffset(), 0);
        }
        if (parentMob != null && this == parentMob.neckPart1) {
            connection = connection.add(0, 2F * parentMob.getScale(), 0);
        }
        if(parentMob != null){
            parentRot = -(parentMob.yBodyRotO + (parentMob.yBodyRot - parentMob.yBodyRotO) * partialTicks) - 90F;
        }
        Vec3 center = centeredPosition(partialTicks);
        Vec3 offset = connection.subtract(center).normalize();
        Vec3 back = center.add(offset.scale(-1 * this.getBbWidth()));
        double d0 = connection.x - back.x;
        double d1 = connection.y - back.y;
        double d2 = connection.z - back.z;
        if (pitch) {
            double d3 = Mth.sqrt((float) (d0 * d0 + d2 * d2));
            return Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 180.0F / (float) Math.PI)));
        } else {
            return (float) (Mth.atan2(d2, d0) * 57.2957763671875D) + parentRot;
        }
    }

    public Vec3 centeredPosition(float partialTicks) {
        return this.getPosition(partialTicks).add(0, this.getBbHeight() * 0.5F, 0);
    }
}
