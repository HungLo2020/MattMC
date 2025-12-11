// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.image;

import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;

import java.util.function.IntSupplier;

/**
 * Interface for managing image bindings in shader programs.
 * 
 * Based on IRIS's ImageHolder interface
 * Reference: frnsrc/Iris-1.21.9/.../gl/image/ImageHolder.java
 */
public interface ImageHolder {
	boolean hasImage(String name);

	void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name);
}
