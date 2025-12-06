package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DimensionOrBossFogEnvironment extends AirBasedFogEnvironment {
	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		fogData.environmentalStart = f * 0.05F;
		fogData.environmentalEnd = Math.min(f, 192.0F) * 0.5F;
		fogData.skyEnd = fogData.environmentalEnd;
		fogData.cloudEnd = fogData.environmentalEnd;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.DIMENSION_OR_BOSS;
	}
}
