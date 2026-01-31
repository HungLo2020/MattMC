package net.alexsmobs.entity;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class EntityCockroachEgg extends ThrowableItemProjectile {

    public EntityCockroachEgg(EntityType p_i50154_1_, Level p_i50154_2_) {
        super(p_i50154_1_, p_i50154_2_);
    }

    public EntityCockroachEgg(Level worldIn, LivingEntity throwerIn) {
        super(EntityType.COCKROACH_EGG, throwerIn, worldIn, new ItemStack(Items.COCKROACH_OOTHECA));
    }

    public EntityCockroachEgg(Level worldIn, double x, double y, double z) {
        super(EntityType.COCKROACH_EGG, x, y, z, worldIn, new ItemStack(Items.COCKROACH_OOTHECA));
    }

    public void handleEntityEvent(byte id) {
        if (id == 3) {
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }

    }

    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(this, (byte)3);
            int i = random.nextInt(3);
            for (int j = 0; j < i; ++j) {
                final EntityCockroach croc = EntityType.COCKROACH.create(serverLevel, EntitySpawnReason.TRIGGERED);
                if (croc != null) {
                    croc.setAge(-24000);
                    croc.setPos(this.getX(), this.getY(), this.getZ());
                    croc.setYRot(this.getYRot());
                    croc.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.TRIGGERED, (SpawnGroupData)null);
                    serverLevel.addFreshEntity(croc);
                }
            }
            serverLevel.broadcastEntityEvent(this, (byte)3);
            this.remove(RemovalReason.DISCARDED);
        }

    }

    protected Item getDefaultItem() {
        return Items.COCKROACH_OOTHECA;
    }
}
