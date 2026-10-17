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
 * Rust decoding and assembly of a section layer's compact Sodium vertices
 * ({@code worldrender/terrain/intake.rs}, {@code assembly.rs}): world-mesh
 * vertices, index bytes, draw ranges, translucent order, mesh key and
 * generation, with the static-terrain audit's fault injections. Render
 * thread only.
 */
final class RustTerrainIntake {
	static final int FAULT_INVERTED_AO = 1;
	static final int FAULT_DOUBLED_FACE_SHADE = 2;
	static final int FAULT_SWAPPED_BLOCK_SKY_LIGHT = 4;
	static final int FAULT_INVERTED_NORMAL = 8;
	static final int FAULT_WRONG_TOP_FACE_SHADE = 16;

	private static final long PARAMS_BYTES = 32;
	private static final long STATS_BYTES = 56;
	private static final MethodHandle ASSEMBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_assemble_layer",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final long ASSEMBLY_PARAMS_BYTES = 96;
	private static final long RECEIPT_BYTES = 144;
	private static final int ASSEMBLY_STAGE_VERTICES = 1;
	private static boolean stagingAnnounced;
	private static final MethodHandle DISCARD_STAGED = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_discard_staged", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
	private static final long OUTPUT_BYTES = 80;
	private static final int SECTION_RECORD_BYTES = 36;
	private static final int SAMPLE_INTS = 6;
	private static final int SAMPLE_LIMIT = 64;
	private static final int ERROR_BYTES = 512;

	/** One built-in water sprite's atlas rectangle. */
	record WaterSprite(int textureId, float u0, float u1, float v0, float v1) {
	}

	/**
	 * One assembled section layer (`worldrender/terrain/assembly.rs`): its
	 * vertices, index bytes and draw ranges, identities and receipts.
	 * `samples` holds six ints per translucent primitive sample.
	 */
	record AssembledLayer(DecodedVertices decoded, byte[] indexBytes,
			List<VulkanicGalBridge.WorldMeshSectionRecord> sections, long meshKey, long meshGeneration, int indexType,
			int assembledIndexCount, int maxIndex, int positiveYSections, int negativeYSections, int horizontalSections,
			boolean translucent, int sourcePrimitives, int nonFluidPrimitives, int waterPrimitives,
			int unsupportedPrimitives, int retainedPrimitives, int omittedPrimitives, int sourceIndices,
			int retainedIndices, int omittedIndices, long sourceHash, long retainedHash, long omittedHash,
			int materialSwitches, int waterStill, int waterFlow, int waterOverlay, int waterTextureSwitches,
			int[] samples) {
	}

	/**
	 * Decodes and assembles one section layer in Rust. `sortedIndices` is the
	 * build sorter's u32 quad order (null or empty when there is none);
	 * `water` holds the still, flow and overlay sprites (null before they are
	 * known).
	 */
	static AssembledLayer assemble(ByteBuffer vertexData, int vertexStride, boolean separateAo, int atlasWidth,
			int atlasHeight, int midBlockOffset, int faultBits, int[] segments, int[] metadata, int metadataStride,
			int vertexCount, ByteBuffer sortedIndices, long sectionPos, int layerOrdinal, long atlasGeneration,
			boolean waterBlockAtlas, WaterSprite[] water, boolean stageVertices) {
		if (stageVertices) {
			AssembledLayer staged = assembleOnce(vertexData, vertexStride, separateAo, atlasWidth, atlasHeight,
				midBlockOffset, faultBits, segments, metadata, metadataStride, vertexCount, sortedIndices, sectionPos,
				layerOrdinal, atlasGeneration, waterBlockAtlas, water, true);
			if (staged != null) {
				if (!stagingAnnounced) {
					stagingAnnounced = true;
					System.out.println("[MattMC terrain] vertex staging active: section vertices stay in Rust");
				}
				return staged;
			}
			// The staging bound was reached: copy the vertices out instead.
		}
		return assembleOnce(vertexData, vertexStride, separateAo, atlasWidth, atlasHeight, midBlockOffset, faultBits,
			segments, metadata, metadataStride, vertexCount, sortedIndices, sectionPos, layerOrdinal, atlasGeneration,
			waterBlockAtlas, water, false);
	}

	/** Drops a layer's staged vertices that will not be (or were already) published. */
	static void discardStaged(long meshKey, long meshGeneration) {
		try {
			DISCARD_STAGED.invokeExact(meshKey, meshGeneration);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust terrain staging discard failed", throwable);
		}
	}

