package net.caffeinemc.mods.sodium.mixin.core.render.world;

import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.FogStorage;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin implements FogStorage {
    @Unique
    private FogParameters parameters = FogParameters.NONE;

    @Override
    public FogParameters sodium$getFogParameters() {
        return parameters;
    }

    /**
     * Capture the updateBuffer call to extract fog parameters.
     * Injecting into updateBuffer instead of setupFog avoids @Local capture issues.
     */
    @Inject(method = "updateBuffer", at = @At("HEAD"))
    private void sodium$captureBuffer(ByteBuffer byteBuffer, int i, Vector4f fogColor, float envStart, float envEnd, float distStart, float distEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
        parameters = new FogParameters(fogColor.x, fogColor.y, fogColor.z, fogColor.w, envStart, envEnd, distStart, distEnd);
    }
}
