package net.irisshaders.iris.mixin.texture;

import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Mixin(TextureManager.class)
public class MixinTextureManager {
	@Shadow
	@Final
	private ResourceManager resourceManager;

	// MattMC: The lambda$reload$X method targeting is fragile. Instead we use a broader approach.
	// This injects at the end of the reload method to clear PBR textures when textures are reloaded.
	// Note: reload returns CompletableFuture<Void>, so we need CallbackInfoReturnable
	@Inject(method = "reload", at = @At("RETURN"))
	private void iris$onReloadReturn(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		PBRTextureManager.INSTANCE.clear();
	}

	@Inject(method = "dumpAllSheets(Ljava/nio/file/Path;)V", at = @At("RETURN"))
	private void iris$onInnerDumpTextures(Path path, CallbackInfo ci) {
		PBRTextureManager.INSTANCE.dumpTextures(path);
	}

	@Inject(method = "close()V", at = @At("TAIL"), remap = false)
	private void iris$onTailClose(CallbackInfo ci) {
		PBRTextureManager.INSTANCE.close();
	}
}
