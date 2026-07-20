package net.minecraft.world.entity.ai.village.poi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;

import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Coarse Java bridge for Rust-owned current-version POI chunk data.
 *
 * <p>Production POI reads and writes pass one bulk typed tape per chunk between
 * Java and Rust. Rust owns region/NBT parsing and typed POI encoding; Java owns
 * registry resolution and {@link PoiSection} object construction. The path
 * based open/close helpers remain for isolated tests, while production uses the
 * persistent native region handle owned by {@code RegionFile}.
 */
public final class NativePoiStorage implements Closeable {
	public static final int OK = 0;
	public static final int OUTPUT_TOO_SMALL = -4;
	public static final int ERROR_DOMAIN_POI = 4;
	public static final int POI_UNSUPPORTED_DATA_VERSION = 2;
	private static final long OPEN_RESULT_SIZE = 24L;
	private static final long WRITE_RESULT_SIZE = 48L;
	private static final long POI_RESULT_SIZE = 72L;
	private static final long OPEN_RESULT_STATUS = 0L;
	private static final long OPEN_RESULT_ERROR_KIND = 4L;
	private static final long OPEN_RESULT_HANDLE = 16L;
	private static final long WRITE_RESULT_STATUS = 0L;
	private static final long WRITE_RESULT_ERROR_KIND = 4L;
	private static final long POI_RESULT_STATUS = 0L;
	private static final long POI_RESULT_ERROR_DOMAIN = 4L;
	private static final long POI_RESULT_ERROR_KIND = 8L;
	private static final long POI_RESULT_PRESENT = 12L;
	private static final long POI_RESULT_COMPRESSION_ID = 16L;
	private static final long POI_RESULT_EXTERNAL = 20L;
	private static final long POI_RESULT_SECTION_COUNT = 24L;
	private static final long POI_RESULT_RECORD_COUNT = 28L;
	private static final long POI_RESULT_TIMESTAMP = 32L;
	private static final long POI_RESULT_COMPRESSED_LEN = 40L;
	private static final long POI_RESULT_DECOMPRESSED_LEN = 48L;
	private static final long POI_RESULT_OUTPUT_LEN = 56L;
	private static final long POI_RESULT_FINGERPRINT = 56L;
	private static final long POI_RESULT_ERROR_OFFSET = 64L;
	private static final int INITIAL_OUTPUT_CAPACITY = 4096;
	private static final int MAGIC = 0x494F504D;
	private static final int VERSION = 1;
	private static final FunctionDescriptor OPEN_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor CLOSE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
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
	private static final FunctionDescriptor WRITE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
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
	private static final MethodHandle OPEN = downcall("mattmc_region_open", OPEN_DESCRIPTOR);
	private static final MethodHandle CLOSE = downcall("mattmc_region_close", CLOSE_DESCRIPTOR);
	private static final MethodHandle DECODE = downcall("mattmc_poi_decode_chunk_from_region", DECODE_DESCRIPTOR);
	private static final MethodHandle WRITE = downcall("mattmc_poi_write_chunk_to_region", WRITE_DESCRIPTOR);
	private long handle;

	private NativePoiStorage(long handle) {
		this.handle = handle;
	}

