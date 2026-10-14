package net.minecraft.world.level.levelgen;

import net.logging.LogUtils;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeBlockRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.NativeDecorationRegion;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Native biome decoration ({@code levelgen/decoration/}). {@link
 * ChunkGenerator#applyBiomeDecoration} keeps its step/feature loop and seeds;
 * a placed feature whose modifiers and configured feature have Rust
 * implementations runs there in one call, including its random draws, region
 * reads and section writes. Everything else keeps Java's path. Programs are
 * compiled once per generator, generation context, biome binding and tag
 * generation. {@code -Dmattmc.worldgen.javaDecoration=true} keeps Java's path.
 */
public final class NativeDecoration {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PLACED = 1, NOT_PLACED = 0, UNAVAILABLE_CHUNK = -2, BROKEN_BINDING = -3, JAVA_ROUTE = -4, PANIC = -5, CALLBACK_FAILED = -6;
	private static final int OP_UPDATE_SHAPE_AT_EDGE = 1, OP_BEEHIVE_PRESENT = 2, OP_STORE_BEES = 3, OP_PLACE_JAVA = 4, OP_TEST_PREDICATE = 5, OP_STRUCTURES = 6, OP_PLACE_FEATURE = 7, OP_JAVA_STEP = 8, OP_MATH_LOG = 9, OP_FAR_STATE = 10, OP_FAR_HEIGHT = 11, OP_FAR_BIOME = 12, OP_MATH_POW = 13, OP_MATH_SIN = 14, OP_MATH_COS = 15, OP_BLOCK_LIGHT = 16;
	private static final MethodHandle CREATE = handle("program_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle RELEASE = handle("program_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle PLAN_CREATE = handle("plan_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle PLAN_RELEASE = handle("plan_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle DECORATE = handle("decorate", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final int DECORATED = 1, RESUME = 2;
	/** {@code decorate} results besides a resume point. */
	public static final long DECORATION_DONE = -1, DECORATION_DECLINED = -2;
	/** {@code -Dmattmc.worldgen.javaSequence=true}: Java's step loop, one native call per placed feature. */
	private static final boolean JAVA_SEQUENCE = Boolean.getBoolean("mattmc.worldgen.javaSequence");
	private static final java.util.concurrent.atomic.LongAdder SEQUENCED = new java.util.concurrent.atomic.LongAdder(),
		SEQUENCE_DECLINED = new java.util.concurrent.atomic.LongAdder(), SEQUENCE_RESUMED = new java.util.concurrent.atomic.LongAdder();
	private static final MethodHandle EVENTS = handle("events", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle INSTALL_FACTS = handle("install_facts", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static volatile boolean factsInstalled;
	private static final MethodHandle LAZY_PRIMING = handle("lazy_priming", FunctionDescriptor.of(ValueLayout.JAVA_INT));
	private static final MethodHandle SINE_MATCHES = handle("sine_matches", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle PLACE = handle("place", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS));
	private static final MethodHandle KIND = handle("program_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle SET_BLOCK = handle("program_set_block", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	/** The placement running on this thread, for Rust's sync upcalls. */
	private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();
	private static final MemorySegment UPCALL;

	static {
		try {
			var target = java.lang.invoke.MethodHandles.lookup().findStatic(NativeDecoration.class, "upcall",
				java.lang.invoke.MethodType.methodType(int.class, int.class, MemorySegment.class, int.class));
			UPCALL = java.lang.foreign.Linker.nativeLinker().upcallStub(target,
				FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Arena.global());
		} catch (ReflectiveOperationException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	/** What a sync upcall needs, and the first failure it saw. */
	private static final class Call {
		final NativeDecorationRegion region;
		final WorldGenLevel level;
		final ChunkGenerator generator;
		final WorldgenRandom random;
		final List<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> javaFeatures;
		final List<NativeDecorationEncoder.JavaPredicate> javaPredicates;
		List<NativeDecorationEncoder.JavaStep> javaSteps = List.of();
		/** Set for the native step loop: its plan's programs by slot, and Java's steps. */
		@Nullable
		Plan plan;
		@Nullable
		Sequence sequence;
		@Nullable
		Throwable failure;

		Call(NativeDecorationRegion region, WorldGenLevel level, ChunkGenerator generator, WorldgenRandom random,
			 List<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> javaFeatures,
			 List<NativeDecorationEncoder.JavaPredicate> javaPredicates) {
			this.region = region;
			this.level = level;
			this.generator = generator;
			this.random = random;
			this.javaFeatures = javaFeatures;
			this.javaPredicates = javaPredicates;
		}
	}
	private static final Cleaner CLEANER = Cleaner.create();
	private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaDecoration") && !SharedConstants.DEBUG_FEATURE_COUNT && sineMatches();
	/**
	 * Verification only, {@code -Dmattmc.worldgen.decorationTest=}: {@code fail:N} makes the Nth native
	 * block write fail mid-placement; {@code javafail:N} throws at the Nth {@code WorldGenRegion.setBlock}.
	 */
	private static final String TEST = System.getProperty("mattmc.worldgen.decorationTest", "");
	/** Constant-folded guard for {@link #javaWriteHook}. */
	public static final boolean JAVA_FAIL_TEST = TEST.startsWith("javafail:");
	private static final java.util.concurrent.atomic.AtomicLong JAVA_FAIL_COUNTDOWN = new java.util.concurrent.atomic.AtomicLong(
		TEST.startsWith("javafail:") ? Long.parseLong(TEST.substring(9)) : -1);

	static {
		if (TEST.startsWith("fail:")) {
			try {
				var hook = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_decoration_test_fail_after", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
				hook.invokeExact(Long.parseLong(TEST.substring(5)));
			} catch (Throwable failure) {
				throw new ExceptionInInitializerError(failure);
			}
		}
	}

	/**
	 * Verification only: arms {@code fail:N} (the Nth native block write fails) or {@code javafail:N}
	 * (the Nth {@code WorldGenRegion.setBlock} throws; needs {@code decorationTest=javafail:-1} at startup).
	 */
	public static void armTestFailure(String spec) {
		long count = Long.parseLong(spec.substring(spec.indexOf(':') + 1));
		if (spec.startsWith("javafail:")) {
			if (!JAVA_FAIL_TEST) throw new IllegalStateException("Start with -Dmattmc.worldgen.decorationTest=javafail:-1");
			JAVA_FAIL_COUNTDOWN.set(count);
			return;
		}
		try {
			var hook = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_decoration_test_fail_after", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
			hook.invokeExact(count);
		} catch (Throwable failure) {
			throw new IllegalStateException("Native failure injection failed", failure);
		}
	}

	/** Called by {@code WorldGenRegion.setBlock}; throws at the injected Java failure. */
	public static void javaWriteHook() {
		if (JAVA_FAIL_COUNTDOWN.get() >= 0 && JAVA_FAIL_COUNTDOWN.getAndDecrement() == 1) {
			throw new IllegalStateException("Injected Java decoration failure");
		}
	}

	/** Route counters: regions bound/declined, placements native/Java. Read by verification drivers. */
	private static final java.util.concurrent.atomic.LongAdder REGIONS = new java.util.concurrent.atomic.LongAdder(),
		DECLINED_REGIONS = new java.util.concurrent.atomic.LongAdder(), NATIVE = new java.util.concurrent.atomic.LongAdder(),
		JAVA = new java.util.concurrent.atomic.LongAdder(), VERIFIED = new java.util.concurrent.atomic.LongAdder(),
		MISMATCHED = new java.util.concurrent.atomic.LongAdder();
	/** Native placements by outermost feature kind (1 ore, 2 replace blobs, 3 glowstone, 4 spring, 5–7 selectors, 8 tree). */
	private static final java.util.concurrent.atomic.LongAdder[] NATIVE_BY_KIND = adders(64);
	/** Replayed events by kind (decoration/write.rs numbering). */
	static final java.util.concurrent.atomic.LongAdder[] EVENTS_BY_KIND = adders(9);

	private static java.util.concurrent.atomic.LongAdder[] adders(int count) {
		var adders = new java.util.concurrent.atomic.LongAdder[count];
		for (int i = 0; i < count; i++) adders[i] = new java.util.concurrent.atomic.LongAdder();
		return adders;
	}

	private static String sums(java.util.concurrent.atomic.LongAdder[] adders) {
		var values = new StringBuilder();
		for (int i = 1; i < adders.length; i++) values.append(i == 1 ? "" : ",").append(adders[i].sum());
		return values.toString();
	}

	private static final java.util.concurrent.atomic.LongAdder[] REFRESH_DECLINED = {null, new java.util.concurrent.atomic.LongAdder(),
		new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()};
	/** {@code -Dmattmc.worldgen.verifyDecoration=true}: replay every native placement on Java's path and compare. */
	private static final boolean VERIFY = Boolean.getBoolean("mattmc.worldgen.verifyDecoration");
	/** {@code -Dmattmc.worldgen.decorationDebug=true}: log why placed features keep Java's path. */
	private static final boolean DEBUG = Boolean.getBoolean("mattmc.worldgen.decorationDebug");

	/** {@code -Dmattmc.worldgen.decorationProfile=true}: time each placed feature and structure step, by route. */
	public static final boolean PROFILE = Boolean.getBoolean("mattmc.worldgen.decorationProfile");
	private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.LongAdder[]> PROFILE_TIMES =
		new java.util.concurrent.ConcurrentHashMap<>();

	/** {@code -Dmattmc.worldgen.decorationJavaKinds=10,11}: outermost feature kinds kept on Java's path, for bisecting. */
	private static final java.util.Set<Integer> JAVA_KINDS = java.util.Arrays.stream(System.getProperty("mattmc.worldgen.decorationJavaKinds", "").split(","))
		.filter(k -> !k.isBlank()).map(k -> Integer.parseInt(k.trim())).collect(java.util.stream.Collectors.toUnmodifiableSet());

	private NativeDecoration() {
	}

	/** Records one placement's wall time under its registry key, split into native and Java nanoseconds and counts. */
	public static void profile(String key, long nanos, boolean nativeRoute) {
		var adders = PROFILE_TIMES.computeIfAbsent(key, k -> adders(4));
		adders[nativeRoute ? 0 : 1].add(nanos);
		adders[nativeRoute ? 2 : 3].increment();
	}

	/** Profiled keys by total time: {@code key native_ms/java_ms native_count/java_count}. */
	private static String profileSummary() {
		var rows = new ArrayList<>(PROFILE_TIMES.entrySet());
		rows.sort(java.util.Comparator.comparingLong(e -> -(e.getValue()[0].sum() + e.getValue()[1].sum())));
		var out = new StringBuilder();
		for (var row : rows) {
			var a = row.getValue();
			out.append(String.format(java.util.Locale.ROOT, "%s;%.1f;%.1f;%d;%d|", row.getKey(), a[0].sum() / 1e6, a[1].sum() / 1e6, a[2].sum(), a[3].sum()));
		}
		return out.toString();
	}

	public static String statistics() {
		return "enabled=" + ENABLED + " regions=" + REGIONS.sum() + " declinedRegions=" + DECLINED_REGIONS.sum()
			+ " nativePlacements=" + NATIVE.sum() + " javaPlacementsInBoundRegions=" + JAVA.sum()
 			+ " declinedOwners=" + REFRESH_DECLINED[1].sum() + " declinedBiomes=" + REFRESH_DECLINED[2].sum() + " declinedHeightmaps=" + REFRESH_DECLINED[3].sum()
			+ " nativeByKind=" + sums(NATIVE_BY_KIND) + " eventsByKind=" + sums(EVENTS_BY_KIND)
			+ (VERIFY ? " verified=" + VERIFIED.sum() + " mismatched=" + MISMATCHED.sum() : "")
			+ " templates=" + TEMPLATES.sum() + " sequenced=" + SEQUENCED.sum() + " sequenceDeclined=" + SEQUENCE_DECLINED.sum() + " sequenceResumed=" + SEQUENCE_RESUMED.sum()
			+ (PROFILE ? " profile=" + profileSummary() : "");
	}

	private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_decoration_" + name, descriptor);
	}

	/** Rust builds {@code Mth.SIN} from its HotSpot sine port; any difference keeps every feature in Java. */
	private static boolean sineMatches() {
		try (var arena = Arena.ofConfined()) {
			float[] table = Mth.sinTable();
			var segment = arena.allocate(table.length * 4L, 4);
			MemorySegment.copy(table, 0, segment, ValueLayout.JAVA_FLOAT, 0, table.length);
			if ((int)SINE_MATCHES.invokeExact(segment) == 1) return true;
			LOGGER.warn("Native decoration disabled: Rust Mth.SIN differs from Java's table");
			return false;
		} catch (Throwable failure) {
			LOGGER.warn("Native decoration disabled", failure);
			return false;
		}
	}

	/**
	 * Per-state facts {@code setBlock} needs ({@code decoration/facts.rs}): POI type,
	 * post-processing (probed with no world: a predicate that reads one is
	 * unknown, and its states keep Java's path) and light-shape use.
	 */
	private static boolean installFacts() {
		if (factsInstalled) return true;
		synchronized (NativeDecoration.class) {
			if (factsInstalled) return true;
			int count = Block.BLOCK_STATE_REGISTRY.size();
			try (var arena = Arena.ofConfined()) {
				var words = arena.allocate(count * 4L, 4);
				for (int id = 0; id < count; id++) {
					BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
					int poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
						.map(holder -> net.minecraft.core.registries.BuiltInRegistries.POINT_OF_INTEREST_TYPE.getId(holder.value()) + 1).orElse(0);
					int postProcess;
					try {
						postProcess = state.hasPostProcess(null, null) ? 1 : 0;
					} catch (RuntimeException unknown) {
						postProcess = 2;
					}
					if (poi < 0 || poi > 0xffff) return false;
					words.setAtIndex(ValueLayout.JAVA_INT, id, poi | postProcess << 16 | (state.useShapeForLightOcclusion() ? 1 << 18 : 0)
						| (state.getBlock().hasDynamicShape() ? 1 << 19 : 0)
						| (state.getFluidState().isSource() ? 1 << 20 : 0)
						| (state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER ? 1 << 21 : 0)
						| (state.getFluidState().isEmpty() ? 1 << 22 : 0));
				}
				factsInstalled = (int)INSTALL_FACTS.invokeExact(words, count) == 1;
			} catch (Throwable failure) {
				throw new IllegalStateException("Native decoration facts install failed", failure);
			}
			return factsInstalled;
		}
	}

	private static boolean lazyPriming() {
		try {
			return (int)LAZY_PRIMING.invokeExact() == 1;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native decoration priming check failed", failure);
		}
	}

	/** Binds a decoration call's region, or null when this chunk keeps Java's path for every feature. */
	@Nullable
	public static NativeDecorationRegion bind(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator) {
		if (!ENABLED || !NativeBlockRegistry.ready() || !installFacts()) return null;
		var region = NativeDecorationRegion.bind(level, chunk, new WorldGenerationContext(generator, level), lazyPriming());
		(region == null ? DECLINED_REGIONS : REGIONS).increment();
		if (region != null) ACTIVE.set(region);
		return region;
	}

	/** The region bound on this thread for {@code applyBiomeDecoration}, for nested native work (templates). */
	private static final ThreadLocal<NativeDecorationRegion> ACTIVE = new ThreadLocal<>();

	/** Ends a decoration binding: nested native work no longer finds it. */
	public static void release(NativeDecorationRegion region) {
		if (ACTIVE.get() == region) ACTIVE.remove();
		region.close();
	}

	/** The bound region serving {@code level}, or null. */
	@Nullable
	public static NativeDecorationRegion activeRegion(Object level) {
		var region = ACTIVE.get();
		return region != null && region.isFor(level) ? region : null;
	}

	private static final MethodHandle TEMPLATE_CREATE = templateHandle("template_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle TEMPLATE_RELEASE = templateHandle("template_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle PROCESSORS_CREATE = templateHandle("template_processors_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle PROCESSORS_RELEASE = templateHandle("template_processors_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle TEMPLATE_PLACE = templateHandle("template_place", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle TEMPLATE_RESULTS = templateHandle("template_results", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final ThreadLocal<Object[]> TEMPLATE_LOADS = new ThreadLocal<>();
	private static final java.util.concurrent.atomic.LongAdder TEMPLATES = new java.util.concurrent.atomic.LongAdder();

	private static MethodHandle templateHandle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, descriptor);
	}

	/** A native template or processor list compiled from words; released with {@code owner}. */
	public static MemorySegment createTemplateObject(boolean processors, int[] words, Object owner) {
		try (var arena = Arena.ofConfined()) {
			var segment = arena.allocate(Math.max(1, words.length) * 4L, 4);
			MemorySegment.copy(words, 0, segment, ValueLayout.JAVA_INT, 0, words.length);
			var pointer = processors ? (MemorySegment)PROCESSORS_CREATE.invokeExact(segment, words.length) : (MemorySegment)TEMPLATE_CREATE.invokeExact(segment, words.length);
			if (pointer.address() == 0) return MemorySegment.NULL;
			long address = pointer.address();
			CLEANER.register(owner, () -> {
				try {
					if (processors) PROCESSORS_RELEASE.invokeExact(MemorySegment.ofAddress(address));
					else TEMPLATE_RELEASE.invokeExact(MemorySegment.ofAddress(address));
				} catch (Throwable ignored) {
				}
			});
			return pointer;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native template creation failed", failure);
		}
	}

	/** Loads one recorded block entity during event replay. */
	public interface BlockEntityLoads {
		void load(BlockPos pos, long[] loads, int at);
	}

	/**
	 * Places a template natively over {@code region} ({@code template.rs}). {@code args} as
	 * {@code mattmc_template_place} takes them; the random state comes back in words 19 and 20.
	 * Returns the placed positions as x, y, z triples, or null when Java must place it (nothing written).
	 */
	@Nullable
	public static long[] placeTemplate(NativeDecorationRegion region, MemorySegment template, MemorySegment processors, long[] args, BlockEntityLoads loads) {
		if (!ENABLED || region.refresh(true, true) != NativeDecorationRegion.REFRESHED) return null;
		int status;
		try (var arena = Arena.ofConfined()) {
			var words = arena.allocate(args.length * 8L, 8);
			MemorySegment.copy(args, 0, words, ValueLayout.JAVA_LONG, 0, args.length);
			status = (int)TEMPLATE_PLACE.invokeExact(template, processors, region.words(), (int)(region.words().byteSize() / 8), region.out(),
				(int)(region.out().byteSize() / 8), words);
			MemorySegment.copy(words, ValueLayout.JAVA_LONG, 0, args, 0, args.length);
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native template placement failed", failure);
		}
		switch (status) {
			case PLACED -> {
				region.afterPlacement();
				long[] positions = templateResults(0), recorded = templateResults(1);
				TEMPLATE_LOADS.set(new Object[] {recorded, loads});
				try {
					replayEvents(region);
				} finally {
					TEMPLATE_LOADS.remove();
				}
				TEMPLATES.increment();
				return positions;
			}
			case JAVA_ROUTE -> {
				return null;
			}
			case UNAVAILABLE_CHUNK -> {
				LOGGER.error("Native template read outside its region: args {} detail {}", java.util.Arrays.toString(args), region.unavailableDetail());
				region.afterPlacement();
				replayEvents(region);
				throw region.unavailableChunk();
			}
			default -> throw new IllegalStateException("Native template placement returned " + status);
		}
	}

	private static long[] templateResults(int which) throws IllegalStateException {
		try {
			int length = (int)TEMPLATE_RESULTS.invokeExact(which, MemorySegment.NULL, 0);
			if (length == 0) return new long[0];
			try (var arena = Arena.ofConfined()) {
				var buffer = arena.allocate(length * 8L, 8);
				if ((int)TEMPLATE_RESULTS.invokeExact(which, buffer, length) != length) throw new IllegalStateException("Native template results changed");
				return buffer.toArray(ValueLayout.JAVA_LONG);
			}
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native template results failed", failure);
		}
	}

	/** Event replay: one template block-entity load at {@code at} in the recorded loads. */
	public static void templateBlockEntity(BlockPos pos, long at) {
		Object[] loads = TEMPLATE_LOADS.get();
		if (loads == null) throw new IllegalStateException("Template block entity outside a template placement");
		((BlockEntityLoads)loads[1]).load(pos, (long[])loads[0], (int)at);
	}

	/**
	 * Places {@code feature} natively after {@code setFeatureSeed(seed, index, step)}.
	 * False when it must run on Java's path; nothing was drawn or written then.
	 */
	public static boolean place(Programs programs, NativeDecorationRegion region, ChunkGenerator generator, WorldGenLevel level,
								WorldgenRandom random, PlacedFeature feature, long seed, int index, int step, BlockPos origin) {
		var program = programs.program(feature, generator, region);
		if (program == null || JAVA_KINDS.contains(program.kind)) {
			JAVA.increment();
			return false;
		}
		int refresh = region.refresh(true, program.setBlock);
		if (refresh != NativeDecorationRegion.REFRESHED) {
			JAVA.increment();
			REFRESH_DECLINED[refresh].increment();
			return false;
		}
		int[][] before = VERIFY ? region.captureStatesForVerification() : null;
		long[] levelRandom = VERIFY ? randomState(level.getRandom()) : null;
		var maps = VERIFY ? region.mapsForVerification() : null;
		var call = new Call(region, level, generator, random, program.javaFeatures, program.javaPredicates);
		call.javaSteps = program.javaSteps;
		var previous = CURRENT.get();
		CURRENT.set(call);
		int result;
		var gaussian = random.nativeGaussian();
		boolean gaussianBefore = gaussian.nativeHas();
		double gaussianValueBefore = gaussian.nativeValue();
		try (var arena = Arena.ofConfined()) {
			var cache = arena.allocate(16, 8);
			putGaussian(cache, 0, gaussian);
			result = (int)PLACE.invokeExact(program.pointer, region.words(), (int)(region.words().byteSize() / 8),
				region.out(), (int)(region.out().byteSize() / 8), seed, index, step, origin.getX(), origin.getY(), origin.getZ(), UPCALL, cache);
			takeGaussian(cache, 0, gaussian);
		} catch (Throwable failure) {
			throw new IllegalStateException("Native decoration failed", failure);
		} finally {
			CURRENT.set(previous);
			java.lang.ref.Reference.reachabilityFence(program);
		}
		switch (result) {
			case PLACED, NOT_PLACED -> {
				region.afterPlacement();
				replayEvents(region);
				NATIVE.increment();
				NATIVE_BY_KIND[Math.min(program.kind, NATIVE_BY_KIND.length - 1)].increment();
				if (VERIFY) {
					// Java features run through upcalls advanced the decoration random.
					random.setFeatureSeed(seed, index, step);
					boolean nativeHas = gaussian.nativeHas();
					double nativeValue = gaussian.nativeValue();
					gaussian.nativeSet(gaussianBefore, gaussianValueBefore);
					verify(region, before, maps, level, generator, random, feature, origin, levelRandom);
					if (gaussian.nativeHas() != nativeHas || nativeHas && Double.doubleToRawLongBits(gaussian.nativeValue()) != Double.doubleToRawLongBits(nativeValue)) {
						MISMATCHED.increment();
						LOGGER.warn("Native decoration mismatch for {} at {}: gaussian cache", feature, origin);
					}
				}
				return true;
			}
			case CALLBACK_FAILED -> {
				var failure = call.failure;
				if (failure instanceof RuntimeException runtime) throw runtime;
				if (failure instanceof Error error) throw error;
				throw new IllegalStateException("Native decoration upcall failed for " + feature, failure);
			}
			case JAVA_ROUTE -> {
				JAVA.increment();
				return false;
			}
			case UNAVAILABLE_CHUNK -> {
				region.afterPlacement();
				replayEvents(region);
				throw region.unavailableChunk();
			}
			case BROKEN_BINDING -> throw new IllegalStateException("Native decoration binding failed mid-placement for " + feature);
			case PANIC -> throw new IllegalStateException("Native decoration panicked for " + feature);
			default -> {
				return false;
			}
		}
	}

	/** Java's halves of the step loop, for {@link #decorate}. */
	public interface Sequence {
		/** The structures of one generation step, exactly as Java's loop places them. */
		void structures(int step);

		/** One placed feature on Java's path after its {@code setFeatureSeed}. */
		void feature(int step, int index);
	}

	/**
	 * Runs {@code applyBiomeDecoration}'s step loop in Rust over the bound region.
	 * Returns {@link #DECORATION_DONE}, {@link #DECORATION_DECLINED} (nothing ran;
	 * Java's loop runs) or a resume point {@code step << 32 | index}: Java's loop
	 * continues after that feature.
	 */
	public static long decorate(Programs programs, NativeDecorationRegion region, ChunkGenerator generator, WorldGenLevel level, WorldgenRandom random,
								long seed, java.util.Collection<Holder<net.minecraft.world.level.biome.Biome>> biomes, long structures, BlockPos origin,
								List<net.minecraft.world.level.biome.FeatureSorter.StepFeatureData> steps, int stepCount, Sequence sequence) {
		if (!ENABLED || VERIFY || PROFILE || JAVA_SEQUENCE || stepCount > 64) return DECORATION_DECLINED;
		Plan plan = programs.plan(generator, region, steps, stepCount);
		int[] biomeIds = new int[biomes.size()];
		int present = 0;
		for (var biome : biomes) {
			Integer id = plan == null ? null : plan.biomeIds.get(biome);
			if (id == null) break;
			biomeIds[present++] = id;
		}
		int refresh = plan == null || present != biomeIds.length ? -1 : region.refresh(true, true);
		if (refresh != NativeDecorationRegion.REFRESHED) {
			SEQUENCE_DECLINED.increment();
			if (DEBUG) LOGGER.info("Native decoration: step loop declined (plan {}, biomes {}/{}, refresh {})", plan != null, present, biomeIds.length, refresh);
			return DECORATION_DECLINED;
		}
		var call = new Call(region, level, generator, random, List.of(), List.of());
		call.plan = plan;
		call.sequence = sequence;
		var previous = CURRENT.get();
		CURRENT.set(call);
		int status;
		long[] info;
		try (var arena = Arena.ofConfined()) {
			var ids = arena.allocate(Math.max(1, biomeIds.length) * 4L, 4);
			MemorySegment.copy(biomeIds, 0, ids, ValueLayout.JAVA_INT, 0, biomeIds.length);
			int count = biomeIds.length;
			var infoSegment = arena.allocate((8 + NATIVE_BY_KIND.length) * 8L, 8);
			putGaussian(infoSegment, 6, random.nativeGaussian());
			status = (int)DECORATE.invokeExact(plan.pointer, region.words(), (int)(region.words().byteSize() / 8), region.out(),
				(int)(region.out().byteSize() / 8), seed, ids, count, structures, origin.getX(), origin.getY(), origin.getZ(), UPCALL, infoSegment);
			info = infoSegment.toArray(ValueLayout.JAVA_LONG);
			takeGaussian(infoSegment, 6, random.nativeGaussian());
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native decoration loop failed", failure);
		} finally {
			CURRENT.set(previous);
			java.lang.ref.Reference.reachabilityFence(plan);
		}
		NATIVE.add(info[2]);
		for (int kind = 0; kind < NATIVE_BY_KIND.length; kind++) NATIVE_BY_KIND[kind].add(info[8 + kind]);
		switch (status) {
			case DECORATED, RESUME -> {
				region.afterPlacement();
				replayEvents(region);
				SEQUENCED.increment();
				if (status == DECORATED) return DECORATION_DONE;
				SEQUENCE_RESUMED.increment();
				return info[0] << 32 | (info[1] & 0xffffffffL);
			}
			case CALLBACK_FAILED -> {
				var failure = call.failure;
				if (failure instanceof RuntimeException runtime) throw runtime;
				if (failure instanceof Error error) throw error;
				throw new IllegalStateException("Native decoration upcall failed", failure);
			}
			case UNAVAILABLE_CHUNK -> {
				region.afterPlacement();
				replayEvents(region);
				throw featureCrash(region.unavailableChunk(), steps, (int)info[4], (int)info[5], level);
			}
			case BROKEN_BINDING -> throw featureCrash(new IllegalStateException("Native decoration binding failed mid-placement"), steps, (int)info[4], (int)info[5], level);
			case PANIC -> throw featureCrash(new IllegalStateException("Native decoration panicked"), steps, (int)info[4], (int)info[5], level);
			default -> throw new IllegalStateException("Native decoration loop returned " + status + " before running");
		}
	}

	/** The crash Java's loop raises for a feature that throws: "Feature placement" with its description. */
	private static RuntimeException featureCrash(RuntimeException cause, List<net.minecraft.world.level.biome.FeatureSorter.StepFeatureData> steps, int step, int index,
												 WorldGenLevel level) {
		if (step < 0 || step >= steps.size() || index < 0 || index >= steps.get(step).features().size()) return cause;
		PlacedFeature feature = steps.get(step).features().get(index);
		var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
		var report = net.minecraft.CrashReport.forThrowable(cause, "Feature placement");
		report.addCategory("Feature").setDetail("Description", () -> registry.getResourceKey(feature).map(Object::toString).orElseGet(feature::toString));
		return new net.minecraft.ReportedException(report);
	}

	/** A generator's step plan over one region context, with every planned program kept alive. */
	static final class Plan {
		final MemorySegment pointer;
		/** By plan slot: native programs, null where Java places the feature. */
		final Program[] programs;
		final java.util.Map<Holder<net.minecraft.world.level.biome.Biome>, Integer> biomeIds;
		final long tagGeneration;
		final Object biomes;
		final int minGenY, genDepth, stepCount;
		final long seed;
		final List<net.minecraft.world.level.biome.FeatureSorter.StepFeatureData> steps;

		Plan(MemorySegment pointer, Program[] programs, java.util.Map<Holder<net.minecraft.world.level.biome.Biome>, Integer> biomeIds, long tagGeneration,
			 Object biomes, int minGenY, int genDepth, long seed, int stepCount, List<net.minecraft.world.level.biome.FeatureSorter.StepFeatureData> steps) {
			this.pointer = pointer;
			this.seed = seed;
			this.programs = programs;
			this.biomeIds = biomeIds;
			this.tagGeneration = tagGeneration;
			this.biomes = biomes;
			this.minGenY = minGenY;
			this.genDepth = genDepth;
			this.stepCount = stepCount;
			this.steps = steps;
			long address = pointer.address();
			CLEANER.register(this, () -> {
				try {
					PLAN_RELEASE.invokeExact(MemorySegment.ofAddress(address));
				} catch (Throwable ignored) {
				}
			});
		}
	}

	/** Writes a gaussian cache as {@code [have, value bits]} at word {@code at}. */
	private static void putGaussian(MemorySegment words, long at, MarsagliaPolarGaussian gaussian) {
		words.setAtIndex(ValueLayout.JAVA_LONG, at, gaussian.nativeHas() ? 1 : 0);
		words.setAtIndex(ValueLayout.JAVA_LONG, at + 1, Double.doubleToRawLongBits(gaussian.nativeValue()));
	}

	private static void takeGaussian(MemorySegment words, long at, MarsagliaPolarGaussian gaussian) {
		gaussian.nativeSet(words.getAtIndex(ValueLayout.JAVA_LONG, at) != 0, Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, at + 1)));
	}

	public static void countEvent(int kind) {
		EVENTS_BY_KIND[kind].increment();
	}

	/**
	 * Rust's mid-placement step: apply everything Rust holds, run the Java
	 * operation, then refresh the binding words Rust reads afterwards.
	 */
	private static int upcall(int op, MemorySegment args, int count) {
		if (op == OP_MATH_LOG || op == OP_MATH_POW || op == OP_MATH_SIN || op == OP_MATH_COS) {
			// Pure: HotSpot's Math intrinsics, which Rust's libm need not match bit for bit.
			var words = args.reinterpret(count * 8L);
			double a = Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, 0));
			double value = switch (op) {
				case OP_MATH_LOG -> Math.log(a);
				case OP_MATH_SIN -> Math.sin(a);
				case OP_MATH_COS -> Math.cos(a);
				default -> Math.pow(a, Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, 1)));
			};
			words.setAtIndex(ValueLayout.JAVA_LONG, 0, Double.doubleToRawLongBits(value));
			return 0;
		}
		Call call = CURRENT.get();
		if (call == null) return -1;
		if (op == OP_FAR_STATE || op == OP_FAR_HEIGHT || op == OP_FAR_BIOME || op == OP_BLOCK_LIGHT) {
			// Pure reads outside the bound 3x3, answered by the WorldGenRegion as Java's own reads are.
			try {
				var words = args.reinterpret(count * 8L);
				long[] a = words.toArray(ValueLayout.JAVA_LONG);
				long value = switch (op) {
					case OP_FAR_STATE -> Block.BLOCK_STATE_REGISTRY.getId(call.level.getBlockState(new BlockPos((int)a[0], (int)a[1], (int)a[2])));
					case OP_BLOCK_LIGHT -> call.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos((int)a[0], (int)a[1], (int)a[2]));
					case OP_FAR_HEIGHT -> call.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.values()[(int)a[0]], (int)a[1], (int)a[2]);
					default -> {
						int index = call.region.biomeValues().indexOf(call.level.getNoiseBiome((int)a[0], (int)a[1], (int)a[2]));
						if (index < 0) throw new IllegalStateException("Far biome outside the region's biome values");
						yield index;
					}
				};
				words.setAtIndex(ValueLayout.JAVA_LONG, 0, value);
				return 0;
			} catch (Throwable failure) {
				if (call.failure == null) call.failure = failure;
				return -1;
			}
		}
		try {
			long[] a = args.reinterpret(count * 8L).toArray(ValueLayout.JAVA_LONG);
			var region = call.region;
			region.afterPlacement();
			replayEvents(region);
			region.resetAfterSync();
			int result = switch (op) {
				case OP_UPDATE_SHAPE_AT_EDGE -> {
					int xs = (int)a[4], ys = (int)a[5], zs = (int)a[6];
					var shape = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(xs, ys, zs);
					for (int x = 0; x < xs; x++) {
						for (int y = 0; y < ys; y++) {
							for (int z = 0; z < zs; z++) {
								int bit = (x * ys + y) * zs + z;
								if ((a[7 + (bit >>> 6)] >>> (bit & 63) & 1) != 0) shape.fill(x, y, z);
							}
						}
					}
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.updateShapeAtEdge(call.level, (int)a[0], shape, (int)a[1], (int)a[2], (int)a[3]);
					yield 0;
				}
				case OP_BEEHIVE_PRESENT -> call.level.getBlockEntity(new BlockPos((int)a[0], (int)a[1], (int)a[2]),
					net.minecraft.world.level.block.entity.BlockEntityType.BEEHIVE).isPresent() ? 1 : 0;
				case OP_PLACE_JAVA -> {
					// The Java feature draws from the decoration's own random, from Rust's state.
					var generator = ((XoroshiroRandomSource)call.random.nativeSource()).nativeGenerator();
					generator.nativeSetState(a[4], a[5]);
					var words = args.reinterpret(count * 8L);
					takeGaussian(words, 7, call.random.nativeGaussian());
					var features = a[6] >= 0 ? call.plan.programs[(int)a[6]].javaFeatures : call.javaFeatures;
					boolean placed = features.get((int)a[0]).place(call.level, call.generator, call.random, new BlockPos((int)a[1], (int)a[2], (int)a[3]));
					words.setAtIndex(ValueLayout.JAVA_LONG, 4, generator.nativeSeedLo());
					words.setAtIndex(ValueLayout.JAVA_LONG, 5, generator.nativeSeedHi());
					putGaussian(words, 7, call.random.nativeGaussian());
					yield placed ? 1 : 0;
				}
				case OP_TEST_PREDICATE -> (a.length > 4 && a[4] >= 0 ? call.plan.programs[(int)a[4]].javaPredicates : call.javaPredicates)
					.get((int)a[0]).test(call.level, new BlockPos((int)a[1], (int)a[2], (int)a[3])) ? 1 : 0;
				case OP_JAVA_STEP -> {
					var generator = ((XoroshiroRandomSource)call.random.nativeSource()).nativeGenerator();
					generator.nativeSetState(a[4], a[5]);
					var words = args.reinterpret(count * 8L);
					takeGaussian(words, 7, call.random.nativeGaussian());
					(a[6] >= 0 ? call.plan.programs[(int)a[6]].javaSteps : call.javaSteps).get((int)a[0])
						.run(call.level, call.random, new BlockPos((int)a[1], (int)a[2], (int)a[3]));
					words.setAtIndex(ValueLayout.JAVA_LONG, 4, generator.nativeSeedLo());
					words.setAtIndex(ValueLayout.JAVA_LONG, 5, generator.nativeSeedHi());
					putGaussian(words, 7, call.random.nativeGaussian());
					yield 0;
				}
				case OP_STRUCTURES -> {
					var words = args.reinterpret(count * 8L);
					takeGaussian(words, 1, call.random.nativeGaussian());
					call.sequence.structures((int)a[0]);
					putGaussian(words, 1, call.random.nativeGaussian());
					yield 0;
				}
				case OP_PLACE_FEATURE -> {
					var words = args.reinterpret(count * 8L);
					takeGaussian(words, 2, call.random.nativeGaussian());
					call.sequence.feature((int)a[0], (int)a[1]);
					putGaussian(words, 2, call.random.nativeGaussian());
					JAVA.increment();
					// The loop goes on natively only over an intact binding; otherwise Java finishes it.
					if (region.refresh(true, true) != NativeDecorationRegion.REFRESHED) yield 2;
					yield 0;
				}
				case OP_STORE_BEES -> {
					var hive = call.level.getBlockEntity(new BlockPos((int)a[0], (int)a[1], (int)a[2]),
						net.minecraft.world.level.block.entity.BlockEntityType.BEEHIVE).orElseThrow();
					for (int i = 3; i < a.length; i++) {
						hive.storeBee(net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant.create((int)a[i]));
					}
					yield 0;
				}
				default -> throw new IllegalStateException("Unknown native decoration upcall " + op);
			};
			if (op == OP_PLACE_FEATURE && result == 2) return result;
			int refresh = region.refresh(true, true);
			if (refresh != NativeDecorationRegion.REFRESHED) throw new IllegalStateException("Native decoration binding changed during upcall " + op + ": " + refresh);
			return result;
		} catch (Throwable failure) {
			if (call.failure == null) call.failure = failure;
			return -1;
		}
	}

	/** Java-owned effects of the last native placement on this thread, in their order. */
	private static void replayEvents(NativeDecorationRegion region) {
		long[] events = takeEvents();
		if (events.length != 0) region.replay(events, events.length / 5);
	}

	/** The last native placement's events, five words each; clears them. */
	private static long[] takeEvents() {
		try {
			int count = (int)EVENTS.invokeExact(MemorySegment.NULL, 0);
			if (count == 0) return new long[0];
			try (var arena = Arena.ofConfined()) {
				var buffer = arena.allocate(count * 5L * 8L, 8);
				if ((int)EVENTS.invokeExact(buffer, count) != count) throw new IllegalStateException("Native decoration events changed");
				return buffer.toArray(ValueLayout.JAVA_LONG);
			}
		} catch (RuntimeException | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native decoration events failed", failure);
		}
	}

	/**
	 * Verification mode: the native placement has been applied in full. Put the
	 * blocks, heightmaps and sky sources back as they were, replay the placement
	 * on Java's path with the same seeded random, and compare all three. Java-
	 * owned effects (POI, block entities, ticks, post-processing) are applied
	 * twice; never use a verification world as parity output.
	 */
	/**
	 * A random source's full state ({@code [seedLo, seedHi, gaussian have, gaussian bits]}), so
	 * verification can replay draws from the level's own random (mossy carpets use it); null when
	 * the source has no native accessor.
	 */
	@Nullable
	private static long[] randomState(net.minecraft.util.RandomSource source) {
		if (!(source instanceof XoroshiroRandomSource xoroshiro)) return null;
		var gaussian = xoroshiro.nativeGaussian();
		var generator = xoroshiro.nativeGenerator();
		return new long[] {generator.nativeSeedLo(), generator.nativeSeedHi(), gaussian.nativeHas() ? 1 : 0, Double.doubleToRawLongBits(gaussian.nativeValue())};
	}

	private static void restoreRandom(net.minecraft.util.RandomSource source, long[] state) {
		var xoroshiro = (XoroshiroRandomSource)source;
		xoroshiro.nativeGenerator().nativeSetState(state[0], state[1]);
		xoroshiro.nativeGaussian().nativeSet(state[2] != 0, Double.longBitsToDouble(state[3]));
	}

	private static void verify(NativeDecorationRegion region, int[][] before, NativeDecorationRegion.Maps maps, WorldGenLevel level,
							   ChunkGenerator generator, WorldgenRandom random, PlacedFeature feature, BlockPos origin, @Nullable long[] levelRandom) {
		int[][] nativeAfter = region.captureStatesForVerification();
		var nativeMaps = region.mapsForVerification();
		long[] nativeRandom = levelRandom == null ? null : randomState(level.getRandom());
		region.restoreStatesForVerification(before);
		region.restoreMapsForVerification(maps);
		if (levelRandom != null) restoreRandom(level.getRandom(), levelRandom);
		feature.placeWithBiomeCheck(level, generator, random, origin);
		if (nativeRandom != null && !java.util.Arrays.equals(nativeRandom, randomState(level.getRandom()))) {
			LOGGER.error("Native decoration level random differs for {}", feature);
			MISMATCHED.increment();
		}
		int[][] javaAfter = region.captureStatesForVerification();
		String mapMismatch = region.compareMapsForVerification(nativeMaps, region.mapsForVerification());
		if (mapMismatch != null) LOGGER.error("Native decoration {} differs for {}", mapMismatch, feature);
		int differences = 0;
		for (int section = 0; section < javaAfter.length; section++) {
			for (int i = 0; i < 4096; i++) {
				if (javaAfter[section][i] != nativeAfter[section][i]) {
					if (differences++ < 4) {
						LOGGER.error("Native decoration mismatch for {} at {}: Java {}, native {}", feature, region.positionForVerification(section, i),
							Block.BLOCK_STATE_REGISTRY.byId(javaAfter[section][i]), Block.BLOCK_STATE_REGISTRY.byId(nativeAfter[section][i]));
					}
				}
			}
		}
		(differences == 0 && mapMismatch == null ? VERIFIED : MISMATCHED).increment();
	}

	/** One compiled program, released when its cache entry is replaced and collected. */
	private static final class Program {
		final MemorySegment pointer;
		final long tagGeneration;
		final Object biomes;
		final int minGenY, genDepth;
		/** The level seed seed-derived state (geode noise) was built for. */
		final long seed;
		/** The feature writes through {@code WorldGenRegion.setBlock}. */
		final boolean setBlock;
		final int kind;
		final List<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> javaFeatures;
		final List<NativeDecorationEncoder.JavaPredicate> javaPredicates;
		final List<NativeDecorationEncoder.JavaStep> javaSteps;
		/** Java objects owning native state the program points at (noises). */
		final List<Object> retained;

		Program(MemorySegment pointer, long tagGeneration, Object biomes, int minGenY, int genDepth, long seed, NativeDecorationEncoder.Tables tables) throws Throwable {
			this.pointer = pointer;
			this.seed = seed;
			this.javaFeatures = tables.javaFeatures();
			this.javaPredicates = tables.javaPredicates();
			this.javaSteps = tables.javaSteps();
			this.retained = tables.retained();
			this.kind = (int)KIND.invokeExact(pointer);
			this.setBlock = (int)SET_BLOCK.invokeExact(pointer) == 1;
			this.tagGeneration = tagGeneration;
			this.biomes = biomes;
			this.minGenY = minGenY;
			this.genDepth = genDepth;
			long address = pointer.address();
			CLEANER.register(this, () -> {
				try {
					RELEASE.invokeExact(MemorySegment.ofAddress(address));
				} catch (Throwable ignored) {
				}
			});
		}
	}

	/** Per-generator program cache. Reads are lock-free; compilation publishes a new map. */
	public static final class Programs {
		private static final Object UNSUPPORTED = new Object();
		private volatile IdentityHashMap<PlacedFeature, Object> entries = new IdentityHashMap<>();

		private volatile Plan plan;

		/** The cached plan for this region context, built (with every program) when it changes. */
		@Nullable
		Plan plan(ChunkGenerator generator, NativeDecorationRegion region, List<net.minecraft.world.level.biome.FeatureSorter.StepFeatureData> steps, int stepCount) {
			long tags = Holder.TAG_GENERATION.get();
			int minGenY = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 6), genDepth = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 7);
			Plan current = this.plan;
			if (current != null && current.tagGeneration == tags && current.biomes == region.biomeValues() && current.minGenY == minGenY
					&& current.genDepth == genDepth && current.steps == steps && current.stepCount == stepCount && current.seed == region.seed()) {
				return current;
			}
			synchronized (this) {
				current = this.plan;
				if (current != null && current.tagGeneration == tags && current.biomes == region.biomeValues() && current.minGenY == minGenY
						&& current.genDepth == genDepth && current.steps == steps && current.stepCount == stepCount && current.seed == region.seed()) {
					return current;
				}
				var biomeValues = region.biomeValues();
				var biomeIds = new IdentityHashMap<Holder<net.minecraft.world.level.biome.Biome>, Integer>();
				for (int id = 0; id < biomeValues.size(); id++) biomeIds.put(biomeValues.get(id), id);
				List<Integer> words = new java.util.ArrayList<>();
				List<Program> slots = new java.util.ArrayList<>();
				words.add(stepCount);
				words.add(steps.size());
				for (int k = 0; k < steps.size(); k++) {
					var step = steps.get(k);
					var features = step.features();
					words.add(features.size());
					words.add(biomeValues.size());
					for (var biome : biomeValues) {
						var lists = generator.getBiomeGenerationSettings(biome).features();
						var indices = new it.unimi.dsi.fastutil.ints.IntRBTreeSet();
						if (k < lists.size()) lists.get(k).stream().map(Holder::value).forEach(f -> indices.add(step.indexMapping().applyAsInt(f)));
						// A biome outside the generator's feature sorting (another dimension's)
						// never decorates here; if one ever does, Java's loop handles that chunk.
						if (indices.contains(-1)) {
							biomeIds.remove(biome);
							indices.clear();
						}
						words.add(indices.size());
						for (int index : indices) words.add(index);
					}
					for (PlacedFeature feature : features) {
						Program program = this.program(feature, generator, region);
						slots.add(program != null && !JAVA_KINDS.contains(program.kind) ? program : null);
					}
				}
				try (var arena = Arena.ofConfined()) {
					var wordSegment = arena.allocate(Math.max(1, words.size()) * 4L, 4);
					for (int i = 0; i < words.size(); i++) wordSegment.setAtIndex(ValueLayout.JAVA_INT, i, words.get(i));
					var programSegment = arena.allocate(Math.max(1, slots.size()) * 8L, 8);
					for (int i = 0; i < slots.size(); i++) programSegment.setAtIndex(ValueLayout.JAVA_LONG, i, slots.get(i) == null ? 0 : slots.get(i).pointer.address());
					var pointer = (MemorySegment)PLAN_CREATE.invokeExact(wordSegment, words.size(), programSegment, slots.size());
					if (pointer.address() == 0) {
						if (DEBUG) {
							LOGGER.info("Native decoration: Rust rejected the decoration plan ({} words, {} programs)", words.size(), slots.size());
							try {
								java.nio.file.Files.writeString(java.nio.file.Path.of("decoration-plan-words.txt"), words.toString());
							} catch (java.io.IOException ignored) {
							}
						}
						return null;
					}
					current = new Plan(pointer, slots.toArray(Program[]::new), biomeIds, tags, region.biomeValues(), minGenY, genDepth, region.seed(), stepCount, steps);
				} catch (Throwable failure) {
					throw new IllegalStateException("Native decoration plan creation failed", failure);
				}
				this.plan = current;
				return current;
			}
		}

		@Nullable
		Program program(PlacedFeature feature, ChunkGenerator generator, NativeDecorationRegion region) {
			Object entry = this.entries.get(feature);
			if (entry == UNSUPPORTED) return null;
			long tags = Holder.TAG_GENERATION.get();
			int minGenY = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 6), genDepth = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 7);
			long seed = region.seed();
			if (entry instanceof Program program && program.tagGeneration == tags && program.biomes == region.biomeValues()
					&& program.minGenY == minGenY && program.genDepth == genDepth && program.seed == seed) {
				return program;
			}
			int[] words = NativeDecorationEncoder.encode(feature, generator, region.biomeValues(), seed);
			Object compiled = UNSUPPORTED;
			if (DEBUG && words == null) LOGGER.info("Native decoration: Java encoder declined {}: {}", feature, NativeDecorationEncoder.lastDecline());
			if (words != null) {
				try (var arena = Arena.ofConfined()) {
					var segment = arena.allocate(words.length * 4L, 4);
					MemorySegment.copy(words, 0, segment, ValueLayout.JAVA_INT, 0, words.length);
					var pointer = (MemorySegment)CREATE.invokeExact(segment, words.length);
					if (pointer.address() != 0) compiled = new Program(pointer, tags, region.biomeValues(), minGenY, genDepth, seed, NativeDecorationEncoder.lastTables());
					else if (DEBUG) LOGGER.info("Native decoration: Rust rejected the words for {} ({} words)", feature, words.length);
				} catch (Throwable failure) {
					throw new IllegalStateException("Native decoration program creation failed", failure);
				}
			}
			synchronized (this) {
				var next = new IdentityHashMap<>(this.entries);
				next.put(feature, compiled);
				this.entries = next;
			}
			return compiled instanceof Program program ? program : null;
		}
	}
}