	/** One assembly call; null when staging was requested but not possible. */
	private static AssembledLayer assembleOnce(ByteBuffer vertexData, int vertexStride, boolean separateAo,
			int atlasWidth, int atlasHeight, int midBlockOffset, int faultBits, int[] segments, int[] metadata,
			int metadataStride, int vertexCount, ByteBuffer sortedIndices, long sectionPos, int layerOrdinal,
			long atlasGeneration, boolean waterBlockAtlas, WaterSprite[] water, boolean stageVertices) {
		MemorySegment buffer = MemorySegment.ofBuffer(vertexData.duplicate());
		VulkanicGalBridge.Struct layout = VulkanicGalBridge.Struct.WORLD_MESH_VERTEX;
		MemorySegment vertices = stageVertices
			? MemorySegment.NULL : Arena.ofAuto().allocate(Math.max(1L, vertexCount * layout.byteSize()), 8);
		int primitiveCount = vertexCount / 4;
		long indexCapacity = Math.max(4L, (long) primitiveCount * 6L * Integer.BYTES);
		int sectionCapacity = Math.max(1, Math.max(segments.length / 2, primitiveCount));
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
			MemorySegment assembly = arena.allocate(ASSEMBLY_PARAMS_BYTES, 8);
			assembly.set(ValueLayout.JAVA_LONG, 0, sectionPos);
			assembly.set(ValueLayout.JAVA_LONG, 8, atlasGeneration);
			assembly.set(ValueLayout.JAVA_INT, 16, layerOrdinal);
			assembly.set(ValueLayout.JAVA_INT, 20, waterBlockAtlas ? 1 : 0);
			assembly.set(ValueLayout.JAVA_INT, 24, water != null ? 1 : 0);
			assembly.set(ValueLayout.JAVA_INT, 28, stageVertices ? ASSEMBLY_STAGE_VERTICES : 0);
			if (water != null) {
				for (int index = 0; index < 3; index++) {
					long base = 32 + index * 20L;
					assembly.set(ValueLayout.JAVA_INT, base, water[index].textureId());
					assembly.set(ValueLayout.JAVA_FLOAT, base + 4, water[index].u0());
					assembly.set(ValueLayout.JAVA_FLOAT, base + 8, water[index].u1());
					assembly.set(ValueLayout.JAVA_FLOAT, base + 12, water[index].v0());
					assembly.set(ValueLayout.JAVA_FLOAT, base + 16, water[index].v1());
				}
			}
			MemorySegment indices = arena.allocate(indexCapacity, 8);
			MemorySegment sections = arena.allocate((long) sectionCapacity * SECTION_RECORD_BYTES, 8);
			MemorySegment samples = arena.allocate((long) SAMPLE_LIMIT * SAMPLE_INTS * Integer.BYTES, 8);
			MemorySegment error = arena.allocate(ERROR_BYTES);
			MemorySegment stats = arena.allocate(STATS_BYTES, 8);
			MemorySegment receipt = arena.allocate(RECEIPT_BYTES, 8);
			MemorySegment output = arena.allocate(OUTPUT_BYTES, 8);
			output.set(ValueLayout.ADDRESS, 0, vertices);
			output.set(ValueLayout.JAVA_INT, 8, stageVertices ? 0 : vertexCount);
			output.set(ValueLayout.JAVA_INT, 12, sectionCapacity);
			output.set(ValueLayout.ADDRESS, 16, indices);
			output.set(ValueLayout.JAVA_LONG, 24, indexCapacity);
			output.set(ValueLayout.ADDRESS, 32, sections);
			output.set(ValueLayout.ADDRESS, 40, samples);
			output.set(ValueLayout.JAVA_INT, 48, SAMPLE_LIMIT * SAMPLE_INTS);
			output.set(ValueLayout.JAVA_INT, 52, ERROR_BYTES);
			output.set(ValueLayout.ADDRESS, 56, error);
			output.set(ValueLayout.ADDRESS, 64, stats);
			output.set(ValueLayout.ADDRESS, 72, receipt);
			MemorySegment sorted = sortedIndices == null || !sortedIndices.hasRemaining()
				? MemorySegment.NULL : MemorySegment.ofBuffer(sortedIndices.duplicate());
			long sortedLength = sorted.equals(MemorySegment.NULL) ? 0L : sortedIndices.remaining();
			int decoded;
			try {
				decoded = (int) ASSEMBLE.invokeExact(buffer, (long) vertexData.remaining(), params,
					arena.allocateFrom(ValueLayout.JAVA_INT, segments), segments.length,
					arena.allocateFrom(ValueLayout.JAVA_INT, metadata), metadata.length, sorted, sortedLength,
					assembly, output);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust terrain layer assembly failed", throwable);
			}
			if (decoded == -3) {
				throw new IllegalArgumentException(error.getString(0));
			}
			boolean staged = receipt.get(ValueLayout.JAVA_INT, 136) != 0;
			if (stageVertices && decoded == -2) {
				return null;
			}
			if (decoded != vertexCount) {
				throw new IllegalArgumentException("Rust terrain layer assembly rejected the section mesh (" + decoded
					+ " of " + vertexCount + " vertices)");
			}
			DecodedVertices decodedVertices = new DecodedVertices(staged ? null : vertices, vertexCount,
				staged ? receipt.get(ValueLayout.JAVA_LONG, 0) : 0L, staged ? receipt.get(ValueLayout.JAVA_LONG, 8) : 0L,
				stats.get(ValueLayout.JAVA_FLOAT, 0), stats.get(ValueLayout.JAVA_FLOAT, 4),
				stats.get(ValueLayout.JAVA_FLOAT, 8), stats.get(ValueLayout.JAVA_FLOAT, 12),
				stats.get(ValueLayout.JAVA_FLOAT, 16), stats.get(ValueLayout.JAVA_FLOAT, 20),
				stats.get(ValueLayout.JAVA_FLOAT, 24), stats.get(ValueLayout.JAVA_FLOAT, 28),
				stats.get(ValueLayout.JAVA_FLOAT, 32), stats.get(ValueLayout.JAVA_FLOAT, 36),
				stats.get(ValueLayout.JAVA_FLOAT, 40), stats.get(ValueLayout.JAVA_FLOAT, 44),
				stats.get(ValueLayout.JAVA_INT, 48), stats.get(ValueLayout.JAVA_INT, 52) != 0);
			int indexBytes = receipt.get(ValueLayout.JAVA_INT, 44);
			int sectionCount = receipt.get(ValueLayout.JAVA_INT, 52);
			List<VulkanicGalBridge.WorldMeshSectionRecord> sectionRecords = new ArrayList<>(sectionCount);
			for (int index = 0; index < sectionCount; index++) {
				long base = (long) index * SECTION_RECORD_BYTES;
				sectionRecords.add(new VulkanicGalBridge.WorldMeshSectionRecord(
					sections.get(ValueLayout.JAVA_INT, base + 4), sections.get(ValueLayout.JAVA_INT, base + 8),
					sections.get(ValueLayout.JAVA_INT, base + 12), sections.get(ValueLayout.JAVA_INT, base + 16),
					sections.get(ValueLayout.JAVA_INT, base + 20), sections.get(ValueLayout.JAVA_INT, base + 24),
					sections.get(ValueLayout.JAVA_INT, base + 28), sections.get(ValueLayout.JAVA_INT, base + 32)));
			}
			int sampleCount = receipt.get(ValueLayout.JAVA_INT, 132);
			int u32 = 40;
			return new AssembledLayer(decodedVertices, indices.asSlice(0, indexBytes).toArray(ValueLayout.JAVA_BYTE),
				List.copyOf(sectionRecords), receipt.get(ValueLayout.JAVA_LONG, 0), receipt.get(ValueLayout.JAVA_LONG, 8),
				receipt.get(ValueLayout.JAVA_INT, u32), receipt.get(ValueLayout.JAVA_INT, u32 + 8),
				receipt.get(ValueLayout.JAVA_INT, u32 + 16), receipt.get(ValueLayout.JAVA_INT, u32 + 20),
				receipt.get(ValueLayout.JAVA_INT, u32 + 24), receipt.get(ValueLayout.JAVA_INT, u32 + 28),
				receipt.get(ValueLayout.JAVA_INT, u32 + 32) != 0, receipt.get(ValueLayout.JAVA_INT, u32 + 36),
				receipt.get(ValueLayout.JAVA_INT, u32 + 40), receipt.get(ValueLayout.JAVA_INT, u32 + 44),
				receipt.get(ValueLayout.JAVA_INT, u32 + 48), receipt.get(ValueLayout.JAVA_INT, u32 + 52),
				receipt.get(ValueLayout.JAVA_INT, u32 + 56), receipt.get(ValueLayout.JAVA_INT, u32 + 60),
				receipt.get(ValueLayout.JAVA_INT, u32 + 64), receipt.get(ValueLayout.JAVA_INT, u32 + 68),
				receipt.get(ValueLayout.JAVA_LONG, 16), receipt.get(ValueLayout.JAVA_LONG, 24),
				receipt.get(ValueLayout.JAVA_LONG, 32), receipt.get(ValueLayout.JAVA_INT, u32 + 72),
				receipt.get(ValueLayout.JAVA_INT, u32 + 76), receipt.get(ValueLayout.JAVA_INT, u32 + 80),
				receipt.get(ValueLayout.JAVA_INT, u32 + 84), receipt.get(ValueLayout.JAVA_INT, u32 + 88),
				samples.asSlice(0, (long) sampleCount * SAMPLE_INTS * Integer.BYTES).toArray(ValueLayout.JAVA_INT));
		}
	}

	/** Decoded vertices in the {@code FfiWorldMeshVertex} layout, and their receipts. */
	record DecodedVertices(MemorySegment vertices, int count, long stagedMeshKey, long stagedMeshGeneration, float minX, float minY, float minZ, float maxX,
			float maxY, float maxZ, float minU, float minV, float maxU, float maxV, float minAo, float maxAo,
			int separateAoVertices, boolean aoContractValid) {
		private static final VulkanicGalBridge.Struct LAYOUT = VulkanicGalBridge.Struct.WORLD_MESH_VERTEX;

		/**
		 * The vertices: a list over the encoded bytes (writes go to the bytes),
		 * or only their count when they are staged in Rust.
		 */
		List<VulkanicGalBridge.WorldMeshVertexRecord> encoded() {
			return this.vertices == null
				? new VulkanicGalBridge.StagedWorldMeshVertices(this.count, this.stagedMeshKey, this.stagedMeshGeneration)
				: new VulkanicGalBridge.EncodedWorldMeshVertices(this.vertices, this.count);
		}

		boolean staged() {
			return this.vertices == null;
		}


	}

	private RustTerrainIntake() {
	}

}
