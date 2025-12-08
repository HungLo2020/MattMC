package net.minecraft.client.gui.narration;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Stub class for narration functionality (disabled).
 */
@Environment(EnvType.CLIENT)
public class NarrationThunk<T extends NarratableEntry> {
	public static final NarrationThunk<?> EMPTY = new NarrationThunk<>(null);

	public NarrationThunk(T entry) {
		// Narration disabled
	}

	public void updateNarration(NarrationElementOutput narrationElementOutput) {
		// Narration disabled
	}
}
