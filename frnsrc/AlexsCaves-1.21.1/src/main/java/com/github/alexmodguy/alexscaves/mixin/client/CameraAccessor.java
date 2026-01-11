package com.github.alexmodguy.alexscaves.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    // In 1.21, move() parameters changed from double to float
    @Invoker("move")
    void invokeMove(float zoom, float dy, float dx);

    // In 1.21, getMaxZoom() parameter and return type changed from double to float
    @Invoker("getMaxZoom")
    float invokeGetMaxZoom(float maxZoom);
}
