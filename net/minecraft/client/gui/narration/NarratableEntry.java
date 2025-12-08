package net.minecraft.client.gui.narration;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Stub interface for narration functionality (disabled).
 */
@Environment(EnvType.CLIENT)
public interface NarratableEntry {
	NarratableEntry.NarrationPriority narrationPriority();

	default void updateNarration(NarrationElementOutput narrationElementOutput) {
		// Narration disabled
	}

	@Environment(EnvType.CLIENT)
	public static enum NarrationPriority {
		NONE,
		HOVERED,
		FOCUSED;

		public boolean isTerminal() {
			return this == FOCUSED;
		}
	}
}