	public static NativePoiStorage open(Path regionPath) throws IOException {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(OPEN_RESULT_SIZE, 8);
			int status = (int)OPEN.invokeExact(pathSegment, (long)path.length, 0, resultSegment);
			int resultStatus = resultSegment.get(ValueLayout.JAVA_INT, OPEN_RESULT_STATUS);
			if (resultStatus == 0 && status != 0) {
				resultStatus = status;
			}
			int errorKind = resultSegment.get(ValueLayout.JAVA_INT, OPEN_RESULT_ERROR_KIND);
			long handle = resultSegment.get(ValueLayout.JAVA_LONG, OPEN_RESULT_HANDLE);
			if (resultStatus != OK || handle == 0L) {
				throw new IOException("Open POI region failed with native region status " + resultStatus + " error " + errorKind);
			}
			return new NativePoiStorage(handle);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Open POI region", throwable);
		}
	}

	public DecodeResult decodeChunk(int chunkX, int chunkZ) throws IOException {
		return decodeChunk(this.handle, chunkX, chunkZ);
	}

	public static DecodeResult decodeChunk(long handle, int chunkX, int chunkZ) throws IOException {
		if (handle == 0L) {
			throw new IOException("Decode POI chunk failed with closed native region handle");
		}
		long capacity = INITIAL_OUTPUT_CAPACITY;
		for (int attempt = 0; attempt < 3; attempt++) {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment output = capacity == 0L ? MemorySegment.NULL : arena.allocate(capacity, 1);
				MemorySegment resultSegment = arena.allocate(POI_RESULT_SIZE, 8);
				Result result = decodeInto(handle, chunkX, chunkZ, output, capacity, resultSegment);
				if (result.status == OK) {
					byte[] bytes = result.present && result.outputLength > 0L
						? output.asSlice(0, result.outputLength).toArray(ValueLayout.JAVA_BYTE)
						: new byte[0];
					return new DecodeResult(result, decodeTape(bytes));
				}
				if (result.status == OUTPUT_TOO_SMALL && result.outputLength > capacity) {
					capacity = result.outputLength;
					continue;
				}
				throw new DecodeException("Decode POI chunk", result);
			}
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(POI_RESULT_SIZE, 8);
			Result result = decodeInto(handle, chunkX, chunkZ, MemorySegment.NULL, 0L, resultSegment);
			throw new DecodeException("Decode POI chunk", result);
		}
	}

	public static WriteResult writeChunk(long handle, int chunkX, int chunkZ, int compressionId, byte[] tape) throws IOException {
		if (handle == 0L) {
			throw new IOException("Write POI chunk failed with closed native region handle");
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment tapeSegment = tape.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, tape);
			MemorySegment resultSegment = arena.allocate(POI_RESULT_SIZE, 8);
			int status = (int)WRITE.invokeExact(
				handle,
				chunkX,
				chunkZ,
				compressionId,
				tapeSegment,
				(long)tape.length,
				0L,
				0L,
				0,
				0,
				0L,
				0L,
				resultSegment
			);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw new WriteException("Write POI chunk", result);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Write POI chunk", throwable);
		}
	}

	public WriteResult writeChunk(int chunkX, int chunkZ, int compressionId, byte[] tape) throws IOException {
		return writeChunk(this.handle, chunkX, chunkZ, compressionId, tape);
	}

	public static byte[] encodeTape(Int2ObjectMap<PoiSection.Packed> sections) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(estimateTapeSize(sections));
		writeIntLE(output, MAGIC);
		writeShortLE(output, VERSION);
		writeShortLE(output, 0);
		writeIntLE(output, sections.size());
		for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<PoiSection.Packed> entry : sections.int2ObjectEntrySet()
			.stream()
			.sorted(Comparator.comparingInt(it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry::getIntKey))
			.toList()) {
			PoiSection.Packed section = entry.getValue();
			writeIntLE(output, entry.getIntKey());
			output.write(section.isValid() ? 1 : 0);
			output.write(0);
			output.write(0);
			output.write(0);
			writeIntLE(output, section.records().size());
			for (PoiRecord.Packed record : section.records()) {
				writeIntLE(output, record.pos().getX());
				writeIntLE(output, record.pos().getY());
				writeIntLE(output, record.pos().getZ());
				writeIntLE(output, record.freeTickets());
				ResourceLocation type = record.poiType()
					.unwrapKey()
					.map(ResourceKey::location)
					.orElseThrow(() -> new IOException("POI record type has no registry key"));
				byte[] typeBytes = type.toString().getBytes(StandardCharsets.UTF_8);
				writeIntLE(output, typeBytes.length);
				output.writeBytes(typeBytes);
			}
		}
		return output.toByteArray();
	}

	@Override
	public void close() throws IOException {
		long handle = this.handle;
		if (handle == 0L) {
			return;
		}
		this.handle = 0L;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)CLOSE.invokeExact(handle, resultSegment);
			int resultStatus = resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_STATUS);
			if (resultStatus == 0 && status != 0) {
				resultStatus = status;
			}
			if (resultStatus != OK) {
				throw new IOException(
					"Close POI region failed with native region status "
						+ resultStatus
						+ " error "
						+ resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_ERROR_KIND)
				);
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Close POI region", throwable);
		}
	}

	public static Int2ObjectMap<PoiSection.Packed> toPackedSections(
		DecodeResult decodeResult, RegistryAccess registryAccess, LevelHeightAccessor levelHeightAccessor
	) throws IOException {
		Int2ObjectMap<PoiSection.Packed> sections = new Int2ObjectOpenHashMap<>();
		Registry<PoiType> registry = registryAccess.lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE);
		for (Section section : decodeResult.sections()) {
			if (section.sectionY() < levelHeightAccessor.getMinSectionY() || section.sectionY() > levelHeightAccessor.getMaxSectionY()) {
				continue;
			}
			List<PoiRecord.Packed> records = new ArrayList<>(section.records().size());
			for (Record record : section.records()) {
				ResourceKey<PoiType> key = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, record.type());
				Holder.Reference<PoiType> holder = registry.get(key).orElseThrow(() -> new UnknownPoiTypeException(record.type()));
				records.add(new PoiRecord.Packed(new BlockPos(record.x(), record.y(), record.z()), holder, record.freeTickets()));
			}
			sections.put(section.sectionY(), new PoiSection.Packed(section.valid(), List.copyOf(records)));
		}
		return sections;
	}

	public static boolean isUnsupportedDataVersion(IOException exception) {
		return exception instanceof DecodeException decodeException
			&& decodeException.result().errorDomain() == ERROR_DOMAIN_POI
			&& decodeException.result().errorKind() == POI_UNSUPPORTED_DATA_VERSION;
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
			throw nativeFailure("Decode POI chunk", throwable);
		}
	}

	private static Result readResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new Result(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_SECTION_COUNT),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_RECORD_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_OUTPUT_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_ERROR_OFFSET)
		);
	}

	private static WriteResult readWriteResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new WriteResult(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_SECTION_COUNT),
			resultSegment.get(ValueLayout.JAVA_INT, POI_RESULT_RECORD_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_FINGERPRINT),
			resultSegment.get(ValueLayout.JAVA_LONG, POI_RESULT_ERROR_OFFSET)
		);
	}

	private static List<Section> decodeTape(byte[] bytes) throws IOException {
		if (bytes.length == 0) {
			return List.of();
		}
		TapeReader reader = new TapeReader(bytes);
		if (reader.readIntLE() != MAGIC) {
			throw new IOException("Invalid POI tape magic");
		}
		if (reader.readUnsignedShortLE() != VERSION) {
			throw new IOException("Unsupported POI tape version");
		}
		reader.readUnsignedShortLE();
		int sectionCount = reader.readIntLE();
		List<Section> sections = new ArrayList<>(sectionCount);
		for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
			int sectionY = reader.readIntLE();
			boolean valid = reader.readUnsignedByte() != 0;
			reader.skip(3);
			int recordCount = reader.readIntLE();
			List<Record> records = new ArrayList<>(recordCount);
			for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
				int x = reader.readIntLE();
				int y = reader.readIntLE();
				int z = reader.readIntLE();
				int freeTickets = reader.readIntLE();
				int typeLength = reader.readIntLE();
				String type = reader.readUtf8(typeLength);
				records.add(new Record(x, y, z, ResourceLocation.parse(type), freeTickets));
			}
			sections.add(new Section(sectionY, valid, List.copyOf(records)));
		}
		if (!reader.done()) {
			throw new IOException("Trailing bytes in POI tape");
		}
		return List.copyOf(sections);
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
	}

	public static final class DecodeException extends IOException {
		private final Result result;

		private DecodeException(String action, Result result) {
			super(
			action
				+ " failed with native POI status "
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

	public static final class UnknownPoiTypeException extends IOException {
		private final ResourceLocation type;

		private UnknownPoiTypeException(ResourceLocation type) {
			super("Unknown POI type from Rust POI decoder: " + type);
			this.type = type;
		}

		public ResourceLocation type() {
			return this.type;
		}
	}

	public static final class WriteException extends IOException {
		private final WriteResult result;

		private WriteException(String action, WriteResult result) {
			super(
				action
					+ " failed with native POI status "
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

		public WriteResult result() {
			return this.result;
		}
	}

	private static int estimateTapeSize(Int2ObjectMap<PoiSection.Packed> sections) {
		int size = 12;
		for (PoiSection.Packed section : sections.values()) {
			size += 12;
			for (PoiRecord.Packed record : section.records()) {
				size += 20;
				size += record.poiType()
					.unwrapKey()
					.map(key -> key.location().toString().getBytes(StandardCharsets.UTF_8).length)
					.orElse(0);
			}
		}
		return size;
	}

	private static void writeShortLE(ByteArrayOutputStream output, int value) {
		output.write(value & 0xFF);
		output.write(value >>> 8 & 0xFF);
	}

	private static void writeIntLE(ByteArrayOutputStream output, int value) {
		output.write(value & 0xFF);
		output.write(value >>> 8 & 0xFF);
		output.write(value >>> 16 & 0xFF);
		output.write(value >>> 24 & 0xFF);
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

	public record DecodeResult(Result result, List<Section> sections) {
		public Optional<Section> section(int sectionY) {
			return this.sections.stream().filter(section -> section.sectionY == sectionY).findFirst();
		}
	}

	public record Result(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		int sectionCount,
		int recordCount,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long outputLength,
		long errorOffset
	) {
	}

	public record WriteResult(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		int sectionCount,
		int recordCount,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long fingerprint,
		long errorOffset
	) {
	}

	public record Section(int sectionY, boolean valid, List<Record> records) {
	}

	public record Record(int x, int y, int z, ResourceLocation type, int freeTickets) {
	}

	private static final class TapeReader {
		private final byte[] bytes;
		private int cursor;

		private TapeReader(byte[] bytes) {
			this.bytes = bytes;
		}

		int readIntLE() throws IOException {
			require(4);
			int value = bytes[cursor] & 0xFF
				| (bytes[cursor + 1] & 0xFF) << 8
				| (bytes[cursor + 2] & 0xFF) << 16
				| (bytes[cursor + 3] & 0xFF) << 24;
			cursor += 4;
			return value;
		}

		int readUnsignedShortLE() throws IOException {
			require(2);
			int value = bytes[cursor] & 0xFF | (bytes[cursor + 1] & 0xFF) << 8;
			cursor += 2;
			return value;
		}

		int readUnsignedByte() throws IOException {
			require(1);
			return bytes[cursor++] & 0xFF;
		}

		String readUtf8(int length) throws IOException {
			if (length < 0) {
				throw new IOException("Negative POI tape string length");
			}
			require(length);
			String value = new String(bytes, cursor, length, StandardCharsets.UTF_8);
			cursor += length;
			return value;
		}

		void skip(int length) throws IOException {
			require(length);
			cursor += length;
		}

		boolean done() {
			return cursor == bytes.length;
		}

		private void require(int length) throws IOException {
			if (length < 0 || bytes.length - cursor < length) {
				throw new IOException("Truncated POI tape");
			}
		}
	}
}
