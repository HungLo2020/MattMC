package net.irisshaders.iris.targets.backed;

import net.blaze3d.platform.NativeImage;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.vulkanic.VulkanicAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntSupplier;

public class NativeImageBackedCustomTexture extends DynamicTexture implements TextureAccess {
	public NativeImageBackedCustomTexture(CustomTextureData.PngData textureData) throws IOException {
		super(() -> "PNG Texture", create(textureData.getContent()));
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris custom textures are unavailable while Rust owns whole-frame presentation");
		}

		// By default, images are unblurred and not clamped.

		if (textureData.getFilteringData().shouldBlur()) {
			IrisRenderSystem.setTextureLinearFiltering(getId());
		}

		if (textureData.getFilteringData().shouldClamp()) {
			IrisRenderSystem.setTextureWrapMode2D(getId(), true);
		}
	}

	private int getId() {
		return net.vulkanic.VulkanicCoreAPI.textureId(this.getTexture());
	}

	private static NativeImage create(byte[] content) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(content.length);
		buffer.put(content);
		buffer.flip();

		return NativeImage.read(buffer);
	}

	@Override
	public void upload() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris custom texture uploads are unavailable while Rust owns whole-frame presentation");
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
		return this::getId;
	}
}
