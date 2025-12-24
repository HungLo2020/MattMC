package net.minecraft.client.gui.narration;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Stub interface for narration functionality (disabled).
 */
@Environment(EnvType.CLIENT)
public interface NarrationSupplier {
	void updateNarration(NarrationElementOutput narrationElementOutput);
}
