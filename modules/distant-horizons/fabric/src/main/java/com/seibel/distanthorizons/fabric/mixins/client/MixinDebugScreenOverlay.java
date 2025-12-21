package com.seibel.distanthorizons.fabric.mixins.client;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MC 1.21.10 uses the DebugScreenEntry system instead of getSystemInformation().
 * DH debug info is registered via DhDebugScreenEntry.register() in AbstractModInitializer.
 * This mixin is kept for compatibility but has no injections for MC 1.21.10.
 */
@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay
{
	// Empty - F3 debug info handled by DhDebugScreenEntry for MC 1.21.10+
}
