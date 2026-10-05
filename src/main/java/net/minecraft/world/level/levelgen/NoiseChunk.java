package net.minecraft.world.level.levelgen;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.jetbrains.annotations.Nullable;

public class NoiseChunk implements DensityBatch.Provider, DensityFunction.FunctionContext {
	private final NoiseSettings noiseSettings;
	final int cellCountXZ;
	final int cellCountY;
	final int cellNoiseMinY;
	private final int firstCellX;
	private final int firstCellZ;
	final int firstNoiseX;
	final int firstNoiseZ;
	final List<NoiseChunk.NoiseInterpolator> interpolators;
	final List<NoiseChunk.CacheAllInCell> cellCaches;
	private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();
    private final DensityFunction.Visitor wrappingVisitor = getClass() == NoiseChunk.class
        ? (NativeSpline.StableVisitor)this::wrap : this::wrap;
    DensityFunction.Visitor splineWrappingVisitor() { return wrappingVisitor; }
	private final Long2IntMap preliminarySurfaceLevelCache = new Long2IntOpenHashMap();
	private final Aquifer aquifer;
	private final DensityFunction preliminarySurfaceLevel;
    CacheAllInCell aquiferDensity;
	private final NoiseChunk.BlockStateFiller blockStateRule;
	// Chunk-wrapped ore vein inputs for the native fill gate; null without ore veins.
	@Nullable
	final DensityFunction veinToggle;
	@Nullable
	final DensityFunction veinRidged;
	@Nullable
	final DensityFunction veinGap;
	private final Blender blender;
	private final NoiseChunk.FlatCache blendAlpha;
	private final NoiseChunk.FlatCache blendOffset;
	private final DensityFunctions.BeardifierOrMarker beardifier;
	private long lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
	private Blender.BlendingOutput lastBlendingOutput = new Blender.BlendingOutput(1.0, 0.0);
	final int noiseSizeXZ;
	final int cellWidth;
	final int cellHeight;
	boolean interpolating;
	boolean fillingCell;
	private int cellStartBlockX;
	int cellStartBlockY;
	private int cellStartBlockZ;
	int inCellX;
	int inCellY;
	int inCellZ;
	long interpolationCounter;
    private long blockZEpoch;
    private double blockZFraction;
    private boolean lazyBlockInterpolation = this.getClass() == NoiseChunk.class;
	long arrayInterpolationCounter;
	int arrayIndex;
	private final DensityFunction.ContextProvider sliceFillingContextProvider = new DensityBatch.Provider() {
		@Override
		public DensityFunction.FunctionContext forIndex(int i) {
			NoiseChunk.this.cellStartBlockY = (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
			NoiseChunk.this.interpolationCounter++;
			NoiseChunk.this.inCellY = 0;
			NoiseChunk.this.arrayIndex = i;
			return NoiseChunk.this;
		}

		@Override
		public void fillAllDirectly(double[] ds, DensityFunction densityFunction) {
			boolean batch = ds.length == NoiseChunk.this.cellCountY + 1
				&& NoiseChunk.this.noiseBatch.prepare(densityFunction, ds.length);
			for (int i = 0; i < NoiseChunk.this.cellCountY + 1; i++) {
				NoiseChunk.this.cellStartBlockY = (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
				NoiseChunk.this.interpolationCounter++;
				NoiseChunk.this.inCellY = 0;
				NoiseChunk.this.arrayIndex = i;
				if (!batch) ds[i] = densityFunction.compute(NoiseChunk.this);
				else NoiseChunk.this.noiseBatch.record(i, NoiseChunk.this);
			}
			if (batch) noiseBatch.finish(ds);
		}
	};

	public static NoiseChunk forChunk(
		ChunkAccess chunkAccess,
		RandomState randomState,
		DensityFunctions.BeardifierOrMarker beardifierOrMarker,
		NoiseGeneratorSettings noiseGeneratorSettings,
		Aquifer.FluidPicker fluidPicker,
		Blender blender
	) {
		NoiseSettings noiseSettings = noiseGeneratorSettings.noiseSettings().clampToHeightAccessor(chunkAccess);
		ChunkPos chunkPos = chunkAccess.getPos();
		int i = 16 / noiseSettings.getCellWidth();
		return new NoiseChunk(
			i, randomState, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), noiseSettings, beardifierOrMarker, noiseGeneratorSettings, fluidPicker, blender
		);
	}

	public NoiseChunk(
		int i,
		RandomState randomState,
		int j,
		int k,
		NoiseSettings noiseSettings,
		DensityFunctions.BeardifierOrMarker beardifierOrMarker,
		NoiseGeneratorSettings noiseGeneratorSettings,
		Aquifer.FluidPicker fluidPicker,
		Blender blender
	) {
		this.randomState = randomState;
		this.noiseSettings = noiseSettings;
		this.cellWidth = noiseSettings.getCellWidth();
		this.cellHeight = noiseSettings.getCellHeight();
		this.cellCountXZ = i;
		this.cellCountY = Mth.floorDiv(noiseSettings.height(), this.cellHeight);
		this.cellNoiseMinY = Mth.floorDiv(noiseSettings.minY(), this.cellHeight);
		this.firstCellX = Math.floorDiv(j, this.cellWidth);
		this.firstCellZ = Math.floorDiv(k, this.cellWidth);
		this.interpolators = Lists.<NoiseChunk.NoiseInterpolator>newArrayList();
		this.cellCaches = Lists.<NoiseChunk.CacheAllInCell>newArrayList();
		this.firstNoiseX = QuartPos.fromBlock(j);
		this.firstNoiseZ = QuartPos.fromBlock(k);
		this.noiseSizeXZ = QuartPos.fromBlock(i * this.cellWidth);
		this.blender = blender;
		this.beardifier = beardifierOrMarker;
		this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
		this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);
		if (!blender.isEmpty()) {
			blender.fillBlendingOutputs(this.firstNoiseX, this.firstNoiseZ, this.noiseSizeXZ + 1,
				this.blendAlpha.values, this.blendOffset.values);
		} else {
			Arrays.fill(this.blendAlpha.values, 1.0);
			Arrays.fill(this.blendOffset.values, 0.0);
		}

		NoiseRouter noiseRouter = randomState.router();
		NoiseRouter noiseRouter2 = noiseRouter.mapAll(this.wrappingVisitor);
		this.preliminarySurfaceLevel = this.randomState.optimizeUnary(noiseRouter2.preliminarySurfaceLevel());
		if (!noiseGeneratorSettings.isAquifersEnabled()) {
			this.aquifer = Aquifer.createDisabled(fluidPicker);
		} else {
			int n = SectionPos.blockToSectionCoord(j);
			int o = SectionPos.blockToSectionCoord(k);
			this.aquifer = Aquifer.create(this, new ChunkPos(n, o), noiseRouter2, randomState.aquiferRandom(), noiseSettings.minY(), noiseSettings.height(), fluidPicker);
		}

		List<NoiseChunk.BlockStateFiller> list = new ArrayList();
		DensityFunction densityFunction = DensityFunctions.cacheAllInCell(
				DensityFunctions.add(noiseRouter2.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)
			)
			.mapAll(this.wrappingVisitor);
		if (densityFunction instanceof CacheAllInCell cache) this.aquiferDensity = cache;
        if(this.aquifer instanceof Aquifer.NoiseBasedAquifer enabled) {
            list.add(context -> enabled.computeMaterial(context,densityFunction.compute(context)));
        } else {
            list.add(context -> this.aquifer.computeSubstance(context,densityFunction.compute(context)));
        }
		if (noiseGeneratorSettings.oreVeinsEnabled()) {
			list.add(OreVeinifier.create(noiseRouter2.veinToggle(), noiseRouter2.veinRidged(), noiseRouter2.veinGap(), randomState.oreRandom()));
			this.veinToggle = noiseRouter2.veinToggle();
			this.veinRidged = noiseRouter2.veinRidged();
			this.veinGap = noiseRouter2.veinGap();
		} else {
			this.veinToggle = null;
			this.veinRidged = null;
			this.veinGap = null;
		}

		this.blockStateRule = new MaterialRuleList((NoiseChunk.BlockStateFiller[])list.toArray(new NoiseChunk.BlockStateFiller[0]));
	}

