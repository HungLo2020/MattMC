package com.github.alexthe666.alexsmobs.item;

import com.github.alexthe666.alexsmobs.entity.EntityCatfish;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class ItemModFishBucket extends MobBucketItem {

    private final Supplier<? extends EntityType<? extends Mob>> fishTypeSupplier;

    public ItemModFishBucket(Supplier<? extends EntityType<? extends Mob>> fishTypeIn, Fluid fluid, Item.Properties builder) {
        super(fishTypeIn.get(), fluid, SoundEvents.BUCKET_EMPTY_FISH, builder.stacksTo(1));
        this.fishTypeSupplier = fishTypeIn;
    }

    public EntityType<? extends Mob> getFishType() {
        return this.fishTypeSupplier.get();
    }

    @Override
    public void checkExtraContent(@Nullable LivingEntity livingEntity, Level level, ItemStack stack, BlockPos pos) {
        if (level instanceof ServerLevel) {
            this.spawnFish((ServerLevel) level, stack, pos);
            level.gameEvent(livingEntity, GameEvent.ENTITY_PLACE, pos);
        }
    }

    private void spawnFish(ServerLevel serverLevel, ItemStack stack, BlockPos pos) {
        Entity entity = getFishType().spawn(serverLevel, stack, (Player) null, pos, EntitySpawnReason.BUCKET, true, false);
        if (entity instanceof Bucketable) {
            Bucketable bucketable = (Bucketable) entity;
            bucketable.loadFromBucketTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            bucketable.setFromBucket(true);
        }
        addExtraAttributes(entity, stack);
    }

    private void addExtraAttributes(Entity entity, ItemStack stack) {
        if (entity instanceof EntityCatfish catfish) {
            if (stack.is(Items.SMALL_CATFISH_BUCKET)) {
                catfish.setCatfishSize(0);
            } else if (stack.is(Items.MEDIUM_CATFISH_BUCKET)) {
                catfish.setCatfishSize(1);
            } else if (stack.is(Items.LARGE_CATFISH_BUCKET)) {
                catfish.setCatfishSize(2);
            }
        }
    }

}
