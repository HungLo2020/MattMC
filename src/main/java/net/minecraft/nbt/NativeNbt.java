package net.minecraft.nbt;

import net.minecraft.util.NativeLibraryLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;

final class NativeNbt {
	static final int OK = 0;
	static final int ERR_OUTPUT_TOO_SMALL = -4;
	static final int FORMAT_AUTO = -1;
	static final int FORMAT_RAW = 0;
	static final int FORMAT_GZIP = 1;
	static final int FORMAT_ZLIB = 2;
	private static final long RESULT_SIZE = 32L;
	private static final long RESULT_STATUS = 0L;
	private static final long RESULT_ERROR_KIND = 4L;
	private static final long RESULT_OFFSET = 8L;
	private static final long RESULT_FINGERPRINT = 16L;
	private static final long RESULT_OUTPUT_LEN = 24L;
	private static final Limits DEFAULT_LIMITS = new Limits(0, 0, 0, 0);
	private static final CompressionLimits DEFAULT_COMPRESSION_LIMITS = new CompressionLimits(0, 0);
	private static final int INITIAL_OUTPUT_CAPACITY = 16 * 1024;
	private static final ThreadLocal<NativeScratch> SCRATCH = ThreadLocal.withInitial(NativeScratch::new);
	private static final FunctionDescriptor PARSE_FINGERPRINT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor REENCODE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor DECODE_FINGERPRINT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor RECOMPRESS_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
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
	private static final FunctionDescriptor DECODE_TO_TAPE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor ENCODE_FROM_TAPE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final MethodHandle PARSE_FINGERPRINT = downcall("mattmc_nbt_parse_fingerprint", PARSE_FINGERPRINT_DESCRIPTOR);
	private static final MethodHandle REENCODE = downcall("mattmc_nbt_reencode", REENCODE_DESCRIPTOR);
	private static final MethodHandle DECODE_FINGERPRINT = downcall("mattmc_nbt_decode_fingerprint", DECODE_FINGERPRINT_DESCRIPTOR);
	private static final MethodHandle RECOMPRESS = downcall("mattmc_nbt_recompress", RECOMPRESS_DESCRIPTOR);
	private static final MethodHandle DECODE_TO_TAPE = downcall("mattmc_nbt_decode_to_tape", DECODE_TO_TAPE_DESCRIPTOR);
	private static final MethodHandle ENCODE_FROM_TAPE = downcall("mattmc_nbt_encode_from_tape", ENCODE_FROM_TAPE_DESCRIPTOR);

	private NativeNbt() {
	}

	static Result fingerprint(byte[] input) {
		return fingerprint(input, DEFAULT_LIMITS);
	}

