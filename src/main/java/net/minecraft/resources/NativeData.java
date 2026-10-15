package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Data-driven entity, item and biome definitions owned by Rust
 * (`content/data`). Rust parses data pack files, decodes them with each
 * registry's schema, stores the definitions and encodes them for saves and
 * registry synchronization; Java objects are projections built by each
 * registry's {@link Projector}. Values that belong to registries Java still
 * owns (text components, holder sets, effect components, int providers,
 * particle options, carvers, features) are resolved and encoded by those
 * registries' codecs.
 */
public final class NativeData {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final Cleaner CLEANER = Cleaner.create();
	private static final MethodHandle DECODE_FILE = handle("mattmc_data_decode_file",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle DECODE_TREE = handle("mattmc_data_decode_tree",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle PAYLOAD = handle("mattmc_data_payload",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle ENCODE = handle("mattmc_data_encode",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle DEFINE = handle("mattmc_data_define",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle RELEASE = handle("mattmc_data_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle LIVE_COUNT = handle("mattmc_data_live_count", FunctionDescriptor.of(ValueLayout.JAVA_LONG));
	/** Each projection's stored definition and resolved slots, for encoding. */
	private static final Map<Object, Element> ELEMENTS = new MapMaker().weakKeys().makeMap();

	private NativeData() {
	}



	static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", name, descriptor);
	}

	/** Registries Rust decodes, in `content/data/registries.rs` order. */
	public enum Kind {
		BIOME,
		PAINTING_VARIANT,
		DAMAGE_TYPE,
		BANNER_PATTERN,
		JUKEBOX_SONG,
		TRIM_PATTERN,
		TRIM_MATERIAL,
		INSTRUMENT,
		ENCHANTMENT,
		ENCHANTMENT_PROVIDER,
		CAT_VARIANT,
		FROG_VARIANT,
		WOLF_VARIANT,
		WOLF_SOUND_VARIANT,
		PIG_VARIANT,
		COW_VARIANT,
		CHICKEN_VARIANT,
		ZOMBIE_NAUTILUS_VARIANT
	}

	/** Builds a registry's Java object from its decoded value. */
	@FunctionalInterface
	public interface Projector<E> {
		E project(Object value, Context context);
	}

	/** Describes a Java-built object as its decoded value, for Rust to store and encode. */
	@FunctionalInterface
	public interface Unprojector<E> {
		Object unproject(E value, Builder builder);
	}

	public static <E> DataCodec<E> codec(Kind kind, boolean network, Projector<E> projector, Unprojector<E> unprojector) {
		return new DataCodec<>(kind, network, projector, unprojector);
	}

	/** Collects a Java-built object's delegated values while it is described. */
	public static final class Builder {
		final List<Object> values = new ArrayList<>();
		final List<Codec<?>> codecs = new ArrayList<>();
		final List<Object> defaults = new ArrayList<>();

		/** A delegated value its codec encodes. */
		public Slot slot(Object value, Codec<?> codec) {
			return this.slotWithDefault(value, codec, null);
		}

		/** An optional delegated value: absent is null. */
		@Nullable
		public Slot optionalSlot(Optional<?> value, Codec<?> codec) {
			return value.isPresent() ? this.slot(value.get(), codec) : null;
		}

		/** A delegated value omitted on encode when equal to {@code fallback}. */
		public Slot slotWithDefault(Object value, Codec<?> codec, @Nullable Object fallback) {
			this.values.add(value);
			this.codecs.add(codec);
			this.defaults.add(fallback);
			return new Slot(this.values.size() - 1);
		}
	}

	/** Decoded values: records as {@code Object[]}, absent as null. */
	public record Variant(int index, Object[] fields) {
	}

	public record Slot(int index) {
	}

	/** A number kept as its source's Java type ({@code ExtraCodecs.JAVA} values). */
	public record JavaNumber(Number value) {
	}

	/** One registry's codec: Rust decodes and encodes, the projector builds Java objects. */
	public static final class DataCodec<E> implements Codec<E> {
		private final Kind kind;
		private final boolean network;
		private final Projector<E> projector;
		private final Unprojector<E> unprojector;

		DataCodec(Kind kind, boolean network, Projector<E> projector, Unprojector<E> unprojector) {
			this.kind = kind;
			this.network = network;
			this.projector = projector;
			this.unprojector = unprojector;
		}

		@Override
		public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
			requireBlocks();
			List<T> nodes = new ArrayList<>();
			Out out = new Out();
			write(ops, input, nodes, out);
			// NbtOps reads numbers as booleans; JsonOps does not.
			int mode = ops.getBooleanValue(ops.createInt(1)).isSuccess() ? 1 : 0;
			boolean registryOps = ops instanceof RegistryOps<?>;
			byte[] bytes = out.toByteArray();
			NativeValues.DataCall call;
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = copy(arena, bytes);
				call = NativeValues.dataCall(ops, nodes, JAVA_THROWN_HANDLE, () ->
					(long)DECODE_TREE.invokeExact(this.kind.ordinal(), this.network ? 1 : 0, mode, registryOps ? 1 : 0, segment, (long)bytes.length));
			} catch (Throwable e) {
				throw new IllegalStateException("Rust data decode failed", e);
			}
			long handle = call.handle();
			this.checkBlocks(handle, "Decoding");
			if (call.thrown() != null) {
				// A Java codec Rust called back threw: the decode throws that same exception.
				throw NativeData.<RuntimeException>rethrow(call.thrown());
			}
			return this.project(handle, new Source<>(ops, nodes), ops instanceof RegistryOps<?> r ? r.withParent(JsonOps.INSTANCE) : null, call.resolved())
				.map(e -> Pair.of(e, ops.empty()));
		}

		/** Decodes a data pack file's bytes; references resolve against {@code ops}. */
		public DataResult<E> decodeFile(RegistryOps<JsonElement> ops, byte[] bytes) {
			requireBlocks();
			NativeValues.DataCall call;
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = copy(arena, bytes);
				// Java codecs Rust calls back decode the file's JSON with the loader's ops.
				call = NativeValues.dataCall(ops, List.of(), JAVA_THROWN_HANDLE, () ->
					(long)DECODE_FILE.invokeExact(this.kind.ordinal(), this.network ? 1 : 0, segment, (long)bytes.length));
			} catch (Throwable e) {
				throw new IllegalStateException("Rust data decode failed", e);
			}
			long handle = call.handle();
			if (handle == JSON_INVALID_HANDLE) {
				// Not JSON Rust parses: the loader's own parser reports it (or, if it
				// accepts the text, its tree decodes as the loader decoded it).
				JsonElement json = net.minecraft.util.StrictJsonParser.parse(
					new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes), java.nio.charset.StandardCharsets.UTF_8)));
				return this.parse(ops, json);
			}
			this.checkBlocks(handle, "Decoding");
			if (call.thrown() != null) {
				// A Java codec Rust called back threw: the decode throws that same exception.
				throw NativeData.<RuntimeException>rethrow(call.thrown());
			}
			return this.project(handle, null, ops, call.resolved());
		}

