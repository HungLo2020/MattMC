package net.minecraft.client.renderer;

import net.blaze3d.vertex.ByteBufferBuilder;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

@Environment(EnvType.CLIENT)
public class SectionBufferBuilderPack implements AutoCloseable {
	public static final int TOTAL_BUFFERS_SIZE = Arrays.stream(ChunkSectionLayer.values()).mapToInt(ChunkSectionLayer::bufferSize).sum();
	private final boolean minimal;
	private final Map<ChunkSectionLayer, ByteBufferBuilder> buffers;

	public SectionBufferBuilderPack() {
		this(false);
	}

	/** Creates a bookkeeping-only pack for the Rust-owned Vulkan route. */
	public SectionBufferBuilderPack(boolean minimal) {
		this.minimal = minimal;
		this.buffers = Util.makeEnumMap(
			ChunkSectionLayer.class, chunkSectionLayer -> new ByteBufferBuilder(this.minimal ? 0 : chunkSectionLayer.bufferSize())
		);
	}

	public ByteBufferBuilder buffer(ChunkSectionLayer chunkSectionLayer) {
		return (ByteBufferBuilder)this.buffers.get(chunkSectionLayer);
	}

	public void clearAll() {
		this.buffers.values().forEach(ByteBufferBuilder::clear);
	}

	public void discardAll() {
		this.buffers.values().forEach(ByteBufferBuilder::discard);
	}

	public void close() {
		this.buffers.values().forEach(ByteBufferBuilder::close);
	}
}
