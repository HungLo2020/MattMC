package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.fog.NativeFog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WaterFogEnvironment extends FogEnvironment {
	private static final int WATER_FOG_DISTANCE = 96;
	private static final float BIOME_FOG_TRANSITION_TIME = 5000.0F;

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
        NativeFog.ranges(fogData, 8, f, 0.0F, 0.0F, 0);
        if (entity instanceof LocalPlayer localPlayer) {
            NativeFog.ranges(fogData, 9, f, localPlayer.getWaterVision(), 0.0F, 0);
            if (clientLevel.getBiome(blockPos).is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
                NativeFog.ranges(fogData, 10, f, 0.0F, 0.0F, 0);
            }
        }
        NativeFog.ranges(fogData, 11, f, 0.0F, 0.0F, 0);
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.WATER;
	}

	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		long l = Util.getMillis();
		int j = ((Biome)clientLevel.getBiome(camera.getBlockPosition()).value()).getWaterFogColor();
        return NativeFog.waterColor(l, j);
	}

	@Override
	public void onNotApplicable() {
        NativeFog.resetWater();
	}
}
