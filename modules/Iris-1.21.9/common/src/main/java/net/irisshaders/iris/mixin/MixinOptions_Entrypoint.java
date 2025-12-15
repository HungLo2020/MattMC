package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 990)
public class MixinOptions_Entrypoint {
	@Unique
	private static boolean iris$initialized;

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V"))
	private void iris$beforeLoadOptions(CallbackInfo ci) {
		if (iris$initialized) {
			return;
		}

		iris$initialized = true;
		new Iris().onEarlyInitialize();
	}
	
	@Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;options:Lnet/minecraft/client/Options;", shift = At.Shift.AFTER))
	private void iris$afterOptionsLoaded(CallbackInfo ci) {
		// Options field has been assigned, sync IrisConfig from loaded values
		Minecraft minecraft = (Minecraft)(Object)this;
		Iris.logger.info("[Mixin] iris$afterOptionsLoaded triggered - calling Iris.syncConfigFromOptions()");
		Iris.syncConfigFromOptions(minecraft.options);
	}
}
