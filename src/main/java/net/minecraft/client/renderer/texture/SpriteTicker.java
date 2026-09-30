package net.minecraft.client.renderer.texture;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface SpriteTicker extends AutoCloseable {
	/** Advances CPU animation state without touching a backend texture. */
	default boolean tickSemantic() {
		return false;
	}

	void close();
}
