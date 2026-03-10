package net.irisshaders.iris.targets.backed;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.RenderSystem;
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

		// By default, images are unblurred and not clamped.

		if (textureData.getFilteringData().shouldBlur()) {
			IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR);
			IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
		}

		if (textureData.getFilteringData().shouldClamp()) {
			IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);
			IrisRenderSystem.texParameteri(getId(), VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_CLAMP_TO_EDGE);
		}
	}

	private int getId() {
		return this.texture.iris$getGlId();
	}

	private static NativeImage create(byte[] content) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(content.length);
		buffer.put(content);
		buffer.flip();

		return NativeImage.read(buffer);
	}

	@Override
	public void upload() {
		NativeImage image = Objects.requireNonNull(getPixels());

		RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);
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
