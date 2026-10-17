package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.hooks.FogColorHooks;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.client.renderer.fog.NativeFog;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public abstract class AirBasedFogEnvironment extends FogEnvironment {
	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		float g = Mth.clamp(Mth.cos(clientLevel.getTimeOfDay(f) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
		BiomeManager biomeManager = clientLevel.getBiomeManager();
		Vec3 vec3 = camera.getPosition().subtract(2.0, 2.0, 2.0).scale(0.25);
		
		// Allow hooks to provide custom fog color sampling
		CubicSampler.Vec3Fetcher rgbFetcher = (ix, jx, kx) -> Vec3.fromRGB24(((Biome)biomeManager.getNoiseBiomeAtQuart(ix, jx, kx).value()).getFogColor());
		Vec3 sampledColor = null;
		for (FogColorHooks hook : HookRegistry.getFogColorHooks()) {
			sampledColor = hook.sampleFogColor(clientLevel, biomeManager, vec3, rgbFetcher);
			if (sampledColor != null) {
				break;
			}
		}

		// Fall back to default implementation if no hook provided a result
		if (sampledColor == null) {
			sampledColor = CubicSampler.gaussianSampleVec3(vec3, rgbFetcher);
		}

		Vec3 vec32 = clientLevel.effects()
			.getBrightnessDependentFogColor(sampledColor, g);
		float h = (float)vec32.x();
		float j = (float)vec32.y();
		float k = (float)vec32.z();
        int sunrise = 0;
        float sunWeight = 0.0F;
        if (i >= 4) {
            float direction = Mth.sin(clientLevel.getSunAngle(f)) > 0.0F ? -1.0F : 1.0F;
            float weight = camera.getLookVector().dot(new Vector3f(direction, 0.0F, 0.0F));
            if (weight > 0.0F && clientLevel.effects().isSunriseOrSunset(clientLevel.getTimeOfDay(f))) {
                sunrise = clientLevel.effects().getSunriseOrSunsetColor(clientLevel.getTimeOfDay(f));
                sunWeight = weight;
            }
        }
        int sky = clientLevel.getSkyColor(camera.getPosition(), f);
        float rain = clientLevel.getRainLevel(f);
        float thunder = clientLevel.getThunderLevel(f);
        return NativeFog.airColor(h, j, k, sunrise, sunWeight, sky, i, rain, thunder);
	}
}
