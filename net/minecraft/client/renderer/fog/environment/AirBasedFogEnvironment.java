package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
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
		Vec3 vec32 = clientLevel.effects()
			.getBrightnessDependentFogColor(
				CubicSampler.gaussianSampleVec3(vec3, (ix, jx, kx) -> Vec3.fromRGB24(((Biome)biomeManager.getNoiseBiomeAtQuart(ix, jx, kx).value()).getFogColor())), g
			);
		float h = (float)vec32.x();
		float j = (float)vec32.y();
		float k = (float)vec32.z();
		if (i >= 4) {
			float l = Mth.sin(clientLevel.getSunAngle(f)) > 0.0F ? -1.0F : 1.0F;
			Vector3f vector3f = new Vector3f(l, 0.0F, 0.0F);
			float m = camera.getLookVector().dot(vector3f);
			if (m > 0.0F && clientLevel.effects().isSunriseOrSunset(clientLevel.getTimeOfDay(f))) {
				int n = clientLevel.effects().getSunriseOrSunsetColor(clientLevel.getTimeOfDay(f));
				m *= ARGB.alphaFloat(n);
				h = Mth.lerp(m, h, ARGB.redFloat(n));
				j = Mth.lerp(m, j, ARGB.greenFloat(n));
				k = Mth.lerp(m, k, ARGB.blueFloat(n));
			}
		}

		int o = clientLevel.getSkyColor(camera.getPosition(), f);
		float p = ARGB.redFloat(o);
		float m = ARGB.greenFloat(o);
		float q = ARGB.blueFloat(o);
		float r = 0.25F + 0.75F * i / 32.0F;
		r = 1.0F - (float)Math.pow(r, 0.25);
		h += (p - h) * r;
		j += (m - j) * r;
		k += (q - k) * r;
		float s = clientLevel.getRainLevel(f);
		if (s > 0.0F) {
			float t = 1.0F - s * 0.5F;
			float u = 1.0F - s * 0.4F;
			h *= t;
			j *= t;
			k *= u;
		}

		float t = clientLevel.getThunderLevel(f);
		if (t > 0.0F) {
			float u = 1.0F - t * 0.5F;
			h *= u;
			j *= u;
			k *= u;
		}

		return ARGB.colorFromFloat(1.0F, h, j, k);
	}
}
