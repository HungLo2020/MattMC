package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AtmosphericFogEnvironment extends AirBasedFogEnvironment {
	private static final int MIN_RAIN_FOG_SKY_LIGHT = 8;
	private static final float RAIN_FOG_START_OFFSET = -160.0F;
	private static final float RAIN_FOG_END_OFFSET = -256.0F;
	private float rainFogMultiplier;

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		Biome biome = (Biome)clientLevel.getBiome(blockPos).value();
		float g = deltaTracker.getGameTimeDeltaTicks();
		boolean bl = biome.hasPrecipitation();
		float h = Mth.clamp((clientLevel.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(blockPos) - 8.0F) / 7.0F, 0.0F, 1.0F);
		float i = clientLevel.getRainLevel(deltaTracker.getGameTimeDeltaPartialTick(false)) * h * (bl ? 1.0F : 0.5F);
		this.rainFogMultiplier = this.rainFogMultiplier + (i - this.rainFogMultiplier) * g * 0.2F;
		fogData.environmentalStart = this.rainFogMultiplier * -160.0F;
		fogData.environmentalEnd = 1024.0F + -256.0F * this.rainFogMultiplier;
		fogData.skyEnd = f;
		fogData.cloudEnd = Minecraft.getInstance().options.cloudRange().get() * 16;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.ATMOSPHERIC;
	}
}
