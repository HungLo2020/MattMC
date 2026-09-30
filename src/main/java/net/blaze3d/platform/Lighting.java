package net.blaze3d.platform;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Vanilla's diffuse-lighting UBO selector. Rust VulkanicGAL derives world,
 * item and GUI lighting from copied gameplay state, so these calls carry no
 * GPU work; they remain as the stable vanilla call surface.
 */
@Environment(EnvType.CLIENT)
public class Lighting implements AutoCloseable {
	public void updateLevel(boolean bl) {
	}

	public void setupFor(Lighting.Entry entry) {
	}

	public void close() {
	}

	@Environment(EnvType.CLIENT)
	public static enum Entry {
		LEVEL,
		LEVEL_UPRIGHT,
		ITEMS_FLAT,
		ITEMS_3D,
		ITEMS_3D_UPRIGHT,
		ENTITY_IN_UI,
		PLAYER_SKIN
    }
}
