package net.blaze3d.systems;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicGalExecutionRequest;
import net.vulkanic.VulkanicGalV2;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface RenderPass extends AutoCloseable, net.irisshaders.iris.mixinterface.RenderPassInterface {
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

	default boolean supportsExplicitGalV2GraphicsDraw() {
		return false;
	}

	default int explicitGalV2FramebufferId() {
		return 0;
	}

	default VulkanicGalExecutionRequest.ExecutionResult executeExplicitGalV2GraphicsDraw(VulkanicGalV2.ExplicitGraphicsDrawRequest request) {
		return VulkanicGalExecutionRequest.backendFailure(
			request.semanticIdentity(),
			"render pass does not support explicit GAL v2 graphics draws"
		);
	}

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
	
	// Iris compatibility methods
	default void iris$setCustomPass(net.irisshaders.iris.mixinterface.CustomPass pass) {
		// No-op by default - Iris mixin implementation
	}
	
	default net.irisshaders.iris.mixinterface.CustomPass iris$getCustomPass() {
		return null;
	}
}
