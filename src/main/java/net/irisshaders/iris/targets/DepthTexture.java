package net.irisshaders.iris.targets;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.vulkanic.VulkanicAPI;

public class DepthTexture extends GlResource {
	public DepthTexture(String name, int width, int height, DepthBufferFormat format) {
		super(IrisRenderSystem.createTexture2D());
		int texture = getGlId();

		resize(width, height, format);
		GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, name);

		IrisRenderSystem.setTextureNearestFiltering(texture);
		IrisRenderSystem.setTextureWrapMode2D(texture, true);

		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindTexture2D(ctx, 0);
	}

	void resize(int width, int height, DepthBufferFormat format) {
		IrisRenderSystem.texImage2D(getTextureId(), 0, format.getGlInternalFormat(), width, height, 0,
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
