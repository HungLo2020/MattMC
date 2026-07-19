package net.minecraft.world.level.chunk.storage;

import net.minecraft.util.NativeLibraryLoader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class NativeRegionFileBridge {
	static final int OK = 0;
	static final int OUTPUT_TOO_SMALL = -4;
	private static final long OPEN_RESULT_SIZE = 24L;
	private static final long RESULT_SIZE = 40L;
	private static final long NBT_RESULT_SIZE = 72L;
	private static final long TAPE_RESULT_SIZE = 80L;
	private static final long WRITE_RESULT_SIZE = 48L;
	private static final long OPEN_RESULT_STATUS = 0L;
	private static final long OPEN_RESULT_ERROR_KIND = 4L;
	private static final long OPEN_RESULT_HANDLE = 16L;
	private static final long RESULT_STATUS = 0L;
	private static final long RESULT_ERROR_KIND = 4L;
	private static final long RESULT_PRESENT = 8L;
	private static final long RESULT_COMPRESSION_ID = 12L;
	private static final long RESULT_EXTERNAL = 16L;
	private static final long RESULT_TIMESTAMP = 24L;
	private static final long RESULT_OUTPUT_LEN = 32L;
	private static final long NBT_RESULT_STATUS = 0L;
	private static final long NBT_RESULT_ERROR_DOMAIN = 4L;
	private static final long NBT_RESULT_ERROR_KIND = 8L;
	private static final long NBT_RESULT_PRESENT = 12L;
	private static final long NBT_RESULT_COMPRESSION_ID = 16L;
	private static final long NBT_RESULT_EXTERNAL = 20L;
	private static final long NBT_RESULT_TIMESTAMP = 32L;
	private static final long NBT_RESULT_COMPRESSED_LEN = 40L;
	private static final long NBT_RESULT_DECOMPRESSED_LEN = 48L;
	private static final long NBT_RESULT_FINGERPRINT = 56L;
	private static final long NBT_RESULT_ERROR_OFFSET = 64L;
	private static final long TAPE_RESULT_STATUS = 0L;
	private static final long TAPE_RESULT_ERROR_DOMAIN = 4L;
	private static final long TAPE_RESULT_ERROR_KIND = 8L;
	private static final long TAPE_RESULT_PRESENT = 12L;
	private static final long TAPE_RESULT_COMPRESSION_ID = 16L;
	private static final long TAPE_RESULT_EXTERNAL = 20L;
	private static final long TAPE_RESULT_TIMESTAMP = 32L;
	private static final long TAPE_RESULT_COMPRESSED_LEN = 40L;
	private static final long TAPE_RESULT_DECOMPRESSED_LEN = 48L;
	private static final long TAPE_RESULT_FINGERPRINT = 56L;
	private static final long TAPE_RESULT_ERROR_OFFSET = 64L;
	private static final long TAPE_RESULT_OUTPUT_LEN = 72L;
	private static final long WRITE_RESULT_STATUS = 0L;
	private static final long WRITE_RESULT_ERROR_KIND = 4L;
	private static final long WRITE_RESULT_PRESENT = 8L;
	private static final long WRITE_RESULT_COMPRESSION_ID = 12L;
	private static final long WRITE_RESULT_EXTERNAL = 16L;
	private static final long WRITE_RESULT_SECTOR_COUNT = 20L;
	private static final long WRITE_RESULT_TIMESTAMP = 24L;
	private static final long WRITE_RESULT_SECTOR_OFFSET = 32L;
	private static final long WRITE_RESULT_PAYLOAD_LEN = 40L;
	private static final FunctionDescriptor OPEN_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor CLOSE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final FunctionDescriptor HANDLE_READ_CHUNK_PAYLOAD_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor HANDLE_READ_CHUNK_NBT_FINGERPRINT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor HANDLE_READ_CHUNK_NBT_TAPE_DESCRIPTOR = FunctionDescriptor.of(
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
	private static final FunctionDescriptor HANDLE_WRITE_CHUNK_PAYLOAD_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor HANDLE_WRITE_CHUNK_NBT_TAPE_DESCRIPTOR = FunctionDescriptor.of(
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
	private static final FunctionDescriptor HANDLE_DELETE_CHUNK_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor HANDLE_FLUSH_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final MethodHandle OPEN = downcall("mattmc_region_open", OPEN_DESCRIPTOR);
	private static final MethodHandle CLOSE = downcall("mattmc_region_close", CLOSE_DESCRIPTOR);
	private static final MethodHandle HANDLE_READ_CHUNK_PAYLOAD = downcall(
		"mattmc_region_handle_read_chunk_payload",
		HANDLE_READ_CHUNK_PAYLOAD_DESCRIPTOR
	);
	private static final MethodHandle HANDLE_READ_CHUNK_NBT_FINGERPRINT = downcall(
		"mattmc_region_handle_read_chunk_nbt_fingerprint",
		HANDLE_READ_CHUNK_NBT_FINGERPRINT_DESCRIPTOR
	);
	private static final MethodHandle HANDLE_READ_CHUNK_NBT_TAPE = downcall(
		"mattmc_region_handle_read_chunk_nbt_tape",
		HANDLE_READ_CHUNK_NBT_TAPE_DESCRIPTOR
	);
	private static final MethodHandle HANDLE_WRITE_CHUNK_PAYLOAD = downcall(
		"mattmc_region_handle_write_chunk_payload",
		HANDLE_WRITE_CHUNK_PAYLOAD_DESCRIPTOR
	);
	private static final MethodHandle HANDLE_WRITE_CHUNK_NBT_TAPE = downcall(
		"mattmc_region_handle_write_chunk_nbt_tape",
		HANDLE_WRITE_CHUNK_NBT_TAPE_DESCRIPTOR
	);
	private static final MethodHandle HANDLE_DELETE_CHUNK = downcall("mattmc_region_handle_delete_chunk", HANDLE_DELETE_CHUNK_DESCRIPTOR);
	private static final MethodHandle HANDLE_FLUSH = downcall("mattmc_region_handle_flush", HANDLE_FLUSH_DESCRIPTOR);

	private NativeRegionFileBridge() {
	}

	static long open(Path regionPath, boolean sync) throws IOException {
		byte[] path = pathBytes(regionPath);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(OPEN_RESULT_SIZE, 8);
			int status = (int)OPEN.invokeExact(pathSegment, (long)path.length, sync ? 1 : 0, resultSegment);
			OpenResult result = readOpenResult(resultSegment, status);
			if (result.status != OK || result.handle == 0L) {
				throw ioFailure("Open region", result.status, result.errorKind);
			}
			return result.handle;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Open region", throwable);
		}
	}

	static void close(long handle) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)CLOSE.invokeExact(handle, resultSegment);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Close region", result.status, result.errorKind);
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Close region", throwable);
		}
	}

	static PayloadResult readPayload(long handle, int chunkX, int chunkZ) throws IOException {
		Result query = readPayloadInto(handle, chunkX, chunkZ, MemorySegment.NULL, 0L);
		if (query.status != OUTPUT_TOO_SMALL && query.status != OK) {
			throw ioFailure("Read region chunk payload", query.status, query.errorKind);
		}
		if (query.status == OK && !query.present) {
			return new PayloadResult(query, new byte[0]);
		}

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = query.outputLength == 0L ? MemorySegment.NULL : arena.allocate(query.outputLength, 1);
			Result result = readPayloadInto(handle, chunkX, chunkZ, output, query.outputLength);
			if (result.status != OK) {
				throw ioFailure("Read region chunk payload", result.status, result.errorKind);
			}
			byte[] bytes = result.outputLength > 0L ? output.asSlice(0L, result.outputLength).toArray(ValueLayout.JAVA_BYTE) : new byte[0];
			return new PayloadResult(result, bytes);
		} catch (IOException exception) {
			throw exception;
		}
	}

	static NbtResult readNbtFingerprint(long handle, int chunkX, int chunkZ) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(NBT_RESULT_SIZE, 8);
			int status = (int)HANDLE_READ_CHUNK_NBT_FINGERPRINT.invokeExact(
				handle,
				chunkX,
				chunkZ,
				0L,
				0L,
				0,
				0,
				0L,
				0L,
				resultSegment
			);
			NbtResult result = readNbtResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Read region chunk NBT fingerprint", result.status, result.errorKind);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Read region chunk NBT fingerprint", throwable);
		}
	}

	static TapeResult readNbtTape(
		long handle,
		int chunkX,
		int chunkZ,
		long maxCompressedBytes,
		long maxDecompressedBytes,
		int maxDepth,
		int maxCollectionLength,
		long maxAllocationBytes,
		long maxTotalBytes
	) throws IOException {
		TapeMetadata query = readNbtTapeInto(
			handle,
			chunkX,
			chunkZ,
			MemorySegment.NULL,
			0L,
			maxCompressedBytes,
			maxDecompressedBytes,
			maxDepth,
			maxCollectionLength,
			maxAllocationBytes,
			maxTotalBytes
		);
		if (query.status != OUTPUT_TOO_SMALL && query.status != OK) {
			throw ioFailure("Read region chunk NBT tape", query.status, query.errorKind);
		}
		if (query.status == OK && !query.present) {
			return new TapeResult(query, new byte[0]);
		}

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = query.outputLength == 0L ? MemorySegment.NULL : arena.allocate(query.outputLength, 1);
			TapeMetadata result = readNbtTapeInto(
				handle,
				chunkX,
				chunkZ,
				output,
				query.outputLength,
				maxCompressedBytes,
				maxDecompressedBytes,
				maxDepth,
				maxCollectionLength,
				maxAllocationBytes,
				maxTotalBytes
			);
			if (result.status != OK) {
				throw ioFailure("Read region chunk NBT tape", result.status, result.errorKind);
			}
			byte[] bytes = result.outputLength > 0L ? output.asSlice(0L, result.outputLength).toArray(ValueLayout.JAVA_BYTE) : new byte[0];
			return new TapeResult(result, bytes);
		} catch (IOException exception) {
			throw exception;
		}
	}

	static WriteResult writeNbtTape(
		long handle,
		int chunkX,
		int chunkZ,
		int compressionId,
		byte[] tape,
		long maxCompressedBytes,
		long maxDecompressedBytes,
		int maxDepth,
		int maxCollectionLength,
		long maxAllocationBytes,
		long maxTotalBytes
	) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment tapeSegment = tape.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, tape);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)HANDLE_WRITE_CHUNK_NBT_TAPE.invokeExact(
				handle,
				chunkX,
				chunkZ,
				compressionId,
				tapeSegment,
				(long)tape.length,
				maxCompressedBytes,
				maxDecompressedBytes,
				maxDepth,
				maxCollectionLength,
				maxAllocationBytes,
				maxTotalBytes,
				resultSegment
			);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Write region chunk NBT tape", result.status, result.errorKind);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Write region chunk NBT tape", throwable);
		}
	}

	static WriteResult writePayload(long handle, int chunkX, int chunkZ, int compressionId, byte[] payload) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment payloadSegment = payload.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, payload);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)HANDLE_WRITE_CHUNK_PAYLOAD.invokeExact(
				handle,
				chunkX,
				chunkZ,
				compressionId,
				payloadSegment,
				(long)payload.length,
				resultSegment
			);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Write region chunk payload", result.status, result.errorKind);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Write region chunk payload", throwable);
		}
	}

	static WriteResult deleteChunk(long handle, int chunkX, int chunkZ) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)HANDLE_DELETE_CHUNK.invokeExact(handle, chunkX, chunkZ, resultSegment);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Delete region chunk", result.status, result.errorKind);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Delete region chunk", throwable);
		}
	}

	static void flush(long handle) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)HANDLE_FLUSH.invokeExact(handle, resultSegment);
			WriteResult result = readWriteResult(resultSegment, status);
			if (result.status != OK) {
				throw ioFailure("Flush region", result.status, result.errorKind);
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Flush region", throwable);
		}
	}

	private static Result readPayloadInto(long handle, int chunkX, int chunkZ, MemorySegment output, long outputCapacity) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)HANDLE_READ_CHUNK_PAYLOAD.invokeExact(handle, chunkX, chunkZ, output, outputCapacity, resultSegment);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Read region chunk payload", throwable);
		}
	}

	private static TapeMetadata readNbtTapeInto(
		long handle,
		int chunkX,
		int chunkZ,
		MemorySegment output,
		long outputCapacity,
		long maxCompressedBytes,
		long maxDecompressedBytes,
		int maxDepth,
		int maxCollectionLength,
		long maxAllocationBytes,
		long maxTotalBytes
	) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(TAPE_RESULT_SIZE, 8);
			int status = (int)HANDLE_READ_CHUNK_NBT_TAPE.invokeExact(
				handle,
				chunkX,
				chunkZ,
				output,
				outputCapacity,
				maxCompressedBytes,
				maxDecompressedBytes,
				maxDepth,
				maxCollectionLength,
				maxAllocationBytes,
				maxTotalBytes,
				resultSegment
			);
			return readTapeResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Read region chunk NBT tape", throwable);
		}
	}

	private static byte[] pathBytes(Path path) {
		return path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
	}

	private static Result readResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new Result(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN)
		);
	}

	private static OpenResult readOpenResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, OPEN_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new OpenResult(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, OPEN_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_LONG, OPEN_RESULT_HANDLE)
		);
	}

	private static NbtResult readNbtResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new NbtResult(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, NBT_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_LONG, NBT_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, NBT_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, NBT_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, NBT_RESULT_FINGERPRINT),
			resultSegment.get(ValueLayout.JAVA_LONG, NBT_RESULT_ERROR_OFFSET)
		);
	}

	private static TapeMetadata readTapeResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new TapeMetadata(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_ERROR_DOMAIN),
			resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, TAPE_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_FINGERPRINT),
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_ERROR_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, TAPE_RESULT_OUTPUT_LEN)
		);
	}

	private static WriteResult readWriteResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new WriteResult(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_COMPRESSION_ID),
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_EXTERNAL) != 0,
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_SECTOR_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_SECTOR_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_PAYLOAD_LEN)
		);
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
	}

	private static IOException ioFailure(String action, int status, int errorKind) {
		return new IOException(action + " failed with native region status " + status + " error " + errorKind);
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

	record PayloadResult(Result result, byte[] bytes) {
	}

	record TapeResult(TapeMetadata result, byte[] bytes) {
	}

	record OpenResult(int status, int errorKind, long handle) {
	}

	record Result(int status, int errorKind, boolean present, int compressionId, boolean external, long timestamp, long outputLength) {
	}

	record NbtResult(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long fingerprint,
		long errorOffset
	) {
	}

	record TapeMetadata(
		int status,
		int errorDomain,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		long timestamp,
		long compressedLength,
		long decompressedLength,
		long fingerprint,
		long errorOffset,
		long outputLength
	) {
	}

	record WriteResult(
		int status,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		int sectorCount,
		long timestamp,
		long sectorOffset,
		long payloadLength
	) {
	}
}
