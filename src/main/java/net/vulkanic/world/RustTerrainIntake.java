package net.vulkanic.world;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.bridge.VulkanicGalBridge;

/**
 * Rust decoding of a section layer's compact Sodium vertices into the world
 * mesh vertex ABI ({@code worldrender/terrain/intake.rs}): segment normals,
 * colour/AO/light, copied-atlas UVs, canonical block identity and mid-block
 * words, with the static-terrain audit's fault injections. Render thread only.
 */
final class RustTerrainIntake {
	static final int FAULT_INVERTED_AO = 1;
	static final int FAULT_DOUBLED_FACE_SHADE = 2;
	static final int FAULT_SWAPPED_BLOCK_SKY_LIGHT = 4;
	static final int FAULT_INVERTED_NORMAL = 8;
	static final int FAULT_WRONG_TOP_FACE_SHADE = 16;

	private static final MethodHandle DECODE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_decode_compact_vertices",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final long PARAMS_BYTES = 32;
	private static final long STATS_BYTES = 56;

	/** Decoded vertices in the {@code FfiWorldMeshVertex} layout, and their receipts. */
	record DecodedVertices(MemorySegment vertices, int count, float minX, float minY, float minZ, float maxX,
			float maxY, float maxZ, float minU, float minV, float maxU, float maxV, float minAo, float maxAo,
			int separateAoVertices, boolean aoContractValid) {
		private static final VulkanicGalBridge.Struct LAYOUT = VulkanicGalBridge.Struct.WORLD_MESH_VERTEX;

		/** The vertices as Java records (for consumers not yet reading the ABI bytes). */
		List<VulkanicGalBridge.WorldMeshVertexRecord> records() {
			List<VulkanicGalBridge.WorldMeshVertexRecord> records = new ArrayList<>(this.count);
			long stride = LAYOUT.byteSize();
			for (int index = 0; index < this.count; index++) {
				long base = index * stride;
				records.add(new VulkanicGalBridge.WorldMeshVertexRecord(
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(4)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(5)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(6)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(7)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(8)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(9)),
					this.vertices.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(10)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(11)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(12)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(13)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(1)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(2)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(3)),
					this.vertices.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(14))));
			}
			return records;
		}
	}

	private RustTerrainIntake() {
	}

	static DecodedVertices decode(ByteBuffer vertexData, int vertexStride, boolean separateAo, int atlasWidth,
			int atlasHeight, int midBlockOffset, int faultBits, int[] segments, int[] metadata, int metadataStride,
			int vertexCount) {
		MemorySegment buffer = MemorySegment.ofBuffer(vertexData);
		VulkanicGalBridge.Struct layout = VulkanicGalBridge.Struct.WORLD_MESH_VERTEX;
		// The decoded vertices live as long as the asset that references them.
		MemorySegment vertices = Arena.ofAuto().allocate(Math.max(1L, vertexCount * layout.byteSize()), 8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment params = arena.allocate(PARAMS_BYTES, 8);
			params.set(ValueLayout.JAVA_INT, 0, vertexStride);
			params.set(ValueLayout.JAVA_INT, 4, separateAo ? 1 : 0);
			params.set(ValueLayout.JAVA_INT, 8, atlasWidth);
			params.set(ValueLayout.JAVA_INT, 12, atlasHeight);
			params.set(ValueLayout.JAVA_INT, 16, midBlockOffset);
			params.set(ValueLayout.JAVA_INT, 20, faultBits);
			params.set(ValueLayout.JAVA_INT, 24, metadataStride);
			params.set(ValueLayout.JAVA_INT, 28, 0);
			MemorySegment segmentInts = arena.allocateFrom(ValueLayout.JAVA_INT, segments);
			MemorySegment metadataInts = arena.allocateFrom(ValueLayout.JAVA_INT, metadata);
			MemorySegment stats = arena.allocate(STATS_BYTES, 8);
			int decoded;
			try {
				decoded = (int) DECODE.invokeExact(buffer, (long) vertexData.remaining(), params, segmentInts,
					segments.length, metadataInts, metadata.length, vertices, vertexCount, stats);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust terrain vertex decode failed", throwable);
			}
			if (decoded != vertexCount) {
				throw new IllegalArgumentException("Rust terrain vertex decode rejected the section mesh (" + decoded
					+ " of " + vertexCount + " vertices)");
			}
			return new DecodedVertices(vertices, vertexCount,
				stats.get(ValueLayout.JAVA_FLOAT, 0), stats.get(ValueLayout.JAVA_FLOAT, 4),
				stats.get(ValueLayout.JAVA_FLOAT, 8), stats.get(ValueLayout.JAVA_FLOAT, 12),
				stats.get(ValueLayout.JAVA_FLOAT, 16), stats.get(ValueLayout.JAVA_FLOAT, 20),
				stats.get(ValueLayout.JAVA_FLOAT, 24), stats.get(ValueLayout.JAVA_FLOAT, 28),
				stats.get(ValueLayout.JAVA_FLOAT, 32), stats.get(ValueLayout.JAVA_FLOAT, 36),
				stats.get(ValueLayout.JAVA_FLOAT, 40), stats.get(ValueLayout.JAVA_FLOAT, 44),
				stats.get(ValueLayout.JAVA_INT, 48), stats.get(ValueLayout.JAVA_INT, 52) != 0);
		}
	}
}
