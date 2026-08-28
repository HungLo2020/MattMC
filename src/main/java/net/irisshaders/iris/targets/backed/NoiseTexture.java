package net.irisshaders.iris.targets.backed;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.TextureUploadHelper;
import net.vulkanic.VulkanicAPI;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * An extremely simple noise texture. Each color channel contains a uniform random value from 0 to 255. Essentially just
 * dumps an array of random bytes into a texture and calls it a day, literally could not be any simpler than that.
 */
public class NoiseTexture extends GlResource {
	int width;
	int height;

	public NoiseTexture(int width, int height) {
		super(requireJavaTextureAllocation());
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();

		int texture = getGlId();
		IrisRenderSystem.setTextureLinearFiltering(texture);
		IrisRenderSystem.setTextureWrapMode2D(texture, false);
		IrisRenderSystem.resetTextureLodRangeToZero(texture);
		resize(texture, width, height);

		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, "noise texture");

		VulkanicAPI.bindTexture2D(ctx, 0);
	}

	private static int requireJavaTextureAllocation() {
		if (VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris noise texture allocation is unavailable on the Rust Vulkan route");
		}
		return IrisRenderSystem.createTexture2D();
	}

	void resize(int texture, int width, int height) {
		this.width = width;
		this.height = height;
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();

		ByteBuffer pixels = generateNoise();

		TextureUploadHelper.resetTextureUploadState();

		// Since we're using tightly-packed RGB data, we must use an alignment of 1 byte instead of the usual 4 bytes.
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, 1);
		IrisRenderSystem.texImage2D(texture, 0, VulkanicAPI.GL_RGB, width, height, 0, VulkanicAPI.GL_RGB, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);

		VulkanicAPI.bindTexture2D(ctx, 0);
	}

	private ByteBuffer generateNoise() {
		byte[] pixels = new byte[3 * width * height];

		Random random = new Random(0);
		random.nextBytes(pixels);

		ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length);
		buffer.put(pixels);
		buffer.flip();

		return buffer;
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(getGlId());
	}
}
