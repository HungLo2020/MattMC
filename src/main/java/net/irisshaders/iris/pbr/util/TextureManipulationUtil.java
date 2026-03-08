package net.irisshaders.iris.pbr.util;

import net.blaze3d.opengl.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.VulkanicAPI;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
		if (colorFillFBO == -1) {
			colorFillFBO = VulkanicAPI.createFramebuffer(VulkanicAPI.getImmediateContext());
		}

		int previousFramebufferId = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER_BINDING);
		float[] previousClearColor = new float[4];
		IrisRenderSystem.getFloatv(VulkanicAPI.GL_COLOR_CLEAR_VALUE, previousClearColor);
		int previousTextureId = VulkanicAPI.getInteger(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_BINDING_2D);
		int[] previousViewport = new int[4];
		IrisRenderSystem.getIntegerv(VulkanicAPI.GL_VIEWPORT, previousViewport);

		net.irisshaders.iris.gl.IrisRenderSystem.bindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, colorFillFBO);
		IrisRenderSystem.clearColor(
			(rgba >> 24 & 0xFF) / 255.0f,
			(rgba >> 16 & 0xFF) / 255.0f,
			(rgba >> 8 & 0xFF) / 255.0f,
			(rgba & 0xFF) / 255.0f
		);
		VulkanicAPI.bindTexture2D(VulkanicAPI.getImmediateContext(), textureId);
		for (int level = 0; level <= maxLevel; ++level) {
			int width = VulkanicAPI.getTextureLevelParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_WIDTH);
			int height = VulkanicAPI.getTextureLevelParameter(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_HEIGHT);
			VulkanicAPI.setDynamicViewport(VulkanicAPI.getImmediateContext(), 0, 0, width, height);
			VulkanicAPI.framebufferColorAttachment0Texture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, textureId, level);
			VulkanicAPI.clearBuffersWithMacosWorkaround(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_COLOR_BUFFER_BIT);
			VulkanicAPI.framebufferColorAttachment0Texture2D(VulkanicAPI.getImmediateContext(), VulkanicAPI.GL_FRAMEBUFFER, 0, level);
		}

		net.irisshaders.iris.gl.IrisRenderSystem.bindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, previousFramebufferId);
		IrisRenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
		VulkanicAPI.bindTexture2D(VulkanicAPI.getImmediateContext(), previousTextureId);
		VulkanicAPI.setDynamicViewport(VulkanicAPI.getImmediateContext(), previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}
}
