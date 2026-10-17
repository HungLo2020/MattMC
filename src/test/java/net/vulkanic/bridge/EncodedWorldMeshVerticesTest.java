package net.vulkanic.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Terrain intake rewrites water quads in place (UVs and material type) on the
 * Rust-encoded vertex list before publishing the asset; the list must accept
 * those writes and expose them to readers, the upload copy and equality.
 */
class EncodedWorldMeshVerticesTest {
	private static VulkanicGalBridge.WorldMeshVertexRecord vertex(int seed) {
		return new VulkanicGalBridge.WorldMeshVertexRecord(seed, seed + 0.5F, -seed, 0.25F * seed, 0.125F, 0.75F,
			0.625F, 40 + seed, seed & 1, 0x105, 0x80102040 + seed, 0x7f00, 0x00f000f0, 0x20e020 | (seed << 24));
	}

	private static VulkanicGalBridge.EncodedWorldMeshVertices encoded(Arena arena, int count) {
		var layout = VulkanicGalBridge.Struct.WORLD_MESH_VERTEX;
		var list = new VulkanicGalBridge.EncodedWorldMeshVertices(arena.allocate(count * layout.byteSize(), 8), count);
		for (int index = 0; index < count; index++) {
			list.set(index, vertex(index));
		}
		return list;
	}

	@Test
	void writesAreVisibleToReadersAndEquality() {
		try (Arena arena = Arena.ofConfined()) {
			var list = encoded(arena, 4);
			for (int index = 0; index < 4; index++) {
				assertEquals(vertex(index), list.get(index));
			}
			List<VulkanicGalBridge.WorldMeshVertexRecord> copy = new ArrayList<>(list);
			assertEquals(copy, list);
			assertEquals(encoded(arena, 4), list);
			// A water rewrite: new local UVs and material type in place.
			var water = new VulkanicGalBridge.WorldMeshVertexRecord(0, 0.5F, 0, 0.9F, 0.1F, 0.75F, 0.625F, 40, 3,
				0x105, 0x80102040, 0x7f00, 0x00f000f0, 0x20e020);
			var previous = list.set(0, water);
			assertEquals(vertex(0), previous);
			assertEquals(water, list.get(0));
			assertNotEquals(encoded(arena, 4), list);
		}
	}

	@Test
	void assetRecordsKeepEncodedVerticesWithoutCopying() {
		try (Arena arena = Arena.ofConfined()) {
			var list = encoded(arena, 4);
			var asset = new VulkanicGalBridge.WorldMeshAssetRecord(1L, 1L, 3, 1, list, new byte[12], List.of());
			assertSame(list, asset.vertices());
		}
	}
}
