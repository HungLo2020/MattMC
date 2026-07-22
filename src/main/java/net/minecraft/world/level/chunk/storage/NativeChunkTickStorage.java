package net.minecraft.world.level.chunk.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bulk bridge and tape codec for Rust-owned scheduled-tick chunk records.
 *
 * <p>Supported current-version chunk-section reads and writes use this typed
 * representation through {@link NativeChunkSectionStorage}. The direct region
 * operations in this class remain test/dev helpers. Java keeps registry
 * resolution and scheduler ownership.
 */
public final class NativeChunkTickStorage implements Closeable {
	public static final int OK = 0;
	private static final int OUTPUT_TOO_SMALL = -4;
	private static final int MAGIC = 0x4B54434D;
	private static final int VERSION = 1;
	private static final long RESULT_SIZE = 88L;
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
	private static final long RESULT_BLOCK_TICK_COUNT = 40L;
	private static final long RESULT_FLUID_TICK_COUNT = 44L;
	private static final long RESULT_TIMESTAMP = 48L;
	private static final long RESULT_COMPRESSED_LEN = 56L;
	private static final long RESULT_DECOMPRESSED_LEN = 64L;
	private static final long RESULT_OUTPUT_LEN = 72L;
	private static final long RESULT_ERROR_OFFSET = 80L;
	private static final long WRITE_RESULT_SIZE = 80L;
	private static final long WRITE_RESULT_STATUS = 0L;
	private static final long WRITE_RESULT_ERROR_DOMAIN = 4L;
	private static final long WRITE_RESULT_ERROR_KIND = 8L;
	private static final long WRITE_RESULT_PRESENT = 12L;
	private static final long WRITE_RESULT_COMPRESSION_ID = 16L;
	private static final long WRITE_RESULT_EXTERNAL = 20L;
	private static final long WRITE_RESULT_SECTOR_COUNT = 24L;
	private static final long WRITE_RESULT_TIMESTAMP = 32L;
	private static final long WRITE_RESULT_SECTOR_OFFSET = 40L;
	private static final long WRITE_RESULT_COMPRESSED_LEN = 48L;
	private static final long WRITE_RESULT_DECOMPRESSED_LEN = 56L;
	private static final long WRITE_RESULT_RESIDUAL_TAPE_LEN = 64L;
	private static final long WRITE_RESULT_TICK_TAPE_LEN = 72L;
	private static final int INITIAL_OUTPUT_CAPACITY = 16384;
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
		"mattmc_chunk_decode_scheduled_ticks_from_region",
		DECODE_DESCRIPTOR
	);
	private static final MethodHandle WRITE = NativeLibraryLoader.downcallHandle(
		"mattmc_rust",
		"mattmc_chunk_write_scheduled_ticks_to_region",
		WRITE_DESCRIPTOR
	);
	private long handle;

	private NativeChunkTickStorage(long handle) {
		this.handle = handle;
	}

	public static NativeChunkTickStorage open(Path regionPath) throws IOException {
		return new NativeChunkTickStorage(NativeRegionFileBridge.open(regionPath, false));
	}

	public DecodeResult decodeChunk(int chunkX, int chunkZ) throws IOException {
		if (this.handle == 0L) {
			throw new IOException("Decode chunk ticks failed with closed native region handle");
		}
		long capacity = INITIAL_OUTPUT_CAPACITY;
		for (int attempt = 0; attempt < 3; attempt++) {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment output = capacity == 0L ? MemorySegment.NULL : arena.allocate(capacity, 1);
				MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
				Result result = decodeInto(this.handle, chunkX, chunkZ, output, capacity, resultSegment);
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
				throw new DecodeException("Decode chunk ticks", result);
			}
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			Result result = decodeInto(this.handle, chunkX, chunkZ, MemorySegment.NULL, 0L, resultSegment);
			throw new DecodeException("Decode chunk ticks", result);
		}
	}

	public WriteResult writeChunk(
		int chunkX,
		int chunkZ,
		int compressionId,
		CompoundTag residual,
		TickData ticks
	) throws IOException {
		return writeChunk(this.handle, chunkX, chunkZ, compressionId, residual, ticks);
	}

	WriteResult writeChunkTapes(
		int chunkX,
		int chunkZ,
		int compressionId,
		byte[] residualTape,
		byte[] tickTape
	) throws IOException {
		return writeChunkTapes(this.handle, chunkX, chunkZ, compressionId, residualTape, tickTape);
	}

	static WriteResult writeChunk(
		long handle,
		int chunkX,
		int chunkZ,
		int compressionId,
		CompoundTag residual,
		TickData ticks
	) throws IOException {
		if (handle == 0L) {
			throw new IOException("Write chunk ticks failed with closed native region handle");
		}
		byte[] residualTape = NativeNbtRegionAccess.writeTape(residual);
		byte[] tickTape = encodeTickTape(ticks);
		return writeChunkTapes(handle, chunkX, chunkZ, compressionId, residualTape, tickTape);
	}

	static WriteResult writeChunkTapes(
		long handle,
		int chunkX,
		int chunkZ,
		int compressionId,
		byte[] residualTape,
		byte[] tickTape
	) throws IOException {
		if (handle == 0L) {
			throw new IOException("Write chunk ticks failed with closed native region handle");
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment residualSegment = residualTape.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, residualTape);
			MemorySegment tickSegment = tickTape.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, tickTape);
			MemorySegment resultSegment = arena.allocate(WRITE_RESULT_SIZE, 8);
			int status = (int)WRITE.invokeExact(
				handle,
				chunkX,
				chunkZ,
				compressionId,
				residualSegment,
				(long)residualTape.length,
				tickSegment,
				(long)tickTape.length,
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
				throw new WriteException("Write chunk ticks", result);
			}
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Write chunk ticks", throwable);
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

	public static TickData fromSavedTicks(
		int dataVersion,
		ChunkPos pos,
		List<SavedTick<Block>> blockTicks,
		List<SavedTick<Fluid>> fluidTicks
	) throws IOException {
		List<TickRecord> blocks = new ArrayList<>(blockTicks.size());
		for (SavedTick<Block> tick : blockTicks) {
			blocks.add(fromBlockTick(tick));
		}
		List<TickRecord> fluids = new ArrayList<>(fluidTicks.size());
		for (SavedTick<Fluid> tick : fluidTicks) {
			fluids.add(fromFluidTick(tick));
		}
		return new TickData(dataVersion, pos.x, pos.z, List.copyOf(blocks), List.copyOf(fluids));
	}

	public static List<SavedTick<Block>> resolveBlockTicks(TickData ticks) throws IOException {
		List<SavedTick<Block>> resolved = new ArrayList<>(ticks.blockTicks.size());
		for (TickRecord tick : ticks.blockTicks) {
			ResourceLocation location = parseLocation(tick.id);
			if (!BuiltInRegistries.BLOCK.containsKey(location)) {
				throw new IOException("Unknown block scheduled-tick id: " + tick.id);
			}
			Block block = BuiltInRegistries.BLOCK.getValue(location);
			resolved.add(new SavedTick<>(block, tick.pos(), tick.delay, TickPriority.byValue(tick.priority)));
		}
		return List.copyOf(resolved);
	}

	public static List<SavedTick<Fluid>> resolveFluidTicks(TickData ticks) throws IOException {
		List<SavedTick<Fluid>> resolved = new ArrayList<>(ticks.fluidTicks.size());
		for (TickRecord tick : ticks.fluidTicks) {
			ResourceLocation location = parseLocation(tick.id);
			if (!BuiltInRegistries.FLUID.containsKey(location)) {
				throw new IOException("Unknown fluid scheduled-tick id: " + tick.id);
			}
			Fluid fluid = BuiltInRegistries.FLUID.getValue(location);
			resolved.add(new SavedTick<>(fluid, tick.pos(), tick.delay, TickPriority.byValue(tick.priority)));
		}
		return List.copyOf(resolved);
	}

	private static ResourceLocation parseLocation(String id) throws IOException {
		try {
			return ResourceLocation.parse(id);
		} catch (RuntimeException exception) {
			throw new IOException("Malformed scheduled-tick id: " + id, exception);
		}
	}

	private static TickRecord fromBlockTick(SavedTick<Block> tick) throws IOException {
		ResourceLocation location = BuiltInRegistries.BLOCK.getKey(tick.type());
		if (location == null) {
			throw new IOException("Cannot write unregistered block scheduled tick");
		}
		return fromSavedTick(tick, location.toString());
	}

	private static TickRecord fromFluidTick(SavedTick<Fluid> tick) throws IOException {
		ResourceLocation location = BuiltInRegistries.FLUID.getKey(tick.type());
		if (location == null) {
			throw new IOException("Cannot write unregistered fluid scheduled tick");
		}
		return fromSavedTick(tick, location.toString());
	}

	private static TickRecord fromSavedTick(SavedTick<?> tick, String id) {
		return new TickRecord(id, tick.pos().getX(), tick.pos().getY(), tick.pos().getZ(), tick.delay(), tick.priority().getValue());
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
			throw nativeFailure("Decode chunk ticks", throwable);
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
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_BLOCK_TICK_COUNT),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_FLUID_TICK_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_ERROR_OFFSET)
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
			resultSegment.get(ValueLayout.JAVA_INT, WRITE_RESULT_SECTOR_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_TIMESTAMP),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_SECTOR_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_COMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_DECOMPRESSED_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_RESIDUAL_TAPE_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, WRITE_RESULT_TICK_TAPE_LEN)
		);
	}

	static TickData decodeTape(byte[] bytes) throws IOException {
		if (bytes.length == 0) {
			return TickData.empty();
		}
		TapeReader reader = new TapeReader(bytes);
		if (reader.readIntLE() != MAGIC) {
			throw new IOException("Invalid scheduled-tick tape magic");
		}
		int version = reader.readUnsignedShortLE();
		if (version < 1 || version > VERSION) {
			throw new IOException("Unsupported scheduled-tick tape version");
		}
		reader.readUnsignedShortLE();
		int dataVersion = reader.readIntLE();
		int chunkX = reader.readIntLE();
		int chunkZ = reader.readIntLE();
		int blockCount = reader.readIntLE();
		int fluidCount = reader.readIntLE();
		List<TickRecord> blockTicks = new ArrayList<>(blockCount);
		for (int i = 0; i < blockCount; i++) {
			blockTicks.add(reader.readTick());
		}
		List<TickRecord> fluidTicks = new ArrayList<>(fluidCount);
		for (int i = 0; i < fluidCount; i++) {
			fluidTicks.add(reader.readTick());
		}
		if (!reader.done()) {
			throw new IOException("Trailing bytes in scheduled-tick tape");
		}
		return new TickData(dataVersion, chunkX, chunkZ, List.copyOf(blockTicks), List.copyOf(fluidTicks));
	}

	static byte[] encodeTickTape(TickData ticks) throws IOException {
		TapeWriter writer = new TapeWriter();
		writer.writeIntLE(MAGIC);
		writer.writeShortLE(VERSION);
		writer.writeShortLE(0);
		writer.writeIntLE(ticks.dataVersion);
		writer.writeIntLE(ticks.chunkX);
		writer.writeIntLE(ticks.chunkZ);
		writer.writeIntLE(ticks.blockTicks.size());
		writer.writeIntLE(ticks.fluidTicks.size());
		for (TickRecord tick : ticks.blockTicks) {
			writer.writeTick(tick);
		}
		for (TickRecord tick : ticks.fluidTicks) {
			writer.writeTick(tick);
		}
		return writer.toByteArray();
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
			super(action + " failed with native chunk status " + result.status + " domain " + result.errorDomain + " error " + result.errorKind);
			this.result = result;
		}

		public Result result() {
			return this.result;
		}
	}

	public static final class WriteException extends IOException {
		private final WriteResult result;

		private WriteException(String action, WriteResult result) {
			super(action + " failed with native chunk status " + result.status + " domain " + result.errorDomain + " error " + result.errorKind);
			this.result = result;
		}

		public WriteResult result() {
			return this.result;
		}
	}

	public record DecodeResult(Result result, TickData ticks) {
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
		int blockTickCount,
		int fluidTickCount,
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
		int sectorCount,
		long timestamp,
		long sectorOffset,
		long compressedLength,
		long decompressedLength,
		long residualTapeLength,
		long tickTapeLength
	) {
	}

	public record TickData(int dataVersion, int chunkX, int chunkZ, List<TickRecord> blockTicks, List<TickRecord> fluidTicks) {
		public static TickData empty() {
			return new TickData(0, 0, 0, List.of(), List.of());
		}

		public boolean isPresent() {
			return this.dataVersion != 0;
		}
	}

	public record TickRecord(String id, int x, int y, int z, int delay, int priority) {
		private BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}
	}

	private static final class TapeReader {
		private final byte[] bytes;
		private int cursor;

		private TapeReader(byte[] bytes) {
			this.bytes = bytes;
		}

		TickRecord readTick() throws IOException {
			return new TickRecord(this.readJavaString(), this.readIntLE(), this.readIntLE(), this.readIntLE(), this.readIntLE(), this.readIntLE());
		}

		int readUnsignedShortLE() throws IOException {
			this.require(2);
			int value = this.bytes[this.cursor] & 0xFF | (this.bytes[this.cursor + 1] & 0xFF) << 8;
			this.cursor += 2;
			return value;
		}

		int readIntLE() throws IOException {
			this.require(4);
			int value = this.bytes[this.cursor] & 0xFF
				| (this.bytes[this.cursor + 1] & 0xFF) << 8
				| (this.bytes[this.cursor + 2] & 0xFF) << 16
				| this.bytes[this.cursor + 3] << 24;
			this.cursor += 4;
			return value;
		}

		String readJavaString() throws IOException {
			int unitCount = this.readIntLE();
			if (unitCount < 0) {
				throw new IOException("Negative scheduled-tick string length");
			}
			this.require(Math.multiplyExact(unitCount, 2));
			char[] chars = new char[unitCount];
			for (int i = 0; i < unitCount; i++) {
				chars[i] = (char)(this.bytes[this.cursor] & 0xFF | (this.bytes[this.cursor + 1] & 0xFF) << 8);
				this.cursor += 2;
			}
			return new String(chars);
		}

		boolean done() {
			return this.cursor == this.bytes.length;
		}

		private void require(int length) throws IOException {
			if (length < 0 || this.bytes.length - this.cursor < length) {
				throw new IOException("Truncated scheduled-tick tape");
			}
		}
	}

	private static final class TapeWriter {
		private byte[] bytes = new byte[1024];
		private int cursor;

		byte[] toByteArray() {
			return Arrays.copyOf(this.bytes, this.cursor);
		}

		void writeTick(TickRecord tick) {
			this.writeJavaString(tick.id);
			this.writeIntLE(tick.x);
			this.writeIntLE(tick.y);
			this.writeIntLE(tick.z);
			this.writeIntLE(tick.delay);
			this.writeIntLE(tick.priority);
		}

		void writeJavaString(String value) {
			char[] chars = value.toCharArray();
			this.writeIntLE(chars.length);
			this.ensure(chars.length * 2);
			for (char c : chars) {
				this.bytes[this.cursor++] = (byte)c;
				this.bytes[this.cursor++] = (byte)(c >>> 8);
			}
		}

		void writeShortLE(int value) {
			this.ensure(2);
			this.bytes[this.cursor++] = (byte)value;
			this.bytes[this.cursor++] = (byte)(value >>> 8);
		}

		void writeIntLE(int value) {
			this.ensure(4);
			this.bytes[this.cursor++] = (byte)value;
			this.bytes[this.cursor++] = (byte)(value >>> 8);
			this.bytes[this.cursor++] = (byte)(value >>> 16);
			this.bytes[this.cursor++] = (byte)(value >>> 24);
		}

		private void ensure(int length) {
			int required = this.cursor + length;
			if (required <= this.bytes.length) {
				return;
			}
			int next = this.bytes.length;
			while (next < required) {
				next = Math.multiplyExact(next, 2);
			}
			this.bytes = Arrays.copyOf(this.bytes, next);
		}
	}
}
