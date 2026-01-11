package com.github.alexmodguy.alexscaves.server.entity.item;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.client.particle.ACParticleRegistry;
import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.OptionalInt;

public class SodaBottleRocketEntity extends FireworkRocketEntity {

    private static final EntityDataAccessor<ItemStack> DATA_FIREWORKS_ITEM = SynchedEntityData.defineId(SodaBottleRocketEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TARGET = SynchedEntityData.defineId(SodaBottleRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.defineId(SodaBottleRocketEntity.class, EntityDataSerializers.BOOLEAN);

    private int phageAge = 0;
    private int life = 0;
    private int lifetime = 0;
    @Nullable
    private LivingEntity attachedToEntity;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FIREWORKS_ITEM, ItemStack.EMPTY);
        builder.define(DATA_ATTACHED_TARGET, OptionalInt.empty());
        builder.define(DATA_SHOT_AT_ANGLE, false);
    }

    public SodaBottleRocketEntity(EntityType entityType, Level level) {
        super(entityType, level);
    }

    public SodaBottleRocketEntity(Level worldIn, double x, double y, double z, ItemStack givenItem) {
        super(ACEntityRegistry.SODA_BOTTLE_ROCKET.get(), worldIn);
        this.setPos(x, y, z);
        if (!givenItem.isEmpty()) {
            this.entityData.set(DATA_FIREWORKS_ITEM, givenItem.copy());
        }

        this.setDeltaMovement(this.random.nextGaussian() * 0.001D, 0.05D, this.random.nextGaussian() * 0.001D);
        this.lifetime = 18 + this.random.nextInt(14);
    }

    public SodaBottleRocketEntity(Level level, @Nullable Entity entity, double x, double y, double z, ItemStack stack) {
        this(level, x, y, z, stack);
        this.setOwner(entity);
    }

    public SodaBottleRocketEntity(Level level, ItemStack stack, LivingEntity livingEntity) {
        this(level, livingEntity, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), stack);
        this.entityData.set(DATA_ATTACHED_TARGET, OptionalInt.of(livingEntity.getId()));
        this.attachedToEntity = livingEntity;
    }

    @Nullable
    private LivingEntity getAttachedToEntity() {
        OptionalInt optionalint = this.entityData.get(DATA_ATTACHED_TARGET);
        if (optionalint.isPresent()) {
            Entity entity = this.level().getEntity(optionalint.getAsInt());
            if (entity instanceof LivingEntity) {
                return (LivingEntity) entity;
            }
        }
        return null;
    }

    public void tick() {
        super.tick();
        ++this.phageAge;
        if (this.level().isClientSide) {
            for(int i = 0; i < 5; i++){
                this.level().addParticle(ACParticleRegistry.PURPLE_SODA_BUBBLE.get(), this.getX(), this.getY() - 0.3D, this.getZ(), this.random.nextGaussian() * 0.25D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.25D);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void handleEntityEvent(byte id) {
        if (id == 17) {
            this.level().addParticle(ACParticleRegistry.FROSTMINT_EXPLOSION.get(), this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, 0.005D, this.random.nextGaussian() * 0.05D);
            for(int i = 0; i < this.random.nextInt(15) + 30; ++i) {
                this.level().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.25D, this.random.nextGaussian() * 0.25D, this.random.nextGaussian() * 0.25D);
            }
            for(int i = 0; i < this.random.nextInt(15) + 15; ++i) {
                this.level().addParticle(ACParticleRegistry.PURPLE_SODA_BUBBLE.get(), this.getX() + this.random.nextGaussian() * 0.95D, this.getY() + this.random.nextGaussian() * 0.95D, this.getZ() + this.random.nextGaussian() * 0.95D, this.random.nextGaussian() * 0.15D, this.random.nextGaussian() * 0.15D, this.random.nextGaussian() * 0.15D);
            }
            SoundEvent soundEvent = AlexsCaves.PROXY.isFarFromCamera(this.getX(), this.getY(), this.getZ()) ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.FIREWORK_ROCKET_BLAST_FAR;
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), soundEvent, SoundSource.AMBIENT, 20.0F, 0.95F + this.random.nextFloat() * 0.1F, true);


        }else{
            super.handleEntityEvent(id);
        }
    }


    @OnlyIn(Dist.CLIENT)
    public ItemStack getItem() {
        return new ItemStack(ACItemRegistry.PURPLE_SODA_BOTTLE_ROCKET.get());
    }

}