	static Result fingerprint(byte[] input, Limits limits) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, input);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)PARSE_FINGERPRINT.invokeExact(
				inputSegment,
				(long)input.length,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Parse NBT fingerprint", throwable);
		}
	}

	static ReencodeResult reencode(byte[] input) {
		return reencode(input, DEFAULT_LIMITS);
	}

	static ReencodeResult reencode(byte[] input, Limits limits) {
		Result query = reencodeInto(input, limits, MemorySegment.NULL, 0);
		if (query.status != ERR_OUTPUT_TOO_SMALL && query.status != OK) {
			return new ReencodeResult(query, new byte[0]);
		}

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = query.outputLength == 0 ? MemorySegment.NULL : arena.allocate(query.outputLength, 1);
			Result result = reencodeInto(input, limits, output, query.outputLength);
			byte[] bytes = result.status == OK && query.outputLength > 0
				? output.asSlice(0, query.outputLength).toArray(ValueLayout.JAVA_BYTE)
				: new byte[0];
			return new ReencodeResult(result, bytes);
		}
	}

	static Result compressedFingerprint(byte[] input) {
		return compressedFingerprint(input, FORMAT_AUTO, DEFAULT_COMPRESSION_LIMITS, DEFAULT_LIMITS);
	}

	static Result compressedFingerprint(byte[] input, int inputCompression, CompressionLimits compressionLimits, Limits limits) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, input);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)DECODE_FINGERPRINT.invokeExact(
				inputSegment,
				(long)input.length,
				inputCompression,
				compressionLimits.maxCompressedBytes,
				compressionLimits.maxDecompressedBytes,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Parse compressed NBT fingerprint", throwable);
		}
	}

	static ReencodeResult recompress(byte[] input, int inputCompression, int outputCompression) {
		return recompress(input, inputCompression, outputCompression, DEFAULT_COMPRESSION_LIMITS, DEFAULT_LIMITS);
	}

	static ReencodeResult recompress(
		byte[] input,
		int inputCompression,
		int outputCompression,
		CompressionLimits compressionLimits,
		Limits limits
	) {
		Result query = recompressInto(input, inputCompression, outputCompression, compressionLimits, limits, MemorySegment.NULL, 0);
		if (query.status != ERR_OUTPUT_TOO_SMALL && query.status != OK) {
			return new ReencodeResult(query, new byte[0]);
		}

		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = query.outputLength == 0 ? MemorySegment.NULL : arena.allocate(query.outputLength, 1);
			Result result = recompressInto(input, inputCompression, outputCompression, compressionLimits, limits, output, query.outputLength);
			byte[] bytes = result.status == OK && query.outputLength > 0
				? output.asSlice(0, query.outputLength).toArray(ValueLayout.JAVA_BYTE)
				: new byte[0];
			return new ReencodeResult(result, bytes);
		}
	}

	static CompoundTag read(byte[] input, int inputCompression, NbtAccounter accounter) throws IOException {
		ReencodeResult tape = decodeToTape(input, inputCompression, compressionLimits(accounter), limits(accounter));
		checkOk("Read NBT with Rust", tape.result());
		return TapeReader.readRoot(tape.bytes());
	}

	static byte[] write(CompoundTag tag, int outputCompression) throws IOException {
		byte[] tape = TapeWriter.writeRoot(tag);
		ReencodeResult encoded = encodeFromTape(tape, outputCompression, DEFAULT_COMPRESSION_LIMITS, DEFAULT_LIMITS);
		checkOk("Write NBT with Rust", encoded.result());
		return encoded.bytes();
	}

	static CompoundTag readTape(byte[] tape) throws IOException {
		return TapeReader.readRoot(tape);
	}

	static byte[] writeTape(CompoundTag tag) throws IOException {
		return TapeWriter.writeRoot(tag);
	}

	static ReencodeResult decodeToTape(byte[] input, int inputCompression, CompressionLimits compressionLimits, Limits limits) {
		long initialCapacity = Math.max(INITIAL_OUTPUT_CAPACITY, Math.min((long)Integer.MAX_VALUE, Math.max(1L, (long)input.length * 4L)));
		return callWithOutputBuffer(initialCapacity, (output, outputCapacity) ->
			decodeToTapeInto(input, inputCompression, compressionLimits, limits, output, outputCapacity)
		);
	}

	static ReencodeResult encodeFromTape(byte[] tape, int outputCompression, CompressionLimits compressionLimits, Limits limits) {
		long initialCapacity = Math.max(INITIAL_OUTPUT_CAPACITY, Math.min((long)Integer.MAX_VALUE, Math.max(1L, tape.length)));
		return callWithOutputBuffer(initialCapacity, (output, outputCapacity) ->
			encodeFromTapeInto(tape, outputCompression, compressionLimits, limits, output, outputCapacity)
		);
	}

	private static ReencodeResult callWithOutputBuffer(long initialCapacity, OutputCall call) {
		NativeScratch scratch = SCRATCH.get();
		long capacity = initialCapacity;
		for (int attempt = 0; attempt < 3; attempt++) {
			MemorySegment output = capacity == 0L ? MemorySegment.NULL : scratch.output(capacity);
			Result result = call.invoke(output, capacity);
			if (result.status == OK) {
				return new ReencodeResult(result, scratch.copy(result.outputLength));
			}
			if (result.status != ERR_OUTPUT_TOO_SMALL) {
				return new ReencodeResult(result, new byte[0]);
			}
			if (result.outputLength <= capacity) {
				return new ReencodeResult(result, new byte[0]);
			}
			capacity = result.outputLength;
		}
		Result result = call.invoke(MemorySegment.NULL, 0L);
		return new ReencodeResult(result, new byte[0]);
	}

	private static Result decodeToTapeInto(
		byte[] input,
		int inputCompression,
		CompressionLimits compressionLimits,
		Limits limits,
		MemorySegment output,
		long outputCapacity
	) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, input);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)DECODE_TO_TAPE.invokeExact(
				inputSegment,
				(long)input.length,
				output,
				outputCapacity,
				inputCompression,
				compressionLimits.maxCompressedBytes,
				compressionLimits.maxDecompressedBytes,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Decode NBT to tape", throwable);
		}
	}

	private static Result encodeFromTapeInto(
		byte[] tape,
		int outputCompression,
		CompressionLimits compressionLimits,
		Limits limits,
		MemorySegment output,
		long outputCapacity
	) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, tape);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)ENCODE_FROM_TAPE.invokeExact(
				inputSegment,
				(long)tape.length,
				output,
				outputCapacity,
				outputCompression,
				compressionLimits.maxCompressedBytes,
				compressionLimits.maxDecompressedBytes,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Encode NBT from tape", throwable);
		}
	}

	private static Result recompressInto(
		byte[] input,
		int inputCompression,
		int outputCompression,
		CompressionLimits compressionLimits,
		Limits limits,
		MemorySegment output,
		long outputCapacity
	) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, input);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)RECOMPRESS.invokeExact(
				inputSegment,
				(long)input.length,
				output,
				outputCapacity,
				inputCompression,
				outputCompression,
				compressionLimits.maxCompressedBytes,
				compressionLimits.maxDecompressedBytes,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Recompress NBT", throwable);
		}
	}

	private static Result reencodeInto(byte[] input, Limits limits, MemorySegment output, long outputCapacity) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment inputSegment = inputSegment(arena, input);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)REENCODE.invokeExact(
				inputSegment,
				(long)input.length,
				output,
				outputCapacity,
				limits.maxDepth,
				limits.maxCollectionLength,
				limits.maxAllocationBytes,
				limits.maxTotalBytes,
				resultSegment
			);
			return readResult(resultSegment, status);
		} catch (Throwable throwable) {
			throw nativeFailure("Re-encode NBT", throwable);
		}
	}

	private static void checkOk(String action, Result result) throws IOException {
		if (result.status != OK) {
			throw new IOException(action + " failed with native status " + result.status + " error " + result.errorKind + " at offset " + result.offset);
		}
	}

	private static Limits limits(NbtAccounter accounter) {
		return new Limits(accounter.maxDepthLimit(), Integer.MAX_VALUE, accounter.quota(), accounter.quota());
	}

	private static CompressionLimits compressionLimits(NbtAccounter accounter) {
		return new CompressionLimits(0, accounter.quota());
	}

	private static MemorySegment inputSegment(Arena arena, byte[] input) {
		return input.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, input);
	}

	private static Result readResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new Result(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OFFSET),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_FINGERPRINT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN)
		);
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

	private static final class TapeReader {
		private static final int MAGIC = 0x5442544E;
		private static final int VERSION = 1;
		private static final int HEADER_LEN = 8;
		private static final int RECORD_LEN = 24;
		private final byte[] input;
		private int cursor;

		private TapeReader(byte[] input) {
			this.input = input;
		}

		static CompoundTag readRoot(byte[] input) throws IOException {
			TapeReader reader = new TapeReader(input);
			if (reader.readIntLE() != MAGIC) {
				throw new IOException("Invalid NBT tape magic");
			}
			if (reader.readUnsignedShortLE() != VERSION) {
				throw new IOException("Unsupported NBT tape version");
			}
			reader.readUnsignedShortLE();
			NamedTag root = reader.readTag();
			if (!(root.tag instanceof CompoundTag compoundTag)) {
				throw new IOException("NBT tape root must be a compound");
			}
			if (reader.cursor != input.length) {
				throw new IOException("Trailing data after NBT tape at offset " + reader.cursor);
			}
			return compoundTag;
		}

		private NamedTag readTag() throws IOException {
			int recordOffset = this.cursor;
			byte tagId = this.readByte();
			byte listElementId = this.readByte();
			this.readUnsignedShortLE();
			int nameLength = this.readLength();
			int count = this.readLength();
			this.readIntLE();
			long scalar = this.readLongLE();
			String name = this.readStringUnits(nameLength);
			Tag tag = switch (tagId) {
				case Tag.TAG_BYTE -> ByteTag.valueOf((byte)scalar);
				case Tag.TAG_SHORT -> ShortTag.valueOf((short)scalar);
				case Tag.TAG_INT -> IntTag.valueOf((int)scalar);
				case Tag.TAG_LONG -> LongTag.valueOf(scalar);
				case Tag.TAG_FLOAT -> FloatTag.valueOf(Float.intBitsToFloat((int)scalar));
				case Tag.TAG_DOUBLE -> DoubleTag.valueOf(Double.longBitsToDouble(scalar));
				case Tag.TAG_BYTE_ARRAY -> new ByteArrayTag(this.readBytes(count));
				case Tag.TAG_STRING -> StringTag.valueOf(this.readStringUnits(count));
				case Tag.TAG_LIST -> this.readList(listElementId, count);
				case Tag.TAG_COMPOUND -> this.readCompound(count);
				case Tag.TAG_INT_ARRAY -> new IntArrayTag(this.readInts(count));
				case Tag.TAG_LONG_ARRAY -> new LongArrayTag(this.readLongs(count));
				default -> throw new IOException("Invalid NBT tape tag type " + tagId + " at offset " + recordOffset);
			};
			return new NamedTag(name, tag);
		}

		private ListTag readList(byte elementId, int count) throws IOException {
			if (elementId == Tag.TAG_END && count > 0) {
				throw new IOException("Non-empty NBT tape list is missing an element type");
			}
			ListTag list = new ListTag();
			for (int i = 0; i < count; i++) {
				NamedTag element = this.readTag();
				if (element.tag.getId() != elementId) {
					throw new IOException("NBT tape list element type mismatch at index " + i);
				}
				list.addAndUnwrap(element.tag);
			}
			return list;
		}

		private CompoundTag readCompound(int count) throws IOException {
			CompoundTag compoundTag = new CompoundTag();
			for (int i = 0; i < count; i++) {
				NamedTag child = this.readTag();
				compoundTag.put(child.name, child.tag);
			}
			return compoundTag;
		}

		private byte[] readBytes(int count) throws IOException {
			this.ensureAvailable(count);
			byte[] bytes = Arrays.copyOfRange(this.input, this.cursor, this.cursor + count);
			this.cursor += count;
			return bytes;
		}

		private int[] readInts(int count) throws IOException {
			int[] values = new int[count];
			for (int i = 0; i < count; i++) {
				values[i] = this.readIntLE();
			}
			return values;
		}

		private long[] readLongs(int count) throws IOException {
			long[] values = new long[count];
			for (int i = 0; i < count; i++) {
				values[i] = this.readLongLE();
			}
			return values;
		}

		private String readStringUnits(int count) throws IOException {
			char[] chars = new char[count];
			for (int i = 0; i < count; i++) {
				chars[i] = (char)this.readUnsignedShortLE();
			}
			return new String(chars);
		}

		private int readLength() throws IOException {
			long value = Integer.toUnsignedLong(this.readIntLE());
			if (value > Integer.MAX_VALUE) {
				throw new IOException("NBT tape length exceeds Java array limits: " + value);
			}
			return (int)value;
		}

		private byte readByte() throws IOException {
			this.ensureAvailable(1);
			return this.input[this.cursor++];
		}

		private int readUnsignedShortLE() throws IOException {
			this.ensureAvailable(2);
			int value = Byte.toUnsignedInt(this.input[this.cursor]) | (Byte.toUnsignedInt(this.input[this.cursor + 1]) << 8);
			this.cursor += 2;
			return value;
		}

		private int readIntLE() throws IOException {
			this.ensureAvailable(4);
			int value = Byte.toUnsignedInt(this.input[this.cursor])
				| (Byte.toUnsignedInt(this.input[this.cursor + 1]) << 8)
				| (Byte.toUnsignedInt(this.input[this.cursor + 2]) << 16)
				| (Byte.toUnsignedInt(this.input[this.cursor + 3]) << 24);
			this.cursor += 4;
			return value;
		}

		private long readLongLE() throws IOException {
			this.ensureAvailable(8);
			long value = (long)Byte.toUnsignedInt(this.input[this.cursor])
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 1]) << 8)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 2]) << 16)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 3]) << 24)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 4]) << 32)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 5]) << 40)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 6]) << 48)
				| ((long)Byte.toUnsignedInt(this.input[this.cursor + 7]) << 56);
			this.cursor += 8;
			return value;
		}

		private void ensureAvailable(int count) throws IOException {
			if (count < 0 || this.cursor > this.input.length - count) {
				throw new IOException("Truncated NBT tape at offset " + this.cursor);
			}
		}
	}

	private static final class TapeWriter {
		private static final int MAGIC = 0x5442544E;
		private static final int HEADER_LEN = 8;
		private final ByteArrayOutputStream output;

		private TapeWriter(int initialCapacity) {
			this.output = new ByteArrayOutputStream(initialCapacity);
		}

		static byte[] writeRoot(CompoundTag tag) throws IOException {
			TapeWriter writer = new TapeWriter(estimateRootBytes(tag));
			writer.writeIntLE(MAGIC);
			writer.writeShortLE(1);
			writer.writeShortLE(0);
			writer.writeTag("", tag);
			return writer.output.toByteArray();
		}

		private static int estimateRootBytes(CompoundTag tag) {
			long estimate = HEADER_LEN + Math.max(64L, (long)tag.sizeInBytes() * 2L);
			if (estimate < 64L) {
				return 64;
			}
			return estimate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)estimate;
		}

		private void writeTag(String name, Tag tag) throws IOException {
			byte tagId = tag.getId();
			byte listElementId = Tag.TAG_END;
			int count = 0;
			long scalar = 0L;
			switch (tagId) {
				case Tag.TAG_BYTE -> scalar = ((ByteTag)tag).value();
				case Tag.TAG_SHORT -> scalar = ((ShortTag)tag).value();
				case Tag.TAG_INT -> scalar = ((IntTag)tag).value();
				case Tag.TAG_LONG -> scalar = ((LongTag)tag).value();
				case Tag.TAG_FLOAT -> scalar = Float.floatToRawIntBits(((FloatTag)tag).value());
				case Tag.TAG_DOUBLE -> scalar = Double.doubleToRawLongBits(((DoubleTag)tag).value());
				case Tag.TAG_BYTE_ARRAY -> count = ((ByteArrayTag)tag).getAsByteArray().length;
				case Tag.TAG_STRING -> count = ((StringTag)tag).value().length();
				case Tag.TAG_LIST -> {
					ListTag list = (ListTag)tag;
					listElementId = list.identifyRawElementType();
					count = list.size();
				}
				case Tag.TAG_COMPOUND -> count = ((CompoundTag)tag).size();
				case Tag.TAG_INT_ARRAY -> count = ((IntArrayTag)tag).getAsIntArray().length;
				case Tag.TAG_LONG_ARRAY -> count = ((LongArrayTag)tag).getAsLongArray().length;
				default -> throw new IOException("Cannot write tag " + tagId + " to NBT tape");
			}
			this.writeRecord(tagId, listElementId, name.length(), count, scalar);
			this.writeStringUnits(name);
			switch (tagId) {
				case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG, Tag.TAG_FLOAT, Tag.TAG_DOUBLE -> {
				}
				case Tag.TAG_BYTE_ARRAY -> this.output.write(((ByteArrayTag)tag).getAsByteArray());
				case Tag.TAG_STRING -> this.writeStringUnits(((StringTag)tag).value());
				case Tag.TAG_LIST -> {
					ListTag list = (ListTag)tag;
					for (int i = 0; i < list.size(); i++) {
						this.writeTag("", wrapListElement(listElementId, list.get(i)));
					}
				}
				case Tag.TAG_COMPOUND -> {
					for (java.util.Map.Entry<String, Tag> entry : ((CompoundTag)tag).entrySet()) {
						this.writeTag(entry.getKey(), entry.getValue());
					}
				}
				case Tag.TAG_INT_ARRAY -> {
					for (int value : ((IntArrayTag)tag).getAsIntArray()) {
						this.writeIntLE(value);
					}
				}
				case Tag.TAG_LONG_ARRAY -> {
					for (long value : ((LongArrayTag)tag).getAsLongArray()) {
						this.writeLongLE(value);
					}
				}
				default -> throw new IOException("Cannot write tag payload " + tagId + " to NBT tape");
			}
		}

		private static Tag wrapListElement(byte rawElementType, Tag tag) {
			if (rawElementType != Tag.TAG_COMPOUND || tag instanceof CompoundTag compoundTag && !isWrapper(compoundTag)) {
				return tag;
			}
			CompoundTag wrapper = new CompoundTag();
			wrapper.put("", tag);
			return wrapper;
		}

		private static boolean isWrapper(CompoundTag tag) {
			return tag.size() == 1 && tag.contains("");
		}

		private void writeRecord(byte tagId, byte listElementId, int nameUnits, int count, long scalar) throws IOException {
			this.output.write(tagId);
			this.output.write(listElementId);
			this.writeShortLE(0);
			this.writeIntLE(nameUnits);
			this.writeIntLE(count);
			this.writeIntLE(0);
			this.writeLongLE(scalar);
		}

		private void writeStringUnits(String value) {
			for (int i = 0; i < value.length(); i++) {
				this.writeShortLE(value.charAt(i));
			}
		}

		private void writeShortLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
		}

		private void writeIntLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
			this.output.write(value >>> 16 & 0xFF);
			this.output.write(value >>> 24 & 0xFF);
		}

		private void writeLongLE(long value) {
			this.writeIntLE((int)value);
			this.writeIntLE((int)(value >>> 32));
		}
	}

	private record NamedTag(String name, Tag tag) {
	}

	record Limits(int maxDepth, int maxCollectionLength, long maxAllocationBytes, long maxTotalBytes) {
	}

	record CompressionLimits(long maxCompressedBytes, long maxDecompressedBytes) {
	}

	record Result(int status, int errorKind, long offset, long fingerprint, long outputLength) {
		boolean ok() {
			return this.status == OK;
		}
	}

	record ReencodeResult(Result result, byte[] bytes) {
	}

	private interface OutputCall {
		Result invoke(MemorySegment output, long outputCapacity);
	}

	private static final class NativeScratch {
		private Arena arena = Arena.ofConfined();
		private MemorySegment output = MemorySegment.NULL;
		private long outputCapacity;

		MemorySegment output(long capacity) {
			if (capacity > this.outputCapacity) {
				this.arena.close();
				this.arena = Arena.ofConfined();
				this.output = this.arena.allocate(capacity, 1);
				this.outputCapacity = capacity;
			}
			return this.output;
		}

		byte[] copy(long length) {
			if (length <= 0L) {
				return new byte[0];
			}
			return this.output.asSlice(0L, length).toArray(ValueLayout.JAVA_BYTE);
		}
	}
}
