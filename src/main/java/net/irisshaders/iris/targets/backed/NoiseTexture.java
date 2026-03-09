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
		super(IrisRenderSystem.createTexture(VulkanicAPI.GL_TEXTURE_2D));
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();

		int texture = getGlId();
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_REPEAT);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_REPEAT);

		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LEVEL, 0);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_LOD, 0);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAX_LOD, 0);
		IrisRenderSystem.texParameterf(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_LOD_BIAS, 0.0F);
		resize(texture, width, height);

		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, "noise texture");

		VulkanicAPI.bindTexture2D(ctx, 0);
	}

	void resize(int texture, int width, int height) {
		this.width = width;
		this.height = height;
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();

		ByteBuffer pixels = generateNoise();

		TextureUploadHelper.resetTextureUploadState();

		// Since we're using tightly-packed RGB data, we must use an alignment of 1 byte instead of the usual 4 bytes.
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, 1);
		IrisRenderSystem.texImage2D(texture, VulkanicAPI.GL_TEXTURE_2D, 0, VulkanicAPI.GL_RGB, width, height, 0, VulkanicAPI.GL_RGB, VulkanicAPI.GL_UNSIGNED_BYTE, pixels);

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
