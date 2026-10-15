package net.minecraft.world.level.levelgen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.BiasedToBottomInt;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.GlowstoneFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.RandomBooleanSelectorFeature;
import net.minecraft.world.level.levelgen.feature.RandomPatchFeature;
import net.minecraft.world.level.levelgen.feature.RandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.ThreeLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.*;
import net.minecraft.world.level.levelgen.feature.rootplacers.MangroveRootPlacer;
import net.minecraft.world.level.levelgen.feature.rootplacers.MangroveRootPlacement;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.feature.trunkplacers.*;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
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

/**
 * Encodes {@code decoration/program.rs}'s version-2 words. Every class is
 * checked exactly; anything else returns null and the placed feature keeps
 * Java's path. Configuration fields that their classes keep private are read
 * reflectively here, once per program, for those exact classes.
 */
public final class NativeDecorationEncoder {
	private final ChunkGenerator generator;
	private final List<Integer> words = new ArrayList<>();
	private final List<int[]> sets = new ArrayList<>();
	private final Map<Object, Integer> setKeys = new HashMap<>();
	/** Configured features that still run in Java, by {@code Feature::Java} index. */
	private final List<ConfiguredFeature<?, ?>> javaFeatures = new ArrayList<>();
	/** Whether the placement being encoded may hand its feature to Java. */
	private boolean javaLeafAllowed;
	/** Scratch for {@code simpleBlock}'s per-state rules. */
	private List<Integer> ruleWords = List.of();
	private static final ThreadLocal<String> DECLINE = new ThreadLocal<>();
	private static final ThreadLocal<Tables> LAST_TABLES = new ThreadLocal<>();
	private final List<JavaPredicate> javaPredicates = new ArrayList<>();
	/** Noise objects whose native states the program's noise table points at, by identity. */
	private final List<Object> noises = new ArrayList<>();
	private final List<Long> noiseAddresses = new ArrayList<>();

	private final List<JavaStep> javaSteps = new ArrayList<>();

	/** A step Rust asks Java to run at a position with the decoration random (block entities). */
	public interface JavaStep {
		void run(net.minecraft.world.level.WorldGenLevel level, net.minecraft.util.RandomSource random, net.minecraft.core.BlockPos pos);
	}

	/** A predicate Rust asks Java to answer at a position, through a sync upcall. */
	public interface JavaPredicate {
		boolean test(net.minecraft.world.level.WorldGenLevel level, net.minecraft.core.BlockPos pos);
	}

	/** What a program needs from Java at run time: Java leaves, Java predicates and retained noise owners. */
	public record Tables(List<ConfiguredFeature<?, ?>> javaFeatures, List<JavaPredicate> javaPredicates, List<JavaStep> javaSteps, List<Object> retained) {}

	/** The Java feature table of this thread's last successful encoding. */
	static Tables lastTables() {
		return LAST_TABLES.get();
	}

	/** Debugging: the class this thread's last declined encoding stopped at. */
	static String lastDecline() {
		return DECLINE.get();
	}

	private boolean decline(Object what) {
		DECLINE.set(String.valueOf(what instanceof Class<?> type ? type.getName() : what));
		return false;
	}

	/** The level seed, for seed-derived state such as the geode noise. */
	private final long seed;

	/** The region's biome values: the indices Rust's biome reads return. */
	private final List<Holder<Biome>> biomes;

	private NativeDecorationEncoder(ChunkGenerator generator, long seed, List<Holder<Biome>> biomes) {
		this.generator = generator;
		this.seed = seed;
		this.biomes = biomes;
	}

	/** {@code GeodeFeature}'s per-placement noise depends only on the level seed: one per seed. */
	private static final java.util.concurrent.ConcurrentHashMap<Long, net.minecraft.world.level.levelgen.synth.NormalNoise> GEODE_NOISES =
		new java.util.concurrent.ConcurrentHashMap<>();

	@Nullable
	static int[] encode(PlacedFeature placed, ChunkGenerator generator, List<Holder<Biome>> biomes, long seed) {
		var encoder = new NativeDecorationEncoder(generator, seed, biomes);
		encoder.words.add(3);
		try {
			if (!encoder.placed(placed, true)) return null;
		} catch (ReflectiveOperationException | ClassCastException failure) {
			DECLINE.set(failure.toString());
			return null;
		}
		var words = encoder.words;
		LAST_TABLES.set(new Tables(List.copyOf(encoder.javaFeatures), List.copyOf(encoder.javaPredicates), List.copyOf(encoder.javaSteps),
			List.copyOf(encoder.noises)));
		words.add(encoder.sets.size());
		for (int[] set : encoder.sets) {
			words.add(set.length);
			for (int word : set) words.add(word);
		}
		int[] biomeWords = new int[(biomes.size() + 31) >>> 5];
		for (int id = 0; id < biomes.size(); id++) {
			if (generator.getBiomeGenerationSettings(biomes.get(id)).hasFeature(placed)) biomeWords[id >>> 5] |= 1 << (id & 31);
		}
		words.add(biomeWords.length);
		for (int word : biomeWords) words.add(word);
		words.add(encoder.noiseAddresses.size());
		for (long address : encoder.noiseAddresses) {
			words.add((int)address);
			words.add((int)(address >>> 32));
		}
		return words.stream().mapToInt(Integer::intValue).toArray();
	}

