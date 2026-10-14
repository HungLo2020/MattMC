package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.jetbrains.annotations.Nullable;

/**
 * One decoration call's {@link WorldGenRegion} as Rust binding words: the
 * center chunk and its eight neighbours' live section owners, their
 * world-generation heightmaps and the biome zoom seed. Layout:
 * {@code levelgen/decoration/region.rs}. The binding pins every owner it
 * records; {@link #refresh} re-checks them and copies current heightmaps
 * before each native placement, because Java features run in between.
 */
public final class NativeDecorationRegion implements AutoCloseable {
	private static final int VERSION = 1, HEADER = 16, SECTION_WORDS = 3, CHUNKS = 9;
	private static final Heightmap.Types[] HEIGHTMAPS = {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG};
	private static final int OUT_DETAIL_X = 0, OUT_DETAIL_Z = 1, OUT_FAR_COUNT = 2, OUT_FAR_LOG = 3, FAR_LOG_ENTRIES = 16;
	private static final int OUT_RESIZED = OUT_FAR_LOG + FAR_LOG_ENTRIES * 3;

	public static final int REFRESHED = 0, REFRESH_BLOCKS = 1, REFRESH_BIOMES = 2, REFRESH_HEIGHTMAP = 3;
	private final WorldGenRegion region;
	private final Arena arena = Arena.ofConfined();
	private final ProtoChunk[] chunks = new ProtoChunk[CHUNKS];
	private final LevelChunkSection[][] sections = new LevelChunkSection[CHUNKS][];
	private final NativeLiveBlockSection[][] blocks = new NativeLiveBlockSection[CHUNKS][];
	private final MemorySegment[][] counters = new MemorySegment[CHUNKS][];
	private final NativeLiveBiomeSection<?>[][] biomes = new NativeLiveBiomeSection<?>[CHUNKS][];
	private final NativeLiveBiomeSection.Binding<?> biomeBinding;
	private final int sectionCount, heightmapWords;
	private final MemorySegment words, out;
	private boolean lazyPriming;

	private NativeDecorationRegion(WorldGenRegion region, int sectionCount, NativeLiveBiomeSection.Binding<?> biomeBinding) {
		this.region = region;
		this.sectionCount = sectionCount;
		this.biomeBinding = biomeBinding;
		int bits = Mth.ceillog2(region.getHeight() + 1);
		this.heightmapWords = (256 + 64 / bits - 1) / (64 / bits);
		this.words = this.arena.allocate((HEADER + CHUNKS * sectionCount * SECTION_WORDS + CHUNKS * HEIGHTMAPS.length * (long)this.heightmapWords) * 8L, 8);
		this.out = this.arena.allocate((OUT_RESIZED + CHUNKS * (long)sectionCount + 1 + CHUNKS * HEIGHTMAPS.length * (long)this.heightmapWords) * 8L, 8);
	}

	/**
	 * Binds a plain proto-chunk neighbourhood whose sections all have canonical
	 * native block, counter and biome owners; otherwise null, and every feature
	 * keeps Java's path.
	 */
	@Nullable
	public static NativeDecorationRegion bind(WorldGenLevel level, ChunkAccess center, WorldGenerationContext context, boolean lazyPriming) {
		if (level.getClass() != WorldGenRegion.class || center.getClass() != ProtoChunk.class || center.isUpgrading()) return null;
		var region = (WorldGenRegion)level;
		if (region.nativeCenter() != center || region.nativeDependencyRadius() < 1 || region.nativeBlockStateWriteRadius() != 1) return null;
		int sectionCount = region.getSectionsCount();
		if (sectionCount < 1 || center.getSectionsCount() != sectionCount || center.getMinY() != region.getMinY()) return null;
		var pos = center.getPos();
		ProtoChunk[] chunks = new ProtoChunk[CHUNKS];
		for (int index = 0; index < CHUNKS; index++) {
			ChunkAccess chunk = region.getChunk(pos.x + index % 3 - 1, pos.z + index / 3 - 1);
			if (chunk.getClass() != ProtoChunk.class || chunk.getSectionsCount() != sectionCount || chunk.getMinY() != center.getMinY()) return null;
			chunks[index] = (ProtoChunk)chunk;
		}
		var result = new NativeDecorationRegion(region, sectionCount, findBinding(chunks));
		try {
			if (result.biomeBinding == null || !result.capture(chunks, context, lazyPriming)) {
				result.close();
				return null;
			}
			return result;
		} catch (RuntimeException | Error failure) {
			result.close();
			throw failure;
		}
	}

	@Nullable
	private static NativeLiveBiomeSection.Binding<?> findBinding(ProtoChunk[] chunks) {
		var section = chunks[4].getSection(0);
		if (!(section.getBiomes() instanceof PalettedContainer<?> container) || container.getClass() != PalettedContainer.class) return null;
		var biomes = container.nativeLiveBiomes();
		return biomes == null ? null : biomes.binding;
	}

