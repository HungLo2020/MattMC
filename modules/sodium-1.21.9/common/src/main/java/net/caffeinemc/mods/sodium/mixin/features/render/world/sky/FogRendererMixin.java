package net.caffeinemc.mods.sodium.mixin.features.render.world.sky;

import net.caffeinemc.mods.sodium.client.util.color.FastCubicSampler;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AirBasedFogEnvironment;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AirBasedFogEnvironment.class)
public class FogRendererMixin {
    @Unique
    private static final ThreadLocal<BiomeManager> sodium$biomeManager = new ThreadLocal<>();

    @Inject(method = "getBaseColor", at = @At("HEAD"))
    private void sodium$captureBiomeManager(ClientLevel clientLevel, Camera camera, int i, float f, CallbackInfoReturnable<Integer> cir) {
        sodium$biomeManager.set(clientLevel.getBiomeManager());
    }

    @Redirect(method = "getBaseColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectSampleColor(Vec3 pos, CubicSampler.Vec3Fetcher vec3Fetcher) {
        BiomeManager biomeManager = sodium$biomeManager.get();
        return FastCubicSampler.sampleColor(pos,
                (i, j, k) -> biomeManager.getNoiseBiomeAtQuart(i, j, k).value().getFogColor(),
                (v) -> v);
    }

    @Inject(method = "getBaseColor", at = @At("RETURN"))
    private void sodium$clearBiomeManager(ClientLevel clientLevel, Camera camera, int i, float f, CallbackInfoReturnable<Integer> cir) {
        sodium$biomeManager.remove();
    }
}
