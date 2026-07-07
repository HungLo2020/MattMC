package net.irisshaders.iris.gl.texture;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.vulkanic.VulkanicAPI;

public interface DepthCopyStrategy {
	static DepthCopyStrategy fastest(boolean combinedStencilRequired) {
		// Check whether glCopyImageSubData is available by checking the function directly...
		// Capability version flags can be false even when specific functions are available,
		// because Minecraft requests an OpenGL 3.2 forward compatible context.
		//
		// Perhaps calling GL43.isAvailable would be a different option, but we only need one
		// function, so we just check for that function.
		if (VulkanicAPI.checkFunctionAvailable("glCopyImageSubData")) {
			return new Gl43CopyImage();
		}

		if (combinedStencilRequired) {
			return new Gl30BlitFbCombinedDepthStencil();
		} else {
			return new Gl20CopyTexture();
		}
	}

	static DepthCopyStrategy fastestDepthSnapshot(boolean combinedStencilRequired) {
		if (VulkanicAPI.isVulkanBackendSelected()) {
			return new Gl30BlitFbDepth();
		}

		return fastest(false);
	}

	boolean needsDestFramebuffer();

	/**
	 * Executes the copy. May or may not clobber GL_READ_FRAMEBUFFER and GL_DRAW_FRAMEBUFFER bindings - the caller is
	 * responsible for ensuring that they are restored to sensible values, or that the previous values are not relied
	 * on. The callee is responsible for ensuring that texture bindings are not modified.
	 *
	 * @param destFb The destination framebuffer. If {@link #needsDestFramebuffer()} returns false, then this param
	 *               will not be used, and it can be null.
	 */
	void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height);

	// FB -> T
	class Gl20CopyTexture implements DepthCopyStrategy {
		private Gl20CopyTexture() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return false;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			sourceFb.bindAsReadBuffer();

			int previousTexture = IrisRenderSystem.getBoundTextureOnActiveUnit();

			IrisRenderSystem.copyTexSubImage2D(
				destTexture,
				// level
				0,
				// xoffset, yoffset
				0, 0,
				// x, y
				0, 0,
				// width
				width,
				// height
				height);

			VulkanicAPI.bindTexture2D(VulkanicAPI.getCommandContext(), previousTexture);
		}
	}

	// FB -> FB
	class Gl30BlitFbDepth implements DepthCopyStrategy {
		private Gl30BlitFbDepth() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return true;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			IrisRenderSystem.blitDepthBufferNearest(sourceFb.getId(), destFb.getId(), 0, 0, width, height,
				0, 0, width, height);
		}
	}

	// FB -> FB
	class Gl30BlitFbCombinedDepthStencil implements DepthCopyStrategy {
		private Gl30BlitFbCombinedDepthStencil() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return true;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			IrisRenderSystem.blitDepthAndStencilBuffersNearest(sourceFb.getId(), destFb.getId(), 0, 0, width, height,
				0, 0, width, height);
		}
	}

	// T -> T
	// Fastest
	class Gl43CopyImage implements DepthCopyStrategy {
		private Gl43CopyImage() {
			// private
		}

		@Override
		public boolean needsDestFramebuffer() {
			return false;
		}

		@Override
		public void copy(GlFramebuffer sourceFb, int sourceTexture, GlFramebuffer destFb, int destTexture, int width, int height) {
			IrisRenderSystem.copyImageSubData2D(
				sourceTexture,
				0,
				0,
				0,
				0,
				destTexture,
				0,
				0,
				0,
				0,
				width,
				height,
				1
			);
		}
	}
}
