package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface UnbakedModel {
	String PARTICLE_TEXTURE_REFERENCE = "particle";

	@Nullable
	default Boolean ambientOcclusion() {
		return null;
	}

	@Nullable
	default UnbakedModel.GuiLight guiLight() {
		return null;
	}

	@Nullable
	default ItemTransforms transforms() {
		return null;
	}

	default TextureSlots.Data textureSlots() {
		return TextureSlots.Data.EMPTY;
	}

	@Nullable
	default UnbakedGeometry geometry() {
		return null;
	}

	@Nullable
	default ResourceLocation parent() {
		return null;
	}

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