	protected Climate.Sampler cachedClimateSampler(NoiseRouter noiseRouter, List<Climate.ParameterPoint> list) {
		return new Climate.Sampler(
			noiseRouter.temperature().mapAll(this.wrappingVisitor),
			noiseRouter.vegetation().mapAll(this.wrappingVisitor),
			noiseRouter.continents().mapAll(this.wrappingVisitor),
			noiseRouter.erosion().mapAll(this.wrappingVisitor),
			noiseRouter.depth().mapAll(this.wrappingVisitor),
			noiseRouter.ridges().mapAll(this.wrappingVisitor),
			list
		);
	}

	@Nullable
	protected BlockState getInterpolatedState() {
		return this.blockStateRule.calculate(this);
	}

	@Override
	public int blockX() {
		return this.cellStartBlockX + this.inCellX;
	}

	@Override
	public int blockY() {
		return this.cellStartBlockY + this.inCellY;
	}

	@Override
	public int blockZ() {
		return this.cellStartBlockZ + this.inCellZ;
	}

	public int maxPreliminarySurfaceLevel(int i, int j, int k, int l) {
		int m = Integer.MIN_VALUE;

		for (int n = j; n <= l; n += 4) {
			for (int o = i; o <= k; o += 4) {
				int p = this.preliminarySurfaceLevel(o, n);
				if (p > m) {
					m = p;
				}
			}
		}

		return m;
	}

