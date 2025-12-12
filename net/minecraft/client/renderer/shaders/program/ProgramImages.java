// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.renderer.shaders.gl.image.ImageBinding;
import net.minecraft.client.renderer.shaders.gl.image.ImageHolder;
import net.minecraft.client.renderer.shaders.gl.image.ImageLimits;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Manages image bindings for shader programs.
 * 
 * Based on IRIS's ProgramImages class
 * Reference: frnsrc/Iris-1.21.9/.../gl/program/ProgramImages.java
 */
public class ProgramImages {
	private final ImmutableList<ImageBinding> imageBindings;
	private List<GlUniform1iCall> initializer;

	private ProgramImages(ImmutableList<ImageBinding> imageBindings, List<GlUniform1iCall> initializer) {
		this.imageBindings = imageBindings;
		this.initializer = initializer;
	}

	public static Builder builder(int program) {
		return new Builder(program);
	}

	public void update() {
		if (initializer != null) {
			for (GlUniform1iCall call : initializer) {
				GlStateManager._glUniform1i(call.location(), call.value());
			}

			initializer = null;
		}

		for (ImageBinding imageBinding : imageBindings) {
			imageBinding.update();
		}
	}

	public int getActiveImages() {
		return imageBindings.size();
	}

	/**
	 * Simple record for storing uniform1i calls to be executed later.
	 */
	public record GlUniform1iCall(int location, int value) {}

	public static final class Builder implements ImageHolder {
		private final int program;
		private final ImmutableList.Builder<ImageBinding> images;
		private final List<GlUniform1iCall> calls;
		private final int maxImageUnits;
		private int nextImageUnit;

		private Builder(int program) {
			this.program = program;
			this.images = ImmutableList.builder();
			this.calls = new ArrayList<>();
			this.nextImageUnit = 0;
			this.maxImageUnits = ImageLimits.get().getMaxImageUnits();
		}

		@Override
		public boolean hasImage(String name) {
			return GlStateManager._glGetUniformLocation(program, name) != -1;
		}

		@Override
		public void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name) {
			int location = GlStateManager._glGetUniformLocation(program, name);

			if (location == -1) {
				return;
			}

			if (nextImageUnit >= maxImageUnits) {
				if (maxImageUnits == 0) {
					throw new IllegalStateException("Image units are not supported on this platform, but a shader" +
						" program attempted to reference " + name + ".");
				} else {
					throw new IllegalStateException("No more available texture units while activating image " + name + "." +
						" Only " + maxImageUnits + " image units are available.");
				}
			}

			InternalTextureFormat format = internalFormat;
			if (format == InternalTextureFormat.RGBA) {
				// Internal detail of Optifine: Set RGBA8 if RGBA is selected, as RGBA is not valid for images.
				format = InternalTextureFormat.RGBA8;
			}

			images.add(new ImageBinding(nextImageUnit, format.getGlFormat(), textureID));
			calls.add(new GlUniform1iCall(location, nextImageUnit));

			nextImageUnit += 1;
		}

		public ProgramImages build() {
			return new ProgramImages(images.build(), calls);
		}
	}
}
