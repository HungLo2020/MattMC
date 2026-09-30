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
			throw new IllegalStateException("Java Vulkan buffer-source rendering is unavailable on selected Vulkan");
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
				// A compatibility builder can outlive the ownership handoff. Retire
				// its copied mesh without reopening Java Vulkan draw submission.
				meshData.close();
				if (renderType.equals(this.lastSharedType)) {
					this.lastSharedType = null;
				}
				return;
			}

			if (renderType.equals(this.lastSharedType)) {
				this.lastSharedType = null;
			}
		}

		// Sodium: Accelerated sorting (merged from MultiBufferSourceMixin)
		private static final int VERTICES_PER_QUAD = 6;

	}
}
