package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {
    private volatile Boolean aquiferBatchSafe;
    // The shared native preliminary surface program: unset, a program, or UNSUPPORTED.
    private static final Object UNSUPPORTED = new Object();
    private volatile Object nativeSurfaceLevel, nativeFluidSources, nativeBiomeFill;
    // The per-seed chunk noise programs, for the settings they were compiled with.
    private volatile Object nativeChunkNoise;
    private volatile NoiseGeneratorSettings nativeChunkNoiseSettings;
    @org.jetbrains.annotations.Nullable
    NativeChunkNoise nativeChunkNoise(NoiseGeneratorSettings settings) {
        if (!NativeChunkNoise.enabled()) return null;
        Object result = nativeChunkNoise;
        if (result == null) {
            synchronized (this) {
                result = nativeChunkNoise;
                if (result == null) {
                    NativeChunkNoise compiled = NativeChunkNoise.compile(this, settings, this.cellPrograms);
                    nativeChunkNoiseSettings = settings;
                    nativeChunkNoise = result = compiled == null ? UNSUPPORTED : compiled;
                }
            }
        }
        // Ore veins and aquifers come from the settings; other settings keep Java.
        return result == UNSUPPORTED || nativeChunkNoiseSettings != settings ? null : (NativeChunkNoise)result;
    }
    // The shared native aquifer noise source program, or null to keep Java's sources.
    @org.jetbrains.annotations.Nullable
    NativeFluidSources nativeFluidSources() {
        if (!NativeFluidSources.enabled()) return null;
        Object result = nativeFluidSources;
        if (result == null) {
            synchronized (this) {
                result = nativeFluidSources;
                if (result == null) {
                    NativeFluidSources compiled = NativeFluidSources.compile(this.router);
                    nativeFluidSources = result = compiled == null ? UNSUPPORTED : compiled;
                }
            }
        }
        return result == UNSUPPORTED ? null : (NativeFluidSources)result;
    }
    // The shared native climate program for chunk biome fills, or null to keep Java's fill.
    @org.jetbrains.annotations.Nullable
    NativeBiomeFill nativeBiomeFill() {
        if (!NativeBiomeFill.enabled()) return null;
        Object result = nativeBiomeFill;
        if (result == null) {
            synchronized (this) {
                result = nativeBiomeFill;
                if (result == null) {
                    NativeBiomeFill compiled = NativeBiomeFill.compile(this.router);
                    nativeBiomeFill = result = compiled == null ? UNSUPPORTED : compiled;
                }
            }
        }
        return result == UNSUPPORTED ? null : (NativeBiomeFill)result;
    }
    @org.jetbrains.annotations.Nullable
    NativeSurfaceLevel nativeSurfaceLevel() {
        if (!NativeSurfaceLevel.enabled()) return null;
        Object result = nativeSurfaceLevel;
        if (result == null) {
            synchronized (this) {
                result = nativeSurfaceLevel;
                if (result == null) {
                    NativeSurfaceLevel compiled = NativeSurfaceLevel.compile(this.router.preliminarySurfaceLevel());
                    nativeSurfaceLevel = result = compiled == null ? UNSUPPORTED : compiled;
                }
            }
        }
        return result == UNSUPPORTED ? null : (NativeSurfaceLevel)result;
    }
    boolean aquiferBatchSafe() {
        Boolean result=aquiferBatchSafe;
        if(result==null)aquiferBatchSafe=result=NativeAquiferSources.safe(router);
        return result;
    }

	final PositionalRandomFactory random;
	private final HolderGetter<NormalNoise.NoiseParameters> noises;
	private final NoiseRouter router;
	private final Climate.Sampler sampler;
	private final SurfaceSystem surfaceSystem;
	private final PositionalRandomFactory aquiferRandom;
	private final PositionalRandomFactory oreRandom;
	private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
	private final Map<ResourceLocation, PositionalRandomFactory> positionalRandoms;

	public static RandomState create(HolderGetter.Provider provider, ResourceKey<NoiseGeneratorSettings> resourceKey, long l) {
		return create(provider.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(resourceKey).value(), provider.lookupOrThrow(Registries.NOISE), l);
	}

	public static RandomState create(NoiseGeneratorSettings noiseGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> holderGetter, long l) {
		return new RandomState(noiseGeneratorSettings, holderGetter, l);
	}

	private RandomState(NoiseGeneratorSettings noiseGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> holderGetter, long l) {
		this.random = noiseGeneratorSettings.getRandomSource().newInstance(l).forkPositional();
		this.noises = holderGetter;
		this.aquiferRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("aquifer")).forkPositional();
		this.oreRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("ore")).forkPositional();
		this.noiseIntances = new ConcurrentHashMap();
		this.positionalRandoms = new ConcurrentHashMap();
		this.surfaceSystem = new SurfaceSystem(this, noiseGeneratorSettings.defaultBlock(), noiseGeneratorSettings.seaLevel(), this.random);
		final boolean bl = noiseGeneratorSettings.useLegacyRandomSource();

		class NoiseWiringHelper implements DensityFunction.Visitor {
			private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();

			private RandomSource newLegacyInstance(long l) {
				return new LegacyRandomSource(l + l);
			}

			@Override
			public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
				Holder<NormalNoise.NoiseParameters> holder = noiseHolder.noiseData();
				if (bl) {
					if (holder.is(Noises.TEMPERATURE)) {
						NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(0L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
						return new DensityFunction.NoiseHolder(holder, normalNoise);
					}

					if (holder.is(Noises.VEGETATION)) {
						NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(1L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
						return new DensityFunction.NoiseHolder(holder, normalNoise);
					}

					if (holder.is(Noises.SHIFT)) {
						NormalNoise normalNoise = NormalNoise.create(RandomState.this.random.fromHashOf(Noises.SHIFT.location()), new NormalNoise.NoiseParameters(0, 0.0));
						return new DensityFunction.NoiseHolder(holder, normalNoise);
					}
				}

				NormalNoise normalNoise = RandomState.this.getOrCreateNoise((ResourceKey<NormalNoise.NoiseParameters>)holder.unwrapKey().orElseThrow());
				return new DensityFunction.NoiseHolder(holder, normalNoise);
			}

			private DensityFunction wrapNew(DensityFunction densityFunction) {
				if (densityFunction instanceof BlendedNoise blendedNoise) {
					RandomSource randomSource = bl ? this.newLegacyInstance(0L) : RandomState.this.random.fromHashOf(ResourceLocation.withDefaultNamespace("terrain"));
					return blendedNoise.withNewRandom(randomSource);
				} else {
					return (DensityFunction)(densityFunction instanceof DensityFunctions.EndIslandDensityFunction
						? new DensityFunctions.EndIslandDensityFunction(l)
						: densityFunction);
				}
			}

			@Override
			public DensityFunction apply(DensityFunction densityFunction) {
				return (DensityFunction)this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
			}
		}

		this.router = noiseGeneratorSettings.noiseRouter().mapAll(new NoiseWiringHelper());
		DensityFunction.Visitor visitor = new DensityFunction.Visitor() {
			private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();

			private DensityFunction wrapNew(DensityFunction densityFunction) {
				if (densityFunction instanceof DensityFunctions.HolderHolder holderHolder) {
					return holderHolder.function().value();
				} else {
					return densityFunction instanceof DensityFunctions.Marker marker ? marker.wrapped() : densityFunction;
				}
			}

			@Override
			public DensityFunction apply(DensityFunction densityFunction) {
				return (DensityFunction)this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
			}
		};
		this.sampler = new Climate.Sampler(
			this.router.temperature().mapAll(visitor),
			this.router.vegetation().mapAll(visitor),
			this.router.continents().mapAll(visitor),
			this.router.erosion().mapAll(visitor),
			this.router.depth().mapAll(visitor),
			this.router.ridges().mapAll(visitor),
			noiseGeneratorSettings.spawnTarget()
		);
	}

	public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> resourceKey) {
		return (NormalNoise)this.noiseIntances.computeIfAbsent(resourceKey, resourceKey2 -> Noises.instantiate(this.noises, this.random, resourceKey));
	}

	public PositionalRandomFactory getOrCreateRandomFactory(ResourceLocation resourceLocation) {
		return (PositionalRandomFactory)this.positionalRandoms
			.computeIfAbsent(resourceLocation, resourceLocation2 -> this.random.fromHashOf(resourceLocation).forkPositional());
	}

    private final Map<DensityFunction, DensityFunction> nativeDensities = new ConcurrentHashMap<>();
    private final Map<java.util.List<NativeUnaryProgram.Step>, NativeUnaryProgram> unaryPrograms = new ConcurrentHashMap<>();
    private final Map<NativeOperands.Key,NativeDensityProgram> operandPrograms = new ConcurrentHashMap<>();

    private final Map<NativeCellDensity.Key,NativeDensityProgram> cellPrograms = new ConcurrentHashMap<>();
    DensityFunction optimizeUnary(DensityFunction function) {
        return NativeUnary.tree(function, unaryPrograms, operandPrograms, cellPrograms);
    }

    DensityFunction optimizeDensity(DensityFunction function) {
        if (!NativeDensity.candidate(function)) return function;
        DensityFunction cached = nativeDensities.get(function);
        if (cached != null) return cached;
        DensityFunction compiled = NativeDensity.compile(function);
        // Never retain unsupported expressions: they can reference a live NoiseChunk.
        if (compiled == function) return function;
        DensityFunction previous = nativeDensities.putIfAbsent(function, compiled);
        return previous == null ? compiled : previous;
    }

	public NoiseRouter router() {
		return this.router;
	}

	public Climate.Sampler sampler() {
		return this.sampler;
	}

	public SurfaceSystem surfaceSystem() {
		return this.surfaceSystem;
	}

	public PositionalRandomFactory aquiferRandom() {
		return this.aquiferRandom;
	}

	public PositionalRandomFactory oreRandom() {
		return this.oreRandom;
	}
}
