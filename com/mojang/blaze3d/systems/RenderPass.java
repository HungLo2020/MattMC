package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface RenderPass extends AutoCloseable {
	void pushDebugGroup(Supplier<String> supplier);

	void popDebugGroup();

	void setPipeline(RenderPipeline renderPipeline);

	void bindSampler(String string, @Nullable GpuTextureView gpuTextureView);

	void setUniform(String string, GpuBuffer gpuBuffer);

	void setUniform(String string, GpuBufferSlice gpuBufferSlice);

	void enableScissor(int i, int j, int k, int l);

	void disableScissor();

	void setVertexBuffer(int i, GpuBuffer gpuBuffer);

	void setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType);

	void drawIndexed(int i, int j, int k, int l);

	<T> void drawMultipleIndexed(
		Collection<RenderPass.Draw<T>> collection,
		@Nullable GpuBuffer gpuBuffer,
		@Nullable VertexFormat.IndexType indexType,
		Collection<String> collection2,
		T object
	);

	void draw(int i, int j);

	void close();

	@Environment(EnvType.CLIENT)
	public record Draw<T>(
		int slot,
		GpuBuffer vertexBuffer,
		@Nullable GpuBuffer indexBuffer,
		@Nullable VertexFormat.IndexType indexType,
		int firstIndex,
		int indexCount,
		@Nullable BiConsumer<T, RenderPass.UniformUploader> uniformUploaderConsumer
	) {
		public Draw(int i, GpuBuffer gpuBuffer, GpuBuffer gpuBuffer2, VertexFormat.IndexType indexType, int j, int k) {
			this(i, gpuBuffer, gpuBuffer2, indexType, j, k, null);
		}
	}

	@Environment(EnvType.CLIENT)
	public interface UniformUploader {
		void upload(String string, GpuBufferSlice gpuBufferSlice);
	}
}
