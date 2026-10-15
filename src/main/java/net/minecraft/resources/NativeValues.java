package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Value types definitions embed, owned by Rust (`content/data/values.rs`):
 * Rust decodes, validates and encodes them; Java objects are projections.
 * A codec's {@code bounded} form adds the {@code codec(min, max)} range check.
 */
public final class NativeValues {
	private static final MethodHandle DECODE = NativeData.handle("mattmc_value_decode", FunctionDescriptor.of(
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle ENCODE = NativeData.handle("mattmc_value_encode", FunctionDescriptor.of(
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	static {
		installLeafCheck();
		installJavaDecode();
	}

	/** Codecs Java still owns, run while Rust decodes (`schema::Java`, by ordinal). */
	public enum JavaCodec {
		DIALOG,
		NBT,
		PROFILE,
		/** Enchantment effect requirements, validated against each component's context (`content/data/enchant.rs`). */
		CONDITION_ENCHANTED_DAMAGE,
		CONDITION_ENCHANTED_ITEM,
		CONDITION_ENCHANTED_ENTITY,
		CONDITION_ENCHANTED_LOCATION,
		CONDITION_HIT_BLOCK,
		DAMAGE_TYPE,
		ATTRIBUTE,
		GAME_EVENT,
		BLOCK_STATE_PROVIDER,
		BLOCK_PREDICATE,
		EXPLOSION_PARTICLES,
		MOB_EFFECTS,
		BLOCKS,
		ENTITY_TYPES,
		SOUND_EVENT,
		/** Data definition fields (`content/data/registries.rs`). */
		ENCHANTMENT_HOLDER,
		ITEMS,
		ENCHANTMENTS,
		STRUCTURES,
		BIOMES,
		CARVERS,
		FEATURES;

		static final JavaCodec[] VALUES = values();

		public Codec<?> codec() {
			return switch (this) {
				case DIALOG -> net.minecraft.server.dialog.Dialog.CODEC;
				case NBT -> net.minecraft.util.ExtraCodecs.NBT;
				case PROFILE -> net.minecraft.world.item.component.ResolvableProfile.CODEC;
				case CONDITION_ENCHANTED_DAMAGE -> Conditions.ENCHANTED_DAMAGE;
				case CONDITION_ENCHANTED_ITEM -> Conditions.ENCHANTED_ITEM;
				case CONDITION_ENCHANTED_ENTITY -> Conditions.ENCHANTED_ENTITY;
				case CONDITION_ENCHANTED_LOCATION -> Conditions.ENCHANTED_LOCATION;
				case CONDITION_HIT_BLOCK -> Conditions.HIT_BLOCK;
				case DAMAGE_TYPE -> net.minecraft.world.damagesource.DamageType.CODEC;
				case ATTRIBUTE -> net.minecraft.world.entity.ai.attributes.Attribute.CODEC;
				case GAME_EVENT -> net.minecraft.world.level.gameevent.GameEvent.CODEC;
				case BLOCK_STATE_PROVIDER -> net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.CODEC;
				case BLOCK_PREDICATE -> net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.CODEC;
				case EXPLOSION_PARTICLES -> Conditions.EXPLOSION_PARTICLES;
				case MOB_EFFECTS -> Conditions.MOB_EFFECTS;
				case BLOCKS -> Conditions.BLOCKS;
				case ENTITY_TYPES -> Conditions.ENTITY_TYPES;
				case SOUND_EVENT -> net.minecraft.sounds.SoundEvent.CODEC;
				case ENCHANTMENT_HOLDER -> net.minecraft.world.item.enchantment.Enchantment.CODEC;
				case ITEMS -> Conditions.ITEMS;
				case ENCHANTMENTS -> Conditions.ENCHANTMENTS;
				case STRUCTURES -> Conditions.STRUCTURES;
				case BIOMES -> Conditions.BIOMES;
				case CARVERS -> net.minecraft.world.level.biome.NativeBiomes.CARVERS;
				case FEATURES -> net.minecraft.world.level.biome.NativeBiomes.FEATURES;
			};
		}

		/** One codec instance each, as each component's codec held its own. */
		private static final class Conditions {
			static final Codec<?> ENCHANTED_DAMAGE = net.minecraft.world.item.enchantment.ConditionalEffect.conditionCodec(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENCHANTED_DAMAGE);
			static final Codec<?> ENCHANTED_ITEM = net.minecraft.world.item.enchantment.ConditionalEffect.conditionCodec(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENCHANTED_ITEM);
			static final Codec<?> ENCHANTED_ENTITY = net.minecraft.world.item.enchantment.ConditionalEffect.conditionCodec(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENCHANTED_ENTITY);
			static final Codec<?> ENCHANTED_LOCATION = net.minecraft.world.item.enchantment.ConditionalEffect.conditionCodec(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENCHANTED_LOCATION);
			static final Codec<?> HIT_BLOCK = net.minecraft.world.item.enchantment.ConditionalEffect.conditionCodec(
				net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.HIT_BLOCK);
			static final Codec<?> EXPLOSION_PARTICLES = net.minecraft.util.random.WeightedList.codec(net.minecraft.core.particles.ExplosionParticleInfo.CODEC);
			static final Codec<?> MOB_EFFECTS = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.MOB_EFFECT);
			static final Codec<?> BLOCKS = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.BLOCK);
			static final Codec<?> ENTITY_TYPES = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.ENTITY_TYPE);
			static final Codec<?> ITEMS = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.ITEM);
			static final Codec<?> ENCHANTMENTS = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.ENCHANTMENT);
			static final Codec<?> STRUCTURES = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.STRUCTURE);
			static final Codec<?> BIOMES = net.minecraft.core.RegistryCodecs.homogeneousList(net.minecraft.core.registries.Registries.BIOME);
		}
	}

	/** One native decode in progress: its input nodes, and the values Java decoded for its delegated slots. */
	private static final class Frame {
		DynamicOps<Object> ops;
		final List<Object> nodes = new ArrayList<>();
		final List<Object> resolved = new ArrayList<>();
	}

	/**
	 * A thread's native call state: decode frames, and one scratch per depth.
	 * A codec call holds its depth until it returns, so nested calls (Rust
	 * calling back into Java, projections decoding delegated parts) use
	 * deeper scratches and never reuse a buffer still being read.
	 */
	private static final class ThreadState {
		final java.util.ArrayDeque<Frame> frames = new java.util.ArrayDeque<>();
		final List<Scratch> scratches = new ArrayList<>();
		/** Exceptions callbacks caught during the outermost call, by the id Rust reports. */
		final List<Throwable> thrown = new ArrayList<>();
		/** A callback answer longer than Rust's buffer, for its immediate retry. */
		@Nullable byte[] pending;
		int pendingNode = -1;
		int pendingSlot = -1;
		int depth;

		Scratch enter() {
			if (this.depth == 0) {
				this.thrown.clear();
			}
			if (this.scratches.size() <= this.depth) {
				this.scratches.add(new Scratch());
			}
			return this.scratches.get(this.depth++);
		}

		void exit() {
			if (--this.depth == 0) {
				this.thrown.clear();
			}
		}

		/** Keeps {@code e} for Rust to report; returns its id. */
		int keep(Throwable e) {
			if (this.depth == 0) {
				this.thrown.clear();
			}
			this.thrown.add(e);
			return this.thrown.size() - 1;
		}

		/** The kept answer for this retry of a callback, clearing it. */
		@Nullable byte[] retry(int node, int slot) {
			byte[] bytes = this.pending;
			if (bytes == null || this.pendingNode != node || this.pendingSlot != slot) {
				return null;
			}
			this.pending = null;
			return bytes;
		}

		/** Writes {@code bytes} when they fit, else keeps them for the retry; returns their length. */
		long answer(byte[] bytes, int node, int slot, MemorySegment out, long capacity) {
			if (bytes.length <= capacity) {
				out.reinterpret(capacity).asByteBuffer().put(bytes);
			} else {
				this.pending = bytes;
				this.pendingNode = node;
				this.pendingSlot = slot;
			}
			return bytes.length;
		}
	}

	/** A data definition call's handle, the values Java's codecs decoded for its slots, and what a callback threw. */
	record DataCall(long handle, List<Object> resolved, @Nullable Throwable thrown) {
	}

	@FunctionalInterface
	interface NativeCall {
		long call() throws Throwable;
	}

	/**
	 * Runs a data definition decode with a frame, so the Java codecs Rust
	 * calls back decode synchronously ({@code ops}; {@code nodes} are the
	 * input's Java nodes, empty for a file Rust parsed). A handle of
	 * {@code thrownHandle - id} reports the exception a callback kept.
	 */
	static DataCall dataCall(DynamicOps<?> ops, List<?> nodes, long thrownHandle, NativeCall call) throws Throwable {
		ThreadState state = STATE.get();
		Frame frame = new Frame();
		frame.ops = (DynamicOps<Object>)ops;
		frame.nodes.addAll(nodes);
		state.enter();
		state.frames.push(frame);
		try {
			long handle = call.call();
			return new DataCall(handle, frame.resolved, handle <= thrownHandle ? state.thrown.get((int)(thrownHandle - handle)) : null);
		} finally {
			state.frames.pop();
			state.exit();
		}
	}

	/** Rethrows a callback's original exception unchanged. */
	@SuppressWarnings("unchecked")
	private static <X extends Throwable> RuntimeException rethrow(Throwable e) throws X {
		throw (X)e;
	}

	private static final ThreadLocal<ThreadState> STATE = ThreadLocal.withInitial(ThreadState::new);

	/** Wraps the codecs callbacks run, for boundary tests (identity otherwise). */
	static java.util.function.UnaryOperator<Codec<?>> testDelegates = java.util.function.UnaryOperator.identity();

	private static void installJavaDecode() {
		try {
			MethodHandle decode = java.lang.invoke.MethodHandles.lookup().findStatic(NativeValues.class, "javaDecode", java.lang.invoke.MethodType.methodType(
				long.class, int.class, int.class, MemorySegment.class, long.class, int.class, int.class, MemorySegment.class, long.class
			));
			FunctionDescriptor descriptor = FunctionDescriptor.of(
				ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
			);
			MemorySegment stub = java.lang.foreign.Linker.nativeLinker().upcallStub(decode, descriptor, Arena.global());
			MethodHandle install = NativeData.handle("mattmc_value_set_java_decode", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			install.invokeExact(stub);
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot install native Java decode", e);
		}
	}

	/**
	 * Decodes a delegated node of the current native decode with a Java codec
	 * (`schema::JavaDecode`), keeping the value or partial for its slot.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static long javaDecode(int kind, int id, MemorySegment name, long nameLength, int node, int slot, MemorySegment out, long capacity) {
		ThreadState state = STATE.get();
		byte[] kept = state.retry(node, slot);
		if (kept != null) {
			return state.answer(kept, node, slot, out, capacity);
		}
		NativeData.Out answer = new NativeData.Out();
		Frame frame = state.frames.peek();
		if (frame == null) {
			answer.write(2);
		} else {
			try {
				// A node Rust parsed itself (a data pack file) arrives as an input
				// tree, after a component codec's type name.
				java.nio.ByteBuffer named = name.reinterpret(nameLength).asByteBuffer().order(java.nio.ByteOrder.LITTLE_ENDIAN);
				Codec codec;
				if (kind == 0) {
					codec = JavaCodec.VALUES[id].codec();
				} else {
					byte[] bytes = new byte[node >= 0 ? (int)nameLength : named.getInt()];
					named.get(bytes);
					String type = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
					codec = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.parse(type)).codecOrThrow();
				}
				Object input = node >= 0 ? frame.nodes.get(node) : NativeData.tree(named);
				DataResult<Pair<Object, Object>> result = ((Codec)testDelegates.apply(codec)).decode(frame.ops, input);
				Optional<Pair<Object, Object>> value = result.resultOrPartial();
				while (frame.resolved.size() <= slot) {
					frame.resolved.add(null);
				}
				frame.resolved.set(slot, value.map(Pair::getFirst).orElse(null));
				if (result.isSuccess()) {
					answer.write(1);
				} else {
					answer.write(0);
					answer.string(result.error().orElseThrow().message());
					answer.write(value.isPresent() ? 1 : 0);
				}
			} catch (Throwable e) {
				// The decode rethrows this same exception, as the original codec did.
				answer.reset();
				answer.write(3);
				answer.u32(state.keep(e));
				answer.string(String.valueOf(e.getMessage()));
			}
		}
		return state.answer(answer.toByteArray(), node, slot, out, capacity);
	}

	/** First guess for a result's size; larger results take a second call. */
	private static final long RESULT_GUESS = 1024;

	private NativeValues() {
	}

	private static final MethodHandle DECODE_CASE = NativeData.handle("mattmc_value_decode_case", FunctionDescriptor.of(
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle CASE_NAMES = NativeData.handle("mattmc_value_case_names", FunctionDescriptor.of(
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle TAKE_PENDING = NativeData.handle("mattmc_value_take_pending", FunctionDescriptor.of(
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

	/** Value kinds, in `content/data/values.rs` order. */
	public enum Kind {
		INT_PROVIDER(false),
		FLOAT_PROVIDER(false),
		LEVEL_BASED_VALUE(false),
		UNIFORM_INT(false),
		PARTICLE(true),
		POSITION_SOURCE(false),
		COMPONENT(false),
		STYLE(false),
		CLICK_EVENT(false),
		HOVER_EVENT(false),
		ITEM_STACK(false),
		SINGLE_ITEM(false),
		COMPONENT_PATCH(false),
		ITEM_STACK_WITH_SLOT(false),
		ENCHANTMENT_EFFECTS(true),
		ENCHANTMENT_VALUE_EFFECT(false),
		ENCHANTMENT_ENTITY_EFFECT(true),
		ENCHANTMENT_LOCATION_EFFECT(true);

		/** Whether values embed block states, read from Rust's installed block registry. */
		final boolean blocks;

		Kind(boolean blocks) {
			this.blocks = blocks;
		}
	}

	/**
	 * Gives Rust the string parsers Java owns (`schema::Leaf`): the command
	 * system's selector parser and the JDK's URI parser, run while decoding.
	 */
	private static void installLeafCheck() {
		try {
			MethodHandle check = java.lang.invoke.MethodHandles.lookup().findStatic(NativeValues.class, "leafCheck", java.lang.invoke.MethodType.methodType(
				long.class, int.class, MemorySegment.class, long.class, MemorySegment.class, long.class
			));
			FunctionDescriptor descriptor = FunctionDescriptor.of(
				ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
			);
			MemorySegment stub = java.lang.foreign.Linker.nativeLinker().upcallStub(check, descriptor, Arena.global());
			MethodHandle install = NativeData.handle("mattmc_value_set_leaf_check", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			install.invokeExact(stub);
		} catch (Throwable e) {
			throw new IllegalStateException("Cannot install native leaf parsers", e);
		}
	}

	/**
	 * Runs a leaf parser: -1 when {@code text} parses, else the error's UTF-8
	 * length; or, when the parser throws, -2 minus the length of the kept
	 * exception's u32 id and message. An answer that does not fit is kept for
	 * Rust's immediate retry.
	 */
	private static long leafCheck(int leaf, MemorySegment text, long length, MemorySegment out, long capacity) {
		ThreadState state = STATE.get();
		byte[] kept = state.retry(-2 - leaf, -1);
		if (kept != null) {
			return leafAnswer(state, leaf, kept, out, capacity);
		}
		String error;
		NativeData.Out answer = new NativeData.Out();
		try {
			byte[] bytes = new byte[(int)length];
			text.reinterpret(length).asByteBuffer().get(bytes);
			String string = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			error = switch (leaf) {
				case 0 -> net.minecraft.commands.arguments.selector.SelectorPattern.parse(string).error().map(e -> e.message()).orElse(null);
				case 1 -> {
					try {
						net.minecraft.Util.parseAndValidateUntrustedUri(string);
						yield null;
					} catch (java.net.URISyntaxException e) {
						yield e.getMessage();
					}
				}
				default -> "Unknown leaf parser " + leaf;
			};
		} catch (Throwable e) {
			// The decode rethrows this same exception, as the original codec did.
			answer.write(1);
			answer.u32(state.keep(e));
			byte[] message = String.valueOf(e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			answer.write(message, 0, message.length);
			return leafAnswer(state, leaf, answer.toByteArray(), out, capacity);
		}
		if (error == null) {
			return -1;
		}
		answer.write(0);
		byte[] message = error.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		answer.write(message, 0, message.length);
		return leafAnswer(state, leaf, answer.toByteArray(), out, capacity);
	}

	/** Writes a leaf answer ({@code tagged}: u8 thrown, then the payload) without its tag; returns its encoded length. */
	private static long leafAnswer(ThreadState state, int leaf, byte[] tagged, MemorySegment out, long capacity) {
		byte[] payload = java.util.Arrays.copyOfRange(tagged, 1, tagged.length);
		if (payload.length <= capacity) {
			out.reinterpret(capacity).asByteBuffer().put(payload);
		} else {
			state.pending = tagged;
			state.pendingNode = -2 - leaf;
			state.pendingSlot = -1;
		}
		return tagged[0] == 0 ? payload.length : -2 - payload.length;
	}

	/** A kind's dispatch case names, in case order (its type registry's IDs). */
	public static List<String> caseNames(Kind kind) {
		ThreadState state = STATE.get();
		Scratch scratch = state.enter();
		try {
			scratch.out.reset();
			ByteBuffer in = invoke(scratch, scratch.out, (bytes, length, result, capacity) -> (long)CASE_NAMES.invokeExact(kind.ordinal(), result, capacity));
			List<String> names = new ArrayList<>();
			for (int i = in.getInt(); i > 0; i--) {
				names.add(NativeData.string(in));
			}
			return names;
		} finally {
			state.exit();
		}
	}

	/** A codec for values without delegated parts. */
	public static <E> ValueCodec<E> codec(Kind kind, Function<Object, ? extends E> projector, Function<? super E, Object> unprojector) {
		return new ValueCodec<>(kind, false, 0.0, 0.0, (value, context) -> projector.apply(value), (value, builder) -> unprojector.apply(value));
	}

	/** A codec whose values have delegated parts, resolved and described like a registry's ({@link NativeData.Projector}). */
	public static <E> ValueCodec<E> codec(Kind kind, NativeData.Projector<? extends E> projector, NativeData.Unprojector<? super E> unprojector) {
		return new ValueCodec<>(kind, false, 0.0, 0.0, projector, unprojector);
	}

	public static final class ValueCodec<E> implements Codec<E> {
		private final Kind kind;
		private final boolean bounded;
		private final double min;
		private final double max;
		private final NativeData.Projector<? extends E> projector;
		private final NativeData.Unprojector<? super E> unprojector;

		ValueCodec(Kind kind, boolean bounded, double min, double max, NativeData.Projector<? extends E> projector, NativeData.Unprojector<? super E> unprojector) {
			this.kind = kind;
			this.bounded = bounded;
			this.min = min;
			this.max = max;
			this.projector = projector;
			this.unprojector = unprojector;
		}

		/** This codec, also checking the value's range lies in {@code [min, max]}. */
		public ValueCodec<E> bounded(double min, double max) {
			return new ValueCodec<>(this.kind, true, min, max, this.projector, this.unprojector);
		}

		@Override
		public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
			return this.decodeWith(ops, input, null);
		}

		/** Decodes {@code input}, or with {@code caseName} one dispatch case's fields from a map without its type. */
		@SuppressWarnings("unchecked")
		private <T> DataResult<Pair<E, T>> decodeWith(DynamicOps<T> ops, T input, @Nullable String caseName) {
			// Rust installs the block registry on the first block state it meets.
			if (this.kind.blocks && net.minecraft.server.Bootstrap.bootstrapDuration.get() < 0) {
				throw new IllegalStateException("Decoding " + this.kind + " needs the native block registry");
			}
			ThreadState state = STATE.get();
			Scratch scratch = state.enter();
			try {
				Frame frame = scratch.frame;
				frame.ops = (DynamicOps<Object>)ops;
				frame.nodes.clear();
				frame.resolved.clear();
				NativeData.Out out = scratch.out;
				out.reset();
				NativeData.write(ops, input, (List<T>)frame.nodes, out);
				DynamicOps<?> base = NativeData.opsBase(ops);
				// Bit 0: NbtOps reads numbers as booleans; bit 1: RegistryOps, whose registry file codecs (sounds) take references.
				int mode = (base == net.minecraft.nbt.NbtOps.INSTANCE ? 1 : base == JsonOps.INSTANCE ? 0 : ops.getBooleanValue(ops.createInt(1)).isSuccess() ? 1 : 0)
					| (ops instanceof RegistryOps<?> ? 2 : 0);
				state.frames.push(frame);
				ByteBuffer in;
				try {
					if (caseName == null) {
						in = invoke(scratch, out, (bytes, length, result, capacity) ->
							(long)DECODE.invokeExact(this.kind.ordinal(), mode, this.bounded ? 1 : 0, this.min, this.max, bytes, length, result, capacity));
					} else {
						byte[] name = caseName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
						try (Arena arena = Arena.ofConfined()) {
							MemorySegment nameSegment = NativeData.copy(arena, name);
							in = invoke(scratch, out, (bytes, length, result, capacity) ->
								(long)DECODE_CASE.invokeExact(this.kind.ordinal(), mode, nameSegment, (long)name.length, bytes, length, result, capacity));
						}
					}
				} finally {
					state.frames.pop();
				}
				byte status = in.get();
				if (status == 2) {
					// An exception the Java codec threw out of decoding: a check Rust
					// made (a constructor's), or a callback's own exception.
					byte exception = in.get();
					int id = in.getInt();
					String message = NativeData.string(in);
					if (exception == 3) {
						throw NativeValues.<RuntimeException>rethrow(state.thrown.get(id));
					}
					if (exception == NativeData.BLOCKS_MISSING) {
						throw NativeData.blocksMissing("Decoding " + this.kind);
					}
					throw exception == 1 ? new NullPointerException(message) : new IllegalArgumentException(message);
				}
				String error = status == 0 ? NativeData.string(in) : null;
				// DFU keeps a partial value for some failures (lists, records of them).
				boolean hasValue = status == 1 || in.get() != 0;
				Object decoded = hasValue ? NativeData.decoded(in) : null;
				NativeData.Context context = NativeData.Context.forValue(
					(DynamicOps<Object>)ops, frame.nodes, ops instanceof RegistryOps<?> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : null, in, frame.resolved
				);
				for (int i = in.getInt(); i > 0; i--) {
					NativeData.LOGGER.error(NativeData.string(in));
				}
				if (!hasValue) {
					return DataResult.error(() -> error);
				}
				E value;
				try {
					value = this.projector.project(decoded, context);
				} catch (NativeData.Context.Failure failure) {
					return DataResult.error(failure::getMessage);
				}
				return error == null ? DataResult.success(Pair.of(value, ops.empty())) : DataResult.error(() -> error, Pair.of(value, ops.empty()));
			} finally {
				state.exit();
			}
		}

		@Override
		public <T> DataResult<T> encode(E value, DynamicOps<T> ops, T prefix) {
			NativeData.Builder builder = new NativeData.Builder();
			Object described;
			try {
				described = this.unprojector.unproject(value, builder);
			} catch (IllegalArgumentException e) {
				return DataResult.error(e::getMessage);
			}
			ThreadState state = STATE.get();
			Scratch scratch = state.enter();
			try {
				NativeData.Out out = scratch.out;
				out.reset();
				NativeData.writeDecoded(out, described);
				ByteBuffer in = invoke(scratch, out, (bytes, length, result, capacity) ->
					(long)ENCODE.invokeExact(this.kind.ordinal(), this.bounded ? 1 : 0, this.min, this.max, bytes, length, result, capacity));
				byte status = in.get();
				if (status == 2) {
					// A block state without the registry throws; another recorded
					// exception stays the error result encoding always gave.
					byte exception = in.get();
					in.getInt();
					String error = NativeData.string(in);
					if (exception == NativeData.BLOCKS_MISSING) {
						throw NativeData.blocksMissing("Encoding " + this.kind);
					}
					return DataResult.error(() -> error);
				}
				if (status == 0) {
					String error = NativeData.string(in);
					return DataResult.error(() -> error);
				}
				NativeData.Element element = builder.values.isEmpty()
					? NativeData.Element.EMPTY
					: new NativeData.Element(0L, false, builder.values.toArray(), builder.codecs.toArray(Codec<?>[]::new), builder.defaults.toArray());
				T result;
				try {
					result = NativeData.build(ops, in, element);
				} catch (NativeData.Context.Failure failure) {
					return DataResult.error(failure::getMessage);
				}
				return prefix.equals(ops.empty()) ? DataResult.success(result) : ops.getMap(result).flatMap(map -> ops.mergeToMap(prefix, map));
			} finally {
				state.exit();
			}
		}

		/** This record-shaped codec as a {@code MapCodec}: its fields merged into the enclosing map. */
		public MapCodec<E> mapCodec() {
			ValueCodec<E> codec = this;
			return new MapCodec<>() {
				@Override
				public <T> Stream<T> keys(DynamicOps<T> ops) {
					return Stream.empty();
				}

				@Override
				public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
					return codec.parse(ops, ops.createMap(input.entries()));
				}

				@Override
				public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
					DataResult<T> encoded = codec.encodeStart(ops, input);
					return encoded.flatMap(ops::getMapValues).map(entries -> {
						entries.forEach(entry -> prefix.add(entry.getFirst(), entry.getSecond()));
						return prefix;
					}).result().orElseGet(() -> prefix.withErrorsFrom(encoded));
				}
			};
		}

		/** One dispatch case of this codec, as its type registry holds it: the fields without {@code type}. */
		public MapCodec<E> dispatchCase(String type) {
			ValueCodec<E> codec = this;
			return new MapCodec<>() {
				@Override
				public <T> Stream<T> keys(DynamicOps<T> ops) {
					return Stream.empty();
				}

				@Override
				public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
					return codec.decodeWith(ops, ops.createMap(input.entries()), type).map(Pair::getFirst);
				}

				@Override
				public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
					DataResult<T> encoded = codec.encodeStart(ops, input);
					return encoded.flatMap(ops::getMapValues).map(entries -> {
						entries.filter(entry -> !ops.getStringValue(entry.getFirst()).result().orElse("").equals("type"))
							.forEach(entry -> prefix.add(entry.getFirst(), entry.getSecond()));
						return prefix;
					}).result().orElseGet(() -> prefix.withErrorsFrom(encoded));
				}
			};
		}

		@Override
		public String toString() {
			return "NativeValue[" + this.kind + (this.bounded ? " " + this.min + ".." + this.max + "]" : "]");
		}
	}

	@FunctionalInterface
	private interface Call {
		long invoke(MemorySegment bytes, long length, MemorySegment result, long capacity) throws Throwable;
	}

	/** One depth's native buffers, input writer, decode frame and result view. */
	private static final class Scratch {
		private MemorySegment input = Arena.ofAuto().allocate(1024);
		private MemorySegment result = Arena.ofAuto().allocate(RESULT_GUESS);
		private ByteBuffer view = this.result.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
		final NativeData.Out out = new NativeData.Out();
		final Frame frame = new Frame();

		MemorySegment input(long size) {
			if (size > this.input.byteSize()) {
				this.input = Arena.ofAuto().allocate(Math.max(size, this.input.byteSize() * 2));
			}
			return this.input;
		}

		void growResult(long size) {
			this.result = Arena.ofAuto().allocate(Math.max(size, this.result.byteSize() * 2));
			this.view = this.result.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
		}
	}

	/** Runs one native call at {@code scratch}'s depth; the result stays valid while that depth is held. */
	private static ByteBuffer invoke(Scratch scratch, NativeData.Out input, Call call) {
		try {
			MemorySegment bytes = scratch.input(Math.max(1, input.size()));
			input.copyTo(bytes);
			long size = call.invoke(bytes, input.size(), scratch.result, scratch.result.byteSize());
			if (size > scratch.result.byteSize()) {
				// Rust kept the result: copy it rather than run the call (and its callbacks) again.
				scratch.growResult(size);
				long taken = (long)TAKE_PENDING.invokeExact(scratch.result, scratch.result.byteSize());
				if (taken != size) {
					throw new IllegalStateException("Native value result was not kept");
				}
			}
			return scratch.view.clear().limit((int)size);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Native value call failed", e);
		}
	}
}
