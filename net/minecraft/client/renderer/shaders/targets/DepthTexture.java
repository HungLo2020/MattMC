package net.minecraft.client.renderer.shaders.targets;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.renderer.shaders.gl.GlResource;
import net.minecraft.client.renderer.shaders.texture.DepthBufferFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

/**
 * Depth texture class following IRIS 1.21.9 structure.
 * Source: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/targets/DepthTexture.java
 */
public class DepthTexture extends GlResource {
	public DepthTexture(String name, int width, int height, DepthBufferFormat format) {
		super(GlStateManager._genTexture());
		int texture = getGlId();

		resize(width, height, format);

		// Set texture parameters
		GlStateManager._bindTexture(texture);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
		GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);

		GlStateManager._bindTexture(0);
	}

	void resize(int width, int height, DepthBufferFormat format) {
		GlStateManager._bindTexture(getTextureId());
		GL11C.glTexImage2D(
			GL11C.GL_TEXTURE_2D,
			0,
			format.getGlInternalFormat(),
			width,
			height,
			0,
			format.getGlType(),
			format.getGlFormat(),
			0L);
	}

	public int getTextureId() {
		return getGlId();
	}

	@Override
	protected void destroyInternal() {
		GlStateManager._deleteTexture(getGlId());
	}
}