	public int preliminarySurfaceLevel(int i, int j) {
		int k = QuartPos.toBlock(QuartPos.fromBlock(i));
		int l = QuartPos.toBlock(QuartPos.fromBlock(j));
		return this.preliminarySurfaceLevelCache.computeIfAbsent(ColumnPos.asLong(k, l), this::computePreliminarySurfaceLevel);
	}

	private int computePreliminarySurfaceLevel(long l) {
		int i = ColumnPos.getX(l);
		int j = ColumnPos.getZ(l);
		return Mth.floor(this.preliminarySurfaceLevel.compute(new DensityFunction.SinglePointContext(i, 0, j)));
	}

	@Override
	public Blender getBlender() {
		return this.blender;
	}

	private void fillSlice(boolean bl, int i, @Nullable NativeNoiseRouter router) {
		this.cellStartBlockX = i * this.cellWidth;
		this.inCellX = 0;
        if (router != null && router.fill(bl, this.cellStartBlockX)) {
            // The column loop's end state. Every field is reassigned before its
            // next read; the counters only need to pass every recorded value.
            this.cellStartBlockZ = (this.firstCellZ + this.cellCountXZ) * this.cellWidth;
            this.inCellZ = 0;
            this.cellStartBlockY = (this.cellCountY + this.cellNoiseMinY) * this.cellHeight;
            this.inCellY = 0;
            this.arrayIndex = this.cellCountY;
            this.interpolationCounter += (long)(this.cellCountXZ + 1) * (this.cellCountY + 1) * this.interpolators.size();
            this.arrayInterpolationCounter += this.cellCountXZ + 2;
            return;
        }

		for (int j = 0; j < this.cellCountXZ + 1; j++) {
			int k = this.firstCellZ + j;
			this.cellStartBlockZ = k * this.cellWidth;
			this.inCellZ = 0;
			this.arrayInterpolationCounter++;

			for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
				double[] ds = (bl ? noiseInterpolator.slice0 : noiseInterpolator.slice1)[j];
				noiseInterpolator.fillArray(ds, this.sliceFillingContextProvider);
			}
		}

