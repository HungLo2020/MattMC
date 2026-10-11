package net.minecraft.client.renderer.block.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * A resolved model's texture slots, as item model APIs pass them along.
 * Rust (`assets/models/resolve.rs`) resolves and reads the slots itself, so
 * Java carries no contents.
 */
@Environment(EnvType.CLIENT)
public final class TextureSlots {
	public static final TextureSlots EMPTY = new TextureSlots();

	private TextureSlots() {
	}
}
