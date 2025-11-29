package net.matt.quantize.mixin;

import net.matt.quantize.tags.QTags;
import net.matt.quantize.modules.entities.WolfEntityVariant;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Wolf.class)
public abstract class WolfEntityMixin extends MobEntityMixin {

    @Unique
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);

    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    public void initTracker (CallbackInfo ci) {
        Wolf wolfEntity = (Wolf) (Object) this;
        wolfEntity.getEntityData().define(VARIANT, 0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    public void writeNBTData (CompoundTag pCompound, CallbackInfo ci) {
        pCompound.putInt("Variant", getTypeVariant());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    public void readNBTData (CompoundTag pCompound, CallbackInfo ci) {
        Wolf wolfEntity = (Wolf) (Object) this;
        wolfEntity.getEntityData().set(VARIANT, pCompound.getInt("Variant"));
    }

    @Override
    protected void onInitialize(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, SpawnGroupData pSpawnData, CompoundTag pDataTag, CallbackInfoReturnable<SpawnGroupData> cir) {
        Wolf wolfEntity = (Wolf) (Object) this;
        Holder<Biome> registryEntry = pLevel.getBiome(wolfEntity.getOnPos());
        WolfEntityVariant variant = WolfEntityVariant.byId(WolfEntityVariant.PALE_WOLF.getId());

        if(registryEntry.is(QTags.SPAWNS_WOODS_WOLF)) {
            variant = WolfEntityVariant.WOODS_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_ASHEN_WOLF)) {
            variant = WolfEntityVariant.ASHEN_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_BLACK_WOLF)) {
            variant = WolfEntityVariant.BLACK_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_CHESTNUT_WOLF)) {
            variant = WolfEntityVariant.CHESTNUT_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_RUSTY_WOLF)) {
            variant = WolfEntityVariant.RUSTY_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_SPOTTED_WOLF)) {
            variant = WolfEntityVariant.SPOTTED_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_STRIPED_WOLF)) {
            variant = WolfEntityVariant.STRIPED_WOLF;
        } else if(registryEntry.is(QTags.SPAWNS_SNOWY_WOLF)) {
            variant = WolfEntityVariant.SNOWY_WOLF;
        }

        this.setVariant(variant);
    }

    @Inject(
            method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/animal/Wolf;",
            at = @At("RETURN")
    )
    private void onCreateChild(ServerLevel pLevel, AgeableMob pOtherParent, CallbackInfoReturnable<Wolf> cir) {
        Wolf child = cir.getReturnValue();
        Wolf wolfEntity = (Wolf) (Object) this;

        CompoundTag childNbt = new CompoundTag();
        child.addAdditionalSaveData(childNbt);

        CompoundTag nbtParent = new CompoundTag();
        (wolfEntity).addAdditionalSaveData(nbtParent);
        CompoundTag nbtOtherParent = new CompoundTag();
        pOtherParent.addAdditionalSaveData(nbtOtherParent);

        int variant = wolfEntity.getRandom().nextBoolean() ? nbtParent.getInt("Variant") : nbtOtherParent.getInt("Variant");

        child.getEntityData().set(VARIANT, variant & 255);

        childNbt.putInt("Variant", variant);

        child.readAdditionalSaveData(childNbt);
    }

    public WolfEntityVariant getVariant() {
        return WolfEntityVariant.byId(getTypeVariant() & 255);
    }

    public int getTypeVariant() {
        Wolf wolfEntity = (Wolf) (Object) this;
        return wolfEntity.getEntityData().get(VARIANT);
    }

    public void setVariant(WolfEntityVariant variant) {
        Wolf wolfEntity = (Wolf) (Object) this;
        wolfEntity.getEntityData().set(VARIANT, variant.getId() & 255);
    }

}
