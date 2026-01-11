package com.github.alexmodguy.alexscaves.server.entity.item;

import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.misc.ACSoundRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class LimestoneSpearEntity extends AbstractArrow {
    private boolean dealtDamage;

    public LimestoneSpearEntity(EntityType entityType, Level level) {
        super(entityType, level);
    }

    public LimestoneSpearEntity(Level level, LivingEntity shooter, ItemStack itemStack) {
        super(ACEntityRegistry.LIMESTONE_SPEAR.get(), shooter, level, new ItemStack(ACItemRegistry.LIMESTONE_SPEAR.get()), ItemStack.EMPTY);
    }

    public LimestoneSpearEntity(Level level, double x, double y, double z) {
        super(ACEntityRegistry.LIMESTONE_SPEAR.get(), x, y, z, level, new ItemStack(ACItemRegistry.LIMESTONE_SPEAR.get()), ItemStack.EMPTY);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ACItemRegistry.LIMESTONE_SPEAR.get());
    }


    @Nullable
    protected EntityHitResult findHitEntity(Vec3 vec3, Vec3 vec31) {
        return this.dealtDamage ? null : super.findHitEntity(vec3, vec31);
    }

    protected void onHitEntity(EntityHitResult hitResult) {
        Entity entity = hitResult.getEntity();
        float f = 4.0F;
        // Note: getMobType() and getDamageBonus with MobType removed in 1.21
        // Base damage is used instead

        Entity entity1 = this.getOwner();
        DamageSource damagesource = this.damageSources().trident(this, (Entity) (entity1 == null ? this : entity1));
        this.dealtDamage = true;
        SoundEvent soundevent = ACSoundRegistry.LIMESTONE_SPEAR_HIT.get();
        if (entity.hurt(damagesource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity1 = (LivingEntity) entity;
                // Note: doPostHurtEffects and doPostDamageEffects removed in 1.21
                // Enchantment effects are now handled automatically by the damage system

                this.doPostHurtEffects(livingentity1);
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        float f1 = 1.0F;
        this.playSound(soundevent, f1, 1.0F);
    }

    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return ACSoundRegistry.LIMESTONE_SPEAR_HIT.get();
    }

    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

}
