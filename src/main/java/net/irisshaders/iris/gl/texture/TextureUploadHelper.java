package net.irisshaders.iris.gl.texture;

import net.vulkanic.VulkanicAPI;

public class TextureUploadHelper {
	private TextureUploadHelper() {
		// no construction
	}

	public static void resetTextureUploadState() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris texture-upload state is unavailable while Rust owns whole-frame presentation");
		}
		// Ensure that the pixel storage mode is in a sane state, otherwise the uploaded texture data will be quite
		// incorrect.
		//
		// It is likely that this also avoids the crashes on AMD that I previously experienced with texture creation.
		//
		// This code is from Canvas: https://github.com/grondag/canvas/commit/f0ab652d7a8b7cc9febf0209bee15cffce9eac83
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH, 0);
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS, 0);
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS, 0);
		VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, 4);
	}
}
