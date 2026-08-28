package net.minecraft.client.renderer;

import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.sodium.client.util.sorting.VertexSorters;
import net.sodium.client.util.sorting.VertexSortingExtended;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface MultiBufferSource {
	static MultiBufferSource.BufferSource immediate(ByteBufferBuilder byteBufferBuilder) {
		return immediateWithBuffers(Object2ObjectSortedMaps.<RenderType, ByteBufferBuilder>emptyMap(), byteBufferBuilder);
	}

	static MultiBufferSource.BufferSource immediateWithBuffers(SequencedMap<RenderType, ByteBufferBuilder> sequencedMap, ByteBufferBuilder byteBufferBuilder) {
		return new MultiBufferSource.BufferSource(byteBufferBuilder, sequencedMap);
	}

	VertexConsumer getBuffer(RenderType renderType);

	@Environment(EnvType.CLIENT)
	public static class BufferSource implements MultiBufferSource {
		protected final ByteBufferBuilder sharedBuffer;
		protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
		protected final Map<RenderType, BufferBuilder> startedBuilders = new HashMap();
		@Nullable
		protected RenderType lastSharedType;

		protected BufferSource(ByteBufferBuilder byteBufferBuilder, SequencedMap<RenderType, ByteBufferBuilder> sequencedMap) {
			this.sharedBuffer = byteBufferBuilder;
			this.fixedBuffers = sequencedMap;
		}

		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Java Vulkan buffer-source rendering is unavailable on selected Vulkan");
			}
			BufferBuilder bufferBuilder = (BufferBuilder)this.startedBuilders.get(renderType);
			if (bufferBuilder != null && !renderType.canConsolidateConsecutiveGeometry()) {
				this.endBatch(renderType, bufferBuilder);
				bufferBuilder = null;
			}

			if (bufferBuilder != null) {
				return bufferBuilder;
			} else {
				ByteBufferBuilder byteBufferBuilder = (ByteBufferBuilder)this.fixedBuffers.get(renderType);
				if (byteBufferBuilder != null) {
					// Iris: From MixinBufferSource - skip extension when not rendering level
					net.irisshaders.iris.vertices.ImmediateState.skipExtension.set(!net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel);
					bufferBuilder = new BufferBuilder(byteBufferBuilder, renderType.mode(), renderType.format());
					net.irisshaders.iris.vertices.ImmediateState.skipExtension.set(false);
				} else {
					if (this.lastSharedType != null) {
						this.endBatch(this.lastSharedType);
					}

					// Iris: From MixinBufferSource - skip extension when not rendering level
					net.irisshaders.iris.vertices.ImmediateState.skipExtension.set(!net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel);
					bufferBuilder = new BufferBuilder(this.sharedBuffer, renderType.mode(), renderType.format());
					net.irisshaders.iris.vertices.ImmediateState.skipExtension.set(false);
					this.lastSharedType = renderType;
				}

				this.startedBuilders.put(renderType, bufferBuilder);
				return bufferBuilder;
			}
		}

		public void endLastBatch() {
			if (this.lastSharedType != null) {
				this.endBatch(this.lastSharedType);
				this.lastSharedType = null;
			}
		}

		public void endBatch() {
			this.endLastBatch();

			for (RenderType renderType : this.fixedBuffers.keySet()) {
				this.endBatch(renderType);
			}
		}

		public void endBatch(RenderType renderType) {
			BufferBuilder bufferBuilder = (BufferBuilder)this.startedBuilders.remove(renderType);
			if (bufferBuilder != null) {
				this.endBatch(renderType, bufferBuilder);
			}
		}

		private void endBatch(RenderType renderType, BufferBuilder bufferBuilder) {
			MeshData meshData = bufferBuilder.build();
			if (meshData != null) {
				if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
					// A compatibility builder can outlive the ownership handoff. Retire
					// its copied mesh without reopening Java Vulkan draw submission.
					meshData.close();
					if (renderType.equals(this.lastSharedType)) {
						this.lastSharedType = null;
					}
					return;
				}
				if (renderType.sortOnUpload()) {
					ByteBufferBuilder byteBufferBuilder = (ByteBufferBuilder)this.fixedBuffers.getOrDefault(renderType, this.sharedBuffer);
					
					// Sodium: Use accelerated sorting if available (merged from MultiBufferSourceMixin)
					VertexSorting sorting = net.vulkanic.VulkanicAPI.getProjectionType().vertexSorting();
					if (sorting instanceof VertexSortingExtended sortingExtended) {
						sodium$acceleratedSort(meshData, byteBufferBuilder, sortingExtended);
					} else {
						meshData.sortQuads(byteBufferBuilder, sorting);
					}
				}

				// Iris: From MixinBufferSource - disable extended vertex format when not rendering level
				boolean iris$notRenderingLevel = !net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel;
				if (iris$notRenderingLevel) {
					net.irisshaders.iris.vertices.ImmediateState.renderWithExtendedVertexFormat = false;
				}
				
				renderType.draw(meshData);
				
				// Iris: From MixinBufferSource - restore extended vertex format
				if (iris$notRenderingLevel) {
					net.irisshaders.iris.vertices.ImmediateState.renderWithExtendedVertexFormat = true;
				}
			}

			if (renderType.equals(this.lastSharedType)) {
				this.lastSharedType = null;
			}
		}

		// Sodium: Accelerated sorting (merged from MultiBufferSourceMixin)
		private static final int VERTICES_PER_QUAD = 6;

		private static void sodium$acceleratedSort(MeshData meshData, ByteBufferBuilder bufferBuilder, VertexSortingExtended sorting) {
			final var drawState = meshData.drawState();

			if (drawState.mode() != VertexFormat.Mode.QUADS) {
				// Only quad lists can be sorted.
				return;
			}

			var sortedPrimitiveIds = VertexSorters.sort(meshData.vertexBuffer(), drawState.vertexCount(), drawState.format().getVertexSize(), sorting);
			var sortedIndexBuffer = sodium$buildSortedIndexBuffer(meshData, bufferBuilder, sortedPrimitiveIds);
			meshData.indexBuffer = sortedIndexBuffer; // Direct field access - indexBuffer is now public
		}

		private static ByteBufferBuilder.Result sodium$buildSortedIndexBuffer(MeshData meshData, ByteBufferBuilder bufferBuilder, int[] primitiveIds) {
			final var indexType = meshData.drawState().indexType();
			final var ptr = bufferBuilder.reserve((primitiveIds.length * VERTICES_PER_QUAD) * indexType.bytes);

			if (indexType == VertexFormat.IndexType.SHORT) {
				sodium$writeIndexBufferShort(ptr, primitiveIds);
			} else if (indexType == VertexFormat.IndexType.INT) {
				sodium$writeIndexBufferInt(ptr, primitiveIds);
			} else {
				throw new UnsupportedOperationException();
			}

			return bufferBuilder.build();
		}

		private static void sodium$writeIndexBufferInt(long ptr, int[] primitiveIds) {
			for (int primitiveId : primitiveIds) {
				org.lwjgl.system.MemoryUtil.memPutInt(ptr +  0L, (primitiveId * 4) + 0);
				org.lwjgl.system.MemoryUtil.memPutInt(ptr +  4L, (primitiveId * 4) + 1);
				org.lwjgl.system.MemoryUtil.memPutInt(ptr +  8L, (primitiveId * 4) + 2);
				org.lwjgl.system.MemoryUtil.memPutInt(ptr + 12L, (primitiveId * 4) + 2);
				org.lwjgl.system.MemoryUtil.memPutInt(ptr + 16L, (primitiveId * 4) + 3);
				org.lwjgl.system.MemoryUtil.memPutInt(ptr + 20L, (primitiveId * 4) + 0);
				ptr += 24L;
			}
		}

		private static void sodium$writeIndexBufferShort(long ptr, int[] primitiveIds) {
			for (int primitiveId : primitiveIds) {
				org.lwjgl.system.MemoryUtil.memPutShort(ptr +  0L, (short) ((primitiveId * 4) + 0));
				org.lwjgl.system.MemoryUtil.memPutShort(ptr +  2L, (short) ((primitiveId * 4) + 1));
				org.lwjgl.system.MemoryUtil.memPutShort(ptr +  4L, (short) ((primitiveId * 4) + 2));
				org.lwjgl.system.MemoryUtil.memPutShort(ptr +  6L, (short) ((primitiveId * 4) + 2));
				org.lwjgl.system.MemoryUtil.memPutShort(ptr +  8L, (short) ((primitiveId * 4) + 3));
				org.lwjgl.system.MemoryUtil.memPutShort(ptr + 10L, (short) ((primitiveId * 4) + 0));
				ptr += 12L;
			}
		}
	}
}
