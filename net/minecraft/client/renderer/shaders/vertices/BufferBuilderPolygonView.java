package net.minecraft.client.renderer.shaders.vertices;

import org.lwjgl.system.MemoryUtil;

/**
 * View into a BufferBuilder's quad data for normal/tangent calculation.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/BufferBuilderPolygonView.java
 */
public class BufferBuilderPolygonView implements QuadView, TriView {
	private long pointer;
	private long[] vertexOffsets;
	private int stride;
	private int vertexAmount;
	
	// Standard offsets for BLOCK/TERRAIN format
	// Position: offset 0 (3 floats = 12 bytes)
	// Color: offset 12 (4 bytes = 4 bytes)
	// UV0: offset 16 (2 floats = 8 bytes)
	// UV2: offset 24 (2 shorts = 4 bytes)
	// Normal: offset 28 (4 bytes)
	// Total base: 32 bytes (+ padding + extensions)
	private static final int POSITION_OFFSET = 0;
	private static final int UV0_OFFSET = 16;
	
	public void setup(long pointer, long[] vertexOffsets, int stride, int vertexAmount) {
		this.pointer = pointer;
		this.vertexOffsets = vertexOffsets;
		this.stride = stride;
		this.vertexAmount = vertexAmount;
	}
	
	private long vertexPointer(int vertexIndex) {
		return pointer + vertexOffsets[vertexIndex];
	}

	@Override
	public float x(int vertexIndex) {
		return MemoryUtil.memGetFloat(vertexPointer(vertexIndex) + POSITION_OFFSET);
	}

	@Override
	public float y(int vertexIndex) {
		return MemoryUtil.memGetFloat(vertexPointer(vertexIndex) + POSITION_OFFSET + 4);
	}

	@Override
	public float z(int vertexIndex) {
		return MemoryUtil.memGetFloat(vertexPointer(vertexIndex) + POSITION_OFFSET + 8);
	}

	@Override
	public float u(int vertexIndex) {
		return MemoryUtil.memGetFloat(vertexPointer(vertexIndex) + UV0_OFFSET);
	}

	@Override
	public float v(int vertexIndex) {
		return MemoryUtil.memGetFloat(vertexPointer(vertexIndex) + UV0_OFFSET + 4);
	}
}
