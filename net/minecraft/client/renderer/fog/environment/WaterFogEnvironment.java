package net.minecraft.client.renderer.fog.environment;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WaterFogEnvironment extends FogEnvironment {
	private static final int WATER_FOG_DISTANCE = 96;
	private static final float BIOME_FOG_TRANSITION_TIME = 5000.0F;
	private static int targetBiomeFog = -1;
	private static int previousBiomeFog = -1;
	private static long biomeChangedTime = -1L;

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
		fogData.environmentalStart = -8.0F;
		fogData.environmentalEnd = 96.0F;
		if (entity instanceof LocalPlayer localPlayer) {
			fogData.environmentalEnd = fogData.environmentalEnd * Math.max(0.25F, localPlayer.getWaterVision());
			if (clientLevel.getBiome(blockPos).is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
				fogData.environmentalEnd *= 0.85F;
			}
		}

		fogData.skyEnd = fogData.environmentalEnd;
		fogData.cloudEnd = fogData.environmentalEnd;
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.WATER;
	}

	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		long l = Util.getMillis();
		int j = ((Biome)clientLevel.getBiome(camera.getBlockPosition()).value()).getWaterFogColor();
		if (biomeChangedTime < 0L) {
			targetBiomeFog = j;
			previousBiomeFog = j;
			biomeChangedTime = l;
		}

		float g = Mth.clamp((float)(l - biomeChangedTime) / 5000.0F, 0.0F, 1.0F);
		int k = ARGB.lerp(g, previousBiomeFog, targetBiomeFog);
		if (targetBiomeFog != j) {
			targetBiomeFog = j;
			previousBiomeFog = k;
			biomeChangedTime = l;
		}

		return k;
	}

	@Override
	public void onNotApplicable() {
		biomeChangedTime = -1L;
	}
}
