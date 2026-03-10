package net.irisshaders.iris.pbr.util;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIntegerQuery;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
		CommandContext ctx = VulkanicAPI.getCommandContext();
		if (colorFillFBO == -1) {
			colorFillFBO = VulkanicAPI.createFramebuffer(ctx);
		}

		int previousFramebufferId = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.FRAMEBUFFER_BINDING);
		float[] previousClearColor = new float[4];
		IrisRenderSystem.getFloatv(VulkanicAPI.GL_COLOR_CLEAR_VALUE, previousClearColor);
		int previousTextureId = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		int[] previousViewport = new int[4];
		IrisRenderSystem.getIntegerv(VulkanicAPI.GL_VIEWPORT, previousViewport);

		VulkanicAPI.bindFramebuffer(ctx, colorFillFBO);
		IrisRenderSystem.clearColor(
			(rgba >> 24 & 0xFF) / 255.0f,
			(rgba >> 16 & 0xFF) / 255.0f,
			(rgba >> 8 & 0xFF) / 255.0f,
			(rgba & 0xFF) / 255.0f
		);
		VulkanicAPI.bindTexture2D(ctx, textureId);
		for (int level = 0; level <= maxLevel; ++level) {
			int width = VulkanicAPI.getTextureLevelParameter(ctx, VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_WIDTH);
			int height = VulkanicAPI.getTextureLevelParameter(ctx, VulkanicAPI.GL_TEXTURE_2D, level, VulkanicAPI.GL_TEXTURE_HEIGHT);
			VulkanicAPI.setDynamicViewport(ctx, 0, 0, width, height);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, textureId, level);
			VulkanicAPI.clearBuffersWithMacosWorkaround(ctx, VulkanicAPI.GL_COLOR_BUFFER_BIT);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, 0, level);
		}

		VulkanicAPI.bindFramebuffer(ctx, previousFramebufferId);
		IrisRenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
		VulkanicAPI.bindTexture2D(ctx, previousTextureId);
		VulkanicAPI.setDynamicViewport(ctx, previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}
}
