// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.blending;

/**
 * Per-buffer blend mode override.
 * 
 * Based on IRIS's BufferBlendOverride class
 * Reference: frnsrc/Iris-1.21.9/.../gl/blending/BufferBlendOverride.java
 */
public class BufferBlendOverride {
	private final int drawBuffer;
	private final BlendMode blendMode;

	public BufferBlendOverride(int drawBuffer, BlendMode blendMode) {
		this.drawBuffer = drawBuffer;
		this.blendMode = blendMode;
	}

	public void apply() {
		BlendModeStorage.overrideBufferBlend(this.drawBuffer, this.blendMode);
	}
}
