package net.irisshaders.iris.compat.sodium.mixin;

import net.minecraft.client.renderer.gl.advanced.device.CommandList;
import net.minecraft.client.renderer.chunk.advanced.compile.BuilderTaskOutput;
import net.minecraft.client.renderer.chunk.advanced.region.RenderRegion;
import net.minecraft.client.renderer.chunk.advanced.region.RenderRegionManager;
import net.irisshaders.iris.mixinterface.ShadowRenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(RenderRegionManager.class)
public class MixinRenderRegionManager {
	@Redirect(method = "uploadResults(Lnet/minecraft/client/renderer/gl/advanced/device/CommandList;Lnet/minecraft/client/renderer/chunk/advanced/region/RenderRegion;Ljava/util/Collection;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/advanced/region/RenderRegion;clearAllCachedBatches()V"))
	private void iris$forceClear(RenderRegion instance) {
		((ShadowRenderRegion) instance).iris$forceClearAllBatches();
	}
}
