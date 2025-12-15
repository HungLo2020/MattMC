package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * Syncs IrisConfig from Options AFTER Options has been loaded from options.txt.
 * This ensures IrisConfig has the correct shader pack name from the config file.
 */
@Mixin(Options.class)
public class MixinOptions_LoadedSync {
	@Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("RETURN"))
	private void iris$afterOptionsLoaded(Minecraft minecraft, File file, CallbackInfo ci) {
		// Options has now loaded from options.txt
		// Sync IrisConfig to get the loaded shader pack name
		Iris.syncConfigFromOptions();
	}
}
