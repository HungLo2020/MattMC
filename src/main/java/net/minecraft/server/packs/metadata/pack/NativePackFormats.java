package net.minecraft.server.packs.metadata.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.logging.LogUtils;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.NativeLibraryLoader;
import org.slf4j.Logger;

/**
 * `pack.mcmeta` format declarations decoded and validated by Rust
 * (`assets/packs/metadata.rs`): a `pack` section's format fields, `overlays`
 * sections, and pack compatibility. These codecs keep the section types'
 * Java API (decoding and DataPackCommand's encoding).
 */
public final class NativePackFormats {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final List<String> PACK_KEYS = List.of("min_format", "max_format", "pack_format", "supported_formats");
	private static final int ERROR_CAPACITY = 2048;
	private static final MethodHandle FORMATS_DECODE = handle("pack_formats_decode", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle FORMATS_ENCODE = voidHandle("pack_formats_encode",
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS);
	private static final MethodHandle OVERLAYS_DECODE = handle("pack_overlays_decode", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG);
	private static final MethodHandle OVERLAYS_ENCODE = handle("pack_overlays_encode", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle COMPATIBILITY = handle("pack_compatibility", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

	private NativePackFormats() {
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	private static MethodHandle voidHandle(String name, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.ofVoid(arguments));
	}

	private static byte[] take(MemorySegment pointer, long length) throws Throwable {
		MemorySegment buffer = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(length);
		try {
			return buffer.toArray(ValueLayout.JAVA_BYTE);
		} finally {
			BUFFER_FREE.invokeExact(buffer, length);
		}
	}

	private static String error(MemorySegment error, long status) {
		long size = Math.min(-status, ERROR_CAPACITY);
		return new String(error.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
	}

	/** `PackCompatibility.forVersion`. */
	public static PackCompatibility compatibility(InclusiveRange<PackFormat> range, PackFormat current) {
		try {
			int ordinal = (int)COMPATIBILITY.invokeExact(range.minInclusive().major(), range.minInclusive().minor(), range.maxInclusive().major(),
				range.maxInclusive().minor(), current.major(), current.minor());
			return PackCompatibility.values()[ordinal];
		} catch (Throwable throwable) {
			throw new IllegalStateException("Pack compatibility failed", throwable);
		}
	}

	private static DataResult<InclusiveRange<PackFormat>> decodeFormats(JsonObject fields, PackType packType) {
		byte[] text = fields.toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.JAVA_INT, 4);
			MemorySegment error = arena.allocate(ERROR_CAPACITY);
			long status = (long)FORMATS_DECODE.invokeExact(arena.allocateFrom(ValueLayout.JAVA_BYTE, text), (long)text.length, packType.ordinal(),
				out, error, (long)ERROR_CAPACITY);
			if (status < 0L) {
				String message = error(error, status);
				return DataResult.error(() -> message);
			}
			return DataResult.success(new InclusiveRange<>(
				PackFormat.of(out.getAtIndex(ValueLayout.JAVA_INT, 0), out.getAtIndex(ValueLayout.JAVA_INT, 1)),
				PackFormat.of(out.getAtIndex(ValueLayout.JAVA_INT, 2), out.getAtIndex(ValueLayout.JAVA_INT, 3))
			));
		} catch (Throwable throwable) {
			throw new IllegalStateException("Decode pack formats failed", throwable);
		}
	}

	private static JsonObject encodeFormats(InclusiveRange<PackFormat> range, PackType packType) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			FORMATS_ENCODE.invokeExact(range.minInclusive().major(), range.minInclusive().minor(), range.maxInclusive().major(),
				range.maxInclusive().minor(), packType.ordinal(), pointer, length);
			String json = new String(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)), StandardCharsets.UTF_8);
			return JsonParser.parseString(json).getAsJsonObject();
		} catch (Throwable throwable) {
			throw new IllegalStateException("Encode pack formats failed", throwable);
		}
	}

	/** `PackFormat.packCodec(type)`: a `pack` section's format fields. */
	public static MapCodec<InclusiveRange<PackFormat>> packCodec(PackType packType) {
		return new MapCodec<>() {
			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return PACK_KEYS.stream().map(ops::createString);
			}

			@Override
			public <T> DataResult<InclusiveRange<PackFormat>> decode(DynamicOps<T> ops, MapLike<T> input) {
				JsonObject fields = new JsonObject();
				for (String key : PACK_KEYS) {
					T value = input.get(key);
					if (value != null) {
						fields.add(key, ops.convertTo(JsonOps.INSTANCE, value));
					}
				}
				return decodeFormats(fields, packType);
			}

			@Override
			public <T> RecordBuilder<T> encode(InclusiveRange<PackFormat> range, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				encodeFormats(range, packType).entrySet().forEach(entry -> prefix.add(entry.getKey(), JsonOps.INSTANCE.convertTo(ops, entry.getValue())));
				return prefix;
			}
		};
	}

	private static DataResult<OverlayMetadataSection> decodeOverlays(JsonElement section, PackType packType) {
		byte[] text = section.toString().getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment error = arena.allocate(ERROR_CAPACITY);
			long status = (long)OVERLAYS_DECODE.invokeExact(arena.allocateFrom(ValueLayout.JAVA_BYTE, text), (long)text.length, packType.ordinal(),
				pointer, length, error, (long)ERROR_CAPACITY);
			if (status < 0L) {
				String message = error(error, status);
				return DataResult.error(() -> message);
			}
			byte[] bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
			MemorySegment buffer = MemorySegment.ofArray(bytes);
			long at = 0L;
			int warnings = buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at);
			at += 4L;
			for (int index = 0; index < warnings; index++) {
				int size = buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at);
				LOGGER.warn(new String(bytes, (int)at + 4, size, StandardCharsets.UTF_8));
				at += 4L + size;
			}
			int count = buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at);
			at += 4L;
			List<OverlayMetadataSection.OverlayEntry> entries = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				int size = buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at);
				String directory = new String(bytes, (int)at + 4, size, StandardCharsets.UTF_8);
				at += 4L + size;
				PackFormat min = PackFormat.of(buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at), buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at + 4L));
				PackFormat max = PackFormat.of(buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at + 8L), buffer.get(ValueLayout.JAVA_INT_UNALIGNED, at + 12L));
				at += 16L;
				entries.add(new OverlayMetadataSection.OverlayEntry(new InclusiveRange<>(min, max), directory));
			}
			return DataResult.success(new OverlayMetadataSection(List.copyOf(entries)));
		} catch (Throwable throwable) {
			throw new IllegalStateException("Decode overlays failed", throwable);
		}
	}

	private static JsonElement encodeOverlays(OverlayMetadataSection section, PackType packType) {
		ByteArrayOutputStream records = new ByteArrayOutputStream();
		for (OverlayMetadataSection.OverlayEntry entry : section.overlays()) {
			byte[] directory = entry.overlay().getBytes(StandardCharsets.UTF_8);
			writeInt(records, directory.length);
			records.write(directory, 0, directory.length);
			writeInt(records, entry.format().minInclusive().major());
			writeInt(records, entry.format().minInclusive().minor());
			writeInt(records, entry.format().maxInclusive().major());
			writeInt(records, entry.format().maxInclusive().minor());
		}
		byte[] bytes = records.toByteArray();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment input = bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			int status = (int)OVERLAYS_ENCODE.invokeExact(input, (long)bytes.length, packType.ordinal(), pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Encode overlays failed: " + status);
			}
			return JsonParser.parseString(new String(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)), StandardCharsets.UTF_8));
		} catch (Throwable throwable) {
			throw new IllegalStateException("Encode overlays failed", throwable);
		}
	}

	private static void writeInt(ByteArrayOutputStream out, int value) {
		out.write(value);
		out.write(value >>> 8);
		out.write(value >>> 16);
		out.write(value >>> 24);
	}

	/** `OverlayMetadataSection.codecForPackType(type)`. */
	public static Codec<OverlayMetadataSection> overlayCodec(PackType packType) {
		return ExtraCodecs.JSON.flatXmap(json -> decodeOverlays(json, packType), section -> DataResult.success(encodeOverlays(section, packType)));
	}
}
