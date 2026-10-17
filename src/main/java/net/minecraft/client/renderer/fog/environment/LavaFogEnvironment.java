package net.minecraft.client.renderer.fog.environment;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.NativeFog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LavaFogEnvironment extends FogEnvironment {
	private static final int COLOR = -6743808;

	@Override
	public int getBaseColor(ClientLevel clientLevel, Camera camera, int i, float f) {
		return -6743808;
	}

	@Override
	public void setupFog(FogData fogData, Entity entity, BlockPos blockPos, ClientLevel clientLevel, float f, DeltaTracker deltaTracker) {
        int flags = entity.isSpectator() ? 1 : 0;
        if (flags == 0 && entity instanceof LivingEntity livingEntity
                && livingEntity.hasEffect(MobEffects.FIRE_RESISTANCE)) flags = 2;
        NativeFog.ranges(fogData, 0, f, 0.0F, 0.0F, flags);
	}

	@Override
	public boolean isApplicable(@Nullable FogType fogType, Entity entity) {
		return fogType == FogType.LAVA;
	}
}
