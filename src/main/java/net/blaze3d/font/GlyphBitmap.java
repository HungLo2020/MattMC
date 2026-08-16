package net.blaze3d.font;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.GpuTexture;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface GlyphBitmap {
	int getPixelWidth();

	int getPixelHeight();

	void upload(int i, int j, GpuTexture gpuTexture);

	boolean isColored();

	/**
	 * Copies this glyph's source pixels into a CPU-owned font atlas. A provider
	 * that cannot expose source pixels must return {@code false}; callers use
	 * that signal to keep a semantic atlas unavailable instead of reading a GPU
	 * texture back or manufacturing a partial payload.
	 */
	default boolean copyTo(NativeImage nativeImage, int i, int j) {
		return false;
	}

	float getOversample();

	default float getLeft() {
		return this.getBearingLeft();
	}

	default float getRight() {
		return this.getLeft() + this.getPixelWidth() / this.getOversample();
	}

	default float getTop() {
		return 7.0F - this.getBearingTop();
	}

	default float getBottom() {
		return this.getTop() + this.getPixelHeight() / this.getOversample();
	}

	default float getBearingLeft() {
		return 0.0F;
	}

	default float getBearingTop() {
		return 7.0F;
	}
}
