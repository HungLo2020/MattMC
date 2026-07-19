package net.minecraft.world.level.chunk.storage;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class NativeRegionFile {
	static final int OK = 0;
	static final int OUTPUT_TOO_SMALL = -4;
	static final int ERROR_MISSING_EXTERNAL_FILE = 15;
	private static final long RESULT_SIZE = 40L;
	private static final long NBT_RESULT_SIZE = 72L;
	private static final long WRITE_RESULT_SIZE = 48L;
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
	private static final long WRITE_RESULT_STATUS = 0L;
	private static final long WRITE_RESULT_ERROR_KIND = 4L;
	private static final long WRITE_RESULT_PRESENT = 8L;
	private static final long WRITE_RESULT_COMPRESSION_ID = 12L;
	private static final long WRITE_RESULT_EXTERNAL = 16L;
	private static final long WRITE_RESULT_SECTOR_COUNT = 20L;
	private static final long WRITE_RESULT_TIMESTAMP = 24L;
	private static final long WRITE_RESULT_SECTOR_OFFSET = 32L;
	private static final long WRITE_RESULT_PAYLOAD_LEN = 40L;
	private static final FunctionDescriptor READ_CHUNK_PAYLOAD_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor READ_CHUNK_NBT_FINGERPRINT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
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
	private static final FunctionDescriptor WRITE_CHUNK_PAYLOAD_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor DELETE_CHUNK_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor FLUSH_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final MethodHandle READ_CHUNK_PAYLOAD = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_region_read_chunk_payload",
		READ_CHUNK_PAYLOAD_DESCRIPTOR
	);
	private static final MethodHandle READ_CHUNK_NBT_FINGERPRINT = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_region_read_chunk_nbt_fingerprint",
		READ_CHUNK_NBT_FINGERPRINT_DESCRIPTOR
	);
	private static final MethodHandle WRITE_CHUNK_PAYLOAD = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_region_write_chunk_payload",
		WRITE_CHUNK_PAYLOAD_DESCRIPTOR
	);
	private static final MethodHandle DELETE_CHUNK = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_region_delete_chunk",
		DELETE_CHUNK_DESCRIPTOR
	);
	private static final MethodHandle FLUSH = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_region_flush",
		FLUSH_DESCRIPTOR
	);

	private NativeRegionFile() {
	}

	static PayloadResult readPayload(Path regionPath, int chunkX, int chunkZ) {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		Result query = readPayloadInto(path, chunkX, chunkZ, MemorySegment.NULL, 0);
		if (query.status != OUTPUT_TOO_SMALL && query.status != OK) {
			return new PayloadResult(query, new byte[0]);
		}
		if (query.status == OK && !query.present) {
			return new PayloadResult(query, new byte[0]);
		}

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = query.outputLength == 0 ? MemorySegment.NULL : arena.allocate(query.outputLength, 1);
			Result result = readPayloadInto(path, chunkX, chunkZ, output, query.outputLength);
			byte[] bytes = result.status == OK && result.outputLength > 0
				? output.asSlice(0, result.outputLength).toArray(ValueLayout.JAVA_BYTE)
				: new byte[0];
			return new PayloadResult(result, bytes);
		}
	}

	static NbtResult readNbtFingerprint(Path regionPath, int chunkX, int chunkZ) {
		return readNbtFingerprint(regionPath, chunkX, chunkZ, 0, 0, 0, 0, 0, 0);
	}

	static NbtResult readNbtFingerprint(
		Path regionPath,
		int chunkX,
		int chunkZ,
		long maxCompressedBytes,
		long maxDecompressedBytes,
		int maxDepth,
		int maxCollectionLength,
		long maxAllocationBytes,
		long maxTotalBytes
	) {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(NBT_RESULT_SIZE, 8);
			int status = (int)READ_CHUNK_NBT_FINGERPRINT.invokeExact(
				pathSegment,
				(long)path.length,
				chunkX,
				chunkZ,
				maxCompressedBytes,
				maxDecompressedBytes,
				maxDepth,
				maxCollectionLength,
				maxAllocationBytes,
				maxTotalBytes,
				resultSegment
			);
			return readNbtResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Read region chunk NBT fingerprint", throwable);
		}
	}

	static WriteResult writePayload(Path regionPath, int chunkX, int chunkZ, int compressionId, byte[] payload) {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment payloadSegment = payload.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, payload);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)WRITE_CHUNK_PAYLOAD.invokeExact(
				pathSegment,
				(long)path.length,
				chunkX,
				chunkZ,
				compressionId,
				payloadSegment,
				(long)payload.length,
				resultSegment
			);
			return readWriteResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Write region chunk payload", throwable);
		}
	}

	static WriteResult deleteChunk(Path regionPath, int chunkX, int chunkZ) {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)DELETE_CHUNK.invokeExact(pathSegment, (long)path.length, chunkX, chunkZ, resultSegment);
			return readWriteResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Delete region chunk", throwable);
		}
	}

	static WriteResult flush(Path regionPath) {
		byte[] path = regionPath.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)FLUSH.invokeExact(pathSegment, (long)path.length, resultSegment);
			return readWriteResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Flush region", throwable);
		}
	}

	private static Result readPayloadInto(byte[] path, int chunkX, int chunkZ, MemorySegment output, long outputCapacity) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pathSegment = path.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, path);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)READ_CHUNK_PAYLOAD.invokeExact(
				pathSegment,
				(long)path.length,
				chunkX,
				chunkZ,
				output,
				outputCapacity,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Read region chunk payload", throwable);
		}
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

	record Result(
		int status,
		int errorKind,
		boolean present,
		int compressionId,
		boolean external,
		long timestamp,
		long outputLength
	) {
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
