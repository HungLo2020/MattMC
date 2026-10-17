package net.minecraft.world.level.chunk;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkGeneratorStructureState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final RandomState randomState;
	private final BiomeSource biomeSource;
	private final long levelSeed;
	private final long concentricRingsSeed;
	private final Map<Structure, List<StructurePlacement>> placementsForStructure = new Object2ObjectOpenHashMap<>();
	private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions = new Object2ObjectArrayMap<>();
	private boolean hasGeneratedPositions;
	private final List<Holder<StructureSet>> possibleStructureSets;

	public static ChunkGeneratorStructureState createForFlat(RandomState randomState, long l, BiomeSource biomeSource, Stream<Holder<StructureSet>> stream) {
		List<Holder<StructureSet>> list = stream.filter(holder -> hasBiomesForStructureSet((StructureSet)holder.value(), biomeSource)).toList();
		return new ChunkGeneratorStructureState(randomState, biomeSource, l, 0L, list);
	}

	public static ChunkGeneratorStructureState createForNormal(RandomState randomState, long l, BiomeSource biomeSource, HolderLookup<StructureSet> holderLookup) {
		List<Holder<StructureSet>> list = holderLookup.listElements()
			.filter(reference -> hasBiomesForStructureSet((StructureSet)reference.value(), biomeSource))
			.map(reference -> (Holder<StructureSet>)reference)
			.collect(Collectors.toUnmodifiableList());
		return new ChunkGeneratorStructureState(randomState, biomeSource, l, l, list);
	}

	private static boolean hasBiomesForStructureSet(StructureSet structureSet, BiomeSource biomeSource) {
		Stream<Holder<Biome>> stream = structureSet.structures().stream().flatMap(structureSelectionEntry -> {
			Structure structure = structureSelectionEntry.structure().value();
			return structure.biomes().stream();
		});
		return stream.anyMatch(biomeSource.possibleBiomes()::contains);
	}

	private ChunkGeneratorStructureState(RandomState randomState, BiomeSource biomeSource, long l, long m, List<Holder<StructureSet>> list) {
		this.randomState = randomState;
		this.levelSeed = l;
		this.biomeSource = biomeSource;
		this.concentricRingsSeed = m;
		this.possibleStructureSets = list;
	}

	public List<Holder<StructureSet>> possibleStructureSets() {
		return this.possibleStructureSets;
	}

	private static final java.lang.invoke.MethodHandle PLACEMENTS_CREATE = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_structure_placements_create", java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_LONG,
			java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT));
	private static final java.lang.invoke.MethodHandle PLACEMENTS_RELEASE = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_structure_placements_release", java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.ADDRESS));
	private static final java.lang.invoke.MethodHandle PLACEMENTS_QUERY = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_structure_placements_query", java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS,
			java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS, java.lang.foreign.ValueLayout.JAVA_INT),
		java.lang.foreign.Linker.Option.critical(true));
	private static final boolean NATIVE_PLACEMENTS = !Boolean.getBoolean("mattmc.worldgen.javaStructurePlacement");
	private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
	/** The native placement table over {@link #possibleStructureSets} (then sets only exclusion zones name), or a failed marker. */
	private volatile Object nativePlacements;

	/**
	 * Per possible structure set, whether chunk (x, z) is one of its structure chunks: 1 yes,
	 * 0 no, 2 Java must ask {@code isStructureChunk}. Null keeps Java's checks for every set.
	 */
	@org.jetbrains.annotations.Nullable
	public byte[] nativeStructureChunks(int x, int z) {
		Object table = this.nativePlacements;
		if (table == null) table = this.createNativePlacements();
		if (!(table instanceof java.lang.foreign.MemorySegment handle)) return null;
		byte[] out = new byte[this.possibleStructureSets.size() + 64];
		try {
			int count = (int)PLACEMENTS_QUERY.invokeExact(handle, x, z, java.lang.foreign.MemorySegment.ofArray(out), out.length);
			return count < this.possibleStructureSets.size() ? null : out;
		} catch (Throwable failure) {
			throw new IllegalStateException("Native structure placement failed", failure);
		}
	}

	private synchronized Object createNativePlacements() {
		if (this.nativePlacements != null) return this.nativePlacements;
		Object result = Boolean.FALSE;
		if (NATIVE_PLACEMENTS) {
			// Rings exist before any chunk asks: Java's first check joins them too.
			this.ensureStructuresGenerated();
			List<Holder<StructureSet>> sets = new ArrayList<>(this.possibleStructureSets);
			java.util.Map<StructureSet, Integer> index = new java.util.IdentityHashMap<>();
			for (int i = 0; i < sets.size(); i++) index.putIfAbsent(sets.get(i).value(), i);
			for (int i = 0; i < sets.size(); i++) {
				var zone = sets.get(i).value().placement().nativeExclusionSet();
				if (zone != null && !index.containsKey(zone.value())) {
					index.put(zone.value(), sets.size());
					sets.add(zone);
				}
			}
			List<Integer> words = new ArrayList<>();
			words.add(sets.size());
			for (var set : sets) {
				var placement = set.value().placement();
				var zone = placement.nativeExclusionSet();
				List<ChunkPos> rings = placement instanceof ConcentricRingsStructurePlacement ringed ? this.getRingPositionsFor(ringed) : null;
				placement.nativePlacementWords(words, zone == null ? -1 : index.get(zone.value()), rings);
			}
			int[] array = words.stream().mapToInt(Integer::intValue).toArray();
			try (var arena = java.lang.foreign.Arena.ofConfined()) {
				var handle = (java.lang.foreign.MemorySegment)PLACEMENTS_CREATE.invokeExact(this.levelSeed,
					arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_INT, array), array.length);
				if (handle.address() != 0) {
					long address = handle.address();
					CLEANER.register(this, () -> {
						try {
							PLACEMENTS_RELEASE.invokeExact(java.lang.foreign.MemorySegment.ofAddress(address));
						} catch (Throwable ignored) {
						}
					});
					result = handle;
				}
			} catch (Throwable failure) {
				throw new IllegalStateException("Cannot create native structure placements", failure);
			}
		}
		this.nativePlacements = result;
		return result;
	}

	private void generatePositions() {
		Set<Holder<Biome>> set = this.biomeSource.possibleBiomes();
		this.possibleStructureSets().forEach(holder -> {
			StructureSet structureSet = (StructureSet)holder.value();
			boolean bl = false;

			for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSet.structures()) {
				Structure structure = structureSelectionEntry.structure().value();
				if (structure.biomes().stream().anyMatch(set::contains)) {
					((List)this.placementsForStructure.computeIfAbsent(structure, structurex -> new ArrayList())).add(structureSet.placement());
					bl = true;
				}
			}

			if (bl && structureSet.placement() instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
				this.ringPositions.put(concentricRingsStructurePlacement, this.generateRingPositions(holder, concentricRingsStructurePlacement));
			}
		});
	}

	private CompletableFuture<List<ChunkPos>> generateRingPositions(
		Holder<StructureSet> holder, ConcentricRingsStructurePlacement concentricRingsStructurePlacement
	) {
		if (concentricRingsStructurePlacement.count() == 0) {
			return CompletableFuture.completedFuture(List.of());
		} else {
			Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
			int i = concentricRingsStructurePlacement.distance();
			int j = concentricRingsStructurePlacement.count();
			List<CompletableFuture<ChunkPos>> list = new ArrayList(j);
			int k = concentricRingsStructurePlacement.spread();
			HolderSet<Biome> holderSet = concentricRingsStructurePlacement.preferredBiomes();
			RandomSource randomSource = RandomSource.create();
			randomSource.setSeed(this.concentricRingsSeed);
			double d = randomSource.nextDouble() * Math.PI * 2.0;
			int l = 0;
			int m = 0;

			for (int n = 0; n < j; n++) {
				double e = 4 * i + i * m * 6 + (randomSource.nextDouble() - 0.5) * (i * 2.5);
				int o = (int)Math.round(Math.cos(d) * e);
				int p = (int)Math.round(Math.sin(d) * e);
				RandomSource randomSource2 = randomSource.fork();
				list.add(
					CompletableFuture.supplyAsync(
						() -> {
							Pair<BlockPos, Holder<Biome>> pair = this.biomeSource
								.findBiomeHorizontal(
									SectionPos.sectionToBlockCoord(o, 8), 0, SectionPos.sectionToBlockCoord(p, 8), 112, holderSet, randomSource2, this.randomState.sampler()
								);
							if (pair != null) {
								BlockPos blockPos = pair.getFirst();
								return new ChunkPos(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
							} else {
								return new ChunkPos(o, p);
							}
						},
						Util.backgroundExecutor().forName("structureRings")
					)
				);
				d += (Math.PI * 2) / k;
				if (++l == k) {
					m++;
					l = 0;
					k += 2 * k / (m + 1);
					k = Math.min(k, j - n);
					d += randomSource.nextDouble() * Math.PI * 2.0;
				}
			}

			return Util.sequence(list).thenApply(listx -> {
				double dx = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) / 1000.0;
				LOGGER.debug("Calculation for {} took {}s", holder, dx);
				return listx;
			});
		}
	}

	public void ensureStructuresGenerated() {
		if (!this.hasGeneratedPositions) {
			this.generatePositions();
			this.hasGeneratedPositions = true;
		}
	}

	@Nullable
	public List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
		this.ensureStructuresGenerated();
		CompletableFuture<List<ChunkPos>> completableFuture = (CompletableFuture<List<ChunkPos>>)this.ringPositions.get(concentricRingsStructurePlacement);
		return completableFuture != null ? (List)completableFuture.join() : null;
	}

	public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> holder) {
		this.ensureStructuresGenerated();
		return (List<StructurePlacement>)this.placementsForStructure.getOrDefault(holder.value(), List.of());
	}

	public RandomState randomState() {
		return this.randomState;
	}

	public boolean hasStructureChunkInRange(Holder<StructureSet> holder, int i, int j, int k) {
		StructurePlacement structurePlacement = holder.value().placement();

		for (int l = i - k; l <= i + k; l++) {
			for (int m = j - k; m <= j + k; m++) {
				if (structurePlacement.isStructureChunk(this, l, m)) {
					return true;
				}
			}
		}

		return false;
	}

	public long getLevelSeed() {
		return this.levelSeed;
	}
}
