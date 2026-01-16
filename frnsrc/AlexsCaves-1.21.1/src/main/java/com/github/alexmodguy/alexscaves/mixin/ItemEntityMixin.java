package com.github.alexmodguy.alexscaves.mixin;

import com.github.alexmodguy.alexscaves.server.entity.util.MagnetUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    public ItemEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // Magnetic data is now handled via NeoForge Attachment API in EntityMixin
    // No longer need to define synched data here

    @Inject(
            method = {"Lnet/minecraft/world/entity/item/ItemEntity;tick()V"},
            remap = true,
            at = @At(value = "TAIL")
    )
    public void ac_tick(CallbackInfo ci) {
        if (MagnetUtil.isPulledByMagnets(this)) {
            MagnetUtil.tickMagnetism(this);
        }
    }
}
