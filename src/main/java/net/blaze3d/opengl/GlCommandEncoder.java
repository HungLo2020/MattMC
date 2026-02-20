package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlCommandEncoder implements CommandEncoder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final GlDevice device;
	@Nullable
	private RenderPipeline lastPipeline;
	private boolean inRenderPass;
	@Nullable
	public GlProgram lastProgram; // Made public for Sodium shader rendering integration
	
	// Iris: From MixinGlCommandEncoder - Shadow rendering state and program tracking
	private int iris$tempFBO;
	private java.util.List<net.irisshaders.iris.pipeline.programs.IrisProgram> iris$programsToClear = new java.util.ArrayList<>();
	private static GlRenderPass iris$lastPass;

	protected GlCommandEncoder(GlDevice glDevice) {
		this.device = glDevice;
		// FBOs are managed by GlDevice (and therefore accessible to OpenGLBackend)
		// so that OpenGLBackend can implement clear/copy/present directly without
		// calling back into GlCommandEncoder (which would create a circular chain).
	}

	@Override
	public RenderPass createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt) {
		return this.createRenderPass(supplier, gpuTextureView, optionalInt, null, OptionalDouble.empty());
	}

	@Override
	public RenderPass createRenderPass(
		Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, @Nullable GpuTextureView gpuTextureView2, OptionalDouble optionalDouble
	) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		}

		// Encoder-level validation (same checks as before, kept here so that the
		// error messages are consistent whether the caller uses the Blaze3D or
		// Vulkanic entry points).
		if (optionalDouble.isPresent() && gpuTextureView2 == null) {
			LOGGER.warn("Depth clear value was provided but no depth texture is being used");
		}
		if (gpuTextureView.isClosed()) {
			throw new IllegalStateException("Color texture is closed");
		} else if ((gpuTextureView.texture().usage() & 8) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
		} else if (gpuTextureView.texture().getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported as an attachment");
		}
		if (gpuTextureView2 != null) {
			if (gpuTextureView2.isClosed()) {
				throw new IllegalStateException("Depth texture is closed");
			}
			if ((gpuTextureView2.texture().usage() & 8) == 0) {
				throw new IllegalStateException("Depth texture must have USAGE_RENDER_ATTACHMENT");
			}
			if (gpuTextureView2.texture().getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported as an attachment");
			}
		}

		this.inRenderPass = true;
		this.lastPipeline = null;

		// Delegate all GL work (FBO lookup, bind, clear, viewport, Iris hooks) to
		// VulkanicAPI → OpenGLBackend.createVulkanicRenderPass().
		// For the Vulkan backend this becomes vkCmdBeginRenderPass().
		return (RenderPass) VulkanicAPI.createVulkanicRenderPass(
				VulkanicAPI.getImmediateContext(),
				supplier,
				(net.vulkanic.resources.VulkanicTextureView) gpuTextureView,
				optionalInt,
				gpuTextureView2 != null ? (net.vulkanic.resources.VulkanicTextureView) gpuTextureView2 : null,
				optionalDouble);
	}

	/**
	 * Called by {@link net.vulkanic.backends.opengl.OpenGLBackend} during render pass
	 * creation when the Iris shadow-rendering or safe-multiply state prevents the normal
	 * {@code glBindFramebuffer} call.  Stores the FBO id so that {@link #trySetup} can
	 * restore it before the first draw command.
	 */
	public void setIrisTempFbo(int fbo) {
		this.iris$tempFBO = fbo;
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		}
		// Delegate to Vulkanic — GL implementation is now in OpenGLBackend.clearColorTexture().
		VulkanicAPI.clearColorTexture(VulkanicAPI.getImmediateContext(),
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, i);
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		}
		VulkanicAPI.clearColorAndDepthTextures(VulkanicAPI.getImmediateContext(),
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, i,
				(net.vulkanic.resources.VulkanicTexture) gpuTexture2, d);
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d, int j, int k, int l, int m) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		}
		VulkanicAPI.clearColorAndDepthTextures(VulkanicAPI.getImmediateContext(),
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, i,
				(net.vulkanic.resources.VulkanicTexture) gpuTexture2, d, j, k, l, m);
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		}
		// Delegate to Vulkanic — GL implementation is now in OpenGLBackend.clearDepthTexture().
		VulkanicAPI.clearDepthTexture(VulkanicAPI.getImmediateContext(),
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, d);
	}

	@Override
	public void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		}
		// Delegate to Vulkanic — GL implementation is in OpenGLBackend.writeToBuffer().
		// Validation (closed, USAGE_COPY_DST, size bounds) moves to OpenGLBackend.
		net.vulkanic.resources.VulkanicBufferSlice slice = new net.vulkanic.resources.VulkanicBufferSlice(
				(net.vulkanic.resources.VulkanicBuffer) gpuBufferSlice.buffer(),
				gpuBufferSlice.offset(), gpuBufferSlice.length());
		VulkanicAPI.writeToBuffer(VulkanicAPI.getImmediateContext(), slice, byteBuffer);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBuffer gpuBuffer, boolean bl, boolean bl2) {
		return this.mapBuffer(gpuBuffer.slice(), bl, bl2);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean bl, boolean bl2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		}
		// Delegate to Vulkanic — validation + GL mapping logic now lives in
		// OpenGLBackend.mapBuffer(), which calls BufferStorage.mapBuffer() directly.
		// GpuBuffer.MappedView extends VulkanicMapView so the cast is safe.
		net.vulkanic.resources.VulkanicBufferSlice vkSlice = new net.vulkanic.resources.VulkanicBufferSlice(
			(net.vulkanic.resources.VulkanicBuffer) gpuBufferSlice.buffer(),
			gpuBufferSlice.offset(), gpuBufferSlice.length());
		return (GpuBuffer.MappedView) VulkanicAPI.mapBuffer(VulkanicAPI.getImmediateContext(), vkSlice, bl, bl2);
	}

	@Override
	public void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			// Delegate to VulkanicAPI — GlCommandEncoder is a thin facade for buffer copies.
			// GpuBufferSlice → VulkanicBufferSlice: GlBuffer implements VulkanicBuffer; safe cast.
			net.vulkanic.resources.VulkanicBufferSlice src = new net.vulkanic.resources.VulkanicBufferSlice(
					(net.vulkanic.resources.VulkanicBuffer) gpuBufferSlice.buffer(),
					gpuBufferSlice.offset(), gpuBufferSlice.length());
			net.vulkanic.resources.VulkanicBufferSlice dst = new net.vulkanic.resources.VulkanicBufferSlice(
					(net.vulkanic.resources.VulkanicBuffer) gpuBufferSlice2.buffer(),
					gpuBufferSlice2.offset(), gpuBufferSlice2.length());
			net.vulkanic.VulkanicAPI.copyVulkanicBuffers(net.vulkanic.VulkanicAPI.getImmediateContext(), src, dst);
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage) {
		int w = gpuTexture.getWidth(0);
		int h = gpuTexture.getHeight(0);
		if (nativeImage.getWidth() != w || nativeImage.getHeight() != h) {
			throw new IllegalArgumentException(
				"Cannot replace texture of size " + w + "x" + h + " with image of size " + nativeImage.getWidth() + "x" + nativeImage.getHeight()
			);
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Destination texture is closed");
		} else if ((gpuTexture.usage() & 1) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
		}
		// Delegate to Vulkanic — GL implementation is in OpenGLBackend.writeToVulkanicTexture().
		VulkanicAPI.writeToVulkanicTexture(VulkanicAPI.getImmediateContext(),
				(net.vulkanic.resources.VulkanicTexture) gpuTexture, nativeImage);
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o, int p) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (o + m > nativeImage.getWidth() || p + n > nativeImage.getHeight()) {
				throw new IllegalArgumentException(
					"Copy source ("
						+ nativeImage.getWidth() + "x" + nativeImage.getHeight()
						+ ") is not large enough to read a rectangle of " + m + "x" + n
						+ " from " + o + "x" + p
				);
			} else if (k + m > gpuTexture.getWidth(i) || l + n > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture (" + m + "x" + n + ") is not large enough to write a rectangle of " + m + "x" + n + " at " + k + "x" + l + " (at mip level " + i + ")"
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 1) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
			} else if (j >= gpuTexture.getDepthOrLayers()) {
				throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + gpuTexture.getDepthOrLayers());
			} else {
				// Delegate to Vulkanic — GL implementation is in OpenGLBackend.writeToVulkanicTexture().
				// param order: (ctx, texture, image, mipLevel, layer, dstX, dstY, srcX, srcY, width, height)
				VulkanicAPI.writeToVulkanicTexture(VulkanicAPI.getImmediateContext(),
						(net.vulkanic.resources.VulkanicTexture) gpuTexture, nativeImage, i, j, k, l, o, p, m, n);
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int j, int k, int l, int m, int n) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (m * n * format.components() > byteBuffer.remaining()) {
				throw new IllegalArgumentException(
					"Copy would overrun the source buffer (remaining length of " + byteBuffer.remaining() + ", but copy is " + m + "x" + n + " of format " + format + ")"
				);
			} else if (k + m > gpuTexture.getWidth(i) || l + n > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture.getWidth(i) + "x" + gpuTexture.getHeight(i)
						+ ") is not large enough to write a rectangle of " + m + "x" + n
						+ " at " + k + "x" + l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 1) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
			} else if (j >= gpuTexture.getDepthOrLayers()) {
				throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + gpuTexture.getDepthOrLayers());
			} else {
				// Delegate to Vulkanic — GL implementation is in OpenGLBackend.writeToVulkanicTexture().
				VulkanicAPI.writeToVulkanicTexture(VulkanicAPI.getImmediateContext(),
						(net.vulkanic.resources.VulkanicTexture) gpuTexture, byteBuffer, format, i, j, k, l, m, n);
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel, must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			// Delegate simple overload to Vulkanic; region defaults to full mip level.
			VulkanicAPI.copyVulkanicTextureToBuffer(VulkanicAPI.getImmediateContext(),
					(net.vulkanic.resources.VulkanicTexture) gpuTexture,
					(net.vulkanic.resources.VulkanicBuffer) gpuBuffer, i, runnable, j);
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j, int k, int l, int m, int n) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (j >= 0 && j < gpuTexture.getMipLevels()) {
			if (gpuTexture.getWidth(j) * gpuTexture.getHeight(j) * gpuTexture.getFormat().pixelSize() + i > gpuBuffer.size()) {
				throw new IllegalArgumentException(
					"Buffer of size "
						+ gpuBuffer.size()
						+ " is not large enough to hold "
						+ m
						+ "x"
						+ n
						+ " pixels ("
						+ gpuTexture.getFormat().pixelSize()
						+ " bytes each) starting from offset "
						+ i
				);
			} else if ((gpuTexture.usage() & 2) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
			} else if ((gpuBuffer.usage() & 8) == 0) {
				throw new IllegalArgumentException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
			} else if (k + m > gpuTexture.getWidth(j) || l + n > gpuTexture.getHeight(j)) {
				throw new IllegalArgumentException(
					"Copy source texture ("
						+ gpuTexture.getWidth(j)
						+ "x"
						+ gpuTexture.getHeight(j)
						+ ") is not large enough to read a rectangle of "
						+ m
						+ "x"
						+ n
						+ " from "
						+ k
						+ ","
						+ l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuBuffer.isClosed()) {
				throw new IllegalStateException("Destination buffer is closed");
			} else if (gpuTexture.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else {
				// Delegate to Vulkanic — GL implementation is in OpenGLBackend.copyVulkanicTextureToBuffer().
				VulkanicAPI.copyVulkanicTextureToBuffer(VulkanicAPI.getImmediateContext(),
						(net.vulkanic.resources.VulkanicTexture) gpuTexture,
						(net.vulkanic.resources.VulkanicBuffer) gpuBuffer, i, runnable, j, k, l, m, n);
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + j + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture2, int i, int j, int k, int l, int m, int n, int o) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels() && i < gpuTexture2.getMipLevels()) {
			if (j + n > gpuTexture2.getWidth(i) || k + o > gpuTexture2.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture2.getWidth(i)
						+ "x"
						+ gpuTexture2.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ j
						+ "x"
						+ k
				);
			} else if (l + n > gpuTexture.getWidth(i) || m + o > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Source texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to read a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ l
						+ "x"
						+ m
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuTexture2.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 2) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
			} else if ((gpuTexture2.usage() & 1) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_DST to be a destination for a copy");
			} else if (gpuTexture.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else if (gpuTexture2.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else {
				// Delegate to Vulkanic — GL implementation is in OpenGLBackend.copyVulkanicTextureToTexture().
				VulkanicAPI.copyVulkanicTextureToTexture(VulkanicAPI.getImmediateContext(),
						(net.vulkanic.resources.VulkanicTexture) gpuTexture,
						(net.vulkanic.resources.VulkanicTexture) gpuTexture2, i, j, k, l, m, n, o);
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels() + " and < " + gpuTexture2.getMipLevels());
		}
	}

	@Override
	public void presentTexture(GpuTextureView gpuTextureView) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (!gpuTextureView.texture().getFormat().hasColorAspect()) {
			throw new IllegalStateException("Cannot present a non-color texture!");
		} else if ((gpuTextureView.texture().usage() & 8) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT to presented to the screen");
		} else if (gpuTextureView.texture().getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for presentation");
		} else {
			// Delegate to Vulkanic — GL implementation is in OpenGLBackend.presentVulkanicTexture().
			VulkanicAPI.presentVulkanicTexture(VulkanicAPI.getImmediateContext(),
					(net.vulkanic.resources.VulkanicTextureView) gpuTextureView);
		}
	}

	@Override
	public GpuFence createFence() {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			// Delegate to VulkanicAPI — GlCommandEncoder is a thin facade for fence creation.
			// GlFence implements both GpuFence and VulkanicFence; cast is safe.
			return (GpuFence) net.vulkanic.VulkanicAPI.createFence(net.vulkanic.VulkanicAPI.getImmediateContext());
		}
	}

	protected <T> void executeDrawMultiple(
		GlRenderPass glRenderPass,
		Collection<RenderPass.Draw<T>> collection,
		@Nullable GpuBuffer gpuBuffer,
		@Nullable VertexFormat.IndexType indexType,
		Collection<String> collection2,
		T object
	) {
		if (this.trySetup(glRenderPass, collection2)) {
			if (indexType == null) {
				indexType = VertexFormat.IndexType.SHORT;
			}

			for (RenderPass.Draw<T> draw : collection) {
				VertexFormat.IndexType indexType2 = draw.indexType() == null ? indexType : draw.indexType();
				glRenderPass.setIndexBuffer(draw.indexBuffer() == null ? gpuBuffer : draw.indexBuffer(), indexType2);
				glRenderPass.setVertexBuffer(draw.slot(), draw.vertexBuffer());
				if (GlRenderPass.VALIDATION) {
					if (glRenderPass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (glRenderPass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}

					if (glRenderPass.vertexBuffers[0] == null) {
						throw new IllegalStateException("Missing vertex buffer at slot 0");
					}

					if (glRenderPass.vertexBuffers[0].isClosed()) {
						throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
					}
				}

				BiConsumer<T, RenderPass.UniformUploader> biConsumer = draw.uniformUploaderConsumer();
				if (biConsumer != null) {
					biConsumer.accept(object, (RenderPass.UniformUploader)(string, gpuBufferSlice) -> {
						if (glRenderPass.pipeline.program().getUniform(string) instanceof Uniform.Ubo(int i)) {
							VulkanicAPI.bindUniformBufferRange(VulkanicAPI.getImmediateContext(), 35345, i, ((GlBuffer)gpuBufferSlice.buffer()).handle, gpuBufferSlice.offset(), gpuBufferSlice.length());
						}
					});
				}

				this.drawFromBuffers(glRenderPass, 0, draw.firstIndex(), draw.indexCount(), indexType2, glRenderPass.pipeline, 1);
			}
		}
	}

	protected void executeDraw(GlRenderPass glRenderPass, int i, int j, int k, @Nullable VertexFormat.IndexType indexType, int l) {
		if (this.trySetup(glRenderPass, Collections.emptyList())) {
			if (GlRenderPass.VALIDATION) {
				if (indexType != null) {
					if (glRenderPass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (glRenderPass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}

					if ((glRenderPass.indexBuffer.usage() & 64) == 0) {
						throw new IllegalStateException("Index buffer must have GpuBuffer.USAGE_INDEX!");
					}
				}

				GlRenderPipeline glRenderPipeline = glRenderPass.pipeline;
				if (glRenderPass.vertexBuffers[0] == null && glRenderPipeline != null && !glRenderPipeline.info().getVertexFormat().getElements().isEmpty()) {
					throw new IllegalStateException("Vertex format contains elements but vertex buffer at slot 0 is null");
				}

				if (glRenderPass.vertexBuffers[0] != null && glRenderPass.vertexBuffers[0].isClosed()) {
					throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
				}

				if (glRenderPass.vertexBuffers[0] != null && (glRenderPass.vertexBuffers[0].usage() & 32) == 0) {
					throw new IllegalStateException("Vertex buffer must have GpuBuffer.USAGE_VERTEX!");
				}
			}

			this.drawFromBuffers(glRenderPass, i, j, k, indexType, glRenderPass.pipeline, l);
		}
	}

	private void drawFromBuffers(
		GlRenderPass glRenderPass, int i, int j, int k, @Nullable VertexFormat.IndexType indexType, GlRenderPipeline glRenderPipeline, int l
	) {
		this.device.vertexArrayCache().bindVertexArray(glRenderPipeline.info().getVertexFormat(), (GlBuffer)glRenderPass.vertexBuffers[0]);
		if (indexType != null) {
			GlStateManager._glBindBuffer(34963, ((GlBuffer)glRenderPass.indexBuffer).handle);
			if (l > 1) {
				if (i > 0) {
					VulkanicAPI.drawIndexedInstancedBaseVertex(
						VulkanicAPI.getImmediateContext(), GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, l, i
					);
				} else {
					VulkanicAPI.drawIndexedInstanced(VulkanicAPI.getImmediateContext(), GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, l);
				}
			} else if (i > 0) {
				VulkanicAPI.drawIndexedBaseVertex(VulkanicAPI.getImmediateContext(), GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, i);
			} else {
				GlStateManager._drawElements(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes);
			}
		} else if (l > 1) {
			VulkanicAPI.drawArraysInstanced(VulkanicAPI.getImmediateContext(), GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), i, k, l);
		} else {
			GlStateManager._drawArrays(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), i, k);
		}
	}

	private boolean trySetup(GlRenderPass glRenderPass, Collection<String> collection) {
		// Iris: From MixinGlCommandEncoder - Unlock depth color and handle custom passes
		net.irisshaders.iris.gl.blending.DepthColorStorage.unlockDepthColor();
		
		if (net.irisshaders.iris.vertices.ImmediateState.safeToMultiply && !(glRenderPass.pipeline.program() instanceof net.irisshaders.iris.pipeline.programs.ExtendedShader)) {
			GlStateManager._glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, iris$tempFBO);
		}
		
		iris$lastPass = glRenderPass;
		
		// Handle Iris custom pass
		if (glRenderPass.iris$getCustomPass() != null) {
			this.lastProgram = null;
			
			((net.irisshaders.iris.mixinterface.CustomPass)glRenderPass.iris$getCustomPass()).setupState();
			
			RenderPipeline renderPipeline = glRenderPass.pipeline.info();
			
			if (glRenderPass.isScissorEnabled()) {
				GlStateManager._enableScissorTest();
				GlStateManager._scissorBox(glRenderPass.getScissorX(), glRenderPass.getScissorY(), glRenderPass.getScissorWidth(), glRenderPass.getScissorHeight());
			} else {
				GlStateManager._disableScissorTest();
			}
			
			if (this.lastPipeline != renderPipeline) {
				this.lastPipeline = renderPipeline;
				
				if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
					GlStateManager._enableDepthTest();
					GlStateManager._depthFunc(GlConst.toGl(renderPipeline.getDepthTestFunction()));
				} else {
					GlStateManager._disableDepthTest();
				}
				
				if (renderPipeline.isCull()) {
					GlStateManager._enableCull();
				} else {
					GlStateManager._disableCull();
				}
				
				GlStateManager._polygonMode(1032, GlConst.toGl(renderPipeline.getPolygonMode()));
				GlStateManager._depthMask(renderPipeline.isWriteDepth());
				GlStateManager._colorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
				
				if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
					GlStateManager._disablePolygonOffset();
				} else {
					GlStateManager._polygonOffset(renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
					GlStateManager._enablePolygonOffset();
				}
				
				switch (renderPipeline.getColorLogic()) {
					case NONE:
						GlStateManager._disableColorLogicOp();
						break;
					case OR_REVERSE:
						GlStateManager._enableColorLogicOp();
						GlStateManager._logicOp(5387);
				}
			}
			
			return true;
		}
		
		if (GlRenderPass.VALIDATION) {
			if (glRenderPass.pipeline == null) {
				throw new IllegalStateException("Can't draw without a render pipeline");
			}

			if (glRenderPass.pipeline.program() == GlProgram.INVALID_PROGRAM) {
				throw new IllegalStateException("Pipeline contains invalid shader program");
			}

			for (RenderPipeline.UniformDescription uniformDescription : glRenderPass.pipeline.info().getUniforms()) {
				GpuBufferSlice gpuBufferSlice = (GpuBufferSlice)glRenderPass.uniforms.get(uniformDescription.name());
				if (!collection.contains(uniformDescription.name())) {
					if (gpuBufferSlice == null) {
						throw new IllegalStateException("Missing uniform " + uniformDescription.name() + " (should be " + uniformDescription.type() + ")");
					}

					if (uniformDescription.type() == UniformType.UNIFORM_BUFFER) {
						if (gpuBufferSlice.buffer().isClosed()) {
							throw new IllegalStateException("Uniform buffer " + uniformDescription.name() + " is already closed");
						}

						if ((gpuBufferSlice.buffer().usage() & 128) == 0) {
							throw new IllegalStateException("Uniform buffer " + uniformDescription.name() + " must have GpuBuffer.USAGE_UNIFORM");
						}
					}

					if (uniformDescription.type() == UniformType.TEXEL_BUFFER) {
						if (gpuBufferSlice.offset() != 0 || gpuBufferSlice.length() != gpuBufferSlice.buffer().size()) {
							throw new IllegalStateException("Uniform texel buffers do not support a slice of a buffer, must be entire buffer");
						}

						if (uniformDescription.textureFormat() == null) {
							throw new IllegalStateException("Invalid uniform texel buffer " + uniformDescription.name() + " (missing a texture format)");
						}
					}
				}
			}

			for (Entry<String, Uniform> entry : glRenderPass.pipeline.program().getUniforms().entrySet()) {
				if (entry.getValue() instanceof Uniform.Sampler) {
					String string = (String)entry.getKey();
					GlTextureView glTextureView = (GlTextureView)glRenderPass.samplers.get(string);
					if (glTextureView == null) {
						throw new IllegalStateException("Missing sampler " + string);
					}

					if (glTextureView.isClosed()) {
						throw new IllegalStateException("Sampler " + string + " (" + glTextureView.texture().getLabel() + ") has been closed!");
					}

					if ((glTextureView.texture().usage() & 4) == 0) {
						throw new IllegalStateException("Sampler " + string + " (" + glTextureView.texture().getLabel() + ") must have USAGE_TEXTURE_BINDING!");
					}
				}
			}

			if (glRenderPass.pipeline.info().wantsDepthTexture() && !glRenderPass.hasDepthTexture()) {
				LOGGER.warn("Render pipeline {} wants a depth texture but none was provided - this is probably a bug", glRenderPass.pipeline.info().getLocation());
			}
		} else if (glRenderPass.pipeline == null || glRenderPass.pipeline.program() == GlProgram.INVALID_PROGRAM) {
			return false;
		}

		RenderPipeline renderPipeline = glRenderPass.pipeline.info();
		GlProgram glProgram = glRenderPass.pipeline.program();
		this.applyPipelineState(renderPipeline);
		boolean bl = this.lastProgram != glProgram;
		if (bl) {
			GlStateManager._glUseProgram(glProgram.getProgramId());
			this.lastProgram = glProgram;
		}

		for (Entry<String, Uniform> entry2 : glProgram.getUniforms().entrySet()) {
			String string2 = (String)entry2.getKey();
			boolean bl2 = glRenderPass.dirtyUniforms.contains(string2);
			switch ((Uniform)entry2.getValue()) {
				case Uniform.Ubo(int var61):
					int var39 = var61;
					if (bl2) {
						GpuBufferSlice gpuBufferSlice2 = (GpuBufferSlice)glRenderPass.uniforms.get(string2);
						VulkanicAPI.bindUniformBufferRange(VulkanicAPI.getImmediateContext(), 35345, var39, ((GlBuffer)gpuBufferSlice2.buffer()).handle, gpuBufferSlice2.offset(), gpuBufferSlice2.length());
					}
					break;
				case Uniform.Utb(int var41, int var42, TextureFormat var43, int var59):
					int var44 = var59;
					if (bl || bl2) {
						GlStateManager._glUniform1i(var41, var42);
					}

					GlStateManager._activeTexture(33984 + var42);
					VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 35882, var44);
					if (bl2) {
						GpuBufferSlice gpuBufferSlice3 = (GpuBufferSlice)glRenderPass.uniforms.get(string2);
						VulkanicAPI.texBuffer(VulkanicAPI.getImmediateContext(), 35882, GlConst.toGlInternalId(var43), ((GlBuffer)gpuBufferSlice3.buffer()).handle);
					}
					break;
				case Uniform.Sampler(int glTextureView2, int var51):
					int var46 = var51;
					GlTextureView glTextureView2x = (GlTextureView)glRenderPass.samplers.get(string2);
					if (glTextureView2x == null) {
						break;
					}

					if (bl || bl2) {
						GlStateManager._glUniform1i(glTextureView2, var46);
					}

					GlStateManager._activeTexture(33984 + var46);
					GlTexture glTexture = glTextureView2x.texture();
					int o;
					if ((glTexture.usage() & 16) != 0) {
						o = 34067;
						VulkanicAPI.bindTexture(VulkanicAPI.getImmediateContext(), 34067, glTexture.id);
					} else {
						o = 3553;
						GlStateManager._bindTexture(glTexture.id);
					}

					GlStateManager._texParameter(o, 33084, glTextureView2x.baseMipLevel());
					GlStateManager._texParameter(o, 33085, glTextureView2x.baseMipLevel() + glTextureView2x.mipLevels() - 1);
					glTexture.flushModeChanges(o);
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		glRenderPass.dirtyUniforms.clear();
		if (glRenderPass.isScissorEnabled()) {
			GlStateManager._enableScissorTest();
			GlStateManager._scissorBox(glRenderPass.getScissorX(), glRenderPass.getScissorY(), glRenderPass.getScissorWidth(), glRenderPass.getScissorHeight());
		} else {
			GlStateManager._disableScissorTest();
		}

		// Iris: From MixinGlCommandEncoder - Setup IrisProgram state if needed
		if (glRenderPass.pipeline.program() instanceof net.irisshaders.iris.pipeline.programs.IrisProgram is && !is.iris$isSetUp()) {
			GpuTextureView sam = glRenderPass.samplers.get("Sampler0");
			if (sam != null) {
				RenderSystem.setShaderTexture(0, sam);
			}
			is.iris$setupState();
			iris$programsToClear.add(is);
		}

		return true;
	}

	public void applyPipelineState(RenderPipeline renderPipeline) { // Made public for Sodium shader rendering integration
		if (this.lastPipeline != renderPipeline) {
			this.lastPipeline = renderPipeline;
			if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
				GlStateManager._enableDepthTest();
				GlStateManager._depthFunc(GlConst.toGl(renderPipeline.getDepthTestFunction()));
			} else {
				GlStateManager._disableDepthTest();
			}

			if (renderPipeline.isCull()) {
				GlStateManager._enableCull();
			} else {
				GlStateManager._disableCull();
			}

			if (renderPipeline.getBlendFunction().isPresent()) {
				GlStateManager._enableBlend();
				BlendFunction blendFunction = (BlendFunction)renderPipeline.getBlendFunction().get();
				GlStateManager._blendFuncSeparate(
					GlConst.toGl(blendFunction.sourceColor()),
					GlConst.toGl(blendFunction.destColor()),
					GlConst.toGl(blendFunction.sourceAlpha()),
					GlConst.toGl(blendFunction.destAlpha())
				);
			} else {
				GlStateManager._disableBlend();
			}

			GlStateManager._polygonMode(1032, GlConst.toGl(renderPipeline.getPolygonMode()));
			GlStateManager._depthMask(renderPipeline.isWriteDepth());
			GlStateManager._colorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
			if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
				GlStateManager._disablePolygonOffset();
			} else {
				GlStateManager._polygonOffset(renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
				GlStateManager._enablePolygonOffset();
			}

			switch (renderPipeline.getColorLogic()) {
				case NONE:
					GlStateManager._disableColorLogicOp();
					break;
				case OR_REVERSE:
					GlStateManager._enableColorLogicOp();
					GlStateManager._logicOp(5387);
			}
		}
	}

	public void finishRenderPass() {
		// Iris: Clear IrisProgram state before ending the pass.
		iris$programsToClear.forEach(net.irisshaders.iris.pipeline.programs.IrisProgram::iris$clearState);
		iris$programsToClear.clear();

		this.inRenderPass = false;

		// Delegate FBO unbind and debug-group pop to VulkanicAPI → OpenGLBackend.endRenderPass().
		// For the Vulkan backend this becomes vkCmdEndRenderPass().
		VulkanicAPI.endRenderPass(VulkanicAPI.getImmediateContext());
	}

	protected GlDevice getDevice() {
		return this.device;
	}
}
