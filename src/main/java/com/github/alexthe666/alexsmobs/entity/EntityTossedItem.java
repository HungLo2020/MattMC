package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EntityTossedItem extends ThrowableItemProjectile {

    protected static final EntityDataAccessor<Boolean> DART = SynchedEntityData.defineId(EntityTossedItem.class, EntityDataSerializers.BOOLEAN);

    public EntityTossedItem(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public EntityTossedItem(Level worldIn, LivingEntity throwerIn) {
        super(EntityType.TOSSED_ITEM, worldIn);
        this.setOwner(throwerIn);
        this.setPos(throwerIn.getX(), throwerIn.getEyeY() - 0.1, throwerIn.getZ());
    }

    public EntityTossedItem(Level worldIn, double x, double y, double z) {
        super(EntityType.TOSSED_ITEM, worldIn);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DART, false);
    }

    public boolean isDart() {
        return this.entityData != null && this.entityData.get(DART);
    }

    public void setDart(boolean dart) {
        this.entityData.set(DART, dart);
    }

    public void handleEntityEvent(byte id) {
        if (id == 3) {
            for(int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }
    }

    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            final float f = Mth.sqrt((float) (x * x + z * z));
            this.setXRot((float)(Mth.atan2(y, (double)f) * (double)Mth.RAD_TO_DEG));
            this.setYRot( (float)(Mth.atan2(x, z) * (double)Mth.RAD_TO_DEG));
            this.xRotO = this.getXRot();
            this.yRotO = this.getYRot();
            this.setPos(this.getX(), this.getY(), this.getZ());
            this.setYRot(this.getYRot());
            this.setXRot(this.getXRot());
        }
    }

    public void tick() {
        super.tick();
        Vec3 vector3d = this.getDeltaMovement();
        float f = Mth.sqrt((float) vector3d.horizontalDistanceSqr());
        this.setXRot(lerpRotation(this.xRotO, (float)(Mth.atan2(vector3d.y, (double)f) * (double)Mth.RAD_TO_DEG)));
        this.setYRot( lerpRotation(this.yRotO, (float)(Mth.atan2(vector3d.x, vector3d.z) * (double)Mth.RAD_TO_DEG)));
    }

    protected static float lerpRotation(float from, float to) {
        while(to - from < -180.0F) {
            from -= 360.0F;
        }

        while(to - from >= 180.0F) {
            from += 360.0F;
        }

        return Mth.lerp(0.2F, from, to);
    }


    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity owner = this.getOwner();
        if(owner instanceof EntityCapuchinMonkey boss){
            if(!boss.isAlliedTo(hitResult.getEntity()) || !boss.isTame() && !(hitResult.getEntity() instanceof EntityCapuchinMonkey)){
                hitResult.getEntity().hurt(damageSources().thrown(this, boss), isDart() ? 8 : 4);
            }
        }
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("Dart", this.isDart());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setDart(compound.getBooleanOr("Dart", false));
    }

    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide() && (!this.isDart() || result.getType() == HitResult.Type.BLOCK)) {
            this.level().broadcastEntityEvent(this, (byte)3);
            this.discard();
        }
    }

    protected Item getDefaultItem() {
        // TODO: Add ancient dart item
        return Items.COBBLESTONE; // isDart() ? Items.ANCIENT_DART : Items.COBBLESTONE;
    }
}
