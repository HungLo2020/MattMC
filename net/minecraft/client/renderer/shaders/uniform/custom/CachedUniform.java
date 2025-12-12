// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.custom;

/**
 * Interface for cached uniform values that can be pushed to GPU.
 * 
 * Based on IRIS's CachedUniform class
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/custom/cached/CachedUniform.java
 */
public interface CachedUniform {
	/**
	 * Updates the cached value.
	 */
	void update();

	/**
	 * Pushes the value to the GPU if it has changed.
	 * 
	 * @param location The uniform location
	 */
	void pushIfChanged(int location);
}
