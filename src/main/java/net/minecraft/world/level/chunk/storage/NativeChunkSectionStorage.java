package net.minecraft.world.level.chunk.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.util.NativeLibraryLoader;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev/test-only bridge for Rust current-version chunk-section decoding.
 *
 * <p>Rust decodes the chunk NBT shape and returns one bulk typed buffer with
 * simple metadata, section palette tapes, packed arrays, light arrays, and
 * heightmaps. Production chunk loading still uses {@link SerializableChunkData}
 * and Java remains authoritative for DFU, registries, codecs, block entities,
 * ticks, structures, and chunk object construction.
 */
public final class NativeChunkSectionStorage implements Closeable {
	public static final int OK = 0;
	public static final int OUTPUT_TOO_SMALL = -4;
	public static final int ERROR_DOMAIN_CHUNK = 6;
	public static final int CHUNK_UNSUPPORTED_DATA_VERSION = 4;
	private static final long RESULT_SIZE = 168L;
	private static final long RESULT_STATUS = 0L;
	private static final long RESULT_ERROR_DOMAIN = 4L;
	private static final long RESULT_ERROR_KIND = 8L;
	private static final long RESULT_PRESENT = 12L;
	private static final long RESULT_REQUIRES_DFU = 16L;
	private static final long RESULT_COMPRESSION_ID = 20L;
	private static final long RESULT_EXTERNAL = 24L;
	private static final long RESULT_DATA_VERSION = 28L;
	private static final long RESULT_CHUNK_X = 32L;
	private static final long RESULT_CHUNK_Z = 36L;
	private static final long RESULT_SECTION_COUNT = 40L;
	private static final long RESULT_HEIGHTMAP_COUNT = 44L;
	private static final long RESULT_TIMESTAMP = 48L;
	private static final long RESULT_COMPRESSED_LEN = 56L;
	private static final long RESULT_DECOMPRESSED_LEN = 64L;
	private static final long RESULT_OUTPUT_LEN = 72L;
	private static final long RESULT_ERROR_OFFSET = 80L;
	private static final long RESULT_REGION_READ_NANOS = 88L;
	private static final long RESULT_DECOMPRESSION_NANOS = 96L;
	private static final long RESULT_NBT_PARSE_NANOS = 104L;
	private static final long RESULT_CHUNK_DECODE_NANOS = 112L;
	private static final long RESULT_TAPE_CREATION_NANOS = 120L;
	private static final long RESULT_REGION_HANDLE_LOOKUP_NANOS = 128L;
	private static final long RESULT_REGION_LOCK_WAIT_NANOS = 136L;
	private static final long RESULT_REGION_LOCK_HOLD_NANOS = 144L;
	private static final long RESULT_RUST_OUTPUT_COPY_NANOS = 152L;
	private static final long RESULT_RUST_FFI_TOTAL_NANOS = 160L;
	private static final int INITIAL_OUTPUT_CAPACITY = 65536;
	private static final int MAGIC = 0x4B48434D;
	private static final int VERSION = 1;
	private static final int SECTION_HAS_BLOCK_STATES = 1;
	private static final int SECTION_HAS_BIOMES = 1 << 1;
	private static final int SECTION_HAS_BLOCK_LIGHT = 1 << 2;
	private static final int SECTION_HAS_SKY_LIGHT = 1 << 3;
	private static final int BIOME_ENTRY_RESOURCE_ID_UTF16 = 1;
	private static final int BIOME_ENTRY_NBT_TAPE = 2;
	private static final FunctionDescriptor DECODE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final MethodHandle DECODE = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_chunk_decode_sections_from_region",
		DECODE_DESCRIPTOR
	);
	private long handle;

	private NativeChunkSectionStorage(long handle) {
		this.handle = handle;
	}

	public static NativeChunkSectionStorage open(Path regionPath) throws IOException {
		return new NativeChunkSectionStorage(NativeRegionFileBridge.open(regionPath, false));
	}

	public DecodeResult decodeChunk(int chunkX, int chunkZ) throws IOException {
		return decodeChunk(this.handle, chunkX, chunkZ);
	}

	public static DecodeResult decodeChunk(long handle, int chunkX, int chunkZ) throws IOException {
		if (handle == 0L) {
			throw new IOException("Decode chunk sections failed with closed native region handle");
		}
		long capacity = INITIAL_OUTPUT_CAPACITY;
		for (int attempt = 0; attempt < 3; attempt++) {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment output = capacity == 0L ? MemorySegment.NULL : arena.allocate(capacity, 1);
				MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
				Result result = decodeInto(handle, chunkX, chunkZ, output, capacity, resultSegment);
				if (result.status == OK) {
					byte[] bytes = result.present && !result.requiresDfu && result.outputLength > 0L
						? output.asSlice(0, result.outputLength).toArray(ValueLayout.JAVA_BYTE)
						: new byte[0];
					return new DecodeResult(result, decodeTape(bytes));
				}
				if (result.status == OUTPUT_TOO_SMALL && result.outputLength > capacity) {
					capacity = result.outputLength;
					continue;
				}
				throw new DecodeException("Decode chunk sections", result);
			}
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			Result result = decodeInto(thisHandle(handle), chunkX, chunkZ, MemorySegment.NULL, 0L, resultSegment);
			throw new DecodeException("Decode chunk sections", result);
		}
	}

	@Override
	public void close() throws IOException {
		long handle = this.handle;
		if (handle == 0L) {
			return;
		}
		this.handle = 0L;
		NativeRegionFileBridge.close(handle);
	}

	public static boolean isRequiresDfu(IOException exception) {
		return exception instanceof DecodeException decodeException
			&& decodeException.result().errorDomain() == ERROR_DOMAIN_CHUNK
			&& decodeException.result().errorKind() == CHUNK_UNSUPPORTED_DATA_VERSION;
	}

	private static long thisHandle(long handle) {
		return handle;
	}

	private static Result decodeInto(long handle, int chunkX, int chunkZ, MemorySegment output, long outputCapacity, MemorySegment resultSegment) {
		try {
			int status = (int)DECODE.invokeExact(
				handle,
				chunkX,
				chunkZ,
				output,
				outputCapacity,
				0L,
				0L,
				0,
				0,
				0L,
				0L,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Decode chunk sections", throwable);
		}
	}

	private static Result readResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new Result(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_REQUIRES_DFU) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_DATA_VERSION),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_CHUNK_X),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_CHUNK_Z),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_SECTION_COUNT),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_HEIGHTMAP_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_ERROR_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_READ_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DECOMPRESSION_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_NBT_PARSE_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_CHUNK_DECODE_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TAPE_CREATION_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_HANDLE_LOOKUP_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_LOCK_WAIT_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_LOCK_HOLD_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_RUST_OUTPUT_COPY_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_RUST_FFI_TOTAL_NANOS)
		);
	}

	private static ChunkData decodeTape(byte[] bytes) throws IOException {
		if (bytes.length == 0) {
			return ChunkData.empty();
		}
		TapeReader reader = new TapeReader(bytes);
		if (reader.readIntLE() != MAGIC) {
			throw new IOException("Invalid chunk-section tape magic");
		}
		if (reader.readUnsignedShortLE() != VERSION) {
			throw new IOException("Unsupported chunk-section tape version");
		}
		reader.readUnsignedShortLE();
		int dataVersion = reader.readIntLE();
		int chunkX = reader.readIntLE();
		int chunkZ = reader.readIntLE();
		int yPos = reader.readIntLE();
		boolean lightOn = reader.readIntLE() != 0;
		long lastUpdate = reader.readLongLE();
		long inhabitedTime = reader.readLongLE();
		int sectionCount = reader.readIntLE();
		int heightmapCount = reader.readIntLE();
		String status = reader.readJavaString();
		List<Section> sections = new ArrayList<>(sectionCount);
		for (int i = 0; i < sectionCount; i++) {
			sections.add(readSection(reader));
		}
		List<Heightmap> heightmaps = new ArrayList<>(heightmapCount);
		for (int i = 0; i < heightmapCount; i++) {
			heightmaps.add(new Heightmap(reader.readJavaString(), reader.readLongArray(reader.readIntLE())));
		}
		if (!reader.done()) {
			throw new IOException("Trailing bytes in chunk-section tape");
		}
		return new ChunkData(dataVersion, chunkX, chunkZ, yPos, status, lightOn, lastUpdate, inhabitedTime, List.copyOf(sections), List.copyOf(heightmaps));
	}

	private static Section readSection(TapeReader reader) throws IOException {
		int sectionY = reader.readIntLE();
		int flags = reader.readIntLE();
		int blockPaletteCount = reader.readIntLE();
		int blockDataCount = reader.readIntLE();
		int biomePaletteCount = reader.readIntLE();
		int biomeDataCount = reader.readIntLE();
		int blockLightLength = reader.readIntLE();
		int skyLightLength = reader.readIntLE();
		List<BlockPaletteEntry> blockPalette = new ArrayList<>(blockPaletteCount);
		for (int i = 0; i < blockPaletteCount; i++) {
			blockPalette.add(new BlockPaletteEntry(reader.readBytes(reader.readIntLE())));
		}
		long[] blockData = reader.readLongArray(blockDataCount);
		List<BiomePaletteEntry> biomePalette = new ArrayList<>(biomePaletteCount);
		for (int i = 0; i < biomePaletteCount; i++) {
			int kind = reader.readIntLE();
			if (kind == BIOME_ENTRY_RESOURCE_ID_UTF16) {
				biomePalette.add(new BiomePaletteEntry(reader.readJavaString(), null));
			} else if (kind == BIOME_ENTRY_NBT_TAPE) {
				biomePalette.add(new BiomePaletteEntry(null, reader.readBytes(reader.readIntLE())));
			} else {
				throw new IOException("Unknown biome palette entry kind " + kind);
			}
		}
		long[] biomeData = reader.readLongArray(biomeDataCount);
		byte[] blockLight = (flags & SECTION_HAS_BLOCK_LIGHT) != 0 ? reader.readBytes(blockLightLength) : new byte[0];
		byte[] skyLight = (flags & SECTION_HAS_SKY_LIGHT) != 0 ? reader.readBytes(skyLightLength) : new byte[0];
		return new Section(
			sectionY,
			(flags & SECTION_HAS_BLOCK_STATES) != 0,
			(flags & SECTION_HAS_BIOMES) != 0,
			List.copyOf(blockPalette),
			blockData,
			List.copyOf(biomePalette),
			biomeData,
			blockLight,
			skyLight
		);
	}

	private static IllegalStateException nativeFailure(String action, Throwable throwable) {
		if (throwable instanceof RuntimeException runtimeException) {
			return new IllegalStateException(action + " failed", runtimeException);
		}
		if (throwable instanceof Error error) {
			throw error;
		}
		return new IllegalStateException(action + " failed", throwable);
	}

	public static final class DecodeException extends IOException {
		private final Result result;

		private DecodeException(String action, Result result) {
			super(
				action
					+ " failed with native chunk status "
					+ result.status
					+ " domain "
					+ result.errorDomain
					+ " error "
					+ result.errorKind
					+ " at offset "
					+ result.errorOffset
			);
			this.result = result;
		}

		public Result result() {
			return this.result;
		}
	}

	public record DecodeResult(Result result, ChunkData chunk) {
	}

	public record Result(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		boolean requiresDfu,
		int compressionId,
		boolean external,
		int dataVersion,
		int chunkX,
		int chunkZ,
		int sectionCount,
		int heightmapCount,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long outputLength,
		long errorOffset,
		long regionReadNanos,
		long decompressionNanos,
		long nbtParseNanos,
		long chunkDecodeNanos,
		long tapeCreationNanos,
		long regionHandleLookupNanos,
		long regionLockWaitNanos,
		long regionLockHoldNanos,
		long rustOutputCopyNanos,
		long rustFfiTotalNanos
	) {
	}

	public record ChunkData(
		int dataVersion,
		int chunkX,
		int chunkZ,
		int yPos,
		String status,
		boolean lightOn,
		long lastUpdate,
		long inhabitedTime,
		List<Section> sections,
		List<Heightmap> heightmaps
	) {
		private static ChunkData empty() {
			return new ChunkData(0, 0, 0, 0, "", false, 0L, 0L, List.of(), List.of());
		}
	}

	public record Section(
		int sectionY,
		boolean hasBlockStates,
		boolean hasBiomes,
		List<BlockPaletteEntry> blockPalette,
		long[] blockData,
		List<BiomePaletteEntry> biomePalette,
		long[] biomeData,
		byte[] blockLight,
		byte[] skyLight
	) {
	}

	public record BlockPaletteEntry(byte[] nbtTape) {
		public CompoundTag asTag() throws IOException {
			return NativeNbtRegionAccess.readTape(this.nbtTape);
		}
	}

	public record BiomePaletteEntry(String resourceId, byte[] nbtTape) {
	}

	public record Heightmap(String name, long[] data) {
	}

	private static final class TapeReader {
		private final byte[] bytes;
		private int cursor;

		private TapeReader(byte[] bytes) {
			this.bytes = bytes;
		}

		int readUnsignedShortLE() throws IOException {
			require(2);
			int value = bytes[cursor] & 0xFF | (bytes[cursor + 1] & 0xFF) << 8;
			cursor += 2;
			return value;
		}

		int readIntLE() throws IOException {
			require(4);
			int value = bytes[cursor] & 0xFF
				| (bytes[cursor + 1] & 0xFF) << 8
				| (bytes[cursor + 2] & 0xFF) << 16
				| bytes[cursor + 3] << 24;
			cursor += 4;
			return value;
		}

		long readLongLE() throws IOException {
			require(8);
			long value = bytes[cursor] & 0xFFL
				| (bytes[cursor + 1] & 0xFFL) << 8
				| (bytes[cursor + 2] & 0xFFL) << 16
				| (bytes[cursor + 3] & 0xFFL) << 24
				| (bytes[cursor + 4] & 0xFFL) << 32
				| (bytes[cursor + 5] & 0xFFL) << 40
				| (bytes[cursor + 6] & 0xFFL) << 48
				| (long)bytes[cursor + 7] << 56;
			cursor += 8;
			return value;
		}

		String readJavaString() throws IOException {
			int unitCount = readIntLE();
			if (unitCount < 0) {
				throw new IOException("Negative chunk tape string length");
			}
			require(unitCount * 2);
			char[] chars = new char[unitCount];
			for (int i = 0; i < unitCount; i++) {
				chars[i] = (char)(bytes[cursor] & 0xFF | (bytes[cursor + 1] & 0xFF) << 8);
				cursor += 2;
			}
			return new String(chars);
		}

		long[] readLongArray(int count) throws IOException {
			if (count < 0) {
				throw new IOException("Negative chunk tape long array length");
			}
			long[] values = new long[count];
			for (int i = 0; i < count; i++) {
				values[i] = readLongLE();
			}
			return values;
		}

		byte[] readBytes(int length) throws IOException {
			if (length < 0) {
				throw new IOException("Negative chunk tape byte length");
			}
			require(length);
			byte[] value = java.util.Arrays.copyOfRange(bytes, cursor, cursor + length);
			cursor += length;
			return value;
		}

		boolean done() {
			return cursor == bytes.length;
		}

		private void require(int length) throws IOException {
			if (length < 0 || bytes.length - cursor < length) {
				throw new IOException("Truncated chunk-section tape");
			}
		}
	}
}
