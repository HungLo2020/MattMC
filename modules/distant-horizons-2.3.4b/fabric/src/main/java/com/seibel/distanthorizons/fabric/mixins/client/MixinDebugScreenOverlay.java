package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay
{
	
	// Updated for MC 1.21.10: getSystemInformation() was removed and replaced with render() using DebugScreenDisplayer
	// We inject into render() before the first renderLines() call to add DH's F3 information to the left-side list
	@Inject(method = "render", 
			at = @At(value = "INVOKE", 
					target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V", 
					ordinal = 0),
			locals = LocalCapture.CAPTURE_FAILHARD,
			require = 0)
	private void addCustomF3(GuiGraphics guiGraphics, CallbackInfo ci, 
			java.util.Collection collection, 
			net.minecraft.util.profiling.ProfilerFiller profilerFiller, 
			net.minecraft.world.level.ChunkPos chunkPos,
			List list, List list2,
			java.util.Map map, List list3)
	{
		// Add DH's custom F3 debug information to the left-side list
		F3Screen.addStringToDisplay(list);
	}
	
}
