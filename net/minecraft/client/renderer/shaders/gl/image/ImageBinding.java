// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.image;

import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL42C;

import java.util.function.IntSupplier;

/**
 * Represents a binding of a texture to an image uniform.
 * 
 * Based on IRIS's ImageBinding class
 * Reference: frnsrc/Iris-1.21.9/.../gl/image/ImageBinding.java
 */
public class ImageBinding {
	private final int imageUnit;
	private final int internalFormat;
	private final IntSupplier textureID;

	public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
		this.textureID = textureID;
		this.imageUnit = imageUnit;
		this.internalFormat = internalFormat;
	}

	public void update() {
		// We can assume that image bindings are supported here as either the EXT extension or 4.2 core, as otherwise ImageLimits
		// would report that zero image units are supported.
		IrisRenderSystem.bindImageTexture(imageUnit, textureID.getAsInt(), 0, true, 0, GL42C.GL_READ_WRITE, internalFormat);
	}
}
