package net.minecraft.client.gui.narration;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;

/**
 * Stub interface for narration functionality (disabled).
 */
@Environment(EnvType.CLIENT)
public interface NarrationElementOutput {
	void add(NarratedElementType narratedElementType, Component component);

	NarrationElementOutput nest();
}