		private DataResult<E> project(long handle, @Nullable Source<?> source, @Nullable RegistryOps<JsonElement> jsonOps, List<Object> resolved) {
			E value;
			Context context;
			String error = null;
			try {
				ByteBuffer in = ByteBuffer.wrap(table(PAYLOAD, handle, 0)).order(ByteOrder.LITTLE_ENDIAN);
				if (in.get() == 0) {
					error = string(in);
					if (in.get() == 0) {
						release(handle);
						String message = error;
						return DataResult.error(() -> message);
					}
					// The partial definition DFU keeps, projected like a decoded one.
				}
				Object decoded = decoded(in);
				context = new Context(source, jsonOps, in);
				context.resolved(resolved);
				for (int i = in.getInt(); i > 0; i--) {
					LOGGER.error(string(in));
				}
				value = this.projector.project(decoded, context);
			} catch (Context.Failure failure) {
				release(handle);
				return DataResult.error(failure::getMessage);
			} catch (RuntimeException e) {
				release(handle);
				throw e;
			}
			ELEMENTS.put(value, new Element(handle, this.network, context.values, context.codecs, context.defaults));
			CLEANER.register(value, () -> release(handle));
			if (error != null) {
				String message = error;
				return DataResult.error(() -> message, value);
			}
			return DataResult.success(value);
		}

		@Override
		public <T> DataResult<T> encode(E value, DynamicOps<T> ops, T prefix) {
			Element element = ELEMENTS.get(value);
			if (element == null || element.network && !this.network) {
				// Java-built objects, and network-decoded ones in direct form (their
				// Java defaults stand in for the fields synchronization omits).
				DataResult<Element> defined = this.define(value, element == null);
				if (defined.isError()) {
					return defined.map(e -> null);
				}
				element = defined.getOrThrow();
			}
			ByteBuffer in;
			try {
				in = ByteBuffer.wrap(table(ENCODE, element.handle, this.network ? 1 : 0)).order(ByteOrder.LITTLE_ENDIAN);
			} finally {
				// The handle lives while its owner does (the Cleaner releases it): keep both through the native read.
				java.lang.ref.Reference.reachabilityFence(value);
				java.lang.ref.Reference.reachabilityFence(element);
			}
			byte status = in.get();
			if (status == 2) {
				throw blocksMissing("Encoding " + (this.kind == Kind.BIOME ? "biomes" : this.kind.toString()));
			}
			if (status == 0) {
				String error = string(in);
				return DataResult.error(() -> error);
			}
			T result;
			try {
				result = build(ops, in, element);
			} catch (Context.Failure failure) {
				return DataResult.error(failure::getMessage);
			}
			return prefix.equals(ops.empty()) ? DataResult.success(result) : ops.getMap(result).flatMap(map -> ops.mergeToMap(prefix, map));
		}

		/** Throws if the call met a block state without the block registry (Rust stored nothing). */
		private void checkBlocks(long handle, String verb) {
			if (handle == BLOCKS_MISSING_HANDLE) {
				throw blocksMissing(verb + " " + (this.kind == Kind.BIOME ? "biomes" : this.kind.toString()));
			}
		}

		/**
		 * Biomes embed particle options, whose block states Rust reads from the
		 * block registry. Rust installs it on the first block state it meets
		 * ({@link #installBlocks}), so only bootstrap must have finished.
		 */
		private void requireBlocks() {
			if (this.kind == Kind.BIOME && net.minecraft.server.Bootstrap.bootstrapDuration.get() < 0) {
				throw new IllegalStateException("Decoding biomes needs the native block registry");
			}
		}

		/** Stores a Java-built object's definition in Rust, which checks it against the schema. */
		private DataResult<Element> define(E value, boolean keep) {
			Builder builder = new Builder();
			Object described = this.unprojector.unproject(value, builder);
			Out out = new Out();
			writeDecoded(out, described);
			byte[] bytes = out.toByteArray();
			long handle;
			try (Arena arena = Arena.ofConfined()) {
				handle = (long)DEFINE.invokeExact(this.kind.ordinal(), builder.values.size(), copy(arena, bytes), (long)bytes.length);
			} catch (Throwable e) {
				throw new IllegalStateException("Rust data define failed", e);
			}
			this.checkBlocks(handle, "Encoding");
			ByteBuffer in = ByteBuffer.wrap(table(PAYLOAD, handle, 0)).order(ByteOrder.LITTLE_ENDIAN);
			if (in.get() == 0) {
				String error = string(in);
				release(handle);
				return DataResult.error(() -> "Invalid " + this.kind + " " + value + ": " + error);
			}
			Element element = new Element(handle, false, builder.values.toArray(), builder.codecs.toArray(Codec<?>[]::new), builder.defaults.toArray());
			if (keep) {
				ELEMENTS.put(value, element);
			}
			CLEANER.register(keep ? value : element, () -> release(handle));
			return DataResult.success(element);
		}

		/** The stored definition behind a projection, or 0. */
		public long nativeHandle(E value) {
			Element element = ELEMENTS.get(value);
			return element == null ? 0 : element.handle;
		}

