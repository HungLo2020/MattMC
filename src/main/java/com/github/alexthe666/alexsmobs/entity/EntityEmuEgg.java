package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class EntityEmuEgg extends ThrowableItemProjectile {

    public EntityEmuEgg(EntityType<? extends ThrowableItemProjectile> p_i50154_1_, Level p_i50154_2_) {
        super(p_i50154_1_, p_i50154_2_);
    }

    public EntityEmuEgg(Level worldIn, LivingEntity throwerIn) {
        super(EntityType.EMU_EGG, throwerIn, worldIn, new ItemStack(Items.EMU_EGG));
    }

    public EntityEmuEgg(Level worldIn, double x, double y, double z) {
        super(EntityType.EMU_EGG, x, y, z, worldIn, new ItemStack(Items.EMU_EGG));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }

    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            if (this.random.nextInt(8) == 0) {
                int lvt_2_1_ = 1;
                if (this.random.nextInt(32) == 0) {
                    lvt_2_1_ = 4;
                }
                for (int lvt_3_1_ = 0; lvt_3_1_ < lvt_2_1_; ++lvt_3_1_) {
                    EntityEmu lvt_4_1_ = EntityType.EMU.create(this.level(), EntitySpawnReason.TRIGGERED);
                    if (lvt_4_1_ != null) {
                        if(this.random.nextInt(50) == 0){
                            lvt_4_1_.setVariant(2);
                        }else if(random.nextInt(3) == 0){
                            lvt_4_1_.setVariant(1);
                        }
                        lvt_4_1_.setAge(-24000);
                        lvt_4_1_.setPos(this.getX(), this.getY(), this.getZ());
                        this.level().addFreshEntity(lvt_4_1_);
                    }
                }
            }
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }

    }

    @Override
    protected Item getDefaultItem() {
        return Items.EMU_EGG;
    }
}