		this.arrayInterpolationCounter++;
	}

	public void initializeForFirstCellX() {
        this.initializeForFirstCellX(null);
	}

    /** With a router, slices come from Rust; Java fills any slice it declines. */
	void initializeForFirstCellX(@Nullable NativeNoiseRouter router) {
		if (this.interpolating) {
			throw new IllegalStateException("Staring interpolation twice");
		} else {
			this.interpolating = true;
			this.interpolationCounter = 0L;
			this.fillSlice(true, this.firstCellX, router);
		}
	}

	public void advanceCellX(int i) {
        this.advanceCellX(i, null);
	}

	void advanceCellX(int i, @Nullable NativeNoiseRouter router) {
		this.fillSlice(false, this.firstCellX + i + 1, router);
		this.cellStartBlockX = (this.firstCellX + i) * this.cellWidth;
	}

    int firstCellZ() {
        return this.firstCellZ;
    }

	public NoiseChunk forIndex(int i) {
		int j = Math.floorMod(i, this.cellWidth);
		int k = Math.floorDiv(i, this.cellWidth);
		int l = Math.floorMod(k, this.cellWidth);
		int m = this.cellHeight - 1 - Math.floorDiv(k, this.cellWidth);
		this.inCellX = l;
		this.inCellY = m;
		this.inCellZ = j;
		this.arrayIndex = i;
		return this;
	}

	@Override
	public void fillAllDirectly(double[] ds, DensityFunction densityFunction) {
		boolean batch = ds.length == this.cellWidth * this.cellWidth * this.cellHeight
			&& noiseBatch.prepare(densityFunction, ds.length);
		this.arrayIndex = 0;

		for (int i = this.cellHeight - 1; i >= 0; i--) {
			this.inCellY = i;

			for (int j = 0; j < this.cellWidth; j++) {
				this.inCellX = j;

				for (int k = 0; k < this.cellWidth; k++) {
					this.inCellZ = k;
					if (!batch) ds[this.arrayIndex++] = densityFunction.compute(this);
					else noiseBatch.record(this.arrayIndex++, this);
				}
			}
		}
		if (batch) noiseBatch.finish(ds);
	}

	private final RandomState randomState;
    boolean aquiferBatchSafe() { return randomState.aquiferBatchSafe(); }
    RandomState randomState() { return this.randomState; }
	private final DensityBatch noiseBatch = new DensityBatch();

	public void selectCellYZ(int i, int j) {
		for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
			noiseInterpolator.selectCellYZ(i, j);
		}

		this.fillingCell = true;
		this.cellStartBlockY = (i + this.cellNoiseMinY) * this.cellHeight;
		this.cellStartBlockZ = (this.firstCellZ + j) * this.cellWidth;
		this.arrayInterpolationCounter++;

		for (NoiseChunk.CacheAllInCell cacheAllInCell : this.cellCaches) {
			cacheAllInCell.evaluator.fillArray(cacheAllInCell.values, this);
		}

		this.arrayInterpolationCounter++;
		this.fillingCell = false;
	}

	public void updateForY(int i, double d) {
		this.inCellY = i - this.cellStartBlockY;

		for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
			noiseInterpolator.updateForY(d);
		}
	}

	public void updateForX(int i, double d) {
		this.inCellX = i - this.cellStartBlockX;

		for (NoiseChunk.NoiseInterpolator noiseInterpolator : this.interpolators) {
			noiseInterpolator.updateForX(d);
		}
	}

	public void updateForZ(int i, double d) {
		this.inCellZ = i - this.cellStartBlockZ;
		this.interpolationCounter++;

        // Material decisions usually read only a subset of the interpolators.
        // Defer their final lerp; updateForX flushes the preceding value before
        // replacing its operands, preserving reads between staging calls too.
        if(!this.lazyBlockInterpolation) {
            for(NoiseInterpolator interpolator:this.interpolators)interpolator.updateForZ(d);
            return;
        }
        this.blockZFraction=d;
        this.blockZEpoch++;
	}

	public void stopInterpolation() {
		if (!this.interpolating) {
			throw new IllegalStateException("Staring interpolation twice");
		} else {
			this.interpolating = false;
		}
	}

	public void swapSlices() {
		this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
	}

	public Aquifer aquifer() {
		return this.aquifer;
	}

	protected int cellWidth() {
		return this.cellWidth;
	}

	protected int cellHeight() {
		return this.cellHeight;
	}

	Blender.BlendingOutput getOrComputeBlendingOutput(int i, int j) {
		long l = ChunkPos.asLong(i, j);
		if (this.lastBlendingDataPos == l) {
			return this.lastBlendingOutput;
		} else {
			this.lastBlendingDataPos = l;
			Blender.BlendingOutput blendingOutput = this.blender.blendOffsetAndFactor(i, j);
			this.lastBlendingOutput = blendingOutput;
			return blendingOutput;
		}
	}

	protected DensityFunction wrap(DensityFunction densityFunction) {
		return (DensityFunction)this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
	}

	private DensityFunction wrapNew(DensityFunction densityFunction) {
		if (densityFunction instanceof DensityFunctions.Marker marker) {
			return (DensityFunction)(switch (marker.type()) {
				case Interpolated -> new NoiseChunk.NoiseInterpolator(marker.wrapped());
				case FlatCache -> new NoiseChunk.FlatCache(marker.wrapped(), true);
				case Cache2D -> new NoiseChunk.Cache2D(marker.wrapped(), this.randomState.optimizeUnary(marker.wrapped()));
				case CacheOnce -> new NoiseChunk.CacheOnce(marker.wrapped());
				case CacheAllInCell -> new NoiseChunk.CacheAllInCell(marker.wrapped());
			});
		} else {
			if (this.blender != Blender.empty()) {
				if (densityFunction == DensityFunctions.BlendAlpha.INSTANCE) {
					return this.blendAlpha;
				}

				if (densityFunction == DensityFunctions.BlendOffset.INSTANCE) {
					return this.blendOffset;
				}
			}

			if (densityFunction == DensityFunctions.BeardifierMarker.INSTANCE) {
				return this.beardifier;
			} else {
				return this.randomState.optimizeDensity(densityFunction instanceof DensityFunctions.HolderHolder holderHolder ? holderHolder.function().value() : densityFunction);
			}
		}
	}

	class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {
		@Override
		public DensityFunction wrapped() {
			return DensityFunctions.BlendAlpha.INSTANCE;
		}

		@Override
		public DensityFunction mapAll(DensityFunction.Visitor visitor) {
			return this.wrapped().mapAll(visitor);
		}

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			return NoiseChunk.this.getOrComputeBlendingOutput(functionContext.blockX(), functionContext.blockZ()).alpha();
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			contextProvider.fillAllDirectly(ds, this);
		}

		@Override
		public double minValue() {
			return 0.0;
		}

		@Override
		public double maxValue() {
			return 1.0;
		}

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec() {
			return DensityFunctions.BlendAlpha.CODEC;
		}
	}

	class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {
		@Override
		public DensityFunction wrapped() {
			return DensityFunctions.BlendOffset.INSTANCE;
		}

		@Override
		public DensityFunction mapAll(DensityFunction.Visitor visitor) {
			return this.wrapped().mapAll(visitor);
		}

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			return NoiseChunk.this.getOrComputeBlendingOutput(functionContext.blockX(), functionContext.blockZ()).blendingOffset();
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			contextProvider.fillAllDirectly(ds, this);
		}

		@Override
		public double minValue() {
			return Double.NEGATIVE_INFINITY;
		}

		@Override
		public double maxValue() {
			return Double.POSITIVE_INFINITY;
		}

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec() {
			return DensityFunctions.BlendOffset.CODEC;
		}
	}

	@FunctionalInterface
	public interface BlockStateFiller {
		@Nullable
		BlockState calculate(DensityFunction.FunctionContext functionContext);
	}

	static class Cache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
		private final DensityFunction function;
        private final DensityFunction evaluator;
		private long lastPos2D = ChunkPos.INVALID_CHUNK_POS;
		private double lastValue;

		Cache2D(DensityFunction densityFunction) { this(densityFunction, densityFunction); }
        Cache2D(DensityFunction densityFunction, DensityFunction evaluator) {
            this.function=densityFunction;this.evaluator=evaluator;
        }

        DensityFunction evaluator() { return this.evaluator; }

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			int i = functionContext.blockX();
			int j = functionContext.blockZ();
			long l = ChunkPos.asLong(i, j);
			if (this.lastPos2D == l) {
				return this.lastValue;
			} else {
				this.lastPos2D = l;
				double d = this.evaluator.compute(functionContext);
				this.lastValue = d;
				return d;
			}
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			this.evaluator.fillArray(ds, contextProvider);
		}

		@Override
		public DensityFunction wrapped() {
			return this.function;
		}

		@Override
		public DensityFunctions.Marker.Type type() {
			return DensityFunctions.Marker.Type.Cache2D;
		}
	}

	class CacheAllInCell implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
		final DensityFunction noiseFiller;
        private final DensityFunction evaluator;
		final double[] values;

		CacheAllInCell(final DensityFunction densityFunction) {
			this.noiseFiller = densityFunction;
            this.evaluator = NoiseChunk.this.randomState.optimizeUnary(densityFunction);
			this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
			NoiseChunk.this.cellCaches.add(this);
		}

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			if (functionContext != NoiseChunk.this) {
				return this.evaluator.compute(functionContext);
			} else if (!NoiseChunk.this.interpolating) {
				throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
			} else {
				int i = NoiseChunk.this.inCellX;
				int j = NoiseChunk.this.inCellY;
				int k = NoiseChunk.this.inCellZ;
				return i >= 0 && j >= 0 && k >= 0 && i < NoiseChunk.this.cellWidth && j < NoiseChunk.this.cellHeight && k < NoiseChunk.this.cellWidth
					? this.values[((NoiseChunk.this.cellHeight - 1 - j) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + k]
					: this.evaluator.compute(functionContext);
			}
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			contextProvider.fillAllDirectly(ds, this);
		}

		@Override
		public DensityFunction wrapped() {
			return this.noiseFiller;
		}

		@Override
		public DensityFunctions.Marker.Type type() {
			return DensityFunctions.Marker.Type.CacheAllInCell;
		}
	}

	class CacheOnce implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
		private final DensityFunction function;
        private final DensityFunction evaluator;
		private long lastCounter;
		private long lastArrayCounter;
		private double lastValue;
		@Nullable
		private double[] lastArray;

		CacheOnce(final DensityFunction densityFunction) {
			this.function = densityFunction;
            this.evaluator = NoiseChunk.this.randomState.optimizeUnary(densityFunction);
		}

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			if (functionContext != NoiseChunk.this) {
				return this.evaluator.compute(functionContext);
			} else if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
				return this.lastArray[NoiseChunk.this.arrayIndex];
			} else if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
				return this.lastValue;
			} else {
				this.lastCounter = NoiseChunk.this.interpolationCounter;
				double d = this.evaluator.compute(functionContext);
				this.lastValue = d;
				return d;
			}
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
				System.arraycopy(this.lastArray, 0, ds, 0, ds.length);
			} else {
				this.evaluator.fillArray(ds, contextProvider);
				if (this.lastArray != null && this.lastArray.length == ds.length) {
					System.arraycopy(ds, 0, this.lastArray, 0, ds.length);
				} else {
					this.lastArray = (double[])ds.clone();
				}

				this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
			}
		}

		@Override
		public DensityFunction wrapped() {
			return this.function;
		}

		@Override
		public DensityFunctions.Marker.Type type() {
			return DensityFunctions.Marker.Type.CacheOnce;
		}
	}

	class FlatCache implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
		boolean containsSplinePoint(DensityFunction.FunctionContext context) {
			return this.splineCacheIndex(context) >= 0;
		}
		boolean sameSplineGrid(FlatCache other) {
			return this.sizeXZ == other.sizeXZ && this.gridX() == other.gridX() && this.gridZ() == other.gridZ();
		}
		private int gridX() { return NoiseChunk.this.firstNoiseX; }
		private int gridZ() { return NoiseChunk.this.firstNoiseZ; }
		int splineCacheIndex(DensityFunction.FunctionContext context) {
			int x = QuartPos.fromBlock(context.blockX()) - NoiseChunk.this.firstNoiseX;
			int z = QuartPos.fromBlock(context.blockZ()) - NoiseChunk.this.firstNoiseZ;
			return x >= 0 && z >= 0 && x < this.sizeXZ && z < this.sizeXZ ? x + z * this.sizeXZ : -1;
		}
		private final DensityFunction noiseFiller;
        private final DensityFunction evaluator;
		final double[] values;
		final int sizeXZ;

		FlatCache(final DensityFunction densityFunction, final boolean bl) {
			this.noiseFiller = densityFunction;
            this.evaluator = NoiseChunk.this.randomState.optimizeUnary(densityFunction);
			this.sizeXZ = NoiseChunk.this.noiseSizeXZ + 1;
			this.values = new double[this.sizeXZ * this.sizeXZ];
			if (bl) {
                DensityBatch batch = new DensityBatch();
                boolean batched = batch.prepare(this.evaluator, this.values.length);
				for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; i++) {
					int j = NoiseChunk.this.firstNoiseX + i;
					int k = QuartPos.toBlock(j);

					for (int l = 0; l <= NoiseChunk.this.noiseSizeXZ; l++) {
						int m = NoiseChunk.this.firstNoiseZ + l;
						int n = QuartPos.toBlock(m);
						var context = new DensityFunction.SinglePointContext(k, 0, n);
                        if (batched) batch.record(i + l * this.sizeXZ, context);
                        else this.values[i + l * this.sizeXZ] = this.evaluator.compute(context);
					}
				}
                if (batched) batch.finish(this.values);
			}
		}

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			int i = QuartPos.fromBlock(functionContext.blockX());
			int j = QuartPos.fromBlock(functionContext.blockZ());
			int k = i - NoiseChunk.this.firstNoiseX;
			int l = j - NoiseChunk.this.firstNoiseZ;
			return k >= 0 && l >= 0 && k < this.sizeXZ && l < this.sizeXZ ? this.values[k + l * this.sizeXZ] : this.evaluator.compute(functionContext);
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			contextProvider.fillAllDirectly(ds, this);
		}

		@Override
		public DensityFunction wrapped() {
			return this.noiseFiller;
		}

		@Override
		public DensityFunctions.Marker.Type type() {
			return DensityFunctions.Marker.Type.FlatCache;
		}
	}

	interface NoiseChunkDensityFunction extends DensityFunction {
		DensityFunction wrapped();

		@Override
		default double minValue() {
			return this.wrapped().minValue();
		}

		@Override
		default double maxValue() {
			return this.wrapped().maxValue();
		}
	}

	public class NoiseInterpolator implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
		double[][] slice0;
		double[][] slice1;
		private final DensityFunction noiseFiller;
        private final DensityFunction evaluator;
		private double noise000;
		private double noise001;
		private double noise100;
		private double noise101;
		private double noise010;
		private double noise011;
		private double noise110;
		private double noise111;
		private double valueXZ00;
		private double valueXZ10;
		private double valueXZ01;
		private double valueXZ11;
		private double valueZ0;
		private double valueZ1;
		private double value;
        private long valueZEpoch;

		NoiseInterpolator(final DensityFunction densityFunction) {
			this.valueZEpoch=NoiseChunk.this.blockZEpoch;
            if(this.getClass()!=NoiseInterpolator.class)NoiseChunk.this.lazyBlockInterpolation=false;
			this.noiseFiller = densityFunction;
            this.evaluator = NoiseChunk.this.randomState.optimizeUnary(densityFunction);
			this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
			this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
			NoiseChunk.this.interpolators.add(this);
		}

		private double[][] allocateSlice(int i, int j) {
			int k = j + 1;
			int l = i + 1;
			double[][] ds = new double[k][l];

			for (int m = 0; m < k; m++) {
				ds[m] = new double[l];
			}

			return ds;
		}

		void selectCellYZ(int i, int j) {
			this.noise000 = this.slice0[j][i];
			this.noise001 = this.slice0[j + 1][i];
			this.noise100 = this.slice1[j][i];
			this.noise101 = this.slice1[j + 1][i];
			this.noise010 = this.slice0[j][i + 1];
			this.noise011 = this.slice0[j + 1][i + 1];
			this.noise110 = this.slice1[j][i + 1];
			this.noise111 = this.slice1[j + 1][i + 1];
		}

		void updateForY(double d) {
			this.valueXZ00 = Mth.lerp(d, this.noise000, this.noise010);
			this.valueXZ10 = Mth.lerp(d, this.noise100, this.noise110);
			this.valueXZ01 = Mth.lerp(d, this.noise001, this.noise011);
			this.valueXZ11 = Mth.lerp(d, this.noise101, this.noise111);
		}

		void updateForX(double d) {
            this.synchronizeValue();
			this.valueZ0 = Mth.lerp(d, this.valueXZ00, this.valueXZ10);
			this.valueZ1 = Mth.lerp(d, this.valueXZ01, this.valueXZ11);
		}

		void updateForZ(double d) {
			this.value = Mth.lerp(d, this.valueZ0, this.valueZ1);
            this.valueZEpoch=NoiseChunk.this.blockZEpoch;
		}
        private double synchronizeValue() {
            if(this.valueZEpoch!=NoiseChunk.this.blockZEpoch) {
                this.value=Mth.lerp(NoiseChunk.this.blockZFraction,this.valueZ0,this.valueZ1);
                this.valueZEpoch=NoiseChunk.this.blockZEpoch;
            }
            return this.value;
        }

		@Override
		public double compute(DensityFunction.FunctionContext functionContext) {
			if (functionContext != NoiseChunk.this) {
				return this.evaluator.compute(functionContext);
			} else if (!NoiseChunk.this.interpolating) {
				throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
			} else {
				return NoiseChunk.this.fillingCell
					? Mth.lerp3(
						(double)NoiseChunk.this.inCellX / NoiseChunk.this.cellWidth,
						(double)NoiseChunk.this.inCellY / NoiseChunk.this.cellHeight,
						(double)NoiseChunk.this.inCellZ / NoiseChunk.this.cellWidth,
						this.noise000,
						this.noise100,
						this.noise010,
						this.noise110,
						this.noise001,
						this.noise101,
						this.noise011,
						this.noise111
					)
					: this.synchronizeValue();
			}
		}

		@Override
		public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
			if (NoiseChunk.this.fillingCell) {
				contextProvider.fillAllDirectly(ds, this);
			} else {
				this.evaluator.fillArray(ds, contextProvider);
			}
		}

		@Override
		public DensityFunction wrapped() {
			return this.noiseFiller;
		}

        NoiseChunk cellOwner() { return NoiseChunk.this; }
        void copyCellCorners(double[] target,int at) {
            target[at]=noise000;target[at+1]=noise100;target[at+2]=noise010;target[at+3]=noise110;
            target[at+4]=noise001;target[at+5]=noise101;target[at+6]=noise011;target[at+7]=noise111;
        }
		private void swapSlices() {
			double[][] ds = this.slice0;
			this.slice0 = this.slice1;
			this.slice1 = ds;
		}

		@Override
		public DensityFunctions.Marker.Type type() {
			return DensityFunctions.Marker.Type.Interpolated;
		}
	}
}
