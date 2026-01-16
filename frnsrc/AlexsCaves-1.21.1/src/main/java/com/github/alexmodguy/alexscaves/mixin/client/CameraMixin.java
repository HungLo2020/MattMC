package com.github.alexmodguy.alexscaves.mixin.client;

import com.github.alexmodguy.alexscaves.server.entity.util.MagnetUtil;
import com.github.alexmodguy.alexscaves.server.potion.ACEffectRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    // In 1.21, move() parameters changed from double to float
    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    protected abstract void setPosition(Vec3 vec3);

    // In 1.21, getMaxZoom() parameter changed from double to float
    @Shadow
    private float getMaxZoom(float maxZoom) {
        return 0;
    }

    @Shadow
    protected abstract void setRotation(float p_90573_, float p_90574_);

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Shadow
    private boolean initialized;

    @Inject(
            method = {"Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"},
            remap = true,
            at = @At(value = "TAIL")
    )
    public void ac_onSyncedDataUpdated(BlockGetter level, Entity entity, boolean detatched, boolean mirrored, float partialTicks, CallbackInfo ci) {
        Direction dir = MagnetUtil.getEntityMagneticDirection(entity);
        if (dir != Direction.DOWN && dir != Direction.UP) {
            this.setPosition(MagnetUtil.getEyePositionForAttachment(entity, dir, partialTicks));
            if (detatched) {
                if (mirrored) {
                    this.setRotation(this.yRot + 180.0F, -this.xRot);
                }
                // In 1.21, move() and getMaxZoom() use float instead of double
                this.move(-this.getMaxZoom(4.0F), 0.0F, 0.0F);
            }
        }
    }

    @Inject(
            method = {"Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"},
            remap = true,
            cancellable = true,
            at = @At(value = "HEAD")
    )
    public void ac_getFluidInCamera(CallbackInfoReturnable<FogType> cir) {
        if (initialized && Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(ACEffectRegistry.BUBBLED) && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(FogType.WATER);
        }
    }
}
