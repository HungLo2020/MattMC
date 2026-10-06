package net.minecraft.world.level.levelgen;

import java.util.ArrayList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {
	public static final MapCodec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				BiomeSource.CODEC.fieldOf("biome_source").forGetter(noiseBasedChunkGenerator -> noiseBasedChunkGenerator.biomeSource),
				NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(noiseBasedChunkGenerator -> noiseBasedChunkGenerator.settings)
			)
			.apply(instance, instance.stable(NoiseBasedChunkGenerator::new))
	);
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private final Holder<NoiseGeneratorSettings> settings;
	private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

	public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> holder) {
		super(biomeSource);
		this.settings = holder;
		this.globalFluidPicker = Suppliers.memoize(() -> createFluidPicker(holder.value()));
	}

	private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings noiseGeneratorSettings) {
		return new AquiferFluidPicker(noiseGeneratorSettings.seaLevel(), noiseGeneratorSettings.defaultFluid());
	}

	@Override
	public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunkAccess) {
		return CompletableFuture.supplyAsync(() -> {
			this.doCreateBiomes(blender, randomState, structureManager, chunkAccess);
			return chunkAccess;
		}, Util.backgroundExecutor().forName("init_biomes"));
	}

	void doCreateBiomes(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess) {
		NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccessx -> this.createNoiseChunk(chunkAccessx, structureManager, blender, randomState));
		BiomeResolver biomeResolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunkAccess);
        // Rust samples, searches and fills plain multi-noise chunks (same sampler, same order).
        if (NativeBiomeFill.fill(chunkAccess, biomeResolver, noiseChunk, randomState)) return;
		chunkAccess.fillBiomesFromNoise(biomeResolver, noiseChunk.cachedClimateSampler(randomState.router(), this.settings.value().spawnTarget()));
	}

	private NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState randomState) {
		return NoiseChunk.forChunk(
			chunkAccess,
			randomState,
			Beardifier.forStructuresInChunk(structureManager, chunkAccess.getPos()),
			this.settings.value(),
			(Aquifer.FluidPicker)this.globalFluidPicker.get(),
			blender
		);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	public Holder<NoiseGeneratorSettings> generatorSettings() {
		return this.settings;
	}

	public boolean stable(ResourceKey<NoiseGeneratorSettings> resourceKey) {
		return this.settings.is(resourceKey);
	}

	@Override
	public int getBaseHeight(int i, int j, Heightmap.Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		return this.iterateNoiseColumn(levelHeightAccessor, randomState, i, j, null, types.isOpaque()).orElse(levelHeightAccessor.getMinY());
	}

	@Override
	public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		MutableObject<NoiseColumn> mutableObject = new MutableObject<>();
		this.iterateNoiseColumn(levelHeightAccessor, randomState, i, j, mutableObject, null);
		return mutableObject.getValue();
	}

	@Override
	public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
		DecimalFormat decimalFormat = new DecimalFormat("0.000");
		NoiseRouter noiseRouter = randomState.router();
		DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(blockPos.getX(), blockPos.getY(), blockPos.getZ());
		double d = noiseRouter.ridges().compute(singlePointContext);
		list.add(
			"NoiseRouter T: "
				+ decimalFormat.format(noiseRouter.temperature().compute(singlePointContext))
				+ " V: "
				+ decimalFormat.format(noiseRouter.vegetation().compute(singlePointContext))
				+ " C: "
				+ decimalFormat.format(noiseRouter.continents().compute(singlePointContext))
				+ " E: "
				+ decimalFormat.format(noiseRouter.erosion().compute(singlePointContext))
				+ " D: "
				+ decimalFormat.format(noiseRouter.depth().compute(singlePointContext))
				+ " W: "
				+ decimalFormat.format(d)
				+ " PV: "
				+ decimalFormat.format(NoiseRouterData.peaksAndValleys((float)d))
				+ " PS: "
				+ decimalFormat.format(noiseRouter.preliminarySurfaceLevel().compute(singlePointContext))
				+ " N: "
				+ decimalFormat.format(noiseRouter.finalDensity().compute(singlePointContext))
		);
	}

	private OptionalInt iterateNoiseColumn(
		LevelHeightAccessor levelHeightAccessor,
		RandomState randomState,
		int i,
		int j,
		@Nullable MutableObject<NoiseColumn> mutableObject,
		@Nullable Predicate<BlockState> predicate
	) {
		NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(levelHeightAccessor);
		int k = noiseSettings.getCellHeight();
		int l = noiseSettings.minY();
		int m = Mth.floorDiv(l, k);
		int n = Mth.floorDiv(noiseSettings.height(), k);
		if (n <= 0) {
			return OptionalInt.empty();
		} else {
			BlockState[] blockStates;
			if (mutableObject == null) {
				blockStates = null;
			} else {
				blockStates = new BlockState[noiseSettings.height()];
				mutableObject.setValue(new NoiseColumn(l, blockStates));
			}

			int o = noiseSettings.getCellWidth();
			int p = Math.floorDiv(i, o);
			int q = Math.floorDiv(j, o);
			int r = Math.floorMod(i, o);
			int s = Math.floorMod(j, o);
			int t = p * o;
			int u = q * o;
			double d = (double)r / o;
			double e = (double)s / o;
			NoiseChunk noiseChunk = new NoiseChunk(
				1,
				randomState,
				t,
				u,
				noiseSettings,
				DensityFunctions.BeardifierMarker.INSTANCE,
				this.settings.value(),
				(Aquifer.FluidPicker)this.globalFluidPicker.get(),
				Blender.empty()
			);
			noiseChunk.initializeForFirstCellX();
			noiseChunk.advanceCellX(0);

			for (int v = n - 1; v >= 0; v--) {
				noiseChunk.selectCellYZ(v, 0);

				for (int w = k - 1; w >= 0; w--) {
					int x = (m + v) * k + w;
					double f = (double)w / k;
					noiseChunk.updateForY(x, f);
					noiseChunk.updateForX(i, d);
					noiseChunk.updateForZ(j, e);
					BlockState blockState = noiseChunk.getInterpolatedState();
					BlockState blockState2 = blockState == null ? this.settings.value().defaultBlock() : blockState;
					if (blockStates != null) {
						int y = v * k + w;
						blockStates[y] = blockState2;
					}

					if (predicate != null && predicate.test(blockState2)) {
						noiseChunk.stopInterpolation();
						return OptionalInt.of(x + 1);
					}
				}
			}

			noiseChunk.stopInterpolation();
			return OptionalInt.empty();
		}
	}

	@Override
	public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
		if (!SharedConstants.debugVoidTerrain(chunkAccess.getPos()) && !SharedConstants.DEBUG_DISABLE_SURFACE) {
			WorldGenerationContext worldGenerationContext = new WorldGenerationContext(this, worldGenRegion);
			this.buildSurface(
				chunkAccess,
				worldGenerationContext,
				randomState,
				structureManager,
				worldGenRegion.getBiomeManager(),
				worldGenRegion.registryAccess().lookupOrThrow(Registries.BIOME),
				Blender.of(worldGenRegion)
			);
		}
	}

	@VisibleForTesting
	public void buildSurface(
		ChunkAccess chunkAccess,
		WorldGenerationContext worldGenerationContext,
		RandomState randomState,
		StructureManager structureManager,
		BiomeManager biomeManager,
		Registry<Biome> registry,
		Blender blender
	) {
		NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccessx -> this.createNoiseChunk(chunkAccessx, structureManager, blender, randomState));
		NoiseGeneratorSettings noiseGeneratorSettings = this.settings.value();
		randomState.surfaceSystem()
			.buildSurface(
				randomState,
				biomeManager,
				registry,
				noiseGeneratorSettings.useLegacyRandomSource(),
				worldGenerationContext,
				chunkAccess,
				noiseChunk,
				noiseGeneratorSettings.surfaceRule()
			);
	}

	@Override
	public void applyCarvers(
		WorldGenRegion worldGenRegion, long l, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess
	) {
		if (!SharedConstants.DEBUG_DISABLE_CARVERS) {
			NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(
				chunkAccessx -> this.createNoiseChunk(chunkAccessx, structureManager, Blender.of(worldGenRegion), randomState)
			);
			this.carveChunk(worldGenRegion::getChunk, worldGenRegion.registryAccess(), l, randomState, biomeManager, chunkAccess, noiseChunk);
		}
	}

	/** Chunks around the carved one, for their carver biomes. */
	interface CarverChunks {
		ChunkAccess getChunk(int x, int z);
	}

	/** applyCarvers after its noise chunk: the carvers of every chunk within 8 of this one. */
	void carveChunk(
		CarverChunks chunks, RegistryAccess registryAccess, long l, RandomState randomState, BiomeManager biomeManager, ChunkAccess chunkAccess, NoiseChunk noiseChunk
	) {
		BiomeManager biomeManager2 = biomeManager.withDifferentSource((ix, jx, kx) -> this.biomeSource.getNoiseBiome(ix, jx, kx, randomState.sampler()));
		WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		Aquifer aquifer = noiseChunk.aquifer();
		CarvingContext carvingContext = new CarvingContext(
			this, registryAccess, chunkAccess.getHeightAccessorForGeneration(), noiseChunk, randomState, this.settings.value().surfaceRule()
		);
		CarvingMask carvingMask = ((ProtoChunk)chunkAccess).getOrCreateCarvingMask();
		if (NativeCarvers.eligible(chunkAccess, noiseChunk, carvingContext)) {
			// Rust runs the carver loop; Java supplies each neighbour's carver biome's carvers.
			ChunkPos chunkPos = chunkAccess.getPos();
			List<NativeCarvers.Neighbour> neighbours = new ArrayList<>(289);
			for (int j = -8; j <= 8; j++) {
				for (int k = -8; k <= 8; k++) {
					ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j, chunkPos.z + k);
					List<ConfiguredWorldCarver<?>> carvers = new ArrayList<>();
					for (Holder<ConfiguredWorldCarver<?>> holder : this.carverBiome(chunks, randomState, chunkPos2).getCarvers()) carvers.add(holder.value());
					neighbours.add(new NativeCarvers.Neighbour(chunkPos2.x, chunkPos2.z, carvers));
				}
			}
			if (NativeCarvers.run(carvingContext, chunkAccess, aquifer, l, neighbours, biomeManager2::getBiome)) return;
		}
		this.carveAll(chunks, l, randomState, chunkAccess, worldgenRandom, carvingContext, biomeManager2, aquifer, carvingMask);
	}

	private BiomeGenerationSettings carverBiome(CarverChunks chunks, RandomState randomState, ChunkPos chunkPos2) {
		return chunks.getChunk(chunkPos2.x, chunkPos2.z).carverBiome(
			() -> this.getBiomeGenerationSettings(
				this.biomeSource.getNoiseBiome(QuartPos.fromBlock(chunkPos2.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos2.getMinBlockZ()), randomState.sampler())
			)
		);
	}

	/** The carver loop of applyCarvers. */
	private void carveAll(
		CarverChunks chunks, long l, RandomState randomState, ChunkAccess chunkAccess, WorldgenRandom worldgenRandom,
		CarvingContext carvingContext, BiomeManager biomeManager2, Aquifer aquifer, CarvingMask carvingMask
	) {
		ChunkPos chunkPos = chunkAccess.getPos();
		for (int j = -8; j <= 8; j++) {
			for (int k = -8; k <= 8; k++) {
				ChunkPos chunkPos2 = new ChunkPos(chunkPos.x + j, chunkPos.z + k);
				BiomeGenerationSettings biomeGenerationSettings = this.carverBiome(chunks, randomState, chunkPos2);
				Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomeGenerationSettings.getCarvers();
				int m = 0;

				for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
					ConfiguredWorldCarver<?> configuredWorldCarver = holder.value();
					worldgenRandom.setLargeFeatureSeed(l + m, chunkPos2.x, chunkPos2.z);
					if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
						configuredWorldCarver.carve(carvingContext, chunkAccess, biomeManager2::getBiome, worldgenRandom, aquifer, chunkPos2, carvingMask);
					}

					m++;
				}
			}
		}
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess) {
		NoiseSettings noiseSettings = this.settings.value().noiseSettings().clampToHeightAccessor(chunkAccess.getHeightAccessorForGeneration());
		int i = noiseSettings.minY();
		int j = Mth.floorDiv(i, noiseSettings.getCellHeight());
		int k = Mth.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
		return k <= 0 ? CompletableFuture.completedFuture(chunkAccess) : CompletableFuture.supplyAsync(() -> {
			int l = chunkAccess.getSectionIndex(k * noiseSettings.getCellHeight() - 1 + i);
			int m = chunkAccess.getSectionIndex(i);
			Set<LevelChunkSection> set = Sets.<LevelChunkSection>newHashSet();

			for (int n = l; n >= m; n--) {
				LevelChunkSection levelChunkSection = chunkAccess.getSection(n);
				levelChunkSection.acquire();
				set.add(levelChunkSection);
			}

			ChunkAccess var20;
			try {
				var20 = this.doFill(blender, structureManager, randomState, chunkAccess, j, k);
			} finally {
				for (LevelChunkSection levelChunkSection3 : set) {
					levelChunkSection3.release();
				}
			}

			return var20;
		}, Util.backgroundExecutor().forName("wgen_fill_noise"));
	}

	private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess, int i, int j) {
		NoiseChunk noiseChunk = chunkAccess.getOrCreateNoiseChunk(chunkAccessx -> this.createNoiseChunk(chunkAccessx, structureManager, blender, randomState));
		Heightmap heightmap = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap heightmap2 = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		// Rust owns the block loop and its writes when every rule it assumes holds.
		NativeNoiseFill nativeFill = NativeNoiseFill.prepare(this.settings.value(), noiseChunk, chunkAccess, heightmap, heightmap2, i, j);
		if (nativeFill != null) {
			nativeFill.run(i, j);
			return chunkAccess;
		}
		ChunkPos chunkPos = chunkAccess.getPos();
		int k = chunkPos.getMinBlockX();
		int l = chunkPos.getMinBlockZ();
		Aquifer aquifer = noiseChunk.aquifer();
		noiseChunk.initializeForFirstCellX();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		int m = noiseChunk.cellWidth();
		int n = noiseChunk.cellHeight();
		int o = 16 / m;
		int p = 16 / m;

		for (int q = 0; q < o; q++) {
			noiseChunk.advanceCellX(q);

			for (int r = 0; r < p; r++) {
				int s = chunkAccess.getSectionsCount() - 1;
				LevelChunkSection levelChunkSection = chunkAccess.getSection(s);

				for (int t = j - 1; t >= 0; t--) {
					noiseChunk.selectCellYZ(t, r);

					for (int u = n - 1; u >= 0; u--) {
						int v = (i + t) * n + u;
						int w = v & 15;
						int x = chunkAccess.getSectionIndex(v);
						if (s != x) {
							s = x;
							levelChunkSection = chunkAccess.getSection(x);
						}

						double d = (double)u / n;
						noiseChunk.updateForY(v, d);

						for (int y = 0; y < m; y++) {
							int z = k + q * m + y;
							int aa = z & 15;
							double e = (double)y / m;
							noiseChunk.updateForX(z, e);

							for (int ab = 0; ab < m; ab++) {
								int ac = l + r * m + ab;
								int ad = ac & 15;
								double f = (double)ab / m;
								noiseChunk.updateForZ(ac, f);
								BlockState blockState = noiseChunk.getInterpolatedState();
								if (blockState == null) {
									blockState = this.settings.value().defaultBlock();
								}

								blockState = this.debugPreliminarySurfaceLevel(noiseChunk, z, v, ac, blockState);
								if (blockState != AIR && !SharedConstants.debugVoidTerrain(chunkAccess.getPos())) {
									levelChunkSection.setBlockState(aa, w, ad, blockState, false);
									heightmap.update(aa, v, ad, blockState);
									heightmap2.update(aa, v, ad, blockState);
									if (aquifer.shouldScheduleFluidUpdate() && !blockState.getFluidState().isEmpty()) {
										mutableBlockPos.set(z, v, ac);
										chunkAccess.markPosForPostprocessing(mutableBlockPos);
									}
								}
							}
						}
					}
				}
			}

			noiseChunk.swapSlices();
		}

		noiseChunk.stopInterpolation();
		return chunkAccess;
	}

	private BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int i, int j, int k, BlockState blockState) {
		if (SharedConstants.DEBUG_AQUIFERS && k >= 0 && k % 4 == 0) {
			int l = noiseChunk.preliminarySurfaceLevel(i, k);
			int m = l + 8;
			if (j == m) {
				blockState = m < this.getSeaLevel() ? Blocks.SLIME_BLOCK.defaultBlockState() : Blocks.HONEY_BLOCK.defaultBlockState();
			}
		}

		return blockState;
	}

	@Override
	public int getGenDepth() {
		return this.settings.value().noiseSettings().height();
	}

	@Override
	public int getSeaLevel() {
		return this.settings.value().seaLevel();
	}

	@Override
	public int getMinY() {
		return this.settings.value().noiseSettings().minY();
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
		if (!this.settings.value().disableMobGeneration()) {
			ChunkPos chunkPos = worldGenRegion.getCenter();
			Holder<Biome> holder = worldGenRegion.getBiome(chunkPos.getWorldPosition().atY(worldGenRegion.getMaxY()));
			WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
			worldgenRandom.setDecorationSeed(worldGenRegion.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
			NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, holder, chunkPos, worldgenRandom);
		}
	}
}
