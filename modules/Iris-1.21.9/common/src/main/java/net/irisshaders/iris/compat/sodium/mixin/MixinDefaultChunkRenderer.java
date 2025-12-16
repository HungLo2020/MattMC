package net.irisshaders.iris.compat.sodium.mixin;

import net.minecraft.client.renderer.sodium.gui.SodiumGameOptions;
import net.minecraft.client.renderer.chunk.advanced.DefaultChunkRenderer;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DefaultChunkRenderer.class)
public class MixinDefaultChunkRenderer {
	@Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/sodium/gui/SodiumGameOptions$PerformanceSettings;useBlockFaceCulling:Z"), remap = false)
	private boolean iris$disableBlockFaceCullingInShadowPass(SodiumGameOptions.PerformanceSettings instance) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return false;
		return instance.useBlockFaceCulling;
	}

	// TODO IMS: Something about this feels... wrong.
	@ModifyArg(method = "prepareIndexedTessellation", index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/advanced/DefaultChunkRenderer;createRegionTessellation(Lnet/minecraft/client/renderer/gl/advanced/device/CommandList;Lnet/minecraft/client/renderer/chunk/advanced/region/RenderRegion$DeviceResources;Z)Lnet/minecraft/client/renderer/gl/advanced/tessellation/GlTessellation;"), remap = false)
	private boolean doNotSortInShadow(boolean useSharedIndexBuffer) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return false;

		return useSharedIndexBuffer;
	}
}
