package net.minecraft.world.level.chunk.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.storage.NativeEntityValueInput;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev/test-only coarse bridge for Rust entity-chunk envelope decoding.
 *
 * <p>Rust decodes only the current-version entity chunk envelope and returns
 * opaque per-root-entity NBT tapes plus syntactic metadata. Production entity
 * loading, DFU, registry resolution, entity construction, custom NBT, and
 * passenger attachment remain Java-owned.
 */
public final class NativeEntityStorage implements Closeable {
	public static final int OK = 0;
	public static final int OUTPUT_TOO_SMALL = -4;
	public static final int ERROR_DOMAIN_ENTITY = 5;
	public static final int ENTITY_UNSUPPORTED_DATA_VERSION = 2;
	private static final long OPEN_RESULT_SIZE = 24L;
	private static final long CLOSE_RESULT_SIZE = 48L;
	private static final long RESULT_SIZE = 168L;
	private static final long WRITE_RESULT_SIZE = 72L;
	private static final long OPEN_RESULT_STATUS = 0L;
	private static final long OPEN_RESULT_ERROR_KIND = 4L;
	private static final long OPEN_RESULT_HANDLE = 16L;
	private static final long CLOSE_RESULT_STATUS = 0L;
	private static final long CLOSE_RESULT_ERROR_KIND = 4L;
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
	private static final long RESULT_ENTITY_COUNT = 40L;
	private static final long RESULT_TIMESTAMP = 48L;
	private static final long RESULT_COMPRESSED_LEN = 56L;
	private static final long RESULT_DECOMPRESSED_LEN = 64L;
	private static final long RESULT_OUTPUT_LEN = 72L;
	private static final long RESULT_ERROR_OFFSET = 80L;
	private static final long RESULT_REGION_READ_NANOS = 88L;
	private static final long RESULT_DECOMPRESSION_NANOS = 96L;
	private static final long RESULT_NBT_PARSE_NANOS = 104L;
	private static final long RESULT_ENVELOPE_TRAVERSAL_NANOS = 112L;
	private static final long RESULT_TAPE_CREATION_NANOS = 120L;
	private static final long RESULT_REGION_HANDLE_LOOKUP_NANOS = 128L;
	private static final long RESULT_REGION_LOCK_WAIT_NANOS = 136L;
	private static final long RESULT_REGION_LOCK_HOLD_NANOS = 144L;
	private static final long RESULT_RUST_OUTPUT_COPY_NANOS = 152L;
	private static final long RESULT_RUST_FFI_TOTAL_NANOS = 160L;
	private static final long WRITE_RESULT_STATUS = 0L;
	private static final long WRITE_RESULT_ERROR_DOMAIN = 4L;
	private static final long WRITE_RESULT_ERROR_KIND = 8L;
	private static final long WRITE_RESULT_PRESENT = 12L;
	private static final long WRITE_RESULT_COMPRESSION_ID = 16L;
	private static final long WRITE_RESULT_EXTERNAL = 20L;
	private static final long WRITE_RESULT_ENTITY_COUNT = 24L;
	private static final long WRITE_RESULT_TIMESTAMP = 32L;
	private static final long WRITE_RESULT_COMPRESSED_LEN = 40L;
	private static final long WRITE_RESULT_DECOMPRESSED_LEN = 48L;
	private static final long WRITE_RESULT_FINGERPRINT = 56L;
	private static final long WRITE_RESULT_ERROR_OFFSET = 64L;
	private static final int INITIAL_OUTPUT_CAPACITY = 65536;
	private static final int MAGIC = 0x544E454D;
	private static final int VERSION = 1;
	private static final int FLAG_ID_PRESENT = 1;
	private static final int FLAG_UUID_PRESENT = 1 << 1;
	private static final int FLAG_POSITION_PRESENT = 1 << 2;
	private static final int FLAG_ID_MALFORMED = 1 << 3;
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
	private static final MethodHandle DECODE = downcall("mattmc_entity_decode_chunk_envelope_from_region", DECODE_DESCRIPTOR);
	private static final MethodHandle WRITE = downcall("mattmc_entity_write_chunk_envelope_to_region", WRITE_DESCRIPTOR);
	private long handle;

	private NativeEntityStorage(long handle) {
		this.handle = handle;
	}

