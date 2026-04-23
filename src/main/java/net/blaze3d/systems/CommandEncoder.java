package net.blaze3d.systems;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface CommandEncoder {
	RenderPass createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt);

	RenderPass createRenderPass(
		Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, @Nullable GpuTextureView gpuTextureView2, OptionalDouble optionalDouble
	);

	default RenderPass createRenderPass(Supplier<String> supplier, int framebuffer, boolean hasDepthTexture) {
		throw new UnsupportedOperationException("This command encoder does not support framebuffer-backed render-pass creation.");
	}

	void clearColorTexture(GpuTexture gpuTexture, int i);

	void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d);

	void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d, int j, int k, int l, int m);

	void clearDepthTexture(GpuTexture gpuTexture, double d);

	void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer);

	GpuBuffer.MappedView mapBuffer(GpuBuffer gpuBuffer, boolean bl, boolean bl2);

	GpuBuffer.MappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean bl, boolean bl2);

	void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2);

	void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage);

	void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o, int p);

	void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int j, int k, int l, int m, int n);

	void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j);

	void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j, int k, int l, int m, int n);

	void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture2, int i, int j, int k, int l, int m, int n, int o);

	void applyPipelineState(RenderPipeline renderPipeline);

	void invalidateCachedProgramBinding();

	void presentTexture(GpuTextureView gpuTextureView);

	GpuFence createFence();
}