	private boolean capture(ProtoChunk[] chunks, WorldGenerationContext context, boolean lazyPriming) {
		var pos = chunks[4].getPos();
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = chunks[index];
			this.chunks[index] = chunk;
			var chunkSections = chunk.getSections();
			if (chunkSections.length != this.sectionCount) return false;
			this.sections[index] = chunkSections.clone();
			this.blocks[index] = new NativeLiveBlockSection[this.sectionCount];
			this.counters[index] = new MemorySegment[this.sectionCount];
			this.biomes[index] = new NativeLiveBiomeSection<?>[this.sectionCount];
			for (int s = 0; s < this.sectionCount; s++) {
				var section = chunkSections[s];
				if (section == null) return false;
				var live = section.nativeGenerationInput();
				if (live == null) return false;
				if (!(section.getBiomes() instanceof PalettedContainer<?> container) || container.getClass() != PalettedContainer.class) return false;
				var biome = container.nativeLiveBiomes();
				if (biome == null || biome.binding != this.biomeBinding) return false;
				this.blocks[index][s] = live;
				this.counters[index][s] = section.nativeGenerationCounters();
				this.biomes[index][s] = biome;
				long at = HEADER + ((long)index * this.sectionCount + s) * SECTION_WORDS;
				this.words.setAtIndex(ValueLayout.JAVA_LONG, at, live.stageOwner().address());
				this.words.setAtIndex(ValueLayout.JAVA_LONG, at + 1, this.counters[index][s].address());
				this.words.setAtIndex(ValueLayout.JAVA_LONG, at + 2, biome.owner.address());
			}
		}
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 0, VERSION);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 1, pos.x);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 2, pos.z);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 3, this.region.getMinY());
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 4, this.sectionCount);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 5, this.region.nativeBlockStateWriteRadius());
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 6, context.getMinGenY());
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 7, context.getGenDepth());
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 8, this.region.getBiomeManager().biomeZoomSeed);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 9, Mth.ceillog2(this.region.getHeight() + 1));
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 10, this.heightmapWords);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 12, (1L << CHUNKS) - 1);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 13, lazyPriming ? 1 : 0);
		this.lazyPriming = lazyPriming;
		return true;
	}

	/** The biome holders indexed by the native biome owners' IDs. */
	@SuppressWarnings("unchecked")
	public List<Holder<Biome>> biomeValues() {
		return (List<Holder<Biome>>)this.biomeBinding.values;
	}

	/**
	 * Re-checks every recorded owner and copies the heightmaps a program reads.
	 * Not {@link #REFRESHED} when Java's own writes replaced storage or a
	 * heightmap is unprimed: that placement keeps Java's path.
	 */
	public int refresh(boolean oceanFloorOnly) {
		long present = 0;
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			var chunkSections = chunk.getSections();
			for (int s = 0; s < this.sectionCount; s++) {
				var section = chunkSections[s];
				if (section != this.sections[index][s] || section.nativeGenerationInput() != this.blocks[index][s]) return REFRESH_BLOCKS;
				if (!(section.getBiomes() instanceof PalettedContainer<?> container) || container.nativeLiveBiomes() != this.biomes[index][s]) return REFRESH_BIOMES;
			}
			for (int type = 0; type < HEIGHTMAPS.length; type++) {
				if (oceanFloorOnly && HEIGHTMAPS[type] != Heightmap.Types.OCEAN_FLOOR_WG) continue;
				if (!chunk.hasPrimedHeightmap(HEIGHTMAPS[type])) {
					// Rust primes it at its first read, exactly as ChunkAccess.getHeight would.
					if (this.lazyPriming) continue;
					return REFRESH_HEIGHTMAP;
				}
				long[] raw = chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[type]).getRawData();
				if (raw.length != this.heightmapWords) return REFRESH_HEIGHTMAP;
				long at = HEADER + (long)CHUNKS * this.sectionCount * SECTION_WORDS + ((long)index * HEIGHTMAPS.length + type) * this.heightmapWords;
				MemorySegment.copy(raw, 0, this.words, ValueLayout.JAVA_LONG, at * 8L, raw.length);
				present |= 1L << (index * HEIGHTMAPS.length + type);
			}
		}
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 11, present);
		return REFRESHED;
	}

	public MemorySegment words() {
		return this.words;
	}

	public MemorySegment out() {
		return this.out;
	}

	/** After a native placement: follow grown palettes, install primed heightmaps and report far writes. */
	public void afterPlacement() {
		this.afterPlacement(true);
	}

	/** {@code installPrimed} false only in verification, which compares them with Java's own priming. */
	public void afterPlacement(boolean installPrimed) {
		for (int index = 0; index < CHUNKS; index++) {
			for (int s = 0; s < this.sectionCount; s++) {
				if (this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_RESIZED + (long)index * this.sectionCount + s) != 0) {
					this.blocks[index][s].reloadViewAfterNativeWrite();
				}
			}
		}
		long primedAt = OUT_RESIZED + (long)CHUNKS * this.sectionCount;
		long primed = this.out.getAtIndex(ValueLayout.JAVA_LONG, primedAt);
		for (int bit = 0; installPrimed && bit < CHUNKS * HEIGHTMAPS.length; bit++) {
			if ((primed >>> bit & 1) == 0) continue;
			long[] raw = new long[this.heightmapWords];
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (primedAt + 1 + (long)bit * this.heightmapWords) * 8L, raw, 0, raw.length);
			this.chunks[bit / HEIGHTMAPS.length].setHeightmap(HEIGHTMAPS[bit % HEIGHTMAPS.length], raw);
		}
		long far = this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_FAR_COUNT);
		for (int i = 0; i < Math.min(far, FAR_LOG_ENTRIES); i++) {
			long at = OUT_FAR_LOG + i * 3L;
			this.region.ensureCanWrite(new BlockPos((int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at),
				(int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at + 1), (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at + 2)));
		}
		if (far > FAR_LOG_ENTRIES) throw new IllegalStateException("Native decoration logged " + far + " far writes");
	}

	/** Verification only: every bound section's state IDs, in binding order. */
	public int[][] captureStatesForVerification() {
		int[][] states = new int[CHUNKS * this.sectionCount][];
		for (int index = 0; index < CHUNKS; index++) {
			for (int s = 0; s < this.sectionCount; s++) {
				var snapshot = this.blocks[index][s].snapshot(null);
				int[] ids = new int[4096];
				for (int i = 0; i < 4096; i++) ids[i] = snapshot.stateId(i);
				states[index * this.sectionCount + s] = ids;
			}
		}
		return states;
	}

	/** Verification only: writes {@code states} back through Java's section path. */
	public void restoreStatesForVerification(int[][] states) {
		for (int index = 0; index < CHUNKS; index++) {
			for (int s = 0; s < this.sectionCount; s++) {
				int[] wanted = states[index * this.sectionCount + s];
				var current = this.captureSection(index, s);
				for (int i = 0; i < 4096; i++) {
					if (current[i] != wanted[i]) {
						this.sections[index][s].setBlockState(i & 15, i >> 8, (i >> 4) & 15, net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(wanted[i]), false);
					}
				}
			}
		}
	}

	private int[] captureSection(int index, int s) {
		var snapshot = this.blocks[index][s].snapshot(null);
		int[] ids = new int[4096];
		for (int i = 0; i < 4096; i++) ids[i] = snapshot.stateId(i);
		return ids;
	}

	/** Verification only: heightmaps Rust primed in the last call, compared with the chunk's own now. */
	public int primedMismatchesForVerification() {
		long primedAt = OUT_RESIZED + (long)CHUNKS * this.sectionCount;
		long primed = this.out.getAtIndex(ValueLayout.JAVA_LONG, primedAt);
		int mismatches = 0;
		for (int bit = 0; bit < CHUNKS * HEIGHTMAPS.length; bit++) {
			if ((primed >>> bit & 1) == 0) continue;
			var chunk = this.chunks[bit / HEIGHTMAPS.length];
			var type = HEIGHTMAPS[bit % HEIGHTMAPS.length];
			long[] raw = new long[this.heightmapWords];
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (primedAt + 1 + (long)bit * this.heightmapWords) * 8L, raw, 0, raw.length);
			if (!chunk.hasPrimedHeightmap(type)) {
				// Java's replay never read it: prime it as its first read would have, from current blocks.
				chunk.setHeightmap(type, raw);
			} else if (!java.util.Arrays.equals(raw, chunk.getOrCreateHeightmapUnprimed(type).getRawData())) {
				mismatches++;
			}
		}
		return mismatches;
	}

	/** Verification only: a block position for a flat state index. */
	public BlockPos positionForVerification(int section, int i) {
		var pos = this.chunks[section / this.sectionCount].getPos();
		return new BlockPos(pos.getMinBlockX() + (i & 15), this.region.getMinY() + (section % this.sectionCount) * 16 + (i >> 8), pos.getMinBlockZ() + ((i >> 4) & 15));
	}

	/** A native read reached a chunk the region does not serve: raise the region's own crash. */
	public RuntimeException unavailableChunk() {
		int x = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_DETAIL_X), z = (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_DETAIL_Z);
		this.region.getChunk(x, z);
		return new IllegalStateException("Native decoration reported unavailable chunk " + x + ", " + z);
	}

	@Override
	public void close() {
		this.arena.close();
		Reference.reachabilityFence(this.blocks);
		Reference.reachabilityFence(this.biomes);
		Reference.reachabilityFence(this.counters);
	}
}