	private static Object field(Object owner, Class<?> declaring, String name) throws ReflectiveOperationException {
		Field field = declaring.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private boolean placed(PlacedFeature placed, boolean top) throws ReflectiveOperationException {
		this.words.add(placed.placement().size());
		for (PlacementModifier modifier : placed.placement()) {
			if (!this.modifier(modifier, top)) return false;
		}
		// A Java feature is worth an upcall below a nested placement, or under
		// modifiers that are expensive in Java; otherwise the placement stays Java.
		boolean allowed = this.javaLeafAllowed;
		this.javaLeafAllowed = !top || placed.placement().stream().anyMatch(m -> m.getClass() == net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement.class
			|| m.getClass() == net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement.class);
		try {
			return this.feature(placed.feature().value());
		} finally {
			this.javaLeafAllowed = allowed;
		}
	}

	private boolean placed(Holder<PlacedFeature> holder) throws ReflectiveOperationException {
		return this.placed(holder.value(), false);
	}

	private boolean modifier(PlacementModifier modifier, boolean top) throws ReflectiveOperationException {
		int[] m = new int[6];
		BlockPredicate predicate = null;
		IntProvider provider = null;
		if (modifier.getClass() == CountPlacement.class) {
			// The provider's words follow the modifier.
			m[0] = 13;
			provider = ((CountPlacement)modifier).countProvider();
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.HeightmapPlacement.class) {
			m[0] = 11;
			m[1] = ((Heightmap.Types)field(modifier, modifier.getClass(), "heightmap")).ordinal();
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement.class) {
			m[0] = 14;
			m[1] = this.set("layer_empty", state -> state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA));
			m[2] = this.set(List.of("block", Blocks.BEDROCK), state -> state.is(Blocks.BEDROCK));
			provider = (IntProvider)field(modifier, modifier.getClass(), "count");
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement.class) {
			var direction = (Direction)field(modifier, modifier.getClass(), "directionOfSearch");
			m[0] = 15;
			m[1] = direction == Direction.UP ? 1 : 0;
			m[2] = (int)field(modifier, modifier.getClass(), "maxSteps");
			for (int word : m) this.words.add(word);
			return this.predicate((BlockPredicate)field(modifier, modifier.getClass(), "targetCondition"))
				&& this.predicate((BlockPredicate)field(modifier, modifier.getClass(), "allowedSearchCondition"));
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.FixedPlacement.class) {
			@SuppressWarnings("unchecked")
			var positions = (List<net.minecraft.core.BlockPos>)field(modifier, modifier.getClass(), "positions");
			m[0] = 21;
			m[1] = positions.size();
			for (int word : m) this.words.add(word);
			for (var pos : positions) this.words.addAll(List.of(pos.getX(), pos.getY(), pos.getZ()));
			return true;
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement.class) {
			m[0] = 16;
			for (int word : m) this.words.add(word);
			return this.intProvider((IntProvider)field(modifier, modifier.getClass(), "xzSpread"))
				&& this.intProvider((IntProvider)field(modifier, modifier.getClass(), "ySpread"));
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.NoiseThresholdCountPlacement.class) {
			long level = Double.doubleToRawLongBits((double)field(modifier, modifier.getClass(), "noiseLevel"));
			m[0] = 17;
			m[1] = (int)level;
			m[2] = (int)(level >>> 32);
			m[3] = (int)field(modifier, modifier.getClass(), "belowNoise");
			m[4] = (int)field(modifier, modifier.getClass(), "aboveNoise");
			m[5] = this.noise(Biome.BIOME_INFO_NOISE, Biome.BIOME_INFO_NOISE.nativeState().state());
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement.class) {
			long factor = Double.doubleToRawLongBits((double)field(modifier, modifier.getClass(), "noiseFactor"));
			long offset = Double.doubleToRawLongBits((double)field(modifier, modifier.getClass(), "noiseOffset"));
			m[0] = 18;
			m[1] = (int)field(modifier, modifier.getClass(), "noiseToCountRatio");
			m[2] = (int)factor;
			m[3] = (int)(factor >>> 32);
			m[4] = (int)offset;
			m[5] = (int)(offset >>> 32);
			for (int word : m) this.words.add(word);
			this.words.add(this.noise(Biome.BIOME_INFO_NOISE, Biome.BIOME_INFO_NOISE.nativeState().state()));
			return true;
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter.class) {
			m[0] = 19;
			m[1] = ((Heightmap.Types)field(modifier, modifier.getClass(), "heightmap")).ordinal();
			m[2] = (int)field(modifier, modifier.getClass(), "minInclusive");
			m[3] = (int)field(modifier, modifier.getClass(), "maxInclusive");
		} else if (modifier.getClass() == net.minecraft.world.level.levelgen.placement.SurfaceWaterDepthFilter.class) {
			m[0] = 12;
			m[1] = (int)field(modifier, modifier.getClass(), "maxWaterDepth");
		} else if (modifier.getClass() == InSquarePlacement.class) {
			m[0] = 3;
		} else if (modifier.getClass() == HeightRangePlacement.class) {
			HeightProvider height = ((HeightRangePlacement)modifier).heightProvider();
			if (height.getClass() == UniformHeight.class) {
				var uniform = (UniformHeight)height;
				m[0] = 4;
				if (!anchor(uniform.minInclusive(), m, 1) || !anchor(uniform.maxInclusive(), m, 3)) return false;
			} else if (height.getClass() == TrapezoidHeight.class) {
				var trapezoid = (TrapezoidHeight)height;
				m[0] = 5;
				m[5] = trapezoid.plateau();
				if (!anchor(trapezoid.minInclusive(), m, 1) || !anchor(trapezoid.maxInclusive(), m, 3)) return false;
			} else if (height.getClass() == BiasedToBottomHeight.class) {
				var biased = (BiasedToBottomHeight)height;
				m[0] = 6;
				m[5] = biased.inner();
				if (!anchor(biased.minInclusive(), m, 1) || !anchor(biased.maxInclusive(), m, 3)) return false;
			} else if (height.getClass() == net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight.class) {
				var type = net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight.class;
				m[0] = 20;
				m[5] = (int)field(height, type, "inner");
				if (!anchor((net.minecraft.world.level.levelgen.VerticalAnchor)field(height, type, "minInclusive"), m, 1)
					|| !anchor((net.minecraft.world.level.levelgen.VerticalAnchor)field(height, type, "maxInclusive"), m, 3)) return false;
			} else return false;
		} else if (modifier.getClass() == BiomeFilter.class) {
			// A nested placement's biome filter throws in Java: keep it there.
			if (!top) return false;
			m[0] = 7;
		} else if (modifier.getClass() == RarityFilter.class) {
			m[0] = 8;
			m[1] = ((RarityFilter)modifier).chance();
		} else if (modifier.getClass() == BlockPredicateFilter.class) {
			m[0] = 10;
			predicate = (BlockPredicate)field(modifier, BlockPredicateFilter.class, "predicate");
		} else return this.decline(modifier.getClass());
		for (int word : m) this.words.add(word);
		if (provider != null) return this.intProvider(provider);
		return predicate == null || this.predicate(predicate);
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

	private static final String PREDICATES = "net.minecraft.world.level.levelgen.blockpredicates.";

	/** Predicate classes are package-private: they are matched by exact class name. */
	private static boolean is(Class<?> type, String simpleName) {
		return type.getName().equals(PREDICATES + simpleName);
	}

	private boolean predicate(BlockPredicate predicate) throws ReflectiveOperationException {
		Class<?> type = predicate.getClass();
		if (is(type, "TrueBlockPredicate")) {
			this.words.add(1);
			return true;
		}
		if (is(type, "AllOfPredicate") || is(type, "AnyOfPredicate")) {
			@SuppressWarnings("unchecked")
			var children = (List<BlockPredicate>)field(predicate, type.getSuperclass(), "predicates");
			this.words.add(is(type, "AllOfPredicate") ? 2 : 3);
			this.words.add(children.size());
			for (var child : children) {
				if (!this.predicate(child)) return false;
			}
			return true;
		}
		if (is(type, "NotPredicate")) {
			this.words.add(4);
			return this.predicate((BlockPredicate)field(predicate, type, "predicate"));
		}
		if (is(type, "SolidPredicate") || is(type, "ReplaceablePredicate")) {
			// State-only tests: the set is the predicate's own test over every state.
			Vec3i offset = (Vec3i)field(predicate, type.getSuperclass(), "offset");
			var test = type.getDeclaredMethod("test", BlockState.class);
			test.setAccessible(true);
			int set = this.set(List.of("state_test", type), state -> {
				try {
					return (boolean)test.invoke(predicate, state);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			this.words.addAll(List.of(5, offset.getX(), offset.getY(), offset.getZ(), set));
			return true;
		}
		if (is(type, "InsideWorldBoundsPredicate")) {
			Vec3i offset = (Vec3i)field(predicate, type, "offset");
			this.words.addAll(List.of(7, offset.getX(), offset.getY(), offset.getZ()));
			return true;
		}
		if (is(type, "HasSturdyFacePredicate")) {
			Vec3i offset = (Vec3i)field(predicate, type, "offset");
			var direction = (Direction)field(predicate, type, "direction");
			this.checked(offset.getX(), offset.getY(), offset.getZ(), List.of("sturdy", direction),
				state -> state.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO, direction), predicate::test);
			return true;
		}
		if (is(type, "MatchingBlocksPredicate") || is(type, "MatchingBlockTagPredicate") || is(type, "MatchingFluidsPredicate")) {
			Vec3i offset = (Vec3i)field(predicate, type.getSuperclass(), "offset");
			int set;
			if (is(type, "MatchingBlocksPredicate")) {
				@SuppressWarnings("unchecked")
				var blocks = (HolderSet<Block>)field(predicate, type, "blocks");
				set = this.set(List.of("blocks", blocks), state -> state.is(blocks));
			} else if (is(type, "MatchingBlockTagPredicate")) {
				var tag = field(predicate, type, "tag");
				@SuppressWarnings("unchecked")
				var key = (net.minecraft.tags.TagKey<Block>)tag;
				set = this.set(List.of("tag", key), state -> state.is(key));
			} else {
				@SuppressWarnings("unchecked")
				var fluids = (HolderSet<net.minecraft.world.level.material.Fluid>)field(predicate, type, "fluids");
				set = this.set(List.of("fluids", fluids), state -> state.getFluidState().is(fluids));
			}
			this.words.addAll(List.of(5, offset.getX(), offset.getY(), offset.getZ(), set));
			return true;
		}
		if (is(type, "WouldSurvivePredicate")) {
			Vec3i offset = (Vec3i)field(predicate, type, "offset");
			BlockState state = (BlockState)field(predicate, type, "state");
			// SaplingBlock keeps VegetationBlock's canSurvive: the block below is dirt or farmland.
			if (state.getBlock().getClass() == SaplingBlock.class) {
				int set = this.set("dirt_or_farmland", candidate -> candidate.is(BlockTags.DIRT) || candidate.is(Blocks.FARMLAND));
				this.words.addAll(List.of(5, offset.getX(), offset.getY() - 1, offset.getZ(), set));
				return true;
			}
		}
		// Anything else (unobstructed, custom predicates) is answered by Java at the position.
		this.javaPredicate(predicate::test);
		return true;
	}

	/** Predicate words 8: Java answers at the predicate's position through a sync upcall. */
	private void javaPredicate(JavaPredicate predicate) {
		this.words.addAll(List.of(8, this.javaPredicates.size()));
		this.javaPredicates.add(predicate);
	}

	/**
	 * Predicate words 6: a shape-dependent set built without a world (exact for
	 * every state with a shape cache); a dynamic-shape state at the offset asks
	 * {@code exact} at the predicate's position instead.
	 */
	private void checked(int dx, int dy, int dz, Object key, Predicate<BlockState> withoutWorld, JavaPredicate exact) {
		int set = this.set(key, state -> !state.getBlock().hasDynamicShape() && withoutWorld.test(state));
		this.words.addAll(List.of(6, dx, dy, dz, set, this.javaPredicates.size()));
		this.javaPredicates.add(exact);
	}

	/** Index of a native noise in the program's noise table; {@code owner} keeps its state alive. */
	private int noise(Object owner, java.lang.foreign.MemorySegment state) {
		for (int i = 0; i < this.noises.size(); i++) {
			if (this.noises.get(i) == owner) return i;
		}
		this.noises.add(owner);
		this.noiseAddresses.add(state.address());
		return this.noises.size() - 1;
	}

	/**
	 * {@code state.canSurvive(level, pos)} as predicate words at the position.
	 * Block families whose rule reads only neighbouring states compile to sets;
	 * any other block asks Java.
	 */
	private void survival(BlockState state) throws ReflectiveOperationException {
		Block block = state.getBlock();
		Class<?> survive = declaring(block.getClass(), "canSurvive", BlockState.class, net.minecraft.world.level.LevelReader.class,
			net.minecraft.core.BlockPos.class);
		JavaPredicate exact = state::canSurvive;
		if (survive == BlockBehaviour.class) {
			this.words.add(1);
			return;
		}
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		var zero = net.minecraft.core.BlockPos.ZERO;
		if (block.getClass() == net.minecraft.world.level.block.SeagrassBlock.class && survive == VegetationBlock.class) {
			this.checked(0, -1, 0, "seagrass_floor", s -> s.isFaceSturdy(empty, zero, Direction.UP) && !s.is(Blocks.MAGMA_BLOCK), exact);
			return;
		}
		if (block.getClass() == net.minecraft.world.level.block.TallSeagrassBlock.class) {
			if (state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
				this.words.addAll(List.of(5, 0, -1, 0, this.set(List.of("lower_of", block), s -> s.is(block) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER)));
			} else {
				this.words.addAll(List.of(2, 2));
				this.checked(0, -1, 0, "seagrass_floor", s -> s.isFaceSturdy(empty, zero, Direction.UP) && !s.is(Blocks.MAGMA_BLOCK),
					(level, pos) -> {
						BlockState below = level.getBlockState(pos.below());
						return below.isFaceSturdy(level, pos.below(), Direction.UP) && !below.is(Blocks.MAGMA_BLOCK);
					});
				this.words.addAll(List.of(5, 0, 0, 0, this.set("water_source_fluid", s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().getAmount() == 8)));
			}
			return;
		}
		if (survive == net.minecraft.world.level.block.BaseCoralPlantTypeBlock.class) {
			this.checked(0, -1, 0, List.of("sturdy", Direction.UP), s -> s.isFaceSturdy(empty, zero, Direction.UP), exact);
			return;
		}
		if (survive == net.minecraft.world.level.block.BaseCoralWallFanBlock.class) {
			var facing = state.getValue(net.minecraft.world.level.block.BaseCoralWallFanBlock.FACING);
			var back = facing.getOpposite();
			this.checked(back.getStepX(), back.getStepY(), back.getStepZ(), List.of("sturdy", facing), s -> s.isFaceSturdy(empty, zero, facing), exact);
			return;
		}
		if (block.getClass() == net.minecraft.world.level.block.SeaPickleBlock.class) {
			this.checked(0, -1, 0, "pickle_floor",
				s -> !s.getCollisionShape(empty, zero).getFaceShape(Direction.UP).isEmpty() || s.isFaceSturdy(empty, zero, Direction.UP), exact);
			return;
		}
		if (survive == net.minecraft.world.level.block.GrowingPlantBlock.class) {
			Class<?> attach = declaring(block.getClass(), "canAttachTo", BlockState.class);
			String attachName = attach == null ? "" : attach.getSimpleName();
			if (attachName.equals("GrowingPlantBlock") || attachName.equals("KelpBlock") || attachName.equals("KelpPlantBlock")) {
				var growth = (Direction)field(block, net.minecraft.world.level.block.GrowingPlantBlock.class, "growthDirection");
				var canAttach = attach.getDeclaredMethod("canAttachTo", BlockState.class);
				canAttach.setAccessible(true);
				var head = net.minecraft.world.level.block.GrowingPlantBlock.class.getDeclaredMethod("getHeadBlock");
				var body = net.minecraft.world.level.block.GrowingPlantBlock.class.getDeclaredMethod("getBodyBlock");
				head.setAccessible(true);
				body.setAccessible(true);
				Block headBlock = (Block)head.invoke(block), bodyBlock = (Block)body.invoke(block);
				var back = growth.getOpposite();
				this.checked(back.getStepX(), back.getStepY(), back.getStepZ(), List.of("growing_support", block), s -> {
					try {
						return (boolean)canAttach.invoke(block, s) && (s.is(headBlock) || s.is(bodyBlock) || s.isFaceSturdy(empty, zero, growth));
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
				}, exact);
				return;
			}
		}
		boolean doublePlant = block instanceof DoublePlantBlock && survive == DoublePlantBlock.class;
		if (doublePlant && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			this.words.addAll(List.of(5, 0, -1, 0, this.set(List.of("lower_of", block), s -> s.is(block) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER)));
			return;
		}
		if (survive == VegetationBlock.class || doublePlant) {
			var mayPlaceOn = declaring(block.getClass(), "mayPlaceOn", BlockState.class, net.minecraft.world.level.BlockGetter.class,
				net.minecraft.core.BlockPos.class);
			if (mayPlaceOn != null && PURE_MAY_PLACE_ON.contains(mayPlaceOn.getSimpleName())
					&& mayPlaceOn.getPackageName().equals(VegetationBlock.class.getPackageName())) {
				var method = mayPlaceOn.getDeclaredMethod("mayPlaceOn", BlockState.class, net.minecraft.world.level.BlockGetter.class,
					net.minecraft.core.BlockPos.class);
				method.setAccessible(true);
				this.words.addAll(List.of(5, 0, -1, 0, this.set(List.of("mayPlaceOn", block), s -> {
					try {
						return (boolean)method.invoke(block, s, null, null);
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
				})));
				return;
			}
		}
		this.javaPredicate(exact);
	}

	/** {@code vegetation.rs}'s state provider words: [0, provider] or [1, rules, (predicate, provider)*, fallback]. */
	private boolean stateProvider(BlockStateProvider provider) throws ReflectiveOperationException {
		this.words.add(0);
		return this.provider(provider);
	}

	private boolean ruleProvider(net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider provider) throws ReflectiveOperationException {
		if (provider.getClass() != net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider.class) return this.decline(provider.getClass());
		this.words.addAll(List.of(1, provider.rules().size()));
		for (var rule : provider.rules()) {
			if (!this.predicate(rule.ifTrue()) || !this.provider(rule.then())) return false;
		}
		return this.provider(provider.fallback());
	}

	/** Features 12–19 ({@code vegetation.rs}); false declines. */
	private boolean vegetation(int kind, Object config) throws ReflectiveOperationException {
		switch (kind) {
			case 12 -> {
				var column = (net.minecraft.world.level.levelgen.feature.configurations.BlockColumnConfiguration)config;
				this.words.add(column.layers().size());
				for (var layer : column.layers()) {
					if (!this.intProvider(layer.height()) || !this.stateProvider(layer.state())) return false;
				}
				this.words.add(column.direction().ordinal());
				if (!this.predicate(column.allowedPlacement())) return false;
				this.words.add(column.prioritizeTip() ? 1 : 0);
			}
			case 13 -> {
				var probability = ((net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration)config).probability;
				BlockState seagrass = Blocks.SEAGRASS.defaultBlockState(), tall = Blocks.TALL_SEAGRASS.defaultBlockState();
				this.words.add(Float.floatToRawIntBits(probability));
				this.words.add(this.set(List.of("block", Blocks.WATER), s -> s.is(Blocks.WATER)));
				this.words.addAll(List.of(stateId(seagrass), stateId(tall), stateId(tall.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))));
				this.survival(seagrass);
				this.survival(tall);
			}
			case 14 -> {
				if (!this.intProvider(((net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration)config).count())) return false;
				this.words.add(this.set(List.of("block", Blocks.WATER), s -> s.is(Blocks.WATER)));
				BlockState pickle = Blocks.SEA_PICKLE.defaultBlockState();
				for (int pickles = 1; pickles <= 4; pickles++) this.words.add(stateId(pickle.setValue(net.minecraft.world.level.block.SeaPickleBlock.PICKLES, pickles)));
				this.survival(pickle);
			}
			case 15 -> {
				this.words.add(this.set(List.of("block", Blocks.WATER), s -> s.is(Blocks.WATER)));
				this.words.add(this.set(List.of("block", Blocks.KELP), s -> s.is(Blocks.KELP)));
				BlockState kelp = Blocks.KELP.defaultBlockState(), plant = Blocks.KELP_PLANT.defaultBlockState();
				for (int age = 20; age < 24; age++) this.words.add(stateId(kelp.setValue(net.minecraft.world.level.block.KelpBlock.AGE, age)));
				this.words.add(stateId(plant));
				this.survival(plant);
				this.survival(kelp);
			}
			case 16 -> {
				var disk = (net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration)config;
				if (!this.ruleProvider(disk.stateProvider()) || !this.predicate(disk.target()) || !this.intProvider(disk.radius())) return false;
				this.words.add(disk.halfHeight());
			}
			case 17 -> {
				BlockState vine = Blocks.VINE.defaultBlockState();
				for (Direction direction : Direction.values()) {
					this.words.add(stateId(direction == Direction.DOWN ? vine : vine.setValue(VineBlock.getPropertyForFace(direction), true)));
					var opposite = direction.getOpposite();
					var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
					var zero = net.minecraft.core.BlockPos.ZERO;
					this.checked(0, 0, 0, List.of("vine_attach", direction),
						s -> Block.isFaceFull(s.getBlockSupportShape(empty, zero), opposite) || Block.isFaceFull(s.getCollisionShape(empty, zero), opposite),
						(level, pos) -> VineBlock.isAcceptableNeighbour(level, pos, direction));
				}
			}
			case 18 -> {
				this.words.add(stateId(((net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration)config).state));
				this.words.add(this.set("dirt_or_base_stone", s -> s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD)));
			}
			case 19 -> {
				var magma = (net.minecraft.world.level.levelgen.feature.configurations.UnderwaterMagmaConfiguration)config;
				this.words.addAll(List.of(magma.floorSearchRange, magma.placementRadiusAroundFloor, Float.floatToRawIntBits(magma.placementProbabilityPerValidPosition)));
				this.words.add(this.set(List.of("block", Blocks.WATER), s -> s.is(Blocks.WATER)));
				this.words.add(stateId(Blocks.MAGMA_BLOCK.defaultBlockState()));
			}
			case 20 -> {
				return this.multiface((net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration)config);
			}
			case 21 -> {
				return this.geode((net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration)config);
			}
			case 22 -> {
				return this.monsterRoom();
			}
			case 30 -> {
				var pointed = (net.minecraft.world.level.levelgen.feature.configurations.PointedDripstoneConfiguration)config;
				this.dripstoneBlocks();
				for (float chance : new float[] {pointed.chanceOfTallerDripstone, pointed.chanceOfDirectionalSpread, pointed.chanceOfSpreadRadius2, pointed.chanceOfSpreadRadius3}) {
					this.words.add(Float.floatToRawIntBits(chance));
				}
			}
			case 31 -> {
				var cluster = (net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration)config;
				this.dripstoneBlocks();
				this.words.add(cluster.floorToCeilingSearchRange);
				if (!this.intProvider(cluster.height) || !this.intProvider(cluster.radius)) return false;
				this.words.addAll(List.of(cluster.maxStalagmiteStalactiteHeightDiff, cluster.heightDeviation));
				if (!this.intProvider(cluster.dripstoneBlockLayerThickness) || !this.floatProvider(cluster.density) || !this.floatProvider(cluster.wetness)) return false;
				this.words.add(Float.floatToRawIntBits(cluster.chanceOfDripstoneColumnAtMaxDistanceFromCenter));
				this.words.addAll(List.of(cluster.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn, cluster.maxDistanceFromCenterAffectingHeightBias));
			}
			case 32 -> {
				var large = (net.minecraft.world.level.levelgen.feature.configurations.LargeDripstoneConfiguration)config;
				this.dripstoneBlocks();
				this.words.add(large.floorToCeilingSearchRange);
				if (!this.intProvider(large.columnRadius)) return false;
				this.words.addAll(List.of(large.columnRadius.getMinValue(), large.columnRadius.getMaxValue()));
				if (!this.floatProvider(large.heightScale)) return false;
				this.words.add(Float.floatToRawIntBits(large.maxColumnRadiusToCaveHeightRatio));
				if (!this.floatProvider(large.stalactiteBluntness) || !this.floatProvider(large.stalagmiteBluntness) || !this.floatProvider(large.windSpeed)) return false;
				this.words.add(large.minRadiusForWind);
				this.words.add(Float.floatToRawIntBits(large.minBluntnessForWind));
				this.words.add(this.set("dripstone_base_or_lava", st -> st.is(Blocks.DRIPSTONE_BLOCK) || st.is(BlockTags.DRIPSTONE_REPLACEABLE) || st.is(Blocks.LAVA)));
				this.words.add(this.set("air_water_or_lava_block", st -> st.isAir() || st.is(Blocks.WATER) || st.is(Blocks.LAVA)));
				this.words.add(this.set(List.of("tag", BlockTags.BASE_STONE_OVERWORLD), st -> st.is(BlockTags.BASE_STONE_OVERWORLD)));
			}
			case 33 -> {
				this.words.add(this.generator.getSeaLevel());
				this.words.add(this.set(List.of("block", Blocks.WATER), st -> st.is(Blocks.WATER)));
				this.words.add(this.set(List.of("block", Blocks.PACKED_ICE), st -> st.is(Blocks.PACKED_ICE)));
				this.words.add(this.set(List.of("block", Blocks.BLUE_ICE), st -> st.is(Blocks.BLUE_ICE)));
				this.words.add(this.set("blue_ice_spreads_into", st -> st.isAir() || st.is(Blocks.WATER) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.ICE)));
				this.words.add(stateId(Blocks.BLUE_ICE.defaultBlockState()));
			}
			case 34 -> {
				this.words.add(this.set(List.of("block", Blocks.SNOW_BLOCK), st -> st.is(Blocks.SNOW_BLOCK)));
				this.words.add(this.set("ice_spike_into", st -> st.isAir() || st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.ICE)));
				this.words.add(this.set("ice_spike_column_into",
					st -> st.isAir() || st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE)));
				this.words.add(stateId(Blocks.PACKED_ICE.defaultBlockState()));
			}
			case 35 -> {
				this.words.add(Float.floatToRawIntBits(((net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration)config).probability));
				BlockState bamboo = Blocks.BAMBOO.defaultBlockState();
				if (bamboo.getClass() != BlockState.class) return this.decline("custom bamboo state");
				if (declaring(bamboo.getBlock().getClass(), "canSurvive", BlockState.class, net.minecraft.world.level.LevelReader.class,
						net.minecraft.core.BlockPos.class) == net.minecraft.world.level.block.BambooStalkBlock.class) {
					this.words.addAll(List.of(5, 0, -1, 0, this.set(List.of("tag", BlockTags.BAMBOO_PLANTABLE_ON), st -> st.is(BlockTags.BAMBOO_PLANTABLE_ON))));
				} else {
					this.javaPredicate(bamboo::canSurvive);
				}
				this.words.add(this.set(List.of("tag", BlockTags.DIRT), st -> st.is(BlockTags.DIRT)));
				this.words.add(stateId(Blocks.PODZOL.defaultBlockState()));
				for (String name : List.of("BAMBOO_TRUNK", "BAMBOO_FINAL_LARGE", "BAMBOO_TOP_LARGE", "BAMBOO_TOP_SMALL")) {
					this.words.add(stateId((BlockState)field(null, net.minecraft.world.level.levelgen.feature.BambooFeature.class, name)));
				}
			}
			case 36, 37 -> {
				var mushroom = (net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration)config;
				var caps = this.providerStates(mushroom.capProvider);
				if (caps == null || !this.provider(mushroom.capProvider) || !this.provider(mushroom.stemProvider)) return false;
				this.words.add(mushroom.foliageRadius);
				var faces = new java.util.ArrayList<BlockState>();
				for (BlockState cap : caps) {
					if (cap.hasProperty(HugeMushroomBlock.WEST) && cap.hasProperty(HugeMushroomBlock.EAST) && cap.hasProperty(HugeMushroomBlock.NORTH)
						&& cap.hasProperty(HugeMushroomBlock.SOUTH) && (kind == 37 || cap.hasProperty(HugeMushroomBlock.UP)) && !faces.contains(cap)) faces.add(cap);
				}
				this.words.add(faces.size());
				for (BlockState cap : faces) {
					this.words.add(stateId(cap));
					for (int bits = 0; bits < 32; bits++) {
						BlockState state = cap.setValue(HugeMushroomBlock.WEST, (bits & 1) != 0).setValue(HugeMushroomBlock.EAST, (bits & 2) != 0)
							.setValue(HugeMushroomBlock.NORTH, (bits & 4) != 0).setValue(HugeMushroomBlock.SOUTH, (bits & 8) != 0);
						if (kind == 36) state = state.setValue(HugeMushroomBlock.UP, (bits & 16) != 0);
						this.words.add(stateId(state));
					}
				}
				this.words.add(this.set("dirt_or_mushroom_grow", st -> st.is(BlockTags.DIRT) || st.is(BlockTags.MUSHROOM_GROW_BLOCK)));
				this.words.add(this.set("air_or_leaves", st -> st.isAir() || st.is(BlockTags.LEAVES)));
				this.words.add(this.set("air_or_replaceable_by_mushrooms", st -> st.isAir() || st.is(BlockTags.REPLACEABLE_BY_MUSHROOMS)));
			}
			case 38 -> {
				var fungus = (net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration)config;
				if (fungus.planted) return this.decline("planted fungus");
				Block base = fungus.validBaseState.getBlock(), hat = fungus.hatState.getBlock();
				this.words.add(this.set(List.of("block", base), st -> st.is(base)));
				this.words.addAll(List.of(stateId(fungus.stemState), stateId(fungus.hatState), stateId(fungus.decorState)));
				if (!this.predicate(fungus.replaceableBlocks)) return false;
				this.words.add(this.set("can_be_replaced", BlockState::canBeReplaced));
				this.words.add(this.set(List.of("block", hat), st -> st.is(hat)));
				this.words.add(fungus.hatState.is(Blocks.NETHER_WART_BLOCK) ? 1 : 0);
				this.words.add(this.generator.getGenDepth());
				this.words.add(stateId(Blocks.AIR.defaultBlockState()));
				for (int age = 23; age <= 25; age++) {
					this.words.add(stateId(Blocks.WEEPING_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE, age)));
				}
				this.words.add(stateId(Blocks.WEEPING_VINES_PLANT.defaultBlockState()));
			}
			case 39 -> {
				this.words.add(this.set(List.of("block", Blocks.END_STONE), st -> st.is(Blocks.END_STONE)));
				this.words.add(this.set("chorus_plant_or_flower", st -> st.is(Blocks.CHORUS_PLANT) || st.is(Blocks.CHORUS_FLOWER)));
				BlockState plant = Blocks.CHORUS_PLANT.defaultBlockState();
				for (int bits = 0; bits < 64; bits++) {
					BlockState state = plant;
					for (Direction direction : Direction.values()) {
						state = state.trySetValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction), (bits & 1 << direction.ordinal()) != 0);
					}
					this.words.add(stateId(state));
				}
				this.words.add(stateId(Blocks.CHORUS_FLOWER.defaultBlockState().setValue(net.minecraft.world.level.block.ChorusFlowerBlock.AGE, 5)));
			}
			case 40 -> this.words.add(stateId(Blocks.END_STONE.defaultBlockState()));
			case 42 -> {
				return this.freeze();
			}
			case 43 -> {
				return this.ore((OreConfiguration)config);
			}
			case 44, 45 -> {
				if (kind == 45) {
					var twisting = (net.minecraft.world.level.levelgen.feature.configurations.TwistingVinesConfig)config;
					this.words.addAll(List.of(twisting.spreadWidth(), twisting.spreadHeight(), twisting.maxHeight()));
					this.words.add(this.set("twisting_vines_support", st -> st.is(Blocks.NETHERRACK) || st.is(Blocks.WARPED_NYLIUM) || st.is(Blocks.WARPED_WART_BLOCK)));
				} else {
					this.words.add(this.set("weeping_vines_support", st -> st.is(Blocks.NETHERRACK) || st.is(Blocks.NETHER_WART_BLOCK)));
				}
				this.words.add(stateId(Blocks.NETHER_WART_BLOCK.defaultBlockState()));
				Block head = kind == 45 ? Blocks.TWISTING_VINES : Blocks.WEEPING_VINES;
				for (int age = 17; age <= 25; age++) {
					this.words.add(stateId(head.defaultBlockState().setValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE, age)));
				}
				this.words.add(stateId((kind == 45 ? Blocks.TWISTING_VINES_PLANT : Blocks.WEEPING_VINES_PLANT).defaultBlockState()));
			}
			case 46 -> this.words.add(stateId(Blocks.BASALT.defaultBlockState()));
			case 50 -> {
				return this.spikes((net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration)config);
			}
			case 51 -> {
				var gateway = (net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration)config;
				for (Block block : List.of(Blocks.END_GATEWAY, Blocks.AIR, Blocks.BEDROCK)) this.words.add(stateId(block.defaultBlockState()));
				var exit = gateway.getExit();
				this.words.add(exit.isPresent() ? 1 : 0);
				if (exit.isPresent()) {
					var target = exit.get();
					boolean exact = gateway.isExitExact();
					this.words.add(this.javaStep((level, random, pos) -> {
						if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity entity) entity.setExitPosition(target, exact);
					}));
				}
			}
			case 52 -> {
				this.words.addAll(List.of(stateId(Blocks.OBSIDIAN.defaultBlockState()), stateId(Blocks.AIR.defaultBlockState())));
				this.words.add(this.set(List.of("block", Blocks.OBSIDIAN), st -> st.is(Blocks.OBSIDIAN)));
				this.words.add(this.set(List.of("block", Blocks.AIR), st -> st.is(Blocks.AIR)));
			}
			case 49 -> {
				this.words.add(this.set(List.of("block", Blocks.SAND), st -> st.is(Blocks.SAND)));
				for (Block block : List.of(Blocks.SAND, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE, Blocks.WATER, Blocks.SUSPICIOUS_SAND)) {
					this.words.add(stateId(block.defaultBlockState()));
				}
				this.words.add(this.javaStep((level, random, pos) -> level.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntityType.BRUSHABLE_BLOCK)
					.ifPresent(sand -> sand.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY, pos.asLong()))));
			}
			case 47, 48 -> {
				return this.vegetationPatch(kind == 48, (net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration)config);
			}
			case 41 -> {
				this.words.add(this.generator.getSeaLevel());
				this.words.add(stateId(((net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration)config).state));
				for (Block block : List.of(Blocks.SNOW_BLOCK, Blocks.WATER, Blocks.AIR)) this.words.add(stateId(block.defaultBlockState()));
				this.words.add(this.set("iceberg_replaces", st -> st.isAir() || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.ICE) || st.is(Blocks.WATER)));
				this.words.add(this.set(List.of("block", Blocks.WATER), st -> st.is(Blocks.WATER)));
				this.words.add(this.set("iceberg_state", st -> st.is(Blocks.PACKED_ICE) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.BLUE_ICE)));
				this.words.add(this.set(List.of("block", Blocks.SNOW_BLOCK), st -> st.is(Blocks.SNOW_BLOCK)));
				this.words.add(this.set(List.of("block", Blocks.SNOW), st -> st.is(Blocks.SNOW)));
			}
			case 29 -> {
				return this.sculk((net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration)config);
			}
			case 28 -> {
				var lake = (net.minecraft.world.level.levelgen.feature.LakeFeature.Configuration)config;
				var fluids = this.providerStates(lake.fluid());
				if (fluids == null) return false;
				long water = fluids.stream().filter(st -> st.getFluidState().is(FluidTags.WATER)).count();
				if (water != 0 && water != fluids.size()) return this.decline("lake fluids mixing water");
				if (!this.provider(lake.fluid()) || !this.provider(lake.barrier())) return false;
				BlockState air = Blocks.CAVE_AIR.defaultBlockState();
				this.words.add(stateId(air));
				this.words.add(BuiltInRegistries.BLOCK.getId(air.getBlock()));
				this.words.add(this.set("liquid", BlockState::liquid));
				this.words.add(this.set("solid", BlockState::isSolid));
				this.words.add(this.set(List.of("not_tag", BlockTags.FEATURES_CANNOT_REPLACE), st -> !st.is(BlockTags.FEATURES_CANNOT_REPLACE)));
				this.words.add(this.set(List.of("tag", BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE), st -> st.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)));
				if (water == 0) {
					this.words.add(-1);
				} else {
					this.words.add(this.javaStep((level, random, pos) -> {
						for (int t = 0; t < 16; t++) {
							for (int u = 0; u < 16; u++) {
								var at = pos.offset(t, 4, u);
								if (level.getBiome(at).value().shouldFreeze(level, at, false) && !level.getBlockState(at).is(BlockTags.FEATURES_CANNOT_REPLACE)) {
									level.setBlock(at, Blocks.ICE.defaultBlockState(), 2);
								}
							}
						}
					}));
				}
			}
			case 25, 26, 27 -> {
				var blocks = tagBlocks(BlockTags.CORAL_BLOCKS);
				var corals = tagBlocks(BlockTags.CORALS);
				var walls = tagBlocks(BlockTags.WALL_CORALS);
				if (blocks.size() > 256 || corals.size() > 256 || walls.size() > 256) return this.decline("coral tags");
				this.words.add(blocks.size());
				for (Block block : blocks) this.words.add(stateId(block.defaultBlockState()));
				this.words.add(corals.size());
				for (Block block : corals) this.words.add(stateId(block.defaultBlockState()));
				this.words.add(walls.size());
				var facing = net.minecraft.world.level.block.BaseCoralWallFanBlock.FACING;
				for (Block block : walls) {
					for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
						BlockState state = block.defaultBlockState();
						this.words.add(stateId(state.hasProperty(facing) ? state.setValue(facing, direction) : state));
					}
				}
				for (int pickles = 1; pickles <= 4; pickles++) {
					this.words.add(stateId(Blocks.SEA_PICKLE.defaultBlockState().setValue(net.minecraft.world.level.block.SeaPickleBlock.PICKLES, pickles)));
				}
				this.words.add(this.set(List.of("block", Blocks.WATER), st -> st.is(Blocks.WATER)));
				this.words.add(this.set(List.of("tag", BlockTags.CORALS), st -> st.is(BlockTags.CORALS)));
			}
			case 23 -> {
				var delta = (net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration)config;
				if (delta.size().getMinValue() < 0 || delta.rimSize().getMinValue() < 0) return this.decline("negative delta size");
				this.words.addAll(List.of(stateId(delta.contents()), stateId(delta.rim())));
				if (!this.intProvider(delta.size()) || !this.intProvider(delta.rimSize())) return false;
				Block contents = delta.contents().getBlock();
				this.words.add(this.set(List.of("block", contents), st -> st.is(contents)));
				@SuppressWarnings("unchecked")
				var cannotReplace = (List<Block>)field(null, net.minecraft.world.level.levelgen.feature.DeltaFeature.class, "CANNOT_REPLACE");
				this.words.add(this.set(List.of("delta_cannot_replace"), st -> cannotReplace.contains(st.getBlock())));
			}
			case 24 -> {
				var column = (net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration)config;
				if (column.height().getMinValue() < 0) return this.decline("negative column height");
				if (!this.intProvider(column.reach()) || !this.intProvider(column.height())) return false;
				this.words.add(this.generator.getSeaLevel());
				this.words.add(stateId(Blocks.BASALT.defaultBlockState()));
				this.words.add(this.set(List.of("block", Blocks.BASALT), st -> st.is(Blocks.BASALT)));
				this.words.add(this.set(List.of("block", Blocks.LAVA), st -> st.is(Blocks.LAVA)));
				@SuppressWarnings("unchecked")
				var cannotPlaceOn = (List<Block>)field(null, net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature.class, "CANNOT_PLACE_ON");
				this.words.add(this.set(List.of("basalt_cannot_place_on"), st -> cannotPlaceOn.contains(st.getBlock())));
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Index of a state set, shared by every user with the same key. */
	private int set(Object key, Predicate<BlockState> test) {
		Integer known = this.setKeys.get(key);
		if (known != null) return known;
		int count = Block.BLOCK_STATE_REGISTRY.size();
		int[] set = new int[(count + 31) >>> 5];
		for (int id = 0; id < count; id++) {
			if (test.test(Block.BLOCK_STATE_REGISTRY.byId(id))) set[id >>> 5] |= 1 << (id & 31);
		}
		this.sets.add(set);
		this.setKeys.put(key, this.sets.size() - 1);
		return this.sets.size() - 1;
	}

	private static int stateId(BlockState state) {
		if (state.getClass() != BlockState.class) throw new ClassCastException("custom state");
		return Block.BLOCK_STATE_REGISTRY.getId(state);
	}

	private boolean feature(ConfiguredFeature<?, ?> configured) throws ReflectiveOperationException {
		var feature = configured.feature();
		var config = configured.config();
		int header = this.words.size();
		this.words.add(0);
		this.words.add(0);
		int kind;
		if (feature == Feature.ORE && feature.getClass() == OreFeature.class && config.getClass() == OreConfiguration.class) {
			kind = 1;
			if (!this.ore((OreConfiguration)config)) return false;
		} else if (feature == Feature.REPLACE_BLOBS && feature.getClass() == ReplaceBlobsFeature.class && config.getClass() == ReplaceSphereConfiguration.class) {
			kind = 2;
			var blobs = (ReplaceSphereConfiguration)config;
			if (!this.intProvider(blobs.radius())) return false;
			var block = blobs.targetState.getBlock();
			this.words.add(this.set(List.of("block", block), state -> state.is(block)));
			this.words.add(stateId(blobs.replaceState));
		} else if (feature == Feature.GLOWSTONE_BLOB && feature.getClass() == GlowstoneFeature.class) {
			kind = 3;
			this.words.add(stateId(Blocks.GLOWSTONE.defaultBlockState()));
			this.words.add(this.set("glowstone_hosts", state -> state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)));
			this.words.add(this.set(List.of("block", Blocks.GLOWSTONE), state -> state.is(Blocks.GLOWSTONE)));
		} else if (feature == Feature.SPRING && feature.getClass() == SpringFeature.class && config.getClass() == SpringConfiguration.class) {
			kind = 4;
			var spring = (SpringConfiguration)config;
			this.words.add(spring.requiresBlockBelow ? 1 : 0);
			this.words.add(spring.rockCount);
			this.words.add(spring.holeCount);
			this.words.add(this.set(List.of("blocks", spring.validBlocks), state -> state.is(spring.validBlocks)));
			this.words.add(stateId(spring.state.createLegacyBlock()));
			this.words.add(BuiltInRegistries.FLUID.getId(spring.state.getType()));
		} else if (feature == Feature.RANDOM_SELECTOR && feature.getClass() == RandomSelectorFeature.class && config.getClass() == RandomFeatureConfiguration.class) {
			kind = 5;
			var random = (RandomFeatureConfiguration)config;
			this.words.add(random.features.size());
			for (WeightedPlacedFeature entry : random.features) {
				if (entry.getClass() != WeightedPlacedFeature.class) return false;
				this.words.add(Float.floatToRawIntBits(entry.chance));
				if (!this.placed(entry.feature)) return false;
			}
			if (!this.placed(random.defaultFeature)) return false;
		} else if (feature == Feature.SIMPLE_RANDOM_SELECTOR && feature.getClass() == SimpleRandomSelectorFeature.class
				&& config.getClass() == SimpleRandomFeatureConfiguration.class) {
			kind = 6;
			var simple = (SimpleRandomFeatureConfiguration)config;
			this.words.add(simple.features.size());
			for (var holder : simple.features) {
				if (!this.placed(holder)) return false;
			}
		} else if (feature == Feature.RANDOM_BOOLEAN_SELECTOR && feature.getClass() == RandomBooleanSelectorFeature.class
				&& config.getClass() == RandomBooleanFeatureConfiguration.class) {
			kind = 7;
			var choice = (RandomBooleanFeatureConfiguration)config;
			if (!this.placed(choice.featureTrue) || !this.placed(choice.featureFalse)) return false;
		} else if (feature == Feature.TREE && feature.getClass() == TreeFeature.class && config.getClass() == TreeConfiguration.class) {
			kind = 8;
			if (!this.tree((TreeConfiguration)config)) return false;
		} else if ((feature == Feature.RANDOM_PATCH || feature == Feature.FLOWER || feature == Feature.NO_BONEMEAL_FLOWER) && feature.getClass() == RandomPatchFeature.class && config.getClass() == RandomPatchConfiguration.class) {
			kind = 10;
			var patch = (RandomPatchConfiguration)config;
			this.words.addAll(List.of(patch.tries(), patch.xzSpread(), patch.ySpread()));
			if (!this.placed(patch.feature())) return false;
		} else if (feature == Feature.SIMPLE_BLOCK && feature.getClass() == SimpleBlockFeature.class && config.getClass() == SimpleBlockConfiguration.class) {
			kind = 11;
			if (!this.simpleBlock((SimpleBlockConfiguration)config)) return this.javaLeaf(configured, header);
		} else if (feature == Feature.END_SPIKE && feature.getClass() == net.minecraft.world.level.levelgen.feature.SpikeFeature.class
                && config.getClass() == net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration.class) {
            // Descriptor lists (including the seed cache's returned list) are
            // retained mutable Java authority. Resolve/iterate on each call;
            // the End executor still owns geometry and placement decisions.
            kind = 99;
            this.words.add(this.javaFeatures.size());
            this.javaFeatures.add(configured);
		} else if ((kind = vegetationKind(feature, config)) != 0) {
			if (!this.vegetation(kind, config)) return this.javaLeaf(configured, header);
		} else if (this.javaLeafAllowed) {
			kind = 99;
			this.words.add(this.javaFeatures.size());
			this.javaFeatures.add(configured);
		} else return this.decline(feature.getClass());
		this.words.set(header, kind);
		this.words.set(header + 1, this.words.size() - header - 2);
		return true;
	}

	/** The {@code vegetation.rs} kind for an exact feature instance and configuration class, or 0. */
	private static int vegetationKind(Feature<?> feature, Object config) {
		Class<?> c = config.getClass();
		if (feature == Feature.BLOCK_COLUMN && feature.getClass() == net.minecraft.world.level.levelgen.feature.BlockColumnFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.BlockColumnConfiguration.class) return 12;
		if (feature == Feature.SEAGRASS && feature.getClass() == net.minecraft.world.level.levelgen.feature.SeagrassFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration.class) return 13;
		if (feature == Feature.SEA_PICKLE && feature.getClass() == net.minecraft.world.level.levelgen.feature.SeaPickleFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration.class) return 14;
		if (feature == Feature.KELP && feature.getClass() == net.minecraft.world.level.levelgen.feature.KelpFeature.class) return 15;
		if (feature == Feature.DISK && feature.getClass() == net.minecraft.world.level.levelgen.feature.DiskFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration.class) return 16;
		if (feature == Feature.VINES && feature.getClass() == net.minecraft.world.level.levelgen.feature.VinesFeature.class) return 17;
		if (feature == Feature.FOREST_ROCK && feature.getClass() == net.minecraft.world.level.levelgen.feature.BlockBlobFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration.class) return 18;
		if (feature == Feature.UNDERWATER_MAGMA && feature.getClass() == net.minecraft.world.level.levelgen.feature.UnderwaterMagmaFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.UnderwaterMagmaConfiguration.class) return 19;
		if (feature == Feature.MONSTER_ROOM && feature.getClass() == net.minecraft.world.level.levelgen.feature.MonsterRoomFeature.class) return 22;
		if (feature == Feature.CORAL_CLAW && feature.getClass() == net.minecraft.world.level.levelgen.feature.CoralClawFeature.class) return 25;
		if (feature == Feature.POINTED_DRIPSTONE && feature.getClass() == net.minecraft.world.level.levelgen.feature.PointedDripstoneFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.PointedDripstoneConfiguration.class) return 30;
		if (feature == Feature.LARGE_DRIPSTONE && feature.getClass() == net.minecraft.world.level.levelgen.feature.LargeDripstoneFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.LargeDripstoneConfiguration.class) return 32;
		if (feature == Feature.DRIPSTONE_CLUSTER && feature.getClass() == net.minecraft.world.level.levelgen.feature.DripstoneClusterFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration.class) return 31;
		if (feature == Feature.SCULK_PATCH && feature.getClass() == net.minecraft.world.level.levelgen.feature.SculkPatchFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration.class) return 29;
		if (feature == Feature.LAKE && feature.getClass() == net.minecraft.world.level.levelgen.feature.LakeFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.LakeFeature.Configuration.class) return 28;
		if (feature == Feature.CORAL_TREE && feature.getClass() == net.minecraft.world.level.levelgen.feature.CoralTreeFeature.class) return 26;
		if (feature == Feature.CORAL_MUSHROOM && feature.getClass() == net.minecraft.world.level.levelgen.feature.CoralMushroomFeature.class) return 27;
		if (feature == Feature.DELTA_FEATURE && feature.getClass() == net.minecraft.world.level.levelgen.feature.DeltaFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration.class) return 23;
		if (feature == Feature.BASALT_COLUMNS && feature.getClass() == net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration.class) return 24;
		if (feature == Feature.GEODE && feature.getClass() == net.minecraft.world.level.levelgen.feature.GeodeFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration.class) return 21;
		if (feature == Feature.BLUE_ICE && feature.getClass() == net.minecraft.world.level.levelgen.feature.BlueIceFeature.class) return 33;
		if (feature == Feature.ICE_SPIKE && feature.getClass() == net.minecraft.world.level.levelgen.feature.IceSpikeFeature.class) return 34;
		if (feature == Feature.BAMBOO && feature.getClass() == net.minecraft.world.level.levelgen.feature.BambooFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration.class) return 35;
		if (feature == Feature.HUGE_RED_MUSHROOM && feature.getClass() == net.minecraft.world.level.levelgen.feature.HugeRedMushroomFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration.class) return 36;
		if (feature == Feature.HUGE_BROWN_MUSHROOM && feature.getClass() == net.minecraft.world.level.levelgen.feature.HugeBrownMushroomFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration.class) return 37;
		if (feature == Feature.HUGE_FUNGUS && feature.getClass() == net.minecraft.world.level.levelgen.feature.HugeFungusFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration.class) return 38;
		if (feature == Feature.CHORUS_PLANT && feature.getClass() == net.minecraft.world.level.levelgen.feature.ChorusPlantFeature.class) return 39;
		if (feature == Feature.ICEBERG && feature.getClass() == net.minecraft.world.level.levelgen.feature.IcebergFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration.class) return 41;
		if (feature == Feature.FREEZE_TOP_LAYER && feature.getClass() == net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature.class) return 42;
		if (feature == Feature.SCATTERED_ORE && feature.getClass() == net.minecraft.world.level.levelgen.feature.ScatteredOreFeature.class && c == OreConfiguration.class) return 43;
		if (feature == Feature.WEEPING_VINES && feature.getClass() == net.minecraft.world.level.levelgen.feature.WeepingVinesFeature.class) return 44;
		if (feature == Feature.TWISTING_VINES && feature.getClass() == net.minecraft.world.level.levelgen.feature.TwistingVinesFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.TwistingVinesConfig.class) return 45;
		if (feature == Feature.BASALT_PILLAR && feature.getClass() == net.minecraft.world.level.levelgen.feature.BasaltPillarFeature.class) return 46;
		if (feature == Feature.VEGETATION_PATCH && feature.getClass() == net.minecraft.world.level.levelgen.feature.VegetationPatchFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration.class) return 47;
		if (feature == Feature.WATERLOGGED_VEGETATION_PATCH && feature.getClass() == net.minecraft.world.level.levelgen.feature.WaterloggedVegetationPatchFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration.class) return 48;
		if (feature == Feature.DESERT_WELL && feature.getClass() == net.minecraft.world.level.levelgen.feature.DesertWellFeature.class) return 49;
		if (feature == Feature.END_SPIKE && feature.getClass() == net.minecraft.world.level.levelgen.feature.SpikeFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration.class) return 50;
		if (feature == Feature.END_GATEWAY && feature.getClass() == net.minecraft.world.level.levelgen.feature.EndGatewayFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration.class) return 51;
		if (feature == Feature.END_PLATFORM && feature.getClass() == net.minecraft.world.level.levelgen.feature.EndPlatformFeature.class) return 52;
		if (feature == Feature.END_ISLAND && feature.getClass() == net.minecraft.world.level.levelgen.feature.EndIslandFeature.class) return 40;
		if (feature == Feature.MULTIFACE_GROWTH && feature.getClass() == net.minecraft.world.level.levelgen.feature.MultifaceGrowthFeature.class
			&& c == net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration.class) return 20;
		return 0;
	}

	/** {@code end.rs}: End spikes, resolved for this level's seed when the configuration lists none. */
	private boolean spikes(net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration config) {
		var spikes = config.getSpikes();
		if (spikes.isEmpty()) {
			// SpikeFeature.getSpikesForLevel without a level: the same seed derivation.
			long key = net.minecraft.util.RandomSource.create(this.seed).nextLong() & 65535L;
			spikes = net.minecraft.world.level.levelgen.feature.SpikeFeature.spikesForKey(key);
		}
		this.words.add(spikes.size());
		for (var spike : spikes) {
			this.words.addAll(List.of(spike.getCenterX(), spike.getCenterZ(), spike.getRadius(), spike.getHeight(), spike.isGuarded() ? 1 : 0));
		}
		this.words.addAll(List.of(stateId(Blocks.OBSIDIAN.defaultBlockState()), stateId(Blocks.AIR.defaultBlockState())));
		for (int bits = 0; bits < 16; bits++) {
			this.words.add(stateId(Blocks.IRON_BARS.defaultBlockState()
				.setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, (bits & 1) != 0)
				.setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, (bits & 2) != 0)
				.setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, (bits & 4) != 0)
				.setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, (bits & 8) != 0)));
		}
		var beam = config.getCrystalBeamTarget();
		boolean invulnerable = config.isCrystalInvulnerable();
		this.words.add(this.javaStep((level, random, pos) -> {
			// SpikeFeature.placeSpike's crystal: pos is the crystal's block, above the spike.
			var crystal = net.minecraft.world.entity.EntityType.END_CRYSTAL.create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
			if (crystal != null) {
				crystal.setBeamTarget(beam);
				crystal.setInvulnerable(invulnerable);
				crystal.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(crystal);
				var at = crystal.blockPosition();
				level.setBlock(at.below(), Blocks.BEDROCK.defaultBlockState(), 3);
				level.setBlock(at, net.minecraft.world.level.block.FireBlock.getState(level, at), 3);
			}
		}));
		return true;
	}

	/** {@code patch.rs}: a vegetation patch, then its nested placement. */
	private boolean vegetationPatch(boolean waterlogged, net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration patch)
			throws ReflectiveOperationException {
		this.words.add(waterlogged ? 1 : 0);
		var replaceable = patch.replaceable;
		this.words.add(this.set(List.of("tag", replaceable), st -> st.is(replaceable)));
		if (!this.provider(patch.groundState)) return false;
		Direction surface = patch.surface.getDirection();
		this.words.add(surface == Direction.UP ? 1 : 0);
		if (!this.intProvider(patch.depth)) return false;
		this.words.add(Float.floatToRawIntBits(patch.extraBottomBlockChance));
		this.words.add(patch.verticalRange);
		this.words.add(Float.floatToRawIntBits(patch.vegetationChance));
		if (!this.intProvider(patch.xzRadius)) return false;
		this.words.add(Float.floatToRawIntBits(patch.extraEdgeColumnChance));
		this.sturdyNeighbour(surface);
		if (waterlogged) {
			this.words.add(stateId(Blocks.WATER.defaultBlockState()));
			for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN)) this.sturdyNeighbour(direction);
			var wet = new ArrayList<Integer>();
			for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
				if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
					&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) wet.add(stateId(state));
			}
			this.words.add(wet.size());
			this.words.addAll(wet);
		}
		return this.placed(patch.vegetationFeature);
	}

	/** A checked predicate: the block at {@code direction} is sturdy on its face back towards the position. */
	private void sturdyNeighbour(Direction direction) {
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		var zero = net.minecraft.core.BlockPos.ZERO;
		Direction face = direction.getOpposite();
		this.checked(direction.getStepX(), direction.getStepY(), direction.getStepZ(), List.of("sturdy", face), st -> st.isFaceSturdy(empty, zero, face),
			(level, pos) -> {
				var at = pos.relative(direction);
				return level.getBlockState(at).isFaceSturdy(level, at, face);
			});
	}

	/** {@code freeze.rs}: {@code SnowAndFreezeFeature} with each region biome's climate. */
	private boolean freeze() throws ReflectiveOperationException {
		BlockState snow = Blocks.SNOW.defaultBlockState();
		this.words.addAll(List.of(stateId(Blocks.ICE.defaultBlockState()), stateId(snow)));
		var snowy = new ArrayList<BlockState>();
		for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
			if (state.hasProperty(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY)) snowy.add(state);
		}
		this.words.add(snowy.size());
		for (BlockState state : snowy) {
			this.words.add(stateId(state));
			this.words.add(stateId(state.setValue(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY, true)));
		}
		this.words.add(this.set("water_in_liquid_block",
			st -> st.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock));
		this.words.add(this.set("air_or_snow", st -> st.isAir() || st.is(Blocks.SNOW)));
		if (declaring(snow.getBlock().getClass(), "canSurvive", BlockState.class, net.minecraft.world.level.LevelReader.class,
				net.minecraft.core.BlockPos.class) != net.minecraft.world.level.block.SnowLayerBlock.class) return this.decline("snow survival");
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		var zero = net.minecraft.core.BlockPos.ZERO;
		this.checked(0, -1, 0, "snow_layer_floor", st -> !st.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)
			&& (st.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON) || Block.isFaceFull(st.getCollisionShape(empty, zero), Direction.UP)
				|| st.is(Blocks.SNOW) && st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 8), snow::canSurvive);
		// Temperatures are Java's own warmEnoughToRain calls (OP_WARM): only precipitation is encoded.
		this.words.add(this.biomes.size());
		for (var holder : this.biomes) this.words.add(holder.value().climateSettings.hasPrecipitation() ? 1 : 0);
		return true;
	}

	/** Features 12–19 after {@code vegetation}: multiface growth with glow lichen's default spreader. */
	private boolean multiface(net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration config) throws ReflectiveOperationException {
		var block = config.placeBlock;
		boolean sculk = block.getClass() == net.minecraft.world.level.block.SculkVeinBlock.class;
		if (block.getClass() != net.minecraft.world.level.block.GlowLichenBlock.class && !sculk) return this.decline(block.getClass());
		var spreaderConfig = field(block.getSpreader(), net.minecraft.world.level.block.MultifaceSpreader.class, "config");
		if (sculk) {
			// The vein spreader: SculkVeinSpreaderConfig over the default spread order.
			if (!spreaderConfig.getClass().getSimpleName().equals("SculkVeinSpreaderConfig")
				|| !java.util.Arrays.equals((Object[])field(spreaderConfig, spreaderConfig.getClass(), "spreadTypes"), net.minecraft.world.level.block.MultifaceSpreader.DEFAULT_SPREAD_ORDER))
				return this.decline(spreaderConfig.getClass());
		} else if (spreaderConfig.getClass() != net.minecraft.world.level.block.MultifaceSpreader.DefaultSpreaderConfig.class) return this.decline(spreaderConfig.getClass());
		var multiface = (net.minecraft.world.level.block.MultifaceBlock)block;
		var states = multiface.getStateDefinition().getPossibleStates();
		int first = stateId(states.getFirst());
		for (int i = 0; i < states.size(); i++) {
			if (stateId(states.get(i)) != first + i) return this.decline("non-contiguous multiface states");
		}
		this.words.addAll(List.of(first, states.size()));
		for (BlockState state : states) {
			int faces = 0;
			for (Direction direction : Direction.values()) {
				if (net.minecraft.world.level.block.MultifaceBlock.hasFace(state, direction)) faces |= 1 << direction.ordinal();
			}
			this.words.add(faces);
			for (Direction direction : Direction.values()) {
				var property = net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(direction);
				this.words.add(stateId(state.hasProperty(property) ? state.setValue(property, true) : state));
			}
		}
		BlockState fallback = multiface.defaultBlockState();
		this.words.add(stateId(fallback));
		this.words.add(stateId(fallback.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)));
		var supported = net.minecraft.world.level.block.MultifaceBlock.class.getDeclaredMethod("isFaceSupported", Direction.class);
		supported.setAccessible(true);
		int mask = 0;
		for (Direction direction : Direction.values()) {
			if ((boolean)supported.invoke(multiface, direction)) mask |= 1 << direction.ordinal();
		}
		this.words.add(mask);
		Block lichen = (Block)block;
		this.words.add(this.set("water_source", st -> st.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)));
		this.words.add(this.set(List.of("multiface_replaceable", lichen),
			st -> st.isAir() || st.is(lichen) || st.is(Blocks.WATER) && st.getFluidState().isSource()));
		this.words.add(this.set("air_or_water_block", st -> st.isAir() || st.is(Blocks.WATER)));
		this.words.add(this.set(List.of("blocks", config.canBePlacedOn), st -> st.is(config.canBePlacedOn)));
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		var zero = net.minecraft.core.BlockPos.ZERO;
		for (Direction direction : Direction.values()) {
			var opposite = direction.getOpposite();
			this.checked(0, 0, 0, List.of("vine_attach", direction),
				st -> Block.isFaceFull(st.getBlockSupportShape(empty, zero), opposite) || Block.isFaceFull(st.getCollisionShape(empty, zero), opposite),
				(level, pos) -> net.minecraft.world.level.block.MultifaceBlock.canAttachTo(level, direction, pos, level.getBlockState(pos)));
		}
		this.words.add(config.searchRange);
		this.words.add(Float.floatToRawIntBits(config.chanceOfSpreading));
		@SuppressWarnings("unchecked")
		var valid = (List<Direction>)field(config, config.getClass(), "validDirections");
		this.words.add(valid.size());
		for (Direction direction : valid) this.words.add(direction.ordinal());
		this.words.add(sculk ? 1 : 0);
		if (sculk) {
			this.words.add(this.set("sculk_spread_blocked_behind", st -> st.is(Blocks.SCULK) || st.is(Blocks.SCULK_CATALYST) || st.is(Blocks.MOVING_PISTON)));
			this.words.add(this.set("fluid_empty", st -> st.getFluidState().isEmpty()));
			this.words.add(this.set("fluid_still_water", st -> st.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)));
			this.words.add(this.set(List.of("tag", BlockTags.FIRE), st -> st.is(BlockTags.FIRE)));
			this.words.add(this.set("can_be_replaced", BlockState::canBeReplaced));
			for (Direction direction : Direction.values()) {
				this.checked(0, 0, 0, List.of("sturdy", direction), st -> st.isFaceSturdy(empty, zero, direction),
					(level, pos) -> level.getBlockState(pos).isFaceSturdy(level, pos, direction));
			}
		}
		return true;
	}

	private void doubleWords(double value) {
		long bits = Double.doubleToRawLongBits(value);
		this.words.add((int)bits);
		this.words.add((int)(bits >>> 32));
	}

	private static final org.slf4j.Logger LOGGER = net.logging.LogUtils.getLogger();

	/** {@code rooms.rs}'s words: Java steps set the chest's loot table and the spawner's mob. */
	private boolean monsterRoom() throws ReflectiveOperationException {
		this.words.add(this.set("solid", BlockState::isSolid));
		this.words.add(this.set(List.of("block", Blocks.CHEST), st -> st.is(Blocks.CHEST)));
		this.words.add(this.set(List.of("block", Blocks.SPAWNER), st -> st.is(Blocks.SPAWNER)));
		this.words.add(this.set(List.of("not_tag", BlockTags.FEATURES_CANNOT_REPLACE), st -> !st.is(BlockTags.FEATURES_CANNOT_REPLACE)));
		this.words.add(this.set("solid_render", BlockState::isSolidRender));
		this.words.add(stateId(Blocks.CAVE_AIR.defaultBlockState()));
		this.words.add(stateId(Blocks.MOSSY_COBBLESTONE.defaultBlockState()));
		this.words.add(stateId(Blocks.COBBLESTONE.defaultBlockState()));
		this.words.add(stateId(Blocks.SPAWNER.defaultBlockState()));
		for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
			this.words.add(stateId(Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, direction)));
		}
		var randomEntityId = net.minecraft.world.level.levelgen.feature.MonsterRoomFeature.class.getDeclaredMethod("randomEntityId", net.minecraft.util.RandomSource.class);
		randomEntityId.setAccessible(true);
		var feature = Feature.MONSTER_ROOM;
		this.words.add(this.javaStep((level, random, pos) -> net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, pos,
			net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON)));
		this.words.add(this.javaStep((level, random, pos) -> {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
				try {
					spawner.setEntityId((net.minecraft.world.entity.EntityType<?>)randomEntityId.invoke(feature, random), random);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			} else {
				LOGGER.error("Failed to fetch mob spawner entity at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
			}
		}));
		return true;
	}

	/** Every state a simple or weighted provider can supply, or null after declining. */
	@Nullable
	private List<BlockState> providerStates(BlockStateProvider provider) throws ReflectiveOperationException {
		if (provider.getClass() == SimpleStateProvider.class) return List.of(provider.getState(null, null));
		if (provider.getClass() == WeightedStateProvider.class) {
			@SuppressWarnings("unchecked")
			var list = (net.minecraft.util.random.WeightedList<BlockState>)field(provider, WeightedStateProvider.class, "weightedList");
			return list.unwrap().stream().map(Weighted::value).toList();
		}
		this.decline(provider.getClass());
		return null;
	}

	/** A block tag's contents in tag order, as {@code getRandomElementOf} draws them; empty when absent. */
	private static List<Block> tagBlocks(net.minecraft.tags.TagKey<Block> tag) {
		return BuiltInRegistries.BLOCK.get(tag).map(named -> named.stream().map(Holder::value).toList()).orElse(List.of());
	}

	private int javaStep(JavaStep step) {
		this.javaSteps.add(step);
		return this.javaSteps.size() - 1;
	}

	/** {@code dripstone.rs}'s shared states and sets. */
	private void dripstoneBlocks() {
		var tip = net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION;
		var thickness = net.minecraft.world.level.block.PointedDripstoneBlock.THICKNESS;
		var wet = net.minecraft.world.level.block.PointedDripstoneBlock.WATERLOGGED;
		var kinds = net.minecraft.world.level.block.state.properties.DripstoneThickness.class;
		for (Direction direction : List.of(Direction.DOWN, Direction.UP)) {
			for (String name : List.of("TIP_MERGE", "TIP", "FRUSTUM", "MIDDLE", "BASE")) {
				for (boolean waterlogged : new boolean[] {false, true}) {
					this.words.add(stateId(Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(tip, direction)
						.setValue(thickness, Enum.valueOf(kinds, name)).setValue(wet, waterlogged)));
				}
			}
		}
		this.words.add(stateId(Blocks.DRIPSTONE_BLOCK.defaultBlockState()));
		this.words.add(stateId(Blocks.WATER.defaultBlockState()));
		this.words.add(this.set("dripstone_base", st -> st.is(Blocks.DRIPSTONE_BLOCK) || st.is(BlockTags.DRIPSTONE_REPLACEABLE)));
		this.words.add(this.set(List.of("tag", BlockTags.DRIPSTONE_REPLACEABLE), st -> st.is(BlockTags.DRIPSTONE_REPLACEABLE)));
		this.words.add(this.set("air_or_water_block", st -> st.isAir() || st.is(Blocks.WATER)));
		this.words.add(this.set(List.of("block", Blocks.LAVA), st -> st.is(Blocks.LAVA)));
		this.words.add(this.set("water_fluid", st -> st.getFluidState().is(FluidTags.WATER)));
		this.words.add(this.set("dripstone_pool_excluded", st -> st.is(Blocks.WATER) || st.is(Blocks.DRIPSTONE_BLOCK) || st.is(Blocks.POINTED_DRIPSTONE)));
		this.words.add(this.set("dripstone_adjacent_to_water", st -> st.is(BlockTags.BASE_STONE_OVERWORLD) || st.getFluidState().is(FluidTags.WATER)));
	}

	/** {@code program.rs}'s float provider words. */
	private boolean floatProvider(net.minecraft.util.valueproviders.FloatProvider provider) throws ReflectiveOperationException {
		Class<?> type = provider.getClass();
		if (type == net.minecraft.util.valueproviders.ConstantFloat.class) {
			this.words.addAll(List.of(1, Float.floatToRawIntBits(provider.sample(null))));
		} else if (type == net.minecraft.util.valueproviders.UniformFloat.class) {
			this.words.addAll(List.of(2, Float.floatToRawIntBits((float)field(provider, type, "minInclusive")), Float.floatToRawIntBits((float)field(provider, type, "maxExclusive"))));
		} else if (type == net.minecraft.util.valueproviders.ClampedNormalFloat.class) {
			this.words.addAll(List.of(3, Float.floatToRawIntBits((float)field(provider, type, "mean")), Float.floatToRawIntBits((float)field(provider, type, "deviation")),
				Float.floatToRawIntBits((float)field(provider, type, "min")), Float.floatToRawIntBits((float)field(provider, type, "max"))));
		} else if (type == net.minecraft.util.valueproviders.TrapezoidFloat.class) {
			this.words.addAll(List.of(4, Float.floatToRawIntBits((float)field(provider, type, "min")), Float.floatToRawIntBits((float)field(provider, type, "max")),
				Float.floatToRawIntBits((float)field(provider, type, "plateau"))));
		} else return this.decline(type);
		return true;
	}

	/** {@code sculk.rs}'s words: the world-generation spreader over vanilla sculk blocks. */
	private boolean sculk(net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration config) throws ReflectiveOperationException {
		Block vein = Blocks.SCULK_VEIN;
		if (vein.getClass() != net.minecraft.world.level.block.SculkVeinBlock.class || Blocks.SCULK.getClass() != net.minecraft.world.level.block.SculkBlock.class)
			return this.decline("sculk block classes");
		this.words.addAll(List.of(config.spreadRounds(), config.growthRounds(), config.chargeCount(), config.amountPerCharge(), config.spreadAttempts()));
		if (!this.intProvider(config.extraRareGrowths())) return false;
		this.words.add(Float.floatToRawIntBits(config.catalystChance()));
		var states = vein.getStateDefinition().getPossibleStates();
		int first = stateId(states.getFirst());
		for (int i = 0; i < states.size(); i++) {
			if (stateId(states.get(i)) != first + i) return this.decline("non-contiguous vein states");
		}
		var waterlogged = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
		this.words.addAll(List.of(first, states.size()));
		for (BlockState state : states) {
			int faces = 0;
			for (Direction direction : Direction.values()) {
				if (net.minecraft.world.level.block.MultifaceBlock.hasFace(state, direction)) faces |= 1 << direction.ordinal();
			}
			this.words.add(faces);
			for (Direction direction : Direction.values()) {
				var property = net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(direction);
				this.words.add(stateId(state.setValue(property, true)));
				this.words.add(stateId(state.setValue(property, false)));
			}
			this.words.add(stateId(state.setValue(waterlogged, true)));
		}
		this.words.add(stateId(vein.defaultBlockState()));
		this.words.add(stateId(vein.defaultBlockState().setValue(waterlogged, true)));
		BlockState shrieker = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(net.minecraft.world.level.block.SculkShriekerBlock.CAN_SUMMON, true);
		BlockState sensor = Blocks.SCULK_SENSOR.defaultBlockState();
		this.words.add(stateId(Blocks.SCULK.defaultBlockState()));
		this.words.add(stateId(Blocks.SCULK_CATALYST.defaultBlockState()));
		this.words.add(stateId(shrieker));
		this.words.add(stateId(shrieker.hasProperty(waterlogged) ? shrieker.setValue(waterlogged, true) : shrieker));
		this.words.add(stateId(sensor));
		this.words.add(stateId(sensor.hasProperty(waterlogged) ? sensor.setValue(waterlogged, true) : sensor));
		this.words.add(stateId(Blocks.AIR.defaultBlockState()));
		this.words.add(stateId(Blocks.WATER.defaultBlockState()));
		this.words.add(this.set(List.of("block", Blocks.SCULK), st -> st.is(Blocks.SCULK)));
		this.words.add(this.set(List.of("block", Blocks.SCULK_CATALYST), st -> st.is(Blocks.SCULK_CATALYST)));
		this.words.add(this.set(List.of("block", Blocks.MOVING_PISTON), st -> st.is(Blocks.MOVING_PISTON)));
		this.words.add(this.set(List.of("block", Blocks.SCULK_SENSOR), st -> st.is(Blocks.SCULK_SENSOR)));
		this.words.add(this.set(List.of("block", Blocks.SCULK_SHRIEKER), st -> st.is(Blocks.SCULK_SHRIEKER)));
		this.words.add(this.set(List.of("block", Blocks.WATER), st -> st.is(Blocks.WATER)));
		this.words.add(this.set(List.of("tag", BlockTags.FIRE), st -> st.is(BlockTags.FIRE)));
		this.words.add(this.set(List.of("tag", BlockTags.SCULK_REPLACEABLE_WORLD_GEN), st -> st.is(BlockTags.SCULK_REPLACEABLE_WORLD_GEN)));
		this.words.add(this.set(List.of("tag", BlockTags.SCULK_REPLACEABLE), st -> st.is(BlockTags.SCULK_REPLACEABLE)));
		this.words.add(this.set("fluid_empty", st -> st.getFluidState().isEmpty()));
		this.words.add(this.set("fluid_still_water", st -> st.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)));
		this.words.add(this.set("fluid_source", st -> st.getFluidState().isSource()));
		this.words.add(this.set("water_source", st -> st.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)));
		this.words.add(this.set("can_be_replaced", BlockState::canBeReplaced));
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		var zero = net.minecraft.core.BlockPos.ZERO;
		for (Direction direction : Direction.values()) {
			this.checked(0, 0, 0, List.of("sturdy", direction), st -> st.isFaceSturdy(empty, zero, direction),
				(level, pos) -> level.getBlockState(pos).isFaceSturdy(level, pos, direction));
		}
		for (Direction direction : Direction.values()) {
			var opposite = direction.getOpposite();
			this.checked(0, 0, 0, List.of("vine_attach", direction),
				st -> Block.isFaceFull(st.getBlockSupportShape(empty, zero), opposite) || Block.isFaceFull(st.getCollisionShape(empty, zero), opposite),
				(level, pos) -> net.minecraft.world.level.block.MultifaceBlock.canAttachTo(level, direction, pos, level.getBlockState(pos)));
		}
		this.checked(0, 0, 0, "collision_full", st -> st.isCollisionShapeFullBlock(empty, zero),
			(level, pos) -> level.getBlockState(pos).isCollisionShapeFullBlock(level, pos));
		return true;
	}

	/** {@code geode.rs}'s words. */
	private boolean geode(net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration config) throws ReflectiveOperationException {
		var blocks = config.geodeBlockSettings;
		var layers = config.geodeLayerSettings;
		var crack = config.geodeCrackSettings;
		if (blocks.getClass() != net.minecraft.world.level.levelgen.GeodeBlockSettings.class || layers.getClass() != net.minecraft.world.level.levelgen.GeodeLayerSettings.class
			|| crack.getClass() != net.minecraft.world.level.levelgen.GeodeCrackSettings.class) return this.decline("geode settings subclass");
		if (config.minGenOffset > config.maxGenOffset || config.minGenOffset < -64 || config.maxGenOffset > 64) return this.decline("geode offsets");
		if (blocks.innerPlacements.isEmpty() || blocks.innerPlacements.size() > 64) return this.decline("geode placements");
		this.words.addAll(List.of(config.minGenOffset, config.maxGenOffset));
		if (!this.intProvider(config.distributionPoints) || !this.intProvider(config.outerWallDistance)) return false;
		this.words.add(config.outerWallDistance.getMaxValue());
		if (!this.intProvider(config.pointOffset)) return false;
		this.words.add(config.invalidBlocksThreshold);
		var invalid = blocks.invalidBlocks;
		var cannotReplace = blocks.cannotReplace;
		this.words.add(this.set(List.of("air_or_tag", invalid), st -> st.isAir() || st.is(invalid)));
		this.words.add(this.set(List.of("not_tag", cannotReplace), st -> !st.is(cannotReplace)));
		this.doubleWords(layers.filling);
		this.doubleWords(layers.innerLayer);
		this.doubleWords(layers.middleLayer);
		this.doubleWords(layers.outerLayer);
		this.doubleWords(crack.generateCrackChance);
		this.doubleWords(crack.baseCrackSize);
		this.words.add(crack.crackPointOffset);
		var noise = GEODE_NOISES.computeIfAbsent(this.seed, s -> net.minecraft.world.level.levelgen.synth.NormalNoise.create(
			new WorldgenRandom(new LegacyRandomSource(s)), -4, 1.0));
		this.words.add(this.noise(noise, noise.nativeState().state()));
		this.doubleWords(config.noiseMultiplier);
		for (var provider : List.of(blocks.fillingProvider, blocks.innerLayerProvider, blocks.alternateInnerLayerProvider, blocks.middleLayerProvider, blocks.outerLayerProvider)) {
			if (!this.provider(provider)) return false;
		}
		this.doubleWords(config.useAlternateLayer0Chance);
		this.doubleWords(config.usePotentialPlacementsChance);
		this.words.add(config.placementsRequireLayer0Alternate ? 1 : 0);
		this.words.add(blocks.innerPlacements.size());
		var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
		var waterlogged = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
		for (BlockState placed : blocks.innerPlacements) {
			for (Direction direction : Direction.values()) {
				BlockState faced = placed.hasProperty(facing) ? placed.setValue(facing, direction) : placed;
				for (boolean wet : new boolean[] {false, true}) {
					this.words.add(stateId(faced.hasProperty(waterlogged) ? faced.setValue(waterlogged, wet) : faced));
				}
			}
		}
		this.words.add(this.set("cluster_can_grow", net.minecraft.world.level.block.BuddingAmethystBlock::canClusterGrowAtState));
		this.words.add(this.set("fluid_source", st -> st.getFluidState().isSource()));
		var fluids = new ArrayList<net.minecraft.world.level.material.Fluid>();
		for (var fluid : BuiltInRegistries.FLUID) {
			if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) fluids.add(fluid);
		}
		this.words.add(fluids.size());
		for (var fluid : fluids) {
			this.words.add(BuiltInRegistries.FLUID.getId(fluid));
			this.words.add(this.set(List.of("fluid_type", fluid), st -> st.getFluidState().getType() == fluid));
		}
		this.words.add(stateId(Blocks.AIR.defaultBlockState()));
		return true;
	}

	/** Falls back to a Java leaf for a feature whose configuration Rust cannot run. */
	private boolean javaLeaf(ConfiguredFeature<?, ?> configured, int header) {
		if (!this.javaLeafAllowed) return false;
		while (this.words.size() > header + 2) this.words.removeLast();
		this.words.add(this.javaFeatures.size());
		this.javaFeatures.add(configured);
		this.words.set(header, 99);
		this.words.set(header + 1, 1);
		return true;
	}

	/** Vegetation whose {@code mayPlaceOn} reads only the state below. */
	private static final java.util.Set<String> PURE_MAY_PLACE_ON = java.util.Set.of(
		"VegetationBlock", "AzaleaBlock", "DryVegetationBlock", "FungusBlock", "RootsBlock", "NetherSproutsBlock",
		"WitherRoseBlock", "NetherWartBlock", "CropBlock", "AttachedStemBlock", "StemBlock");

	private static Class<?> declaring(Class<?> type, String name, Class<?>... parameters) {
		for (Class<?> at = type; at != null; at = at.getSuperclass()) {
			try {
				at.getDeclaredMethod(name, parameters);
				return at;
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	/** {@code program.rs}'s simple-block words: provider, tick flag, water set, then each state's survival rule. */
	private boolean simpleBlock(SimpleBlockConfiguration config) throws ReflectiveOperationException {
		List<BlockState> states = new ArrayList<>();
		if (config.toPlace().getClass() == SimpleStateProvider.class) {
			states.add(config.toPlace().getState(null, null));
		} else if (config.toPlace().getClass() == WeightedStateProvider.class) {
			@SuppressWarnings("unchecked")
			var list = (net.minecraft.util.random.WeightedList<BlockState>)field(config.toPlace(), WeightedStateProvider.class, "weightedList");
			for (Weighted<BlockState> entry : list.unwrap()) {
				if (!states.contains(entry.value())) states.add(entry.value());
			}
		} else return this.decline(config.toPlace().getClass());
		// Rules are written after the provider, so encode them into a side list first.
		var outer = new ArrayList<>(this.words);
		this.words.clear();
		try {
			for (BlockState state : states) {
				Block block = state.getBlock();
				if (block instanceof MossyCarpetBlock) return this.decline(block.getClass());
				this.words.add(stateId(state));
				if (block instanceof DoublePlantBlock) {
					// DoublePlantBlock.placeAt writes both halves; the lower half must be the one placed.
					if (state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) return this.decline("upper half");
					this.words.add(2);
					this.survival(state);
					this.words.addAll(List.of(stateId(state), stateId(state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))));
				} else {
					this.words.add(3);
					this.survival(state);
				}
			}
		} finally {
			var rules = new ArrayList<>(this.words);
			this.words.clear();
			this.words.addAll(outer);
			this.ruleWords = rules;
		}
		List<Integer> rules = this.ruleWords;
		if (!this.provider(config.toPlace())) return false;
		this.words.add(config.scheduleTick() ? 1 : 0);
		this.words.add(this.set("water_fluid", state -> state.getFluidState().is(FluidTags.WATER)));
		this.words.add(states.size());
		this.words.addAll(rules);
		return true;
	}

	private boolean ore(OreConfiguration ore) {
		if (ore.size < 0 || ore.size > 64 || ore.targetStates.size() > 64) return false;
		this.words.add(ore.size);
		this.words.add(Float.floatToRawIntBits(ore.discardChanceOnAirExposure));
		this.words.add(ore.targetStates.size());
		for (var target : ore.targetStates) {
			int state = stateId(target.state);
			RuleTest rule = target.target;
			float probability;
			int set;
			if (rule.getClass() == TagMatchTest.class || rule.getClass() == BlockMatchTest.class
					|| rule.getClass() == BlockStateMatchTest.class || rule.getClass() == AlwaysTrueTest.class) {
				probability = Float.NaN;
				set = this.set(List.of("rule", rule), candidate -> rule.test(candidate, null));
			} else if (rule.getClass() == RandomBlockMatchTest.class) {
				var random = (RandomBlockMatchTest)rule;
				probability = random.probability();
				set = this.set(List.of("block", random.block()), candidate -> candidate.is(random.block()));
			} else if (rule.getClass() == RandomBlockStateMatchTest.class) {
				var random = (RandomBlockStateMatchTest)rule;
				probability = random.probability();
				set = this.set(List.of("state", random.blockState()), candidate -> candidate == random.blockState());
			} else return false;
			this.words.add(Float.isNaN(probability) ? 1 : 2);
			this.words.add(Float.isNaN(probability) ? 0 : Float.floatToRawIntBits(probability));
			this.words.add(set);
			this.words.add(state);
		}
		return true;
	}

	/** {@code program.rs}'s provider words: (kind, a, b), or a weighted list of providers. */
	private boolean intProvider(IntProvider provider) throws ReflectiveOperationException {
		if (provider.getClass() == ConstantInt.class) {
			this.words.addAll(List.of(1, ((ConstantInt)provider).getValue(), 0));
		} else if (provider.getClass() == UniformInt.class) {
			this.words.addAll(List.of(2, provider.getMinValue(), provider.getMaxValue()));
		} else if (provider.getClass() == BiasedToBottomInt.class) {
			this.words.addAll(List.of(3, provider.getMinValue(), provider.getMaxValue()));
		} else if (provider.getClass() == net.minecraft.util.valueproviders.ClampedNormalInt.class) {
			var type = provider.getClass();
			this.words.addAll(List.of(6, Float.floatToRawIntBits((float)field(provider, type, "mean")), Float.floatToRawIntBits((float)field(provider, type, "deviation")),
				(int)field(provider, type, "minInclusive"), (int)field(provider, type, "maxInclusive")));
			return true;
		} else if (provider.getClass() == net.minecraft.util.valueproviders.ClampedInt.class) {
			this.words.addAll(List.of(5, (int)field(provider, provider.getClass(), "minInclusive"), (int)field(provider, provider.getClass(), "maxInclusive")));
			return this.intProvider((IntProvider)field(provider, provider.getClass(), "source"));
		} else if (provider.getClass() == net.minecraft.util.valueproviders.WeightedListInt.class) {
			@SuppressWarnings("unchecked")
			var list = (net.minecraft.util.random.WeightedList<IntProvider>)field(provider, provider.getClass(), "distribution");
			var entries = list.unwrap();
			this.words.addAll(List.of(4, entries.size()));
			for (Weighted<IntProvider> entry : entries) {
				this.words.add(entry.weight());
				if (!this.intProvider(entry.value())) return false;
			}
		} else return this.decline(provider.getClass());
		return true;
	}

	/** {@code tree/mod.rs}'s provider words: simple or weighted entries. */
	private boolean provider(BlockStateProvider provider) throws ReflectiveOperationException {
		if (provider.getClass() == SimpleStateProvider.class) {
			this.words.addAll(List.of(1, 1, 1, stateId(provider.getState(null, null))));
			return true;
		}
		if (provider.getClass() == net.minecraft.world.level.levelgen.feature.stateproviders.RandomizedIntStateProvider.class) {
			var type = provider.getClass();
			var source = (BlockStateProvider)field(provider, type, "source");
			String name = (String)field(provider, type, "propertyName");
			var values = (IntProvider)field(provider, type, "values");
			var sources = this.providerStates(source);
			if (sources == null || sources.size() > 256) return false;
			this.words.add(3);
			if (!this.intProvider(values) || !this.provider(source)) return false;
			this.words.add(sources.size());
			for (BlockState state : sources) {
				var property = state.getProperties().stream().filter(p -> p.getName().equals(name))
					.filter(p -> p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty)
					.map(p -> (net.minecraft.world.level.block.state.properties.IntegerProperty)p).findAny().orElse(null);
				this.words.add(stateId(state));
				if (property == null) {
					this.words.addAll(List.of(0, 0));
					continue;
				}
				var possible = property.getPossibleValues().stream().sorted().toList();
				int min = possible.getFirst(), max = possible.getLast();
				// setValue throws for a value outside the property: such providers stay in Java.
				if (values.getMinValue() < min || values.getMaxValue() > max || max - min + 1 != possible.size() || possible.size() > 256) return this.decline("randomized int range");
				this.words.addAll(List.of(min, possible.size()));
				for (int value = min; value <= max; value++) this.words.add(stateId(state.setValue(property, value)));
			}
			return true;
		}
		if (provider.getClass() == WeightedStateProvider.class) {
			@SuppressWarnings("unchecked")
			var list = (net.minecraft.util.random.WeightedList<BlockState>)field(provider, WeightedStateProvider.class, "weightedList");
			var entries = list.unwrap();
			this.words.addAll(List.of(2, entries.size()));
			for (Weighted<BlockState> entry : entries) {
				this.words.add(entry.weight());
				this.words.add(stateId(entry.value()));
			}
			return true;
		}
		return this.decline(provider.getClass());
	}

	private boolean tree(TreeConfiguration tree) throws ReflectiveOperationException {
		if (!this.provider(tree.trunkProvider) || !this.provider(tree.foliageProvider) || !this.provider(tree.dirtProvider)) return false;
		if (!this.trunk(tree.trunkPlacer) || !this.foliage(tree.foliagePlacer) || !this.size(tree.minimumSize)) return false;
		if (tree.rootPlacer.isEmpty()) {
			this.words.add(0);
		} else {
			var placer = tree.rootPlacer.get();
			if (placer.getClass() != MangroveRootPlacer.class) return false;
			this.words.add(1);
			Class<?> base = placer.getClass().getSuperclass();
			if (!this.intProvider((IntProvider)field(placer, base, "trunkOffsetY")) || !this.provider((BlockStateProvider)field(placer, base, "rootProvider"))) return false;
			@SuppressWarnings("unchecked")
			var above = (java.util.Optional<net.minecraft.world.level.levelgen.feature.rootplacers.AboveRootPlacement>)field(placer, base, "aboveRootPlacement");
			this.words.add(above.isPresent() ? 1 : 0);
			if (above.isPresent()) {
				if (!this.provider(above.get().aboveRootProvider())) return false;
				this.words.add(Float.floatToRawIntBits(above.get().aboveRootPlacementChance()));
			}
			var mangrove = (MangroveRootPlacement)field(placer, MangroveRootPlacer.class, "mangroveRootPlacement");
			this.words.add(this.set(List.of("blocks", mangrove.canGrowThrough()), state -> state.is(mangrove.canGrowThrough())));
			this.words.add(this.set(List.of("blocks", mangrove.muddyRootsIn()), state -> state.is(mangrove.muddyRootsIn())));
			if (!this.provider(mangrove.muddyRootsProvider())) return false;
			this.words.add(mangrove.maxRootWidth());
			this.words.add(mangrove.maxRootLength());
			this.words.add(Float.floatToRawIntBits(mangrove.randomSkewChance()));
		}
		this.words.add(tree.decorators.size());
		for (var decorator : tree.decorators) {
			if (!this.decorator(decorator)) return false;
		}
		this.words.add(tree.ignoreVines ? 1 : 0);
		this.words.add(tree.forceDirt ? 1 : 0);
		this.words.add(this.set("valid_tree", state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES)));
		this.words.add(this.set(List.of("tag", BlockTags.LOGS), state -> state.is(BlockTags.LOGS)));
		this.words.add(this.set(List.of("tag", BlockTags.LEAVES), state -> state.is(BlockTags.LEAVES)));
		this.words.add(this.set(List.of("block", Blocks.VINE), state -> state.is(Blocks.VINE)));
		this.words.add(this.set("plain_dirt", state -> Feature.isDirt(state) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.MYCELIUM)));
		this.words.add(this.set(List.of("tag", BlockTags.DIRT), Feature::isDirt));
		this.words.add(this.set("water_source", state -> state.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)));
		this.words.add(this.set("water_fluid", state -> state.getFluidState().is(FluidTags.WATER)));
		var vine = Blocks.VINE.defaultBlockState();
		this.words.add(stateId(vine.setValue(VineBlock.EAST, true)));
		this.words.add(stateId(vine.setValue(VineBlock.WEST, true)));
		this.words.add(stateId(vine.setValue(VineBlock.SOUTH, true)));
		this.words.add(stateId(vine.setValue(VineBlock.NORTH, true)));
		return true;
	}

	private boolean trunk(TrunkPlacer placer) throws ReflectiveOperationException {
		Class<?> type = placer.getClass();
		int kind;
		if (type == StraightTrunkPlacer.class) kind = 1;
		else if (type == ForkingTrunkPlacer.class) kind = 2;
		else if (type == FancyTrunkPlacer.class) kind = 3;
		else if (type == GiantTrunkPlacer.class) kind = 4;
		else if (type == MegaJungleTrunkPlacer.class) kind = 5;
		else if (type == DarkOakTrunkPlacer.class) kind = 6;
		else if (type == BendingTrunkPlacer.class) kind = 7;
		else if (type == CherryTrunkPlacer.class) kind = 8;
		else if (type == UpwardsBranchingTrunkPlacer.class) kind = 9;
		else if (type == OffshootTrunkPlacer.class) kind = 10;
		else return this.decline(type);
		this.words.add(kind);
		this.words.add((int)field(placer, TrunkPlacer.class, "baseHeight"));
		this.words.add((int)field(placer, TrunkPlacer.class, "heightRandA"));
		this.words.add((int)field(placer, TrunkPlacer.class, "heightRandB"));
		switch (kind) {
			case 7 -> {
				this.words.add((int)field(placer, type, "minHeightForLeaves"));
				return this.intProvider((IntProvider)field(placer, type, "bendLength"));
			}
			case 8 -> {
				var start = (UniformInt)field(placer, type, "branchStartOffsetFromTop");
				if (!this.intProvider((IntProvider)field(placer, type, "branchCount"))
						|| !this.intProvider((IntProvider)field(placer, type, "branchHorizontalLength"))) return false;
				this.words.add(start.getMinValue());
				this.words.add(start.getMaxValue());
				return this.intProvider((IntProvider)field(placer, type, "branchEndOffsetFromTop"));
			}
			case 9 -> {
				if (!this.intProvider((IntProvider)field(placer, type, "extraBranchSteps"))) return false;
				this.words.add(Float.floatToRawIntBits((float)field(placer, type, "placeBranchPerLogProbability")));
				if (!this.intProvider((IntProvider)field(placer, type, "extraBranchLength"))) return false;
				@SuppressWarnings("unchecked")
				var through = (HolderSet<Block>)field(placer, type, "canGrowThrough");
				this.words.add(this.set(List.of("blocks", through), state -> state.is(through)));
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	private boolean foliage(FoliagePlacer placer) throws ReflectiveOperationException {
		Class<?> type = placer.getClass();
		int kind;
		if (type == BlobFoliagePlacer.class) kind = 1;
		else if (type == FancyFoliagePlacer.class) kind = 2;
		else if (type == BushFoliagePlacer.class) kind = 3;
		else if (type == SpruceFoliagePlacer.class) kind = 4;
		else if (type == PineFoliagePlacer.class) kind = 5;
		else if (type == AcaciaFoliagePlacer.class) kind = 6;
		else if (type == MegaJungleFoliagePlacer.class) kind = 7;
		else if (type == MegaPineFoliagePlacer.class) kind = 8;
		else if (type == DarkOakFoliagePlacer.class) kind = 9;
		else if (type == RandomSpreadFoliagePlacer.class) kind = 10;
		else if (type == CherryFoliagePlacer.class) kind = 11;
		else return this.decline(type);
		this.words.add(kind);
		if (!this.intProvider((IntProvider)field(placer, FoliagePlacer.class, "radius"))
				|| !this.intProvider((IntProvider)field(placer, FoliagePlacer.class, "offset"))) return false;
		switch (kind) {
			case 1, 2, 3 -> this.words.add((int)field(placer, BlobFoliagePlacer.class, "height"));
			case 4 -> {
				return this.intProvider((IntProvider)field(placer, type, "trunkHeight"));
			}
			case 5 -> {
				return this.intProvider((IntProvider)field(placer, type, "height"));
			}
			case 7 -> this.words.add((int)field(placer, type, "height"));
			case 8 -> {
				return this.intProvider((IntProvider)field(placer, type, "crownHeight"));
			}
			case 10 -> {
				if (!this.intProvider((IntProvider)field(placer, type, "foliageHeight"))) return false;
				this.words.add((int)field(placer, type, "leafPlacementAttempts"));
			}
			case 11 -> {
				if (!this.intProvider((IntProvider)field(placer, type, "height"))) return false;
				for (String name : new String[]{"wideBottomLayerHoleChance", "cornerHoleChance", "hangingLeavesChance", "hangingLeavesExtensionChance"}) {
					this.words.add(Float.floatToRawIntBits((float)field(placer, type, name)));
				}
			}
			default -> {
			}
		}
		return true;
	}

	private boolean size(net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize size) throws ReflectiveOperationException {
		var clipped = size.minClippedHeight();
		int min = clipped.isPresent() ? clipped.getAsInt() : -1;
		if (size.getClass() == TwoLayersFeatureSize.class) {
			this.words.addAll(List.of(1, min, (int)field(size, TwoLayersFeatureSize.class, "limit"), (int)field(size, TwoLayersFeatureSize.class, "lowerSize"),
				(int)field(size, TwoLayersFeatureSize.class, "upperSize"), 0, 0));
			return true;
		}
		if (size.getClass() == ThreeLayersFeatureSize.class) {
			Class<?> type = ThreeLayersFeatureSize.class;
			this.words.addAll(List.of(2, min, (int)field(size, type, "limit"), (int)field(size, type, "upperLimit"), (int)field(size, type, "lowerSize"),
				(int)field(size, type, "middleSize"), (int)field(size, type, "upperSize")));
			return true;
		}
		return false;
	}

	private boolean decorator(TreeDecorator decorator) throws ReflectiveOperationException {
		Class<?> type = decorator.getClass();
		int header = this.words.size();
		this.words.add(0);
		this.words.add(0);
		int kind;
		if (type == TrunkVineDecorator.class) {
			kind = 1;
		} else if (type == LeaveVineDecorator.class) {
			kind = 2;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "probability")));
		} else if (type == CocoaDecorator.class) {
			kind = 3;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "probability")));
			for (int age = 0; age < 3; age++) {
				for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
					this.words.add(stateId(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, age).setValue(CocoaBlock.FACING, facing)));
				}
			}
		} else if (type == AlterGroundDecorator.class) {
			kind = 4;
			if (!this.provider((BlockStateProvider)field(decorator, type, "provider"))) return false;
		} else if (type == AttachedToLeavesDecorator.class) {
			kind = 5;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "probability")));
			this.words.add((int)field(decorator, type, "exclusionRadiusXZ"));
			this.words.add((int)field(decorator, type, "exclusionRadiusY"));
			if (!this.provider((BlockStateProvider)field(decorator, type, "blockProvider"))) return false;
			this.words.add((int)field(decorator, type, "requiredEmptyBlocks"));
			@SuppressWarnings("unchecked")
			var directions = (List<Direction>)field(decorator, type, "directions");
			this.words.add(directions.size());
			for (Direction direction : directions) this.words.add(direction.ordinal());
		} else if (type == PlaceOnGroundDecorator.class) {
			kind = 6;
			this.words.add((int)field(decorator, type, "tries"));
			this.words.add((int)field(decorator, type, "radius"));
			this.words.add((int)field(decorator, type, "height"));
			if (!this.provider((BlockStateProvider)field(decorator, type, "blockStateProvider"))) return false;
		} else if (type == CreakingHeartDecorator.class) {
			kind = 7;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "probability")));
			this.words.add(stateId(Blocks.CREAKING_HEART.defaultBlockState().setValue(CreakingHeartBlock.STATE, CreakingHeartState.DORMANT)
				.setValue(CreakingHeartBlock.NATURAL, true)));
		} else if (type == BeehiveDecorator.class) {
			kind = 8;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "probability")));
			this.words.add(stateId(Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.SOUTH)));
		} else if (type == PaleMossDecorator.class) {
			kind = 9;
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "leavesProbability")));
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "trunkProbability")));
			this.words.add(Float.floatToRawIntBits((float)field(decorator, type, "groundProbability")));
			var tip = net.minecraft.world.level.block.HangingMossBlock.TIP;
			this.words.add(stateId(Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(tip, false)));
			this.words.add(stateId(Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(tip, true)));
			// The registry's pale moss patch at the lowest log's top, as PaleMossDecorator places it.
			this.words.add(this.javaStep((level, random, pos) -> level.registryAccess()
				.lookup(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
				.flatMap(registry -> registry.get(net.minecraft.data.worldgen.features.VegetationFeatures.PALE_MOSS_PATCH))
				.ifPresent(reference -> ((ConfiguredFeature<?, ?>)reference.value()).place(level, level.getLevel().getChunkSource().getGenerator(), random, pos))));
		} else return this.decline(type);
		this.words.set(header, kind);
		this.words.set(header + 1, this.words.size() - header - 2);
		return true;
	}
}
