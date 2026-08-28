package net.irisshaders.iris.targets;

import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.vulkanic.VulkanicAPI;
import org.joml.Vector2i;

import java.nio.ByteBuffer;

public class RenderTarget {
	private static final ByteBuffer NULL_BUFFER = null;
	private final InternalTextureFormat internalFormat;
	private final PixelFormat format;
	private final PixelType type;
	private final int mainTexture;
	private final int altTexture;
	private int width;
	private int height;
	private boolean isValid;
	private String name;

	public RenderTarget(Builder builder) {
		this.isValid = true;

		this.name = builder.name;
		this.internalFormat = builder.internalFormat;
		this.format = builder.format;
		this.type = builder.type;

		this.width = builder.width;
		this.height = builder.height;


		this.mainTexture = requireJavaTextureAllocation();
		this.altTexture = requireJavaTextureAllocation();

		boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
		setupTexture(mainTexture, builder.width, builder.height, !isPixelFormatInteger, false);
		setupTexture(altTexture, builder.width, builder.height, !isPixelFormatInteger, true);

		// Clean up after ourselves
		// This is strictly defensive to ensure that other buggy code doesn't tamper with our textures
		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindTexture2D(ctx, 0);
	}

	private static int requireJavaTextureAllocation() {
		if (VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris render-target allocation is unavailable on the Rust Vulkan route");
		}
		return net.irisshaders.iris.gl.IrisRenderSystem.createTextureId();
	}

	public static Builder builder() {
		return new Builder();
	}

	private void setupTexture(int texture, int width, int height, boolean allowsLinear, boolean alt) {
		resizeTexture(texture, width, height, alt);

		if (allowsLinear) {
			IrisRenderSystem.setTextureLinearFiltering(texture);
		} else {
			IrisRenderSystem.setTextureNearestFiltering(texture);
		}
		IrisRenderSystem.setTextureWrapMode2D(texture, true);
	}

	private void resizeTexture(int texture, int width, int height, boolean alt) {
		IrisRenderSystem.texImage2D(texture, 0, internalFormat.getGlFormat(), width, height, 0, format.getGlFormat(), type.getGlFormat(), NULL_BUFFER);

		if (name != null) {
			GLDebug.nameObject(VulkanicAPI.GL_TEXTURE, texture, name + " " + (alt ? "alt" : "main"));
		}
	}

	void resize(Vector2i textureScaleOverride) {
		this.resize(textureScaleOverride.x, textureScaleOverride.y);
	}

	// Package private, call CompositeRenderTargets#resizeIfNeeded instead.
	void resize(int width, int height) {
		requireValid();

		this.width = width;
		this.height = height;

		resizeTexture(mainTexture, width, height, false);

		resizeTexture(altTexture, width, height, true);
	}

	public InternalTextureFormat getInternalFormat() {
		return internalFormat;
	}

	public int getMainTexture() {
		requireValid();

		return mainTexture;
	}

	public int getAltTexture() {
		requireValid();

		return altTexture;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void destroy() {
		requireValid();
		isValid = false;

		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(mainTexture);
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(altTexture);
	}

	private void requireValid() {
		if (!isValid) {
			throw new IllegalStateException("Attempted to use a deleted composite render target");
		}
	}

	public static class Builder {
		private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
		private int width = 0;
		private int height = 0;
		private PixelFormat format = PixelFormat.RGBA;
		private PixelType type = PixelType.UNSIGNED_BYTE;
		private String name = null;

		private Builder() {
			// No-op
		}

		public Builder setName(String name) {
			this.name = name;

			return this;
		}

		public Builder setInternalFormat(InternalTextureFormat format) {
			this.internalFormat = format;

			return this;
		}

		public Builder setDimensions(int width, int height) {
			if (width <= 0) {
				throw new IllegalArgumentException("Width must be greater than zero");
			}

			if (height <= 0) {
				throw new IllegalArgumentException("Height must be greater than zero");
			}

			this.width = width;
			this.height = height;

			return this;
		}

		public Builder setPixelFormat(PixelFormat pixelFormat) {
			this.format = pixelFormat;

			return this;
		}

		public Builder setPixelType(PixelType pixelType) {
			this.type = pixelType;

			return this;
		}

		public RenderTarget build() {
			return new RenderTarget(this);
		}
	}
}
