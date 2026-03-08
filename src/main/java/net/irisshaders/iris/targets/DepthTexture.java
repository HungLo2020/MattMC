package net.irisshaders.iris.targets;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.vulkanic.VulkanicAPI;

public class DepthTexture extends GlResource {
	public DepthTexture(String name, int width, int height, DepthBufferFormat format) {
		super(IrisRenderSystem.createTexture(VulkanicAPI.GL_TEXTURE_2D));
		int texture = getGlId();

		resize(width, height, format);
		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, name);

		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER, VulkanicAPI.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER, VulkanicAPI.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S, VulkanicAPI.GL_CLAMP_TO_EDGE);
		IrisRenderSystem.texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T, VulkanicAPI.GL_CLAMP_TO_EDGE);

		VulkanicAPI.bindTexture2D(VulkanicAPI.getImmediateContext(), 0);
	}

	void resize(int width, int height, DepthBufferFormat format) {
		IrisRenderSystem.texImage2D(getTextureId(), VulkanicAPI.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), width, height, 0,
			format.getGlType(), format.getGlFormat(), null);
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(getGlId());
	}
}
