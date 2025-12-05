package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlCommandEncoder implements CommandEncoder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final GlDevice device;
	private final int readFbo;
	private final int drawFbo;
	@Nullable
	private RenderPipeline lastPipeline;
	private boolean inRenderPass;
	@Nullable
	private GlProgram lastProgram;

	protected GlCommandEncoder(GlDevice glDevice) {
		this.device = glDevice;
		this.readFbo = glDevice.directStateAccess().createFrameBufferObject();
		this.drawFbo = glDevice.directStateAccess().createFrameBufferObject();
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
		} else {
			if (optionalDouble.isPresent() && gpuTextureView2 == null) {
				LOGGER.warn("Depth clear value was provided but no depth texture is being used");
			}

			if (gpuTextureView.isClosed()) {
				throw new IllegalStateException("Color texture is closed");
			} else if ((gpuTextureView.texture().usage() & 8) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
			} else if (gpuTextureView.texture().getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported as an attachment");
			} else {
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
				this.device.debugLabels().pushDebugGroup(supplier);
				int i = ((GlTexture)gpuTextureView.texture()).getFbo(this.device.directStateAccess(), gpuTextureView2 == null ? null : gpuTextureView2.texture());
				GlStateManager._glBindFramebuffer(36160, i);
				int j = 0;
				if (optionalInt.isPresent()) {
					int k = optionalInt.getAsInt();
					GL11.glClearColor(ARGB.redFloat(k), ARGB.greenFloat(k), ARGB.blueFloat(k), ARGB.alphaFloat(k));
					j |= 16384;
				}

				if (gpuTextureView2 != null && optionalDouble.isPresent()) {
					GL11.glClearDepth(optionalDouble.getAsDouble());
					j |= 256;
				}

				if (j != 0) {
					GlStateManager._disableScissorTest();
					GlStateManager._depthMask(true);
					GlStateManager._colorMask(true, true, true, true);
					GlStateManager._clear(j);
				}

				GlStateManager._viewport(0, 0, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0));
				this.lastPipeline = null;
				return new GlRenderPass(this, gpuTextureView2 != null);
			}
		}
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, ((GlTexture)gpuTexture).id, 0, 0, 36160);
			GL11.glClearColor(ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			GlStateManager._disableScissorTest();
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._clear(16384);
			GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, 0, 0);
			GlStateManager._glBindFramebuffer(36160, 0);
		}
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			this.verifyDepthTexture(gpuTexture2);
			int j = ((GlTexture)gpuTexture).getFbo(this.device.directStateAccess(), gpuTexture2);
			GlStateManager._glBindFramebuffer(36160, j);
			GlStateManager._disableScissorTest();
			GL11.glClearDepth(d);
			GL11.glClearColor(ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			GlStateManager._depthMask(true);
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._clear(16640);
			GlStateManager._glBindFramebuffer(36160, 0);
		}
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d, int j, int k, int l, int m) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			this.verifyDepthTexture(gpuTexture2);
			this.verifyRegion(gpuTexture, j, k, l, m);
			int n = ((GlTexture)gpuTexture).getFbo(this.device.directStateAccess(), gpuTexture2);
			GlStateManager._glBindFramebuffer(36160, n);
			GlStateManager._scissorBox(j, k, l, m);
			GlStateManager._enableScissorTest();
			GL11.glClearDepth(d);
			GL11.glClearColor(ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			GlStateManager._depthMask(true);
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._clear(16640);
			GlStateManager._glBindFramebuffer(36160, 0);
		}
	}

	private void verifyRegion(GpuTexture gpuTexture, int i, int j, int k, int l) {
		if (i < 0 || i >= gpuTexture.getWidth(0)) {
			throw new IllegalArgumentException("regionX should not be outside of the texture");
		} else if (j < 0 || j >= gpuTexture.getHeight(0)) {
			throw new IllegalArgumentException("regionY should not be outside of the texture");
		} else if (k <= 0) {
			throw new IllegalArgumentException("regionWidth should be greater than 0");
		} else if (i + k > gpuTexture.getWidth(0)) {
			throw new IllegalArgumentException("regionWidth + regionX should be less than the texture width");
		} else if (l <= 0) {
			throw new IllegalArgumentException("regionHeight should be greater than 0");
		} else if (j + l > gpuTexture.getHeight(0)) {
			throw new IllegalArgumentException("regionWidth + regionX should be less than the texture height");
		}
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyDepthTexture(gpuTexture);
			this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, 0, ((GlTexture)gpuTexture).id, 0, 36160);
			GL11.glDrawBuffer(0);
			GL11.glClearDepth(d);
			GlStateManager._depthMask(true);
			GlStateManager._disableScissorTest();
			GlStateManager._clear(256);
			GL11.glDrawBuffer(36064);
			GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, 0, 0);
			GlStateManager._glBindFramebuffer(36160, 0);
		}
	}

	private void verifyColorTexture(GpuTexture gpuTexture) {
		if (!gpuTexture.getFormat().hasColorAspect()) {
			throw new IllegalStateException("Trying to clear a non-color texture as color");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Color texture is closed");
		} else if ((gpuTexture.usage() & 8) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
		} else if (gpuTexture.getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
		}
	}

	private void verifyDepthTexture(GpuTexture gpuTexture) {
		if (!gpuTexture.getFormat().hasDepthAspect()) {
			throw new IllegalStateException("Trying to clear a non-depth texture as depth");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Depth texture is closed");
		} else if ((gpuTexture.usage() & 8) == 0) {
			throw new IllegalStateException("Depth texture must have USAGE_RENDER_ATTACHMENT");
		} else if (gpuTexture.getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
		}
	}

	@Override
	public void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if ((glBuffer.usage() & 8) == 0) {
				throw new IllegalStateException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
			} else {
				int i = byteBuffer.remaining();
				if (i > gpuBufferSlice.length()) {
					throw new IllegalArgumentException(
						"Cannot write more data than the slice allows (attempting to write " + i + " bytes into a slice of length " + gpuBufferSlice.length() + ")"
					);
				} else if (gpuBufferSlice.length() + gpuBufferSlice.offset() > glBuffer.size()) {
					throw new IllegalArgumentException(
						"Cannot write more data than this buffer can hold (attempting to write "
							+ i
							+ " bytes at offset "
							+ gpuBufferSlice.offset()
							+ " to "
							+ glBuffer.size()
							+ " size buffer)"
					);
				} else {
					this.device.directStateAccess().bufferSubData(glBuffer.handle, gpuBufferSlice.offset(), byteBuffer, glBuffer.usage());
				}
			}
		}
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBuffer gpuBuffer, boolean bl, boolean bl2) {
		return this.mapBuffer(gpuBuffer.slice(), bl, bl2);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean bl, boolean bl2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if (!bl && !bl2) {
				throw new IllegalArgumentException("At least read or write must be true");
			} else if (bl && (glBuffer.usage() & 1) == 0) {
				throw new IllegalStateException("Buffer is not readable");
			} else if (bl2 && (glBuffer.usage() & 2) == 0) {
				throw new IllegalStateException("Buffer is not writable");
			} else if (gpuBufferSlice.offset() + gpuBufferSlice.length() > glBuffer.size()) {
				throw new IllegalArgumentException(
					"Cannot map more data than this buffer can hold (attempting to map "
						+ gpuBufferSlice.length()
						+ " bytes at offset "
						+ gpuBufferSlice.offset()
						+ " from "
						+ glBuffer.size()
						+ " size buffer)"
				);
			} else {
				int i = 0;
				if (bl) {
					i |= 1;
				}

				if (bl2) {
					i |= 34;
				}

				return this.device.getBufferStorage().mapBuffer(this.device.directStateAccess(), glBuffer, gpuBufferSlice.offset(), gpuBufferSlice.length(), i);
			}
		}
	}

	@Override
	public void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Source buffer already closed");
			} else if ((glBuffer.usage() & 16) == 0) {
				throw new IllegalStateException("Source buffer needs USAGE_COPY_SRC to be a source for a copy");
			} else {
				GlBuffer glBuffer2 = (GlBuffer)gpuBufferSlice2.buffer();
				if (glBuffer2.closed) {
					throw new IllegalStateException("Target buffer already closed");
				} else if ((glBuffer2.usage() & 8) == 0) {
					throw new IllegalStateException("Target buffer needs USAGE_COPY_DST to be a destination for a copy");
				} else if (gpuBufferSlice.length() != gpuBufferSlice2.length()) {
					throw new IllegalArgumentException(
						"Cannot copy from slice of size " + gpuBufferSlice.length() + " to slice of size " + gpuBufferSlice2.length() + ", they must be equal"
					);
				} else if (gpuBufferSlice.offset() + gpuBufferSlice.length() > glBuffer.size()) {
					throw new IllegalArgumentException(
						"Cannot copy more data than the source buffer holds (attempting to copy "
							+ gpuBufferSlice.length()
							+ " bytes at offset "
							+ gpuBufferSlice.offset()
							+ " from "
							+ glBuffer.size()
							+ " size buffer)"
					);
				} else if (gpuBufferSlice2.offset() + gpuBufferSlice2.length() > glBuffer2.size()) {
					throw new IllegalArgumentException(
						"Cannot copy more data than the target buffer can hold (attempting to copy "
							+ gpuBufferSlice2.length()
							+ " bytes at offset "
							+ gpuBufferSlice2.offset()
							+ " to "
							+ glBuffer2.size()
							+ " size buffer)"
					);
				} else {
					this.device
						.directStateAccess()
						.copyBufferSubData(glBuffer.handle, glBuffer2.handle, gpuBufferSlice.offset(), gpuBufferSlice2.offset(), gpuBufferSlice.length());
				}
			}
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage) {
		int i = gpuTexture.getWidth(0);
		int j = gpuTexture.getHeight(0);
		if (nativeImage.getWidth() != i || nativeImage.getHeight() != j) {
			throw new IllegalArgumentException(
				"Cannot replace texture of size " + i + "x" + j + " with image of size " + nativeImage.getWidth() + "x" + nativeImage.getHeight()
			);
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Destination texture is closed");
		} else if ((gpuTexture.usage() & 1) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
		} else {
			this.writeToTexture(gpuTexture, nativeImage, 0, 0, 0, 0, i, j, 0, 0);
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o, int p) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (o + m > nativeImage.getWidth() || p + n > nativeImage.getHeight()) {
				throw new IllegalArgumentException(
					"Copy source ("
						+ nativeImage.getWidth()
						+ "x"
						+ nativeImage.getHeight()
						+ ") is not large enough to read a rectangle of "
						+ m
						+ "x"
						+ n
						+ " from "
						+ o
						+ "x"
						+ p
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
				int q;
				if ((gpuTexture.usage() & 16) != 0) {
					q = GlConst.CUBEMAP_TARGETS[j % 6];
					GL11.glBindTexture(34067, ((GlTexture)gpuTexture).id);
				} else {
					q = 3553;
					GlStateManager._bindTexture(((GlTexture)gpuTexture).id);
				}

				GlStateManager._pixelStore(3314, nativeImage.getWidth());
				GlStateManager._pixelStore(3316, o);
				GlStateManager._pixelStore(3315, p);
				GlStateManager._pixelStore(3317, nativeImage.format().components());
				GlStateManager._texSubImage2D(q, i, k, l, m, n, GlConst.toGl(nativeImage.format()), 5121, nativeImage.getPointer());
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int j, int k, int l, int m, int n) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (m * n * format.components() > byteBuffer.remaining()) {
				throw new IllegalArgumentException(
					"Copy would overrun the source buffer (remaining length of " + byteBuffer.remaining() + ", but copy is " + m + "x" + n + " of format " + format + ")"
				);
			} else if (k + m > gpuTexture.getWidth(i) || l + n > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ m
						+ "x"
						+ n
						+ " at "
						+ k
						+ "x"
						+ l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 1) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
			} else if (j >= gpuTexture.getDepthOrLayers()) {
				throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + gpuTexture.getDepthOrLayers());
			} else {
				int o;
				if ((gpuTexture.usage() & 16) != 0) {
					o = GlConst.CUBEMAP_TARGETS[j % 6];
					GL11.glBindTexture(34067, ((GlTexture)gpuTexture).id);
				} else {
					o = 3553;
					GlStateManager._bindTexture(((GlTexture)gpuTexture).id);
				}

				GlStateManager._pixelStore(3314, m);
				GlStateManager._pixelStore(3316, 0);
				GlStateManager._pixelStore(3315, 0);
				GlStateManager._pixelStore(3317, format.components());
				GlStateManager._texSubImage2D(o, i, k, l, m, n, GlConst.toGl(format), 5121, byteBuffer);
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
			this.copyTextureToBuffer(gpuTexture, gpuBuffer, i, runnable, j, 0, 0, gpuTexture.getWidth(j), gpuTexture.getHeight(j));
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
				GlStateManager.clearGlErrors();
				this.device.directStateAccess().bindFrameBufferTextures(this.readFbo, ((GlTexture)gpuTexture).glId(), 0, j, 36008);
				GlStateManager._glBindBuffer(35051, ((GlBuffer)gpuBuffer).handle);
				GlStateManager._pixelStore(3330, m);
				GlStateManager._readPixels(k, l, m, n, GlConst.toGlExternalId(gpuTexture.getFormat()), GlConst.toGlType(gpuTexture.getFormat()), i);
				RenderSystem.queueFencedTask(runnable);
				GlStateManager._glFramebufferTexture2D(36008, 36064, 3553, 0, j);
				GlStateManager._glBindFramebuffer(36008, 0);
				GlStateManager._glBindBuffer(35051, 0);
				int o = GlStateManager._getError();
				if (o != 0) {
					throw new IllegalStateException("Couldn't perform copyTobuffer for texture " + gpuTexture.getLabel() + ": GL error " + o);
				}
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
				GlStateManager.clearGlErrors();
				GlStateManager._disableScissorTest();
				boolean bl = gpuTexture.getFormat().hasDepthAspect();
				int p = ((GlTexture)gpuTexture).glId();
				int q = ((GlTexture)gpuTexture2).glId();
				this.device.directStateAccess().bindFrameBufferTextures(this.readFbo, bl ? 0 : p, bl ? p : 0, 0, 0);
				this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, bl ? 0 : q, bl ? q : 0, 0, 0);
				this.device.directStateAccess().blitFrameBuffers(this.readFbo, this.drawFbo, l, m, n, o, j, k, n, o, bl ? 256 : 16384, 9728);
				int r = GlStateManager._getError();
				if (r != 0) {
					throw new IllegalStateException(
						"Couldn't perform copyToTexture for texture " + gpuTexture.getLabel() + " to " + gpuTexture2.getLabel() + ": GL error " + r
					);
				}
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
			GlStateManager._disableScissorTest();
			GlStateManager._viewport(0, 0, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0));
			GlStateManager._depthMask(true);
			GlStateManager._colorMask(true, true, true, true);
			this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, ((GlTexture)gpuTextureView.texture()).glId(), 0, 0, 0);
			this.device
				.directStateAccess()
				.blitFrameBuffers(
					this.drawFbo, 0, 0, 0, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0), 0, 0, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0), 16384, 9728
				);
		}
	}

	@Override
	public GpuFence createFence() {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			return new GlFence();
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
							GL32.glBindBufferRange(35345, i, ((GlBuffer)gpuBufferSlice.buffer()).handle, gpuBufferSlice.offset(), gpuBufferSlice.length());
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
					GL32.glDrawElementsInstancedBaseVertex(
						GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, l, i
					);
				} else {
					GL31.glDrawElementsInstanced(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, l);
				}
			} else if (i > 0) {
				GL32.glDrawElementsBaseVertex(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes, i);
			} else {
				GlStateManager._drawElements(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), k, GlConst.toGl(indexType), (long)j * indexType.bytes);
			}
		} else if (l > 1) {
			GL31.glDrawArraysInstanced(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), i, k, l);
		} else {
			GlStateManager._drawArrays(GlConst.toGl(glRenderPipeline.info().getVertexFormatMode()), i, k);
		}
	}

	private boolean trySetup(GlRenderPass glRenderPass, Collection<String> collection) {
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
						GL32.glBindBufferRange(35345, var39, ((GlBuffer)gpuBufferSlice2.buffer()).handle, gpuBufferSlice2.offset(), gpuBufferSlice2.length());
					}
					break;
				case Uniform.Utb(int var41, int var42, TextureFormat var43, int var59):
					int var44 = var59;
					if (bl || bl2) {
						GlStateManager._glUniform1i(var41, var42);
					}

					GlStateManager._activeTexture(33984 + var42);
					GL11C.glBindTexture(35882, var44);
					if (bl2) {
						GpuBufferSlice gpuBufferSlice3 = (GpuBufferSlice)glRenderPass.uniforms.get(string2);
						GL31.glTexBuffer(35882, GlConst.toGlInternalId(var43), ((GlBuffer)gpuBufferSlice3.buffer()).handle);
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
						GL11.glBindTexture(34067, glTexture.id);
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

		return true;
	}

	private void applyPipelineState(RenderPipeline renderPipeline) {
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
		this.inRenderPass = false;
		GlStateManager._glBindFramebuffer(36160, 0);
		this.device.debugLabels().popDebugGroup();
	}

	protected GlDevice getDevice() {
		return this.device;
	}
}