		@Override
		public String toString() {
			return "NativeData[" + this.kind + (this.network ? ", network]" : "]");
		}
	}

	/** A projection's stored definition (in network form when synchronization decoded it) and resolved slots. */
	record Element(long handle, boolean network, Object[] values, Codec<?>[] codecs, Object[] defaults) {
		/** No delegated slots. */
		static final Element EMPTY = new Element(0L, false, new Object[0], new Codec<?>[0], new Object[0]);
	}

	/** The input a tree came from, for resolving delegated nodes from their originals. */
	record Source<T>(DynamicOps<T> ops, List<T> nodes) {
		<A> DataResult<A> parse(Codec<A> codec, int index) {
			return codec.parse(this.ops, this.nodes.get(index));
		}
	}

	/** Resolves a decoded value's delegated slots and sounds, remembering them for encoding. */
	public static final class Context {
		@Nullable
		private final Source<?> source;
		@Nullable
		private final RegistryOps<JsonElement> jsonOps;
		private final int[] indices;
		private final JsonElement[] trees;
		private final boolean[] present;
		private final ByteBuffer payload;
		private final int[] treeStarts;
		final Object[] values;
		final Codec<?>[] codecs;
		final Object[] defaults;

		Context(@Nullable Source<?> source, @Nullable RegistryOps<JsonElement> jsonOps, ByteBuffer in) {
			this.source = source;
			this.jsonOps = jsonOps;
			int count = in.getInt();
			this.payload = in;
			if (count == 0) {
				this.indices = NO_INTS;
				this.trees = NO_TREES;
				this.present = NO_FLAGS;
				this.treeStarts = NO_INTS;
				this.values = NO_OBJECTS;
				this.codecs = NO_CODECS;
				this.defaults = NO_OBJECTS;
				return;
			}
			this.indices = new int[count];
			this.trees = new JsonElement[count];
			this.present = new boolean[count];
			this.treeStarts = new int[count];
			for (int i = 0; i < count; i++) {
				this.present[i] = in.get() != 0;
				if (this.present[i]) {
					this.indices[i] = in.getInt();
					// Built only if a codec needs it (see tree(int)).
					this.treeStarts[i] = in.position();
					skipTree(in);
				}
			}
			this.values = new Object[count];
			this.codecs = new Codec<?>[count];
			this.defaults = new Object[count];
		}

		private static final int[] NO_INTS = new int[0];
		private static final JsonElement[] NO_TREES = new JsonElement[0];
		private static final boolean[] NO_FLAGS = new boolean[0];
		private static final Object[] NO_OBJECTS = new Object[0];
		private static final Codec<?>[] NO_CODECS = new Codec<?>[0];

		/** A value codec's context: slots resolve from Java's own input nodes, or were decoded already. */
		@SuppressWarnings({"unchecked", "rawtypes"})
		static Context forValue(DynamicOps<Object> ops, List<Object> nodes, @Nullable RegistryOps<JsonElement> jsonOps, ByteBuffer in, List<Object> resolved) {
			Context context = new Context(new Source(ops, nodes), jsonOps, in);
			context.resolved = resolved;
			return context;
		}

		/** Values Java already decoded for slots while Rust decoded (`NativeValues.JavaCodec`). */
		@Nullable
		private List<Object> resolved;

		void resolved(List<Object> resolved) {
			this.resolved = resolved;
		}

		static final class Failure extends RuntimeException {
			Failure(String message) {
				super(message, null, false, false);
			}
		}

		/** A delegated value, decoded with {@code codec}. */
		public <A> A slot(Object value, Codec<A> codec) {
			int i = ((Slot)value).index();
			A result = this.parse(i, codec);
			this.values[i] = result;
			this.codecs[i] = codec;
			return result;
		}

		/** An optional delegated value: null is absent. */
		public <A> Optional<A> optionalSlot(@Nullable Object value, Codec<A> codec) {
			return value == null ? Optional.empty() : Optional.of(this.slot(value, codec));
		}

		/** A delegated value whose absent default Java holds; encoding omits it when equal. */
		public <A> A slotOrDefault(Object value, Codec<A> codec, A fallback) {
			int i = ((Slot)value).index();
			A result = this.present[i] ? this.parse(i, codec) : fallback;
			this.values[i] = result;
			this.codecs[i] = codec;
			this.defaults[i] = fallback;
			return result;
		}

		@SuppressWarnings("unchecked")
		private <A> A parse(int i, Codec<A> codec) {
			if (this.resolved != null && i < this.resolved.size() && this.resolved.get(i) != null) {
				return (A)this.resolved.get(i);
			}
			DataResult<A> result = this.source != null && this.indices[i] != -1
				? this.source.parse(codec, this.indices[i])
				: codec.parse(this.jsonOps != null ? this.jsonOps : JsonOps.INSTANCE, this.tree(i));
			return result.getOrThrow(Failure::new);
		}

		private JsonElement tree(int i) {
			if (this.trees[i] == null) {
				this.trees[i] = NativeData.tree(this.payload.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(this.treeStarts[i]));
			}
			return this.trees[i];
		}
	}

	private static final ClassValue<Map<String, Object>> NAMES = new ClassValue<>() {
		@Override
		protected Map<String, Object> computeValue(Class<?> type) {
			Map<String, Object> names = new java.util.HashMap<>();
			for (Object constant : type.getEnumConstants()) {
				names.put(((StringRepresentable)constant).getSerializedName(), constant);
			}
			return Map.copyOf(names);
		}
	};

	/** A serialized name's constant (lookups are cached per enum). */
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E> & StringRepresentable> E named(Class<E> type, Object name) {
		Object constant = NAMES.get(type).get(name);
		if (constant == null) {
			throw new IllegalStateException("Rust decoded unknown name " + name);
		}
		return (E)constant;
	}

	public static ResourceLocation location(Object value) {
		return ResourceLocation.parse((String)value);
	}

	@SuppressWarnings("unchecked")
	public static List<Object> list(Object value) {
		return (List<Object>)value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> map(Object value) {
		return (Map<String, Object>)value;
	}

	public static Object[] record(Object value) {
		return (Object[])value;
	}

	/** Stored native definitions; each live projection holds one. */
	/** The native handle behind a projected object, or 0 (tests). */
	static long handleOf(Object value) {
		Element element = ELEMENTS.get(value);
		return element == null ? 0 : element.handle();
	}

	/** Whether a native handle is still stored (tests). */
	static boolean stored(long handle) {
		try {
			return (long)PAYLOAD.invokeExact(handle, MemorySegment.NULL, 0L) >= 0;
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	public static long liveCount() {
		try {
			return (long)LIVE_COUNT.invokeExact();
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	static MemorySegment copy(Arena arena, byte[] bytes) {
		MemorySegment segment = arena.allocate(Math.max(1, bytes.length));
		// Direct-buffer copies throughout: heap segments (MemorySegment.ofArray,
		// toArray) passing through the JDK's shared segment-access code during
		// world load pollute the JIT profiles of hot native-segment readers.
		segment.asByteBuffer().put(bytes);
		return segment;
	}

	/** First guess for a table's size; larger tables take a second call. */
	private static final long TABLE_GUESS = 16 * 1024;

	private static byte[] table(MethodHandle function, long handle, int network) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment segment = arena.allocate(TABLE_GUESS);
			long size = call(function, handle, network, segment, TABLE_GUESS);
			if (size < 0) {
				throw new IllegalStateException("Unknown native definition " + handle);
			}
			if (size > TABLE_GUESS) {
				segment = arena.allocate(size);
				if (call(function, handle, network, segment, size) != size) {
					throw new IllegalStateException("Native definition table changed size");
				}
			}
			byte[] table = new byte[(int)size];
			segment.asByteBuffer().get(table);
			return table;
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot read native definition", e);
		}
	}

	private static long call(MethodHandle function, long handle, int network, MemorySegment segment, long capacity) throws Throwable {
		return function == ENCODE ? (long)ENCODE.invokeExact(handle, network, segment, capacity) : (long)PAYLOAD.invokeExact(handle, segment, capacity);
	}

	private static void release(long handle) {
		try {
			RELEASE.invokeExact(handle);
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot release native definition", e);
		}
	}

	/** Preorder tree of {@code value}; see `content/data/ffi.rs`. */
	@SuppressWarnings("unchecked")
	static <T> void write(DynamicOps<T> ops, @Nullable T value, List<T> nodes, Out out) {
		// The two ops games use, walked directly; the same classification as below.
		if (value instanceof net.minecraft.nbt.Tag tag && opsBase(ops) == net.minecraft.nbt.NbtOps.INSTANCE) {
			writeTag(tag, (List<Object>)nodes, out);
			return;
		}
		if (value instanceof JsonElement json && opsBase(ops) == JsonOps.INSTANCE) {
			writeJson(json, (List<Object>)nodes, out);
			return;
		}
		nodes.add(value);
		if (value == null || value.equals(ops.empty())) {
			out.write(0);
			return;
		}
		var map = ops.getMapValues(value);
		if (map.isSuccess()) {
			List<Pair<T, T>> entries = map.getOrThrow().toList();
			out.write(12);
			out.u32(entries.size());
			for (Pair<T, T> entry : entries) {
				out.string(ops.getStringValue(entry.getFirst()).result().orElse(""));
				write(ops, entry.getSecond(), nodes, out);
			}
			return;
		}
		var list = ops.getStream(value);
		if (list.isSuccess()) {
			List<T> items = list.getOrThrow().toList();
			out.write(11);
			out.u32(items.size());
			for (T item : items) {
				write(ops, item, nodes, out);
			}
			return;
		}
		var string = ops.getStringValue(value);
		if (string.isSuccess()) {
			out.write(10);
			out.string(string.getOrThrow());
			return;
		}
		var number = ops.getNumberValue(value);
		if (number.isSuccess()) {
			switch (number.getOrThrow()) {
				case Byte b -> {
					out.write(4);
					out.write(b);
				}
				case Short s -> {
					out.write(5);
					out.write(s);
					out.write(s >> 8);
				}
				case Integer i -> {
					out.write(6);
					out.u32(i);
				}
				case Long l -> {
					out.write(7);
					out.u64(l);
				}
				case Float f -> {
					out.write(8);
					out.u32(Float.floatToRawIntBits(f));
				}
				case Double d -> {
					out.write(9);
					out.u64(Double.doubleToRawLongBits(d));
				}
				case Number other -> {
					out.write(3);
					out.string(other.toString());
				}
			}
			return;
		}
		var bool = ops.getBooleanValue(value);
		out.write(bool.isSuccess() ? (bool.getOrThrow() ? 2 : 1) : 0);
	}

	/** The ops a {@code RegistryOps} delegates to, or {@code ops} itself. */
	static DynamicOps<?> opsBase(DynamicOps<?> ops) {
		return ops instanceof RegistryOps<?> registryOps ? registryOps.delegate : ops;
	}

	/** {@link #write} for {@code NbtOps}: maps, collections, strings and numbers. */
	private static void writeTag(net.minecraft.nbt.Tag tag, List<Object> nodes, Out out) {
		nodes.add(tag);
		switch (tag) {
			case net.minecraft.nbt.CompoundTag compound -> {
				out.write(12);
				out.u32(compound.size());
				for (Map.Entry<String, net.minecraft.nbt.Tag> entry : compound.entrySet()) {
					out.string(entry.getKey());
					writeTag(entry.getValue(), nodes, out);
				}
			}
			case net.minecraft.nbt.CollectionTag collection -> {
				out.write(11);
				out.u32(collection.size());
				for (int i = 0; i < collection.size(); i++) {
					writeTag(collection.get(i), nodes, out);
				}
			}
			case net.minecraft.nbt.StringTag(String string) -> {
				out.write(10);
				out.string(string);
			}
			case net.minecraft.nbt.ByteTag(byte b) -> {
				out.write(4);
				out.write(b);
			}
			case net.minecraft.nbt.ShortTag(short v) -> {
				out.write(5);
				out.write(v);
				out.write(v >> 8);
			}
			case net.minecraft.nbt.IntTag(int v) -> {
				out.write(6);
				out.u32(v);
			}
			case net.minecraft.nbt.LongTag(long v) -> {
				out.write(7);
				out.u64(v);
			}
			case net.minecraft.nbt.FloatTag(float v) -> {
				out.write(8);
				out.u32(Float.floatToRawIntBits(v));
			}
			case net.minecraft.nbt.DoubleTag(double v) -> {
				out.write(9);
				out.u64(Double.doubleToRawLongBits(v));
			}
			default -> out.write(0);
		}
	}

	/** {@link #write} for {@code JsonOps}: objects (null members absent), arrays and primitives. */
	private static void writeJson(@Nullable JsonElement json, List<Object> nodes, Out out) {
		nodes.add(json);
		switch (json) {
			case null -> out.write(0);
			case JsonObject object -> {
				out.write(12);
				out.u32(object.size());
				for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
					out.string(entry.getKey());
					JsonElement member = entry.getValue();
					writeJson(member instanceof JsonNull ? null : member, nodes, out);
				}
			}
			case JsonArray array -> {
				out.write(11);
				out.u32(array.size());
				for (JsonElement item : array) {
					writeJson(item, nodes, out);
				}
			}
			case JsonPrimitive primitive when primitive.isString() -> {
				out.write(10);
				out.string(primitive.getAsString());
			}
			case JsonPrimitive primitive when primitive.isNumber() -> {
				switch (primitive.getAsNumber()) {
					case Byte b -> {
						out.write(4);
						out.write(b);
					}
					case Short v -> {
						out.write(5);
						out.write(v);
						out.write(v >> 8);
					}
					case Integer v -> {
						out.write(6);
						out.u32(v);
					}
					case Long v -> {
						out.write(7);
						out.u64(v);
					}
					case Float v -> {
						out.write(8);
						out.u32(Float.floatToRawIntBits(v));
					}
					case Double v -> {
						out.write(9);
						out.u64(Double.doubleToRawLongBits(v));
					}
					case Number other -> {
						out.write(3);
						out.string(other.toString());
					}
				}
			}
			case JsonPrimitive primitive when primitive.isBoolean() -> out.write(primitive.getAsBoolean() ? 2 : 1);
			default -> out.write(0);
		}
	}

	/** A growable little-endian byte buffer for FFI inputs. */
	static final class Out {
		private byte[] buf = new byte[256];
		private int count;

		private void ensure(int extra) {
			if (this.count + extra > this.buf.length) {
				this.buf = java.util.Arrays.copyOf(this.buf, Math.max(this.buf.length * 2, this.count + extra));
			}
		}

		void write(int b) {
			this.ensure(1);
			this.buf[this.count++] = (byte)b;
		}

		void write(byte[] bytes, int offset, int length) {
			this.ensure(length);
			System.arraycopy(bytes, offset, this.buf, this.count, length);
			this.count += length;
		}

		void u32(int v) {
			this.ensure(4);
			byte[] b = this.buf;
			int c = this.count;
			b[c] = (byte)v;
			b[c + 1] = (byte)(v >> 8);
			b[c + 2] = (byte)(v >> 16);
			b[c + 3] = (byte)(v >> 24);
			this.count = c + 4;
		}

		void u64(long v) {
			this.u32((int)v);
			this.u32((int)(v >> 32));
		}

		void string(String s) {
			int length = s.length();
			// ASCII strings, the common case, need no encoder.
			this.ensure(4 + length);
			int start = this.count + 4;
			for (int i = 0; i < length; i++) {
				char c = s.charAt(i);
				if (c >= 0x80) {
					byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
					this.u32(bytes.length);
					this.write(bytes, 0, bytes.length);
					return;
				}
				this.buf[start + i] = (byte)c;
			}
			this.u32(length);
			this.count += length;
		}

		void reset() {
			this.count = 0;
		}

		int size() {
			return this.count;
		}

		byte[] toByteArray() {
			return java.util.Arrays.copyOf(this.buf, this.count);
		}

		/** Copies the bytes into {@code segment} at offset 0. */
		void copyTo(MemorySegment segment) {
			MemorySegment.copy(this.buf, 0, segment, ValueLayout.JAVA_BYTE, 0, this.count);
		}
	}

	/**
	 * Recent short ASCII strings (field names, IDs). Strings are immutable, so
	 * racing threads only miss.
	 */
	private static final String[] STRINGS = new String[1024];

	static String string(ByteBuffer in) {
		int length = in.getInt();
		if (in.hasArray()) {
			byte[] array = in.array();
			int at = in.arrayOffset() + in.position();
			in.position(in.position() + length);
			if (length > 48) {
				return new String(array, at, length, StandardCharsets.UTF_8);
			}
			int hash = length;
			boolean ascii = true;
			for (int i = 0; i < length; i++) {
				byte b = array[at + i];
				hash = hash * 31 + b;
				ascii &= b >= 0;
			}
			if (!ascii) {
				return new String(array, at, length, StandardCharsets.UTF_8);
			}
			int slot = (hash ^ hash >>> 16) & (STRINGS.length - 1);
			String cached = STRINGS[slot];
			if (cached != null && cached.length() == length) {
				boolean same = true;
				for (int i = 0; i < length && same; i++) {
					same = cached.charAt(i) == array[at + i];
				}
				if (same) {
					return cached;
				}
			}
			String string = new String(array, at, length, StandardCharsets.ISO_8859_1);
			STRINGS[slot] = string;
			return string;
		}
		if (length <= 48) {
			int at = in.position();
			int hash = length;
			boolean ascii = true;
			for (int i = 0; i < length; i++) {
				byte b = in.get(at + i);
				hash = hash * 31 + b;
				ascii &= b >= 0;
			}
			if (ascii) {
				int slot = (hash ^ hash >>> 16) & (STRINGS.length - 1);
				String cached = STRINGS[slot];
				if (cached != null && cached.length() == length) {
					boolean same = true;
					for (int i = 0; i < length && same; i++) {
						same = cached.charAt(i) == in.get(at + i);
					}
					if (same) {
						in.position(at + length);
						return cached;
					}
				}
				byte[] bytes = new byte[length];
				in.get(bytes);
				String string = new String(bytes, StandardCharsets.ISO_8859_1);
				STRINGS[slot] = string;
				return string;
			}
		}
		byte[] bytes = new byte[length];
		in.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/** A number node of the input tree format (tags 3 to 9). */
	static Number readNumber(ByteBuffer in) {
		return switch (in.get()) {
			case 3 -> new LazilyParsedNumber(string(in));
			case 4 -> in.get();
			case 5 -> in.getShort();
			case 6 -> in.getInt();
			case 7 -> in.getLong();
			case 8 -> in.getFloat();
			case 9 -> in.getDouble();
			default -> throw new IllegalStateException("Not a native number tag");
		};
	}

	static void writeNumber(Out out, Number number) {
		switch (number) {
			case Byte b -> {
				out.write(4);
				out.write(b);
			}
			case Short v -> {
				out.write(5);
				out.write(v);
				out.write(v >> 8);
			}
			case Integer v -> {
				out.write(6);
				out.u32(v);
			}
			case Long v -> {
				out.write(7);
				out.u64(v);
			}
			case Float v -> {
				out.write(8);
				out.u32(Float.floatToRawIntBits(v));
			}
			case Double v -> {
				out.write(9);
				out.u64(Double.doubleToRawLongBits(v));
			}
			default -> {
				out.write(3);
				out.string(number.toString());
			}
		}
	}

	/** Skips one input tree. */
	private static void skipTree(ByteBuffer in) {
		switch (in.get()) {
			case 0, 1, 2 -> {
			}
			case 3, 10 -> {
				int length = in.getInt();
				in.position(in.position() + length);
			}
			case 4 -> in.position(in.position() + 1);
			case 5 -> in.position(in.position() + 2);
			case 6, 8 -> in.position(in.position() + 4);
			case 7, 9 -> in.position(in.position() + 8);
			case 11 -> {
				for (int i = in.getInt(); i > 0; i--) {
					skipTree(in);
				}
			}
			case 12 -> {
				for (int i = in.getInt(); i > 0; i--) {
					int length = in.getInt();
					in.position(in.position() + length);
					skipTree(in);
				}
			}
			default -> throw new IllegalStateException("Unknown native value tree tag");
		}
	}

	/** A Rust input tree as Gson would have parsed it (numbers keep their text). */
	static JsonElement tree(ByteBuffer in) {
		return switch (in.get()) {
			case 0 -> JsonNull.INSTANCE;
			case 1 -> new JsonPrimitive(false);
			case 2 -> new JsonPrimitive(true);
			case 3 -> new JsonPrimitive(new LazilyParsedNumber(string(in)));
			case 4 -> new JsonPrimitive(in.get());
			case 5 -> new JsonPrimitive(in.getShort());
			case 6 -> new JsonPrimitive(in.getInt());
			case 7 -> new JsonPrimitive(in.getLong());
			case 8 -> new JsonPrimitive(in.getFloat());
			case 9 -> new JsonPrimitive(in.getDouble());
			case 10 -> new JsonPrimitive(string(in));
			case 11 -> {
				JsonArray array = new JsonArray();
				for (int i = in.getInt(); i > 0; i--) {
					array.add(tree(in));
				}
				yield array;
			}
			case 12 -> {
				JsonObject object = new JsonObject();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					object.add(key, tree(in));
				}
				yield object;
			}
			default -> throw new IllegalStateException("Unknown native value tree tag");
		};
	}

	/** A decoded value for Rust (see `content/data/ffi.rs`). */
	static void writeDecoded(Out out, @Nullable Object value) {
		switch (value) {
			case null -> out.write(0);
			case Boolean b -> out.write(b ? 2 : 1);
			case Integer i -> {
				out.write(3);
				out.u32(i);
			}
			case Float f -> {
				out.write(4);
				out.u32(Float.floatToRawIntBits(f));
			}
			case Double d -> {
				out.write(5);
				out.u64(Double.doubleToRawLongBits(d));
			}
			case String string -> {
				out.write(6);
				out.string(string);
			}
			case List<?> list -> {
				out.write(7);
				out.u32(list.size());
				list.forEach(item -> writeDecoded(out, item));
			}
			case Map<?, ?> map -> {
				out.write(8);
				out.u32(map.size());
				map.forEach((key, item) -> {
					out.string((String)key);
					writeDecoded(out, item);
				});
			}
			case Object[] fields -> {
				out.write(9);
				out.u32(fields.length);
				for (Object field : fields) {
					writeDecoded(out, field);
				}
			}
			case Variant variant -> {
				out.write(10);
				out.write(variant.index());
				out.write(variant.index() >> 8);
				out.u32(variant.fields().length);
				for (Object field : variant.fields()) {
					writeDecoded(out, field);
				}
			}
			case Slot slot -> {
				out.write(11);
				out.u32(slot.index());
			}
			case JavaNumber number -> {
				out.write(15);
				writeNumber(out, number.value());
			}
			default -> throw new IllegalArgumentException("Cannot describe " + value);
		}
	}

	/** A decoded value (see `content/data/ffi.rs`). */
	static Object decoded(ByteBuffer in) {
		return switch (in.get()) {
			case 0 -> null;
			case 1 -> false;
			case 2 -> true;
			case 3 -> in.getInt();
			case 4 -> in.getFloat();
			case 5 -> in.getDouble();
			case 6 -> string(in);
			case 7 -> {
				List<Object> list = new ArrayList<>();
				for (int i = in.getInt(); i > 0; i--) {
					list.add(decoded(in));
				}
				yield list;
			}
			case 8 -> {
				Map<String, Object> map = new LinkedHashMap<>();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					map.put(key, decoded(in));
				}
				yield map;
			}
			case 9 -> {
				Object[] fields = new Object[in.getInt()];
				for (int i = 0; i < fields.length; i++) {
					fields[i] = decoded(in);
				}
				yield fields;
			}
			case 10 -> {
				int index = in.getShort() & 0xFFFF;
				Object[] fields = new Object[in.getInt()];
				for (int i = 0; i < fields.length; i++) {
					fields[i] = decoded(in);
				}
				yield new Variant(index, fields);
			}
			case 11 -> new Slot(in.getInt());
			case 15 -> new JavaNumber(readNumber(in));
			default -> throw new IllegalStateException("Unknown native decoded tag");
		};
	}

	/** Builds Rust's encoding in {@code ops}, filling delegated slots with their codecs. */
	@SuppressWarnings("unchecked")
	static <T> T build(DynamicOps<T> ops, ByteBuffer in, @Nullable Element element) {
		// The two ops games use, built directly: the same trees `createMap` and
		// `createList` produce.
		DynamicOps<?> base = opsBase(ops);
		if (base == net.minecraft.nbt.NbtOps.INSTANCE) {
			return (T)buildTag(ops, in, element);
		}
		if (base == JsonOps.INSTANCE) {
			return (T)buildJson(ops, in, element);
		}
		return buildGeneric(ops, in, element);
	}

	@SuppressWarnings("unchecked")
	private static <T> net.minecraft.nbt.Tag buildTag(DynamicOps<T> ops, ByteBuffer in, @Nullable Element element) {
		int position = in.position();
		return switch (in.get()) {
			case 1 -> net.minecraft.nbt.ByteTag.valueOf(false);
			case 2 -> net.minecraft.nbt.ByteTag.valueOf(true);
			case 3 -> net.minecraft.nbt.IntTag.valueOf(in.getInt());
			case 4 -> net.minecraft.nbt.FloatTag.valueOf(in.getFloat());
			case 5 -> net.minecraft.nbt.DoubleTag.valueOf(in.getDouble());
			case 6 -> net.minecraft.nbt.StringTag.valueOf(string(in));
			case 7 -> {
				net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
				for (int i = in.getInt(); i > 0; i--) {
					list.add(buildTag(ops, in, element));
				}
				yield list;
			}
			case 8 -> {
				net.minecraft.nbt.CompoundTag compound = new net.minecraft.nbt.CompoundTag();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					int entry = in.position();
					byte entryTag = in.get();
					if (entryTag == 18) {
						T merged = encodeSlot(ops, element, in.getInt());
						if (merged instanceof net.minecraft.nbt.CompoundTag map) {
							for (Map.Entry<String, net.minecraft.nbt.Tag> e : map.entrySet()) {
								compound.put(e.getKey(), e.getValue());
							}
						} else {
							throw new Context.Failure("Not a map: " + merged);
						}
						continue;
					}
					if (entryTag == 14) {
						int slot = in.getInt();
						if (Objects.equals(element.values[slot], element.defaults[slot])) {
							continue;
						}
						compound.put(key, (net.minecraft.nbt.Tag)encodeSlot(ops, element, slot));
						continue;
					}
					in.position(entry);
					compound.put(key, buildTag(ops, in, element));
				}
				yield compound;
			}
			default -> {
				in.position(position);
				yield (net.minecraft.nbt.Tag)buildGeneric(ops, in, element);
			}
		};
	}

	private static <T> JsonElement buildJson(DynamicOps<T> ops, ByteBuffer in, @Nullable Element element) {
		int position = in.position();
		return switch (in.get()) {
			case 1 -> new JsonPrimitive(false);
			case 2 -> new JsonPrimitive(true);
			case 3 -> new JsonPrimitive(in.getInt());
			case 4 -> new JsonPrimitive(in.getFloat());
			case 5 -> new JsonPrimitive(in.getDouble());
			case 6 -> new JsonPrimitive(string(in));
			case 7 -> {
				JsonArray array = new JsonArray();
				for (int i = in.getInt(); i > 0; i--) {
					array.add(buildJson(ops, in, element));
				}
				yield array;
			}
			case 8 -> {
				JsonObject object = new JsonObject();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					int entry = in.position();
					byte entryTag = in.get();
					if (entryTag == 18) {
						Object merged = encodeSlot(ops, element, in.getInt());
						if (merged instanceof JsonObject map) {
							for (Map.Entry<String, JsonElement> e : map.entrySet()) {
								object.add(e.getKey(), e.getValue());
							}
						} else {
							throw new Context.Failure("Not a JSON object: " + merged);
						}
						continue;
					}
					if (entryTag == 14) {
						int slot = in.getInt();
						if (Objects.equals(element.values[slot], element.defaults[slot])) {
							continue;
						}
						object.add(key, (JsonElement)encodeSlot(ops, element, slot));
						continue;
					}
					in.position(entry);
					object.add(key, buildJson(ops, in, element));
				}
				yield object;
			}
			default -> {
				in.position(position);
				yield (JsonElement)buildGeneric(ops, in, element);
			}
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> T buildGeneric(DynamicOps<T> ops, ByteBuffer in, @Nullable Element element) {
		int tag = in.get();
		return switch (tag) {
			case 1 -> ops.createBoolean(false);
			case 2 -> ops.createBoolean(true);
			case 3 -> ops.createInt(in.getInt());
			case 4 -> ops.createFloat(in.getFloat());
			case 5 -> ops.createDouble(in.getDouble());
			case 6 -> ops.createString(string(in));
			case 7 -> {
				List<T> items = new ArrayList<>();
				for (int i = in.getInt(); i > 0; i--) {
					items.add(build(ops, in, element));
				}
				yield ops.createList(items.stream());
			}
			case 8 -> {
				List<Pair<T, T>> entries = new ArrayList<>();
				for (int i = in.getInt(); i > 0; i--) {
					String key = string(in);
					int position = in.position();
					byte entryTag = in.get();
					if (entryTag == 18) {
						// A delegated MapCodec's entries, merged into this map.
						T merged = encodeSlot(ops, element, in.getInt());
						ops.getMapValues(merged).getOrThrow(Context.Failure::new).forEach(entries::add);
						continue;
					}
					if (entryTag == 14) {
						int slot = in.getInt();
						if (Objects.equals(element.values[slot], element.defaults[slot])) {
							continue;
						}
						entries.add(Pair.of(ops.createString(key), encodeSlot(ops, element, slot)));
					} else {
						in.position(position);
						entries.add(Pair.of(ops.createString(key), build(ops, in, element)));
					}
				}
				yield ops.createMap(entries.stream());
			}
			case 11 -> encodeSlot(ops, element, in.getInt());
			case 14 -> encodeSlot(ops, element, in.getInt());
			case 17 -> {
				Number number = readNumber(in);
				yield switch (number) {
					case Byte b -> ops.createByte(b);
					case Short v -> ops.createShort(v);
					case Integer v -> ops.createInt(v);
					case Long v -> ops.createLong(v);
					case Float v -> ops.createFloat(v);
					case Double v -> ops.createDouble(v);
					default -> ops.createNumeric(number);
				};
			}
			case 16 -> {
				int[] ints = new int[in.getInt()];
				for (int i = 0; i < ints.length; i++) {
					ints[i] = in.getInt();
				}
				yield ops.createIntList(java.util.stream.IntStream.of(ints));
			}
			default -> throw new IllegalStateException("Unknown native encoded tag " + tag);
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> T encodeSlot(DynamicOps<T> ops, Element element, int slot) {
		Codec codec = element.codecs[slot];
		if (codec == null) {
			throw new Context.Failure("Unresolved native slot " + slot);
		}
		DataResult<T> result = (DataResult<T>)codec.encodeStart(ops, element.values[slot]);
		return result.getOrThrow(Context.Failure::new);
	}

	/**
	 * A data generator bootstrap ({@code VanillaRegistries}) that registers
	 * the vanilla data pack's definitions of {@code kind}, decoded by Rust
	 * against the bootstrap's lookups, instead of definitions built in Java.
	 */
	public static <E> net.minecraft.core.RegistrySetBuilder.RegistryBootstrap<E> bootstrap(Kind kind, Codec<E> registryCodec) {
		DataCodec<E> codec = (DataCodec<E>)registryCodec;
		return context -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
				@SuppressWarnings({"unchecked", "rawtypes"})
				@Override
				public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
					try {
						net.minecraft.core.HolderGetter<T> getter = (net.minecraft.core.HolderGetter<T>)context.lookup((ResourceKey)key);
						return Optional.of(new RegistryOps.RegistryInfo<>(new net.minecraft.core.HolderOwner<T>() {
						}, getter, com.mojang.serialization.Lifecycle.stable()));
					} catch (IllegalStateException missing) {
						return Optional.empty();
					}
				}
			});
			ResourceKey<? extends Registry<E>> registry = (ResourceKey<? extends Registry<E>>)(ResourceKey<?>)registry(kind);
			String directory = Registries.elementsDirPath(registry);
			try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
				net.minecraft.server.packs.PackType.SERVER_DATA, List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource())
			)) {
				var files = net.minecraft.resources.FileToIdConverter.json(directory).listMatchingResources(resources);
				for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> file : files.entrySet()) {
					ResourceLocation id = net.minecraft.resources.FileToIdConverter.json(directory).fileToId(file.getKey());
					byte[] bytes;
					try (var in = file.getValue().open()) {
						bytes = in.readAllBytes();
					} catch (java.io.IOException e) {
						throw new IllegalStateException("Cannot read " + file.getKey(), e);
					}
					E element = codec.decodeFile(ops, bytes).getOrThrow(error -> new IllegalStateException("Cannot decode " + file.getKey() + ": " + error));
					context.register(ResourceKey.create(registry, id), element);
				}
			}
		};
	}

	/** Registry keys of each kind, for loaders and bootstraps. */
	public static ResourceKey<? extends Registry<?>> registry(Kind kind) {
		return switch (kind) {
			case BIOME -> Registries.BIOME;
			case PAINTING_VARIANT -> Registries.PAINTING_VARIANT;
			case DAMAGE_TYPE -> Registries.DAMAGE_TYPE;
			case BANNER_PATTERN -> Registries.BANNER_PATTERN;
			case JUKEBOX_SONG -> Registries.JUKEBOX_SONG;
			case TRIM_PATTERN -> Registries.TRIM_PATTERN;
			case TRIM_MATERIAL -> Registries.TRIM_MATERIAL;
			case INSTRUMENT -> Registries.INSTRUMENT;
			case ENCHANTMENT -> Registries.ENCHANTMENT;
			case ENCHANTMENT_PROVIDER -> Registries.ENCHANTMENT_PROVIDER;
			case CAT_VARIANT -> Registries.CAT_VARIANT;
			case FROG_VARIANT -> Registries.FROG_VARIANT;
			case WOLF_VARIANT -> Registries.WOLF_VARIANT;
			case WOLF_SOUND_VARIANT -> Registries.WOLF_SOUND_VARIANT;
			case PIG_VARIANT -> Registries.PIG_VARIANT;
			case COW_VARIANT -> Registries.COW_VARIANT;
			case CHICKEN_VARIANT -> Registries.CHICKEN_VARIANT;
			case ZOMBIE_NAUTILUS_VARIANT -> Registries.ZOMBIE_NAUTILUS_VARIANT;
		};
	}

	static {
		try {
			MethodHandle install = java.lang.invoke.MethodHandles.lookup().findStatic(NativeData.class, "installBlocks", java.lang.invoke.MethodType.methodType(int.class));
			MemorySegment stub = java.lang.foreign.Linker.nativeLinker().upcallStub(install, FunctionDescriptor.of(ValueLayout.JAVA_INT), Arena.global());
			MethodHandle set = handle("mattmc_value_set_install_blocks", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			set.invokeExact(stub);
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot install the native block registry callback", e);
		}
	}

	/** What {@code ready()} itself threw during a callback, if it did. */
	private static volatile Throwable installError;

	/** A data call's handle when it met a block state the registry couldn't be installed for. */
	static final long BLOCKS_MISSING_HANDLE = -2;
	/** A file call's handle when its bytes are not JSON Rust parses. */
	static final long JSON_INVALID_HANDLE = -1;
	/** A data call's handles when a Java codec it called back threw: {@code -3 - id}. */
	static final long JAVA_THROWN_HANDLE = -3;

	@SuppressWarnings("unchecked")
	private static <X extends Throwable> RuntimeException rethrow(Throwable e) throws X {
		throw (X)e;
	}
	/** The status-2 exception class Rust reports for that (`schema::BLOCKS_MISSING`). */
	static final int BLOCKS_MISSING = 4;

	/**
	 * Rust's first block state of a call: installs the block registry (1 once
	 * installed). It never throws into Rust. If installing fails, Rust ends the
	 * call with its BLOCKS_MISSING exception pending, so no further callback
	 * runs, and the Java entry point throws {@link #blocksMissing}.
	 */
	private static int installBlocks() {
		try {
			if (net.minecraft.world.level.block.NativeBlockRegistry.ready()) {
				return 1;
			}
		} catch (Throwable e) {
			// ready() catches installation errors itself; anything else is kept for the report.
			if (installError == null) {
				installError = e;
			}
		}
		return 0;
	}

	/** The guard's exception for a call that met a block state without the registry, caused by the installation failure. */
	static IllegalStateException blocksMissing(String action) {
		Throwable cause = net.minecraft.world.level.block.NativeBlockRegistry.failure();
		return new IllegalStateException(action + " needs the native block registry", cause != null ? cause : installError);
	}
}
