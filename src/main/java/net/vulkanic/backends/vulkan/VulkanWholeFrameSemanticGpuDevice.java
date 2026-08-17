package net.vulkanic.backends.vulkan;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * CPU-only startup device for the private Rust whole-frame route.
 *
 * <p>It models the small amount of ordinary game state constructed before the
 * Rust Vulkan presenter exists (uniform bytes, render-target descriptions and
 * texture metadata), but deliberately owns no GL/Vulkan object, context, or
 * native handle. Rendering, texture upload, render passes and presentation are
 * rejected: those callsites must become VulkanicGAL semantic submissions.</p>
 */
public final class VulkanWholeFrameSemanticGpuDevice implements GpuDevice {
	private static final int MAX_TEXTURE_SIZE = 16_384;
	private static final int UNIFORM_ALIGNMENT = 256;
	private static final String RENDERING_UNAVAILABLE =
		"Rust Vulkan whole-frame startup device is semantic-only; port this callsite to explicit VulkanicGAL semantics.";
	private final CommandEncoder encoder = new SemanticCommandEncoder();

	@Override
	public CommandEncoder createCommandEncoder() {
		return this.encoder;
	}

	@Override
	public GpuTexture createTexture(@Nullable Supplier<String> label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		return this.createTexture(label == null ? null : label.get(), usage, format, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public GpuTexture createTexture(@Nullable String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		if (width < 1 || height < 1 || depthOrLayers < 1 || mipLevels < 1) {
			throw new IllegalArgumentException("semantic texture dimensions and mip levels must be positive");
		}
		return new SemanticTexture(usage, label == null ? "semantic-texture" : label, format, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture) {
		return this.createTextureView(texture, 0, texture.getMipLevels());
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
		if (baseMipLevel < 0 || mipLevels < 1 || baseMipLevel + mipLevels > texture.getMipLevels()) {
			throw new IllegalArgumentException("semantic texture view range is outside its texture");
		}
		return new SemanticTextureView(texture, baseMipLevel, mipLevels);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, int size) {
		return new SemanticBuffer(usage, size);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> label, int usage, ByteBuffer data) {
		SemanticBuffer buffer = new SemanticBuffer(usage, data.remaining());
		buffer.write(0, data.duplicate());
		return buffer;
	}

	@Override public String getImplementationInformation() { return "Rust Vulkan semantic startup device (no Java GPU execution)"; }
	@Override public List<String> getLastDebugMessages() { return List.of(); }
	@Override public boolean isDebuggingEnabled() { return false; }
	@Override public String getVendor() { return "Rust Vulkan"; }
	@Override public String getBackendName() { return "Vulkan"; }
	@Override public String getVersion() { return "semantic-bootstrap"; }
	@Override public String getRenderer() { return "Rust Vulkan whole-frame"; }
	@Override public int getMaxTextureSize() { return MAX_TEXTURE_SIZE; }
	@Override public int getUniformOffsetAlignment() { return UNIFORM_ALIGNMENT; }
	@Override public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, @Nullable BiFunction<ResourceLocation, ShaderType, String> sources) { return () -> false; }
	@Override public void clearPipelineCache() { }
	@Override public List<String> getEnabledExtensions() { return List.of(); }
	@Override public void close() { }

	private static UnsupportedOperationException renderingUnavailable() {
		return new UnsupportedOperationException(RENDERING_UNAVAILABLE);
	}

	private static final class SemanticBuffer extends GpuBuffer {
		private final ByteBuffer bytes;
		private boolean closed;
		SemanticBuffer(int usage, int size) {
			super(usage, size);
			if (size < 0) throw new IllegalArgumentException("semantic buffer size must not be negative");
			this.bytes = ByteBuffer.allocateDirect(size);
		}
		void write(int offset, ByteBuffer source) {
			if (this.closed) throw new IllegalStateException("semantic buffer is closed");
			if (offset < 0 || source.remaining() > this.size() - offset) throw new IllegalArgumentException("semantic buffer write is outside its allocation");
			ByteBuffer target = this.bytes.duplicate(); target.position(offset); target.put(source);
		}
		ByteBuffer range(int offset, int length) {
			if (this.closed) throw new IllegalStateException("semantic buffer is closed");
			if (offset < 0 || length < 0 || length > this.size() - offset) throw new IllegalArgumentException("semantic buffer range is outside its allocation");
			ByteBuffer result = this.bytes.duplicate(); result.position(offset); result.limit(offset + length); return result.slice();
		}
		@Override public boolean isClosed() { return this.closed; }
		@Override public void close() { this.closed = true; }
	}

	private static final class SemanticTexture extends GpuTexture {
		private boolean closed;
		SemanticTexture(int usage, String label, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels) { super(usage, label, format, width, height, depthOrLayers, mipLevels); }
		@Override public void close() { this.closed = true; }
		@Override public boolean isClosed() { return this.closed; }
	}

	private static final class SemanticTextureView extends GpuTextureView {
		private boolean closed;
		SemanticTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) { super(texture, baseMipLevel, mipLevels); }
		@Override public void close() { this.closed = true; }
		@Override public boolean isClosed() { return this.closed; }
	}

	private static final class SemanticCommandEncoder implements CommandEncoder {
		private static SemanticBuffer buffer(GpuBuffer value) {
			if (value instanceof SemanticBuffer buffer) return buffer;
			throw new IllegalArgumentException("semantic startup encoder received a non-semantic buffer");
		}
		@Override public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clear) { throw renderingUnavailable(); }
		@Override public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clear, @Nullable GpuTextureView depth, OptionalDouble clearDepth) { throw renderingUnavailable(); }
		@Override public void clearColorTexture(GpuTexture texture, int color) { throw renderingUnavailable(); }
		@Override public void clearColorAndDepthTextures(GpuTexture color, int clearColor, GpuTexture depth, double clearDepth) { throw renderingUnavailable(); }
		@Override public void clearColorAndDepthTextures(GpuTexture color, int clearColor, GpuTexture depth, double clearDepth, int x, int y, int width, int height) { throw renderingUnavailable(); }
		@Override public void clearDepthTexture(GpuTexture texture, double depth) { throw renderingUnavailable(); }
		@Override public void writeToBuffer(GpuBufferSlice destination, ByteBuffer source) { buffer(destination.buffer()).write(destination.offset(), source.duplicate()); }
		@Override public GpuBuffer.MappedView mapBuffer(GpuBuffer value, boolean read, boolean write) { SemanticBuffer buffer = buffer(value); return new Mapped(buffer.range(0, buffer.size())); }
		@Override public GpuBuffer.MappedView mapBuffer(GpuBufferSlice value, boolean read, boolean write) { return new Mapped(buffer(value.buffer()).range(value.offset(), value.length())); }
		@Override public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice destination) { ByteBuffer bytes = buffer(source.buffer()).range(source.offset(), source.length()); buffer(destination.buffer()).write(destination.offset(), bytes); }
		@Override public void writeToTexture(GpuTexture texture, NativeImage image) { throw renderingUnavailable(); }
		@Override public void writeToTexture(GpuTexture texture, NativeImage image, int a, int b, int c, int d, int e, int f, int g, int h) { throw renderingUnavailable(); }
		@Override public void writeToTexture(GpuTexture texture, ByteBuffer bytes, NativeImage.Format format, int a, int b, int c, int d, int e, int f) { throw renderingUnavailable(); }
		@Override public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int level, Runnable callback, int flags) { throw renderingUnavailable(); }
		@Override public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, int level, Runnable callback, int flags, int a, int b, int c, int d) { throw renderingUnavailable(); }
		@Override public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int a, int b, int c, int d, int e, int f, int g) { throw renderingUnavailable(); }
		@Override public void applyPipelineState(RenderPipeline pipeline) { throw renderingUnavailable(); }
		@Override public void invalidateCachedProgramBinding() { }
		@Override public void presentTexture(GpuTextureView texture) { throw renderingUnavailable(); }
		@Override public GpuFence createFence() { return new GpuFence() { @Override public void close() { } @Override public boolean awaitCompletion(long timeout) { return true; } }; }
	}

	private record Mapped(ByteBuffer data) implements GpuBuffer.MappedView { @Override public void close() { } }
}
