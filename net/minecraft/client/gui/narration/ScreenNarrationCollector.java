package net.minecraft.client.gui.narration;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;

/**
 * Stub class for narration functionality (disabled).
 */
@Environment(EnvType.CLIENT)
public class ScreenNarrationCollector {
	public void update(Runnable runnable) {
		// Narration disabled
	}

	public NarrationElementOutput nest() {
		return new NarrationElementOutput() {
			@Override
			public void add(NarratedElementType narratedElementType, Component... components) {
				// Narration disabled
			}

			@Override
			public NarrationElementOutput nest() {
				return this;
			}
		};
	}
}
