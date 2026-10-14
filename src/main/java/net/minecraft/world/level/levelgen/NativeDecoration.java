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
	private static final int PLACED = 1, NOT_PLACED = 0, UNAVAILABLE_CHUNK = -2, BROKEN_BINDING = -3, JAVA_ROUTE = -4, PANIC = -5;
	private static final MethodHandle CREATE = handle("program_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle RELEASE = handle("program_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle LAZY_PRIMING = handle("lazy_priming", FunctionDescriptor.of(ValueLayout.JAVA_INT));
	private static final MethodHandle SINE_MATCHES = handle("sine_matches", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle PLACE = handle("place", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	private static final Cleaner CLEANER = Cleaner.create();
	private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaDecoration") && !SharedConstants.DEBUG_FEATURE_COUNT && sineMatches();

	/** Route counters: regions bound/declined, placements native/Java. Read by verification drivers. */
	private static final java.util.concurrent.atomic.LongAdder REGIONS = new java.util.concurrent.atomic.LongAdder(),
		DECLINED_REGIONS = new java.util.concurrent.atomic.LongAdder(), NATIVE = new java.util.concurrent.atomic.LongAdder(),
		JAVA = new java.util.concurrent.atomic.LongAdder(), VERIFIED = new java.util.concurrent.atomic.LongAdder(),
		MISMATCHED = new java.util.concurrent.atomic.LongAdder();
	private static final java.util.concurrent.atomic.LongAdder[] REFRESH_DECLINED = {null, new java.util.concurrent.atomic.LongAdder(),
		new java.util.concurrent.atomic.LongAdder(), new java.util.concurrent.atomic.LongAdder()};
	/** {@code -Dmattmc.worldgen.verifyDecoration=true}: replay every native placement on Java's path and compare. */
	private static final boolean VERIFY = Boolean.getBoolean("mattmc.worldgen.verifyDecoration");

	private NativeDecoration() {
	}

	public static String statistics() {
		return "enabled=" + ENABLED + " regions=" + REGIONS.sum() + " declinedRegions=" + DECLINED_REGIONS.sum()
			+ " nativePlacements=" + NATIVE.sum() + " javaPlacementsInBoundRegions=" + JAVA.sum()
 			+ " declinedOwners=" + REFRESH_DECLINED[1].sum() + " declinedBiomes=" + REFRESH_DECLINED[2].sum() + " declinedHeightmaps=" + REFRESH_DECLINED[3].sum()
			+ (VERIFY ? " verified=" + VERIFIED.sum() + " mismatched=" + MISMATCHED.sum() : "");
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
		if (!ENABLED || !NativeBlockRegistry.ready()) return null;
		var region = NativeDecorationRegion.bind(level, chunk, new WorldGenerationContext(generator, level), lazyPriming());
		(region == null ? DECLINED_REGIONS : REGIONS).increment();
		return region;
	}

	/**
	 * Places {@code feature} natively after {@code setFeatureSeed(seed, index, step)}.
	 * False when it must run on Java's path; nothing was drawn or written then.
	 */
	public static boolean place(Programs programs, NativeDecorationRegion region, ChunkGenerator generator, WorldGenLevel level,
								WorldgenRandom random, PlacedFeature feature, long seed, int index, int step, BlockPos origin) {
		var program = programs.program(feature, generator, region);
		if (program == null) {
			JAVA.increment();
			return false;
		}
		int refresh = region.refresh(true);
		if (refresh != NativeDecorationRegion.REFRESHED) {
			JAVA.increment();
			REFRESH_DECLINED[refresh].increment();
			return false;
		}
		int[][] before = VERIFY ? region.captureStatesForVerification() : null;
		int result;
		try {
			result = (int)PLACE.invokeExact(program.pointer, region.words(), (int)(region.words().byteSize() / 8),
				region.out(), (int)(region.out().byteSize() / 8), seed, index, step, origin.getX(), origin.getY(), origin.getZ());
		} catch (Throwable failure) {
			throw new IllegalStateException("Native decoration failed", failure);
		} finally {
			java.lang.ref.Reference.reachabilityFence(program);
		}
		switch (result) {
			case PLACED, NOT_PLACED -> {
				region.afterPlacement(!VERIFY);
				NATIVE.increment();
				if (VERIFY) verify(region, before, level, generator, random, feature, origin);
				return true;
			}
			case JAVA_ROUTE -> {
				JAVA.increment();
				return false;
			}
			case UNAVAILABLE_CHUNK -> {
				region.afterPlacement();
				throw region.unavailableChunk();
			}
			case BROKEN_BINDING -> throw new IllegalStateException("Native decoration binding failed mid-placement for " + feature);
			case PANIC -> throw new IllegalStateException("Native decoration panicked for " + feature);
			default -> {
				return false;
			}
		}
	}

	/**
	 * Verification mode: undo the native placement, replay it on Java's path
	 * with the same seeded random, and compare every bound section's states.
	 */
	private static void verify(NativeDecorationRegion region, int[][] before, WorldGenLevel level, ChunkGenerator generator,
							   WorldgenRandom random, PlacedFeature feature, BlockPos origin) {
		int[][] nativeAfter = region.captureStatesForVerification();
		region.restoreStatesForVerification(before);
		feature.placeWithBiomeCheck(level, generator, random, origin);
		int[][] javaAfter = region.captureStatesForVerification();
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
		int heightmaps = region.primedMismatchesForVerification();
		if (heightmaps != 0) LOGGER.error("Native decoration primed {} heightmaps unlike Java for {}", heightmaps, feature);
		(differences == 0 && heightmaps == 0 ? VERIFIED : MISMATCHED).increment();
	}

	/** One compiled program, released when its cache entry is replaced and collected. */
	private static final class Program {
		final MemorySegment pointer;
		final long tagGeneration;
		final Object biomes;
		final int minGenY, genDepth;

		Program(MemorySegment pointer, long tagGeneration, Object biomes, int minGenY, int genDepth) {
			this.pointer = pointer;
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

		@Nullable
		Program program(PlacedFeature feature, ChunkGenerator generator, NativeDecorationRegion region) {
			Object entry = this.entries.get(feature);
			if (entry == UNSUPPORTED) return null;
			long tags = Holder.TAG_GENERATION.get();
			int minGenY = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 6), genDepth = (int)region.words().getAtIndex(ValueLayout.JAVA_LONG, 7);
			if (entry instanceof Program program && program.tagGeneration == tags && program.biomes == region.biomeValues()
					&& program.minGenY == minGenY && program.genDepth == genDepth) {
				return program;
			}
			int[] words = encode(feature, generator, region.biomeValues());
			Object compiled = UNSUPPORTED;
			if (words != null) {
				try (var arena = Arena.ofConfined()) {
					var segment = arena.allocate(words.length * 4L, 4);
					MemorySegment.copy(words, 0, segment, ValueLayout.JAVA_INT, 0, words.length);
					var pointer = (MemorySegment)CREATE.invokeExact(segment, words.length);
					if (pointer.address() != 0) compiled = new Program(pointer, tags, region.biomeValues(), minGenY, genDepth);
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

	/** Encodes {@code program.rs}'s words, or null for any unsupported modifier, feature or rule. */
	@Nullable
	static int[] encode(PlacedFeature placed, ChunkGenerator generator, List<Holder<Biome>> biomes) {
		var words = new ArrayList<Integer>();
		words.add(1);
		words.add(placed.placement().size());
		for (PlacementModifier modifier : placed.placement()) {
			int[] m = new int[6];
			if (modifier.getClass() == CountPlacement.class) {
				IntProvider count = ((CountPlacement)modifier).countProvider();
				if (count.getClass() == ConstantInt.class) {
					m[0] = 1;
					m[1] = ((ConstantInt)count).getValue();
				} else if (count.getClass() == UniformInt.class) {
					m[0] = 2;
					m[1] = count.getMinValue();
					m[2] = count.getMaxValue();
				} else return null;
			} else if (modifier.getClass() == InSquarePlacement.class) {
				m[0] = 3;
			} else if (modifier.getClass() == HeightRangePlacement.class) {
				HeightProvider height = ((HeightRangePlacement)modifier).heightProvider();
				if (height.getClass() == UniformHeight.class) {
					var uniform = (UniformHeight)height;
					m[0] = 4;
					if (!anchor(uniform.minInclusive(), m, 1) || !anchor(uniform.maxInclusive(), m, 3)) return null;
				} else if (height.getClass() == TrapezoidHeight.class) {
					var trapezoid = (TrapezoidHeight)height;
					m[0] = 5;
					m[5] = trapezoid.plateau();
					if (!anchor(trapezoid.minInclusive(), m, 1) || !anchor(trapezoid.maxInclusive(), m, 3)) return null;
				} else if (height.getClass() == BiasedToBottomHeight.class) {
					var biased = (BiasedToBottomHeight)height;
					m[0] = 6;
					m[5] = biased.inner();
					if (!anchor(biased.minInclusive(), m, 1) || !anchor(biased.maxInclusive(), m, 3)) return null;
				} else return null;
			} else if (modifier.getClass() == BiomeFilter.class) {
				m[0] = 7;
			} else if (modifier.getClass() == RarityFilter.class) {
				m[0] = 8;
				m[1] = ((RarityFilter)modifier).chance();
			} else return null;
			for (int word : m) words.add(word);
		}
		var configured = placed.feature().value();
		if (configured.feature() != Feature.ORE || configured.feature().getClass() != OreFeature.class
				|| configured.config().getClass() != OreConfiguration.class) return null;
		var ore = (OreConfiguration)configured.config();
		if (ore.size < 0 || ore.size > 64 || ore.targetStates.size() > 64) return null;
		words.add(1);
		words.add(ore.size);
		words.add(Float.floatToRawIntBits(ore.discardChanceOnAirExposure));
		words.add(ore.targetStates.size());
		var sets = new ArrayList<int[]>();
		for (var target : ore.targetStates) {
			int state = Block.BLOCK_STATE_REGISTRY.getId(target.state);
			if (state < 0 || target.state.getClass() != BlockState.class) return null;
			RuleTest rule = target.target;
			float probability;
			int[] set;
			if (rule.getClass() == TagMatchTest.class || rule.getClass() == BlockMatchTest.class
					|| rule.getClass() == BlockStateMatchTest.class || rule.getClass() == AlwaysTrueTest.class) {
				probability = Float.NaN;
				set = stateSet(rule::test);
			} else if (rule.getClass() == RandomBlockMatchTest.class) {
				var random = (RandomBlockMatchTest)rule;
				probability = random.probability();
				set = stateSet((candidate, ignored) -> candidate.is(random.block()));
			} else if (rule.getClass() == RandomBlockStateMatchTest.class) {
				var random = (RandomBlockStateMatchTest)rule;
				probability = random.probability();
				set = stateSet((candidate, ignored) -> candidate == random.blockState());
			} else return null;
			if (set == null) return null;
			words.add(Float.isNaN(probability) ? 1 : 2);
			words.add(Float.isNaN(probability) ? 0 : Float.floatToRawIntBits(probability));
			words.add(sets.size());
			words.add(state);
			sets.add(set);
		}
		words.add(sets.size());
		for (int[] set : sets) {
			words.add(set.length);
			for (int word : set) words.add(word);
		}
		int[] biomeWords = new int[(biomes.size() + 31) >>> 5];
		for (int id = 0; id < biomes.size(); id++) {
			if (generator.getBiomeGenerationSettings(biomes.get(id)).hasFeature(placed)) biomeWords[id >>> 5] |= 1 << (id & 31);
		}
		words.add(biomeWords.length);
		for (int word : biomeWords) words.add(word);
		return words.stream().mapToInt(Integer::intValue).toArray();
	}

	private static boolean anchor(VerticalAnchor anchor, int[] m, int at) {
		if (anchor.getClass() == VerticalAnchor.Absolute.class) {
			m[at] = 0;
			m[at + 1] = ((VerticalAnchor.Absolute)anchor).y();
		} else if (anchor.getClass() == VerticalAnchor.AboveBottom.class) {
			m[at] = 1;
			m[at + 1] = ((VerticalAnchor.AboveBottom)anchor).offset();
		} else if (anchor.getClass() == VerticalAnchor.BelowTop.class) {
			m[at] = 2;
			m[at + 1] = ((VerticalAnchor.BelowTop)anchor).offset();
		} else return false;
		return true;
	}

	private interface StateTest {
		boolean test(BlockState state, net.minecraft.util.RandomSource random);
	}

	/** Every canonical state the deterministic test accepts, as a bit set over state IDs. */
	@Nullable
	private static int[] stateSet(StateTest test) {
		int count = Block.BLOCK_STATE_REGISTRY.size();
		int[] set = new int[(count + 31) >>> 5];
		for (int id = 0; id < count; id++) {
			BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
			if (state == null) return null;
			if (test.test(state, null)) set[id >>> 5] |= 1 << (id & 31);
		}
		return set;
	}
}
