package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.iris.Iris;
import net.iris.gl.GLDebug;
import net.iris.gl.IrisRenderSystem;
import net.iris.pbr.TextureTracker;
import net.iris.samplers.IrisSamplers;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
	@Inject(method = "initRenderer", at = @At("RETURN"), remap = false)
	private static void iris$onRendererInit(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2, CallbackInfo ci) {
		Iris.duringRenderSystemInit();
		GLDebug.reloadDebugState();
		IrisRenderSystem.initRenderer();
		IrisSamplers.initRenderer();
		Iris.onRenderSystemInit();
	}

	@Inject(method = "setShaderTexture", at = @At(value = "RETURN"))
	private static void _setShaderTexture(int i, GpuTextureView gpuTextureView, CallbackInfo ci) {
		if (gpuTextureView != null) {
			//gpuTexture.setTextureFilter(FilterMode.NEAREST, false);
		}
		TextureTracker.INSTANCE.onSetShaderTexture(i, gpuTextureView);
	}
}
