package net.irisshaders.iris.targets.backed;

import net.blaze3d.opengl.GlStateManager;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.TextureUploadHelper;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class SingleColorTexture extends GlResource {
	public SingleColorTexture(int red, int green, int blue, int alpha) {
		super(IrisRenderSystem.createTexture(VulkanicAPI.GL_TEXTURE_2D));
		ByteBuffer pixel = BufferUtils.createByteBuffer(4);
		pixel.put((byte) red);
		pixel.put((byte) green);
		pixel.put((byte) blue);
		pixel.put((byte) alpha);
		pixel.position(0);

		int texture = getGlId();

		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, "single color (" + red + ", " + green + "," + blue + "," + alpha + ")");

		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_LINEAR);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_REPEAT);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_REPEAT);

		TextureUploadHelper.resetTextureUploadState();
		IrisRenderSystem.texImage2D(texture, VulkanicAPI.GL_TEXTURE_2D, 0, VulkanicAPI.GL_RGBA, 1, 1, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_BYTE, pixel);
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		GlStateManager._deleteTexture(getGlId());
	}
}
