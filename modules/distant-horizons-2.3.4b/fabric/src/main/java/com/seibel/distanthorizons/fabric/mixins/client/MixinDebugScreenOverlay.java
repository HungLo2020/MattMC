package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay
{
	// MC 1.21.10 refactored the debug screen - getSystemInformation() no longer exists.
	// Instead, we inject into renderLines() which receives the final list of debug strings.
	// The 'bl' parameter is true for the left side (game info) and false for right side (system info).
	// We inject into the right side (system info) to add DH info.
	@Inject(method = "renderLines", at = @At("HEAD"))
	private void addCustomF3(GuiGraphics guiGraphics, List<String> list, boolean bl, CallbackInfo ci)
	{
		// Only add to right side (system information) - bl=false means right side
		if (!bl)
		{
			F3Screen.addStringToDisplay(list);
		}
	}
	
}