	public static NativeEntityStorage open(Path regionPath) throws IOException {
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
				throw new IOException("Open entity region failed with native region status " + resultStatus + " error " + errorKind);
			}
			return new NativeEntityStorage(handle);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Open entity region", throwable);
		}
	}

	public DecodeResult decodeChunk(int chunkX, int chunkZ) throws IOException {
		return decodeChunk(this.handle, chunkX, chunkZ);
	}

	public WriteResult writeChunk(int chunkX, int chunkZ, int compressionId, List<byte[]> entityTapes) throws IOException {
		return writeChunk(this.handle, chunkX, chunkZ, compressionId, entityTapes);
	}

	public static DecodeResult decodeChunk(long handle, int chunkX, int chunkZ) throws IOException {
		if (handle == 0L) {
			throw new IOException("Decode entity chunk failed with closed native region handle");
		}
		long capacity = INITIAL_OUTPUT_CAPACITY;
		int nativeCalls = 0;
		int retries = 0;
		long wrapperStarted = System.nanoTime();
		long arenaNanos = 0L;
		long outputAllocationNanos = 0L;
		long resultAllocationNanos = 0L;
		long ffiInvokeNanos = 0L;
		long resultParseNanos = 0L;
		long allocatedBytes = 0L;
		long clearedBytes = 0L;
		for (int attempt = 0; attempt < 3; attempt++) {
			long arenaStarted = System.nanoTime();
			Arena arena = Arena.ofConfined();
			arenaNanos += System.nanoTime() - arenaStarted;
			try (arena) {
				long outputAllocationStarted = System.nanoTime();
				MemorySegment output = capacity == 0L ? MemorySegment.NULL : arena.allocate(capacity, 1);
				outputAllocationNanos += System.nanoTime() - outputAllocationStarted;
				long resultAllocationStarted = System.nanoTime();
				MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
				resultAllocationNanos += System.nanoTime() - resultAllocationStarted;
				allocatedBytes += capacity + RESULT_SIZE;
				clearedBytes += capacity + RESULT_SIZE;
				long ffiInvokeStarted = System.nanoTime();
				int status = invokeDecode(handle, chunkX, chunkZ, output, capacity, resultSegment);
				ffiInvokeNanos += System.nanoTime() - ffiInvokeStarted;
				long resultParseStarted = System.nanoTime();
				Result result = readResult(resultSegment, status);
				resultParseNanos += System.nanoTime() - resultParseStarted;
				nativeCalls++;
				if (result.status == OK) {
					long copyStarted = System.nanoTime();
					byte[] bytes = result.present && !result.requiresDfu && result.outputLength > 0L
						? output.asSlice(0, result.outputLength).toArray(ValueLayout.JAVA_BYTE)
						: new byte[0];
					long copyNanos = System.nanoTime() - copyStarted;
					long decodeStarted = System.nanoTime();
					List<EntityRecord> entities = decodeTape(bytes);
					long javaEnvelopeNanos = System.nanoTime() - decodeStarted;
					return new DecodeResult(
						result,
						entities,
						bytes,
						Metrics.create(
							nativeCalls,
							retries,
							result.outputLength,
							System.nanoTime() - wrapperStarted,
							arenaNanos,
							outputAllocationNanos,
							resultAllocationNanos,
							ffiInvokeNanos,
							resultParseNanos,
							copyNanos,
							javaEnvelopeNanos,
							allocatedBytes,
							clearedBytes
						)
					);
				}
				if (result.status == OUTPUT_TOO_SMALL && result.outputLength > capacity) {
					capacity = result.outputLength;
					retries++;
					continue;
				}
				throw new DecodeException("Decode entity chunk", result);
			}
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			Result result = decodeInto(handle, chunkX, chunkZ, MemorySegment.NULL, 0L, resultSegment);
			throw new DecodeException("Decode entity chunk", result);
		}
	}

	public static WriteResult writeChunk(long handle, int chunkX, int chunkZ, int compressionId, List<byte[]> entityTapes) throws IOException {
		if (handle == 0L) {
			throw new IOException("Write entity chunk failed with closed native region handle");
		}
		byte[] tape = encodeWriteEnvelope(chunkX, chunkZ, entityTapes);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment tapeSegment = tape.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, tape);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
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
				throw new WriteException("Write entity chunk", result);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Write entity chunk", throwable);
		}
	}

	@Override
	public void close() throws IOException {
		long handle = this.handle;
		if (handle == 0L) {
			return;
		}
		this.handle = 0L;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(CLOSE_RESULT_SIZE, 8);
			int status = (int)CLOSE.invokeExact(handle, resultSegment);
			int resultStatus = resultSegment.get(ValueLayout.JAVA_INT, CLOSE_RESULT_STATUS);
			if (resultStatus == 0 && status != 0) {
				resultStatus = status;
			}
			if (resultStatus != OK) {
				throw new IOException(
					"Close entity region failed with native region status "
						+ resultStatus
						+ " error "
						+ resultSegment.get(ValueLayout.JAVA_INT, CLOSE_RESULT_ERROR_KIND)
				);
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Close entity region", throwable);
		}
	}

	public static boolean isRequiresDfu(IOException exception) {
		return exception instanceof DecodeException decodeException
			&& decodeException.result().errorDomain() == ERROR_DOMAIN_ENTITY
			&& decodeException.result().errorKind() == ENTITY_UNSUPPORTED_DATA_VERSION;
	}

	private static Result decodeInto(long handle, int chunkX, int chunkZ, MemorySegment output, long outputCapacity, MemorySegment resultSegment) {
		int status = invokeDecode(handle, chunkX, chunkZ, output, outputCapacity, resultSegment);
		return readResult(resultSegment, status);
	}

	private static int invokeDecode(long handle, int chunkX, int chunkZ, MemorySegment output, long outputCapacity, MemorySegment resultSegment) {
		try {
			return (int)DECODE.invokeExact(
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
		} catch (Throwable throwable) {
			throw nativeFailure("Decode entity chunk", throwable);
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
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ENTITY_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_ERROR_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_READ_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DECOMPRESSION_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_NBT_PARSE_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_ENVELOPE_TRAVERSAL_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TAPE_CREATION_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_HANDLE_LOOKUP_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_LOCK_WAIT_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_REGION_LOCK_HOLD_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_RUST_OUTPUT_COPY_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_RUST_FFI_TOTAL_NANOS)
		);
	}

	private static WriteResult readWriteResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new WriteResult(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_ENTITY_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_FINGERPRINT),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_ERROR_OFFSET)
		);
	}

	private static byte[] encodeWriteEnvelope(int chunkX, int chunkZ, List<byte[]> entityTapes) throws IOException {
		ByteArrayBuilder output = new ByteArrayBuilder();
		output.writeIntLE(MAGIC);
		output.writeShortLE(VERSION);
		output.writeShortLE(0);
		output.writeIntLE(4556);
		output.writeIntLE(chunkX);
		output.writeIntLE(chunkZ);
		output.writeIntLE(entityTapes.size());
		for (byte[] entityTape : entityTapes) {
			if (entityTape == null) {
				throw new IOException("Entity write tape list contains null entry");
			}
			output.writeIntLE(0);
			output.writeIntLE(0);
			output.writeIntLE(0);
			output.writeLongLE(0L);
			output.writeLongLE(0L);
			output.writeLongLE(0L);
			output.writeLongLE(0L);
			output.writeLongLE(0L);
			output.writeLongLE(0L);
			output.writeIntLE(0);
			output.writeIntLE(entityTape.length);
			output.writeBytes(entityTape);
		}
		return output.toByteArray();
	}

	private static List<EntityRecord> decodeTape(byte[] bytes) throws IOException {
		if (bytes.length == 0) {
			return List.of();
		}
		TapeReader reader = new TapeReader(bytes);
		if (reader.readIntLE() != MAGIC) {
			throw new IOException("Invalid entity envelope tape magic");
		}
		if (reader.readUnsignedShortLE() != VERSION) {
			throw new IOException("Unsupported entity envelope tape version");
		}
		reader.readUnsignedShortLE();
		reader.readIntLE();
		reader.readIntLE();
		reader.readIntLE();
		int entityCount = reader.readIntLE();
		List<EntityRecord> entities = new ArrayList<>(entityCount);
		for (int i = 0; i < entityCount; i++) {
			int flags = reader.readIntLE();
			int passengerCount = reader.readIntLE();
			int passengerDepth = reader.readIntLE();
			long fingerprint = reader.readLongLE();
			long uuidMost = reader.readLongLE();
			long uuidLeast = reader.readLongLE();
			long xBits = reader.readLongLE();
			long yBits = reader.readLongLE();
			long zBits = reader.readLongLE();
			int idLength = reader.readIntLE();
			int blobLength = reader.readIntLE();
			Optional<String> id = (flags & FLAG_ID_PRESENT) != 0 ? Optional.of(reader.readUtf8(idLength)) : Optional.empty();
			if ((flags & FLAG_ID_PRESENT) == 0) {
				reader.skip(idLength);
			}
			int tapeOffset = reader.cursor();
			reader.skip(blobLength);
			Optional<UUID> uuid = (flags & FLAG_UUID_PRESENT) != 0 ? Optional.of(new UUID(uuidMost, uuidLeast)) : Optional.empty();
			Optional<Position> position = (flags & FLAG_POSITION_PRESENT) != 0
				? Optional.of(new Position(Double.longBitsToDouble(xBits), Double.longBitsToDouble(yBits), Double.longBitsToDouble(zBits)))
				: Optional.empty();
			entities.add(
				new EntityRecord(
					id,
					(flags & FLAG_ID_MALFORMED) != 0,
					uuid,
					position,
					passengerCount,
					passengerDepth,
					fingerprint,
					bytes,
					tapeOffset,
					blobLength,
					id.flatMap(value -> {
						try {
							return Optional.of(ResourceLocation.parse(value));
						} catch (Exception exception) {
							return Optional.empty();
						}
					})
				)
			);
		}
		if (!reader.done()) {
			throw new IOException("Trailing bytes in entity envelope tape");
		}
		return List.copyOf(entities);
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
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
					+ " failed with native entity status "
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

	public static final class WriteException extends IOException {
		private final WriteResult result;

		private WriteException(String action, WriteResult result) {
			super(
				action
					+ " failed with native entity status "
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

	public record DecodeResult(Result result, List<EntityRecord> entities, byte[] backingTape, Metrics metrics) {
		public List<NativeEntityValueInput.TapeSlice> entityTapeSlices() {
			return this.entities.stream().map(EntityRecord::tapeSlice).toList();
		}

		DecodeResult withAsyncTimings(long workerQueueWaitNanos, long workerExecutionNanos) {
			return new DecodeResult(this.result, this.entities, this.backingTape, this.metrics.withAsyncTimings(workerQueueWaitNanos, workerExecutionNanos));
		}
	}

	public record Metrics(
		int nativeCalls,
		int retries,
		long copiedBytes,
		long javaNativeCallNanos,
		long javaArenaNanos,
		long javaOutputAllocationNanos,
		long javaResultAllocationNanos,
		long javaFfiInvokeNanos,
		long javaResultParseNanos,
		long copyNanos,
		long javaEnvelopeDecodeNanos,
		long javaWrapperOtherNanos,
		long javaAllocatedBytes,
		long javaClearedBytes,
		long workerQueueWaitNanos,
		long workerExecutionNanos
	) {
		private static Metrics create(
			int nativeCalls,
			int retries,
			long copiedBytes,
			long javaNativeCallNanos,
			long javaArenaNanos,
			long javaOutputAllocationNanos,
			long javaResultAllocationNanos,
			long javaFfiInvokeNanos,
			long javaResultParseNanos,
			long copyNanos,
			long javaEnvelopeDecodeNanos,
			long javaAllocatedBytes,
			long javaClearedBytes
		) {
			long accounted = javaArenaNanos
				+ javaOutputAllocationNanos
				+ javaResultAllocationNanos
				+ javaFfiInvokeNanos
				+ javaResultParseNanos
				+ copyNanos
				+ javaEnvelopeDecodeNanos;
			return new Metrics(
				nativeCalls,
				retries,
				copiedBytes,
				javaNativeCallNanos,
				javaArenaNanos,
				javaOutputAllocationNanos,
				javaResultAllocationNanos,
				javaFfiInvokeNanos,
				javaResultParseNanos,
				copyNanos,
				javaEnvelopeDecodeNanos,
				Math.max(0L, javaNativeCallNanos - accounted),
				javaAllocatedBytes,
				javaClearedBytes,
				0L,
				0L
			);
		}

		private Metrics withAsyncTimings(long workerQueueWaitNanos, long workerExecutionNanos) {
			return new Metrics(
				this.nativeCalls,
				this.retries,
				this.copiedBytes,
				this.javaNativeCallNanos,
				this.javaArenaNanos,
				this.javaOutputAllocationNanos,
				this.javaResultAllocationNanos,
				this.javaFfiInvokeNanos,
				this.javaResultParseNanos,
				this.copyNanos,
				this.javaEnvelopeDecodeNanos,
				this.javaWrapperOtherNanos,
				this.javaAllocatedBytes,
				this.javaClearedBytes,
				workerQueueWaitNanos,
				workerExecutionNanos
			);
		}
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
		int entityCount,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long outputLength,
		long errorOffset,
		long regionReadNanos,
		long decompressionNanos,
		long nbtParseNanos,
		long envelopeTraversalNanos,
		long tapeCreationNanos,
		long regionHandleLookupNanos,
		long regionLockWaitNanos,
		long regionLockHoldNanos,
		long rustOutputCopyNanos,
		long rustFfiTotalNanos
	) {
	}

	public record WriteResult(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		int entityCount,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long fingerprint,
		long errorOffset
	) {
	}

	public record WriteRequest(
		List<byte[]> entityTapes,
		CompoundTag pendingTag,
		int entityCount,
		long saveTraversalNanos,
		long tapeConstructionNanos,
		long codecSubtreeNanos,
		long codecSubtreeMaterializations,
		long shadowValidationNanos
	) {
		public WriteRequest {
			entityTapes = List.copyOf(entityTapes);
		}

		public long tapeBytes() {
			long bytes = 0L;
			for (byte[] tape : this.entityTapes) {
				bytes += tape.length;
			}
			return bytes;
		}
	}

	public record EntityRecord(
		Optional<String> id,
		boolean idMalformed,
		Optional<UUID> uuid,
		Optional<Position> position,
		int passengerCount,
		int passengerDepth,
		long fingerprint,
		byte[] tapeSource,
		int tapeOffset,
		int tapeLength,
		Optional<ResourceLocation> parsedId
	) {
		public byte[] nbtTape() {
			return Arrays.copyOfRange(this.tapeSource, this.tapeOffset, this.tapeOffset + this.tapeLength);
		}

		public NativeEntityValueInput.TapeSlice tapeSlice() {
			return new NativeEntityValueInput.TapeSlice(this.tapeSource, this.tapeOffset, this.tapeLength);
		}

		public CompoundTag readTapeAsTag() throws IOException {
			return NativeNbtRegionAccess.readTape(this.nbtTape());
		}
	}

	public record Position(double x, double y, double z) {
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
				| bytes[cursor + 3] << 24;
			cursor += 4;
			return value;
		}

		int readUnsignedShortLE() throws IOException {
			require(2);
			int value = bytes[cursor] & 0xFF | (bytes[cursor + 1] & 0xFF) << 8;
			cursor += 2;
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

		String readUtf8(int length) throws IOException {
			require(length);
			String value = new String(bytes, cursor, length, StandardCharsets.UTF_8);
			cursor += length;
			return value;
		}

		byte[] readBytes(int length) throws IOException {
			require(length);
			byte[] value = java.util.Arrays.copyOfRange(bytes, cursor, cursor + length);
			cursor += length;
			return value;
		}

		void skip(int length) throws IOException {
			require(length);
			cursor += length;
		}

		int cursor() {
			return cursor;
		}

		boolean done() {
			return cursor == bytes.length;
		}

		private void require(int length) throws IOException {
			if (length < 0 || bytes.length - cursor < length) {
				throw new IOException("Truncated entity envelope tape");
			}
		}
	}

	private static final class ByteArrayBuilder {
		private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

		void writeShortLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
		}

		void writeIntLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
			this.output.write(value >>> 16 & 0xFF);
			this.output.write(value >>> 24 & 0xFF);
		}

		void writeLongLE(long value) {
			this.writeIntLE((int)value);
			this.writeIntLE((int)(value >>> 32));
		}

		void writeBytes(byte[] bytes) {
			this.output.writeBytes(bytes);
		}

		byte[] toByteArray() {
			return this.output.toByteArray();
		}
	}
}
