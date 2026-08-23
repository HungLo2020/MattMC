package net.irisshaders.iris.targets.backed;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.FilterMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCoreAPI;

import java.util.Objects;
import java.util.Random;
import java.util.function.IntSupplier;

public class NativeImageBackedNoiseTexture extends DynamicTexture implements TextureAccess {
	public NativeImageBackedNoiseTexture(int size) {
		super(() -> "Noise / " + size, create(size));
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris noise textures are unavailable while Rust owns whole-frame presentation");
		}
		this.texture.setTextureFilter(FilterMode.LINEAR, false);
	}

	private static NativeImage create(int size) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		Random random = new Random(0);

		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				int color = random.nextInt() | (255 << 24);

				image.setPixel(x, y, color);
			}
		}

		return image;
	}

	@Override
	public void upload() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris noise texture uploads are unavailable while Rust owns whole-frame presentation");
		}
		NativeImage image = Objects.requireNonNull(getPixels());

		VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, image);
	}

	@Override
	public TextureType getType() {
		return TextureType.TEXTURE_2D;
	}

	@Override
	public IntSupplier getTextureId() {
		return () -> VulkanicCoreAPI.textureId(this.getTexture());
	}
}
