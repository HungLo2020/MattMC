package net.irisshaders.iris.pbr.util;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIntegerQuery;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
		if (VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris PBR texture mutation is unavailable on the Rust Vulkan route");
		}
		CommandContext ctx = VulkanicAPI.getCommandContext();
		if (colorFillFBO == -1) {
			colorFillFBO = VulkanicAPI.createFramebuffer(ctx);
		}

		int previousFramebufferId = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.FRAMEBUFFER_BINDING);
		float[] previousClearColor = new float[4];
		IrisRenderSystem.getClearColor(previousClearColor);
		int previousTextureId = VulkanicAPI.getInteger(ctx, VulkanicIntegerQuery.TEXTURE_BINDING_2D);
		int[] previousViewport = new int[4];
		IrisRenderSystem.getViewport(previousViewport);

		VulkanicAPI.bindFramebuffer(ctx, colorFillFBO);
		IrisRenderSystem.clearColor(
			(rgba >> 24 & 0xFF) / 255.0f,
			(rgba >> 16 & 0xFF) / 255.0f,
			(rgba >> 8 & 0xFF) / 255.0f,
			(rgba & 0xFF) / 255.0f
		);
		VulkanicAPI.bindTexture2D(ctx, textureId);
		for (int level = 0; level <= maxLevel; ++level) {
			int width = VulkanicAPI.getTexture2DLevelWidth(ctx, level);
			int height = VulkanicAPI.getTexture2DLevelHeight(ctx, level);
			VulkanicAPI.setDynamicViewport(ctx, 0, 0, width, height);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, textureId, level);
			VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, 0, level);
		}

		VulkanicAPI.bindFramebuffer(ctx, previousFramebufferId);
		IrisRenderSystem.clearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
		VulkanicAPI.bindTexture2D(ctx, previousTextureId);
		VulkanicAPI.setDynamicViewport(ctx, previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}
}
