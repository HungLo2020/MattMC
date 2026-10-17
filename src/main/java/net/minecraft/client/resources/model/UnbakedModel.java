package net.minecraft.client.resources.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/** Model file vocabulary Java still uses; model files are read by Rust (`assets/models/model.rs`). */
@Environment(EnvType.CLIENT)
public interface UnbakedModel {
	String PARTICLE_TEXTURE_REFERENCE = "particle";

	@Environment(EnvType.CLIENT)
	public static enum GuiLight {
		FRONT("front"),
		SIDE("side");

		private final String name;

		private GuiLight(final String string2) {
			this.name = string2;
		}

		public static UnbakedModel.GuiLight getByName(String string) {
			for (UnbakedModel.GuiLight guiLight : values()) {
				if (guiLight.name.equals(string)) {
					return guiLight;
				}
			}

			throw new IllegalArgumentException("Invalid gui light: " + string);
		}

		public boolean lightLikeBlock() {
			return this == SIDE;
		}
	}
}
