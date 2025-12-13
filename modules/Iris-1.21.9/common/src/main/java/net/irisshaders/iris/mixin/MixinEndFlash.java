package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndFlashState.class)
public class MixinEndFlash {
	@Shadow
	private float xAngle;

	@Shadow
	private float yAngle;

	@Shadow
	private long flashSeed;

	private static final float ABOVE_HORIZON_EPS = 1.0F; // degrees above horizon

	// Working on it... - This method is currently disabled
	// The @Local annotations have been removed to prevent crashes on MC 1.21.10
	// If this needs to be re-enabled, use @ModifyVariable or @Inject at HEAD with field access
	//@Inject(method = "calculateFlashParameters", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;randomBetween(Lnet/minecraft/util/RandomSource;FF)F", ordinal = 0), cancellable = true)
	//private void iris$calculateNewAngles(long l, CallbackInfo ci) {
	//	// Implementation would need to recreate the RandomSource from the flash seed
	//}
}
