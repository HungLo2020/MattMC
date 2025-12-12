package net.irisshaders.iris.mixin;

import net.irisshaders.iris.mixinterface.BiomeAmbienceInterface;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeAmbientSoundsHandler.class)
public class MixinBiomeAmbientSoundsHandler implements BiomeAmbienceInterface {
	@Shadow
	@Final
	private LocalPlayer player;

	@Unique
	private float constantMoodiness;

	// MattMC: Changed from lambda targeting to tick method injection
	// This calculates the moodiness based on ambient light conditions
	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V", ordinal = 1))
	private void calculateConstantMoodiness(CallbackInfo ci) {
		BlockPos blockPos = this.player.blockPosition();
		int j = this.player.level().getBrightness(LightLayer.SKY, blockPos);
		if (j > 0) {
			this.constantMoodiness -= (float) j / (float) 15 * 0.001F;
		} else {
			this.constantMoodiness -= (float) (this.player.level().getBrightness(LightLayer.BLOCK, blockPos) - 1) / 6000.0F;
		}

		this.constantMoodiness = Mth.clamp(constantMoodiness, 0.0f, 1.0f);
	}

	@Override
	public float getConstantMood() {
		return constantMoodiness;
	}
}
