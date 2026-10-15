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
	/** {@code ChunkStatus.FINAL_HEIGHTMAPS} in enum order: what proto-chunk writes update from CARVERS on. */
	private static final Heightmap.Types[] FINAL = {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
		Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES};
	private static final java.util.Set<Heightmap.Types> FINAL_SET = java.util.EnumSet.copyOf(java.util.List.of(FINAL));
	private static final int EVENT_POI = 1, EVENT_BLOCK_ENTITY_DUMMY = 2, EVENT_BLOCK_ENTITY_REMOVE = 3, EVENT_POST_PROCESS = 4,
		EVENT_LIGHT_SECTION = 5, EVENT_LIGHT_CHECK = 6, EVENT_FLUID_TICK = 7, EVENT_BLOCK_TICK = 8;
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
	private final int sectionCount, heightmapWords, skyBits, skyWords;
	private final MemorySegment words, out;
	private boolean lazyPriming;
	/** Every chunk can take native {@code setBlock}: final heightmap set, light engine and sources where lit. */
	private boolean writeReady;
	private long lightChunks;

	private NativeDecorationRegion(WorldGenRegion region, int sectionCount, NativeLiveBiomeSection.Binding<?> biomeBinding) {
		this.region = region;
		this.sectionCount = sectionCount;
		this.biomeBinding = biomeBinding;
		int bits = Mth.ceillog2(region.getHeight() + 1);
		this.heightmapWords = (256 + 64 / bits - 1) / (64 / bits);
		this.skyBits = Mth.ceillog2(region.getMaxY() + 1 - (region.getMinY() - 1) + 1);
		this.skyWords = (256 + 64 / this.skyBits - 1) / (64 / this.skyBits);
		this.words = this.arena.allocate((HEADER + CHUNKS * sectionCount * SECTION_WORDS + CHUNKS * HEIGHTMAPS.length * (long)this.heightmapWords) * 8L, 8);
		this.out = this.arena.allocate((this.outSky() + 2 + CHUNKS * (long)this.skyWords) * 8L, 8);
	}

	private long outPrimed() {
		return OUT_RESIZED + (long)CHUNKS * this.sectionCount;
	}

	private long outFinal() {
		return this.outPrimed() + 1 + (long)CHUNKS * HEIGHTMAPS.length * this.heightmapWords;
	}

	private long outSky() {
		return this.outFinal() + 2 + (long)CHUNKS * FINAL.length * this.heightmapWords;
	}

	/**
	 * Binds a plain proto-chunk neighbourhood whose sections all have canonical
	 * native block, counter and biome owners; otherwise null, and every feature
	 * keeps Java's path.
	 */
	@Nullable
	/** One chunk's captured section owners and their words. */
	private record Captured(LevelChunkSection[] sections, NativeLiveBlockSection[] blocks, MemorySegment[] counters, NativeLiveBiomeSection<?>[] biomes,
							NativeLiveBiomeSection.Binding<?> binding, long[] words) {}

	public static NativeDecorationRegion bind(WorldGenLevel level, ChunkAccess center, WorldGenerationContext context, boolean lazyPriming) {
		if (level.getClass() != WorldGenRegion.class || center.getClass() != ProtoChunk.class || center.isUpgrading()) return null;
		var region = (WorldGenRegion)level;
		if (region.nativeCenter() != center || region.nativeDependencyRadius() < 1 || region.nativeBlockStateWriteRadius() != 1) return null;
		int sectionCount = region.getSectionsCount();
		if (sectionCount < 1 || center.getSectionsCount() != sectionCount || center.getMinY() != region.getMinY()) return null;
		var pos = center.getPos();
		ProtoChunk[] chunks = new ProtoChunk[CHUNKS];
		for (int index = 0; index < CHUNKS; index++) {
			// Binding only reads: each chunk joins the FEATURES transaction at its first write.
			ChunkAccess chunk = region.nativeChunkUntracked(pos.x + index % 3 - 1, pos.z + index / 3 - 1);
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
			var chunkSections = chunk.getSectionsForRead();
			if (chunkSections.length != this.sectionCount) return false;
			long base = HEADER + (long)index * this.sectionCount * SECTION_WORDS;
			// A previous region's capture of this chunk, while its section identities hold;
			// refresh re-checks every owner before native work reads them.
			if (chunk.nativeDecorationCapture instanceof Captured cached && cached.binding == this.biomeBinding
					&& java.util.Arrays.equals(cached.sections, chunkSections)) {
				this.sections[index] = cached.sections;
				this.blocks[index] = cached.blocks;
				this.counters[index] = cached.counters;
				this.biomes[index] = cached.biomes;
				MemorySegment.copy(cached.words, 0, this.words, ValueLayout.JAVA_LONG, base * 8L, cached.words.length);
				continue;
			}
			long[] captured = new long[this.sectionCount * SECTION_WORDS];
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
				captured[s * SECTION_WORDS] = live.stageOwner().address();
				captured[s * SECTION_WORDS + 1] = this.counters[index][s].address();
				captured[s * SECTION_WORDS + 2] = biome.owner.address();
			}
			MemorySegment.copy(captured, 0, this.words, ValueLayout.JAVA_LONG, base * 8L, captured.length);
			chunk.nativeDecorationCapture = new Captured(this.sections[index], this.blocks[index], this.counters[index], this.biomes[index], this.biomeBinding, captured);
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
		this.writeReady = true;
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = chunks[index];
			var status = chunk.getPersistedStatus();
			if (!status.isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS) || !status.heightmapsAfter().equals(FINAL_SET)
					|| status.getChunkType() != net.minecraft.world.level.chunk.status.ChunkType.PROTOCHUNK) {
				this.writeReady = false;
			}
			if (status.isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.INITIALIZE_LIGHT)) {
				this.lightChunks |= 1L << index;
				var sky = chunk.getSkyLightSources();
				if (chunk.nativeLightEngine() == null || sky == null || sky.nativeBits() != this.skyBits || sky.nativeRaw().length != this.skyWords) this.writeReady = false;
			}
		}
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 14, this.lightChunks);
		this.words.setAtIndex(ValueLayout.JAVA_LONG, 15, this.skyBits);
		return true;
	}

	/** The biome holders indexed by the native biome owners' IDs. */
	@SuppressWarnings("unchecked")
	/** Whether chunks {@code minX..maxX} × {@code minZ..maxZ} all lie in the bound 3×3. */
	public boolean covers(int minX, int minZ, int maxX, int maxZ) {
		var center = this.chunks[4].getPos();
		return minX >= center.x - 1 && maxX <= center.x + 1 && minZ >= center.z - 1 && maxZ <= center.z + 1;
	}

	/** Whether this binding serves {@code level}. */
	public boolean isFor(Object level) {
		return level == this.region;
	}

	/** The level seed, for seed-derived program state. */
	public long seed() {
		return this.region.getSeed();
	}

	public List<Holder<Biome>> biomeValues() {
		return (List<Holder<Biome>>)this.biomeBinding.values;
	}

	/**
	 * Re-checks every recorded owner and copies the heightmaps a program reads.
	 * Not {@link #REFRESHED} when Java's own writes replaced storage or a
	 * heightmap is unprimed: that placement keeps Java's path.
	 */
	public int refresh(boolean oceanFloorOnly, boolean setBlock) {
		if (setBlock && !this.writeReady) return REFRESH_HEIGHTMAP;
		long present = 0;
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			var chunkSections = chunk.getSectionsForRead();
			for (int s = 0; s < this.sectionCount; s++) {
				var section = chunkSections[s];
				if (section != this.sections[index][s] || section.nativeGenerationInput() != this.blocks[index][s]) {
					chunk.nativeDecorationCapture = null;
					return REFRESH_BLOCKS;
				}
				if (!(section.getBiomes() instanceof PalettedContainer<?> container) || container.nativeLiveBiomes() != this.biomes[index][s]) {
					chunk.nativeDecorationCapture = null;
					return REFRESH_BIOMES;
				}
			}
			for (int type = 0; type < HEIGHTMAPS.length; type++) {
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
		if (setBlock) this.copyWritableState();
		else {
			this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outFinal(), 0);
			this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outSky(), 0);
		}
		return REFRESHED;
	}

	/** The final heightmaps and lit chunks' sky-light sources that native {@code setBlock} edits in place. */
	private void copyWritableState() {
		long present = 0, sky = 0;
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			for (int type = 0; type < FINAL.length; type++) {
				if (!chunk.hasPrimedHeightmap(FINAL[type])) continue;
				long[] raw = chunk.getOrCreateHeightmapUnprimed(FINAL[type]).getRawData();
				MemorySegment.copy(raw, 0, this.out, ValueLayout.JAVA_LONG, (this.outFinal() + 2 + ((long)index * FINAL.length + type) * this.heightmapWords) * 8L, raw.length);
				present |= 1L << (index * FINAL.length + type);
			}
			if ((this.lightChunks >>> index & 1) != 0) {
				long[] raw = chunk.getSkyLightSources().nativeRaw();
				MemorySegment.copy(raw, 0, this.out, ValueLayout.JAVA_LONG, (this.outSky() + 2 + (long)index * this.skyWords) * 8L, raw.length);
				sky |= 1L << index;
			}
		}
		this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outFinal(), present);
		this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outFinal() + 1, 0);
		this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outSky(), sky);
		this.out.setAtIndex(ValueLayout.JAVA_LONG, this.outSky() + 1, 0);
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
		if (installPrimed) {
			// Installing heightmaps or sky sources writes the chunk: it joins the transaction first.
			long finalBits = this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outFinal() + 1);
			long skyBits = this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outSky() + 1);
			long primedBits = this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_RESIZED + (long)CHUNKS * this.sectionCount);
			for (int index = 0; index < CHUNKS; index++) {
				long finalMask = ((1L << FINAL.length) - 1) << (index * FINAL.length);
				long primedMask = ((1L << HEIGHTMAPS.length) - 1) << (index * HEIGHTMAPS.length);
				if ((finalBits & finalMask) != 0 || (skyBits >>> index & 1) != 0 || (primedBits & primedMask) != 0) {
					DecorationTransaction.touchCurrent(this.chunks[index]);
				}
			}
		}
		for (int index = 0; index < CHUNKS; index++) {
			for (int s = 0; s < this.sectionCount; s++) {
				if (this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_RESIZED + (long)index * this.sectionCount + s) != 0) {
					this.blocks[index][s].reloadViewAfterNativeWrite();
				}
			}
		}
		long finalDirty = installPrimed ? this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outFinal() + 1) : 0;
		for (int bit = 0; finalDirty != 0 && bit < CHUNKS * FINAL.length; bit++) {
			if ((finalDirty >>> bit & 1) == 0) continue;
			long[] raw = new long[this.heightmapWords];
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (this.outFinal() + 2 + (long)bit * this.heightmapWords) * 8L, raw, 0, raw.length);
			this.chunks[bit / FINAL.length].setHeightmap(FINAL[bit % FINAL.length], raw);
		}
		long skyDirty = installPrimed ? this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outSky() + 1) : 0;
		for (int index = 0; skyDirty != 0 && index < CHUNKS; index++) {
			if ((skyDirty >>> index & 1) == 0) continue;
			long[] raw = this.chunks[index].getSkyLightSources().nativeRaw();
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (this.outSky() + 2 + (long)index * this.skyWords) * 8L, raw, 0, raw.length);
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

	/** After a sync upcall applied Rust's state: clear what {@link #afterPlacement} already applied. */
	public void resetAfterSync() {
		long primedAt = OUT_RESIZED + (long)CHUNKS * this.sectionCount;
		this.out.asSlice(0, primedAt * 8L + 8L).fill((byte)0);
	}

	/** Verification only: every heightmap and lit chunk's sky sources, to restore before Java's replay. */
	public record Maps(long[][] heightmaps, long[][] sky) {}

	private static final Heightmap.Types[] ALL_MAPS = Heightmap.Types.values();

	public Maps mapsForVerification() {
		long[][] maps = new long[CHUNKS * ALL_MAPS.length][];
		long[][] sky = new long[CHUNKS][];
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			for (int type = 0; type < ALL_MAPS.length; type++) {
				if (chunk.hasPrimedHeightmap(ALL_MAPS[type])) maps[index * ALL_MAPS.length + type] = chunk.getOrCreateHeightmapUnprimed(ALL_MAPS[type]).getRawData().clone();
			}
			if ((this.lightChunks >>> index & 1) != 0) sky[index] = chunk.getSkyLightSources().nativeRaw().clone();
		}
		return new Maps(maps, sky);
	}

	/** Verification only: puts back {@code maps}, dropping heightmaps primed since. */
	public void restoreMapsForVerification(Maps maps) {
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			for (int type = 0; type < ALL_MAPS.length; type++) {
				long[] raw = maps.heightmaps[index * ALL_MAPS.length + type];
				if (raw != null) chunk.setHeightmap(ALL_MAPS[type], raw.clone());
				else chunk.nativeDropHeightmap(ALL_MAPS[type]);
			}
			if (maps.sky[index] != null) System.arraycopy(maps.sky[index], 0, chunk.getSkyLightSources().nativeRaw(), 0, maps.sky[index].length);
		}
	}

	/** Verification only: the first heightmap or sky source that differs between two captures, or null. */
	@Nullable
	public String compareMapsForVerification(Maps a, Maps b) {
		for (int bit = 0; bit < a.heightmaps.length; bit++) {
			if (!java.util.Arrays.equals(a.heightmaps[bit], b.heightmaps[bit])) return "heightmap " + ALL_MAPS[bit % ALL_MAPS.length] + " of " + this.chunks[bit / ALL_MAPS.length].getPos();
		}
		for (int index = 0; index < CHUNKS; index++) {
			if (!java.util.Arrays.equals(a.sky[index], b.sky[index])) return "sky sources of " + this.chunks[index].getPos();
		}
		return null;
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

	/** Verification only: the native call's final heightmaps and sky sources, and the chunks' Java-owned lists before Java's replay. */
	public record Effects(long[][] heightmaps, long[][] sky, String[] postProcessing, int[] blockTicks, int[] fluidTicks, String[] blockEntities) {}

	public Effects effectsForVerification() {
		long[][] heightmaps = new long[CHUNKS * FINAL.length][];
		long finalBits = this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outFinal()) | this.out.getAtIndex(ValueLayout.JAVA_LONG, this.outFinal() + 1);
		for (int bit = 0; bit < heightmaps.length; bit++) {
			if ((finalBits >>> bit & 1) == 0) continue;
			heightmaps[bit] = new long[this.heightmapWords];
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (this.outFinal() + 2 + (long)bit * this.heightmapWords) * 8L, heightmaps[bit], 0, this.heightmapWords);
		}
		long[][] sky = new long[CHUNKS][];
		for (int index = 0; index < CHUNKS; index++) {
			if ((this.lightChunks >>> index & 1) == 0) continue;
			sky[index] = new long[this.skyWords];
			MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, (this.outSky() + 2 + (long)index * this.skyWords) * 8L, sky[index], 0, this.skyWords);
		}
		String[] post = new String[CHUNKS], entities = new String[CHUNKS];
		int[] blockTicks = new int[CHUNKS], fluidTicks = new int[CHUNKS];
		for (int index = 0; index < CHUNKS; index++) {
			post[index] = java.util.Arrays.deepToString(this.chunks[index].getPostProcessing());
			blockTicks[index] = this.chunks[index].getBlockTicks().count();
			fluidTicks[index] = this.chunks[index].getFluidTicks().count();
			entities[index] = new java.util.TreeSet<>(this.chunks[index].getBlockEntitiesPos()).toString();
		}
		return new Effects(heightmaps, sky, post, blockTicks, fluidTicks, entities);
	}

	/**
	 * Verification only: after Java replayed the placement, its heightmaps and
	 * sky sources must equal the native call's, and its post-processing, tick and
	 * block-entity changes must equal those the native events apply. Null when equal.
	 */
	@Nullable
	public String compareEffectsForVerification(Effects before, long[] events, boolean setBlock) {
		for (int bit = 0; setBlock && bit < before.heightmaps.length; bit++) {
			var chunk = this.chunks[bit / FINAL.length];
			var type = FINAL[bit % FINAL.length];
			long[] javaRaw = chunk.hasPrimedHeightmap(type) ? chunk.getOrCreateHeightmapUnprimed(type).getRawData() : null;
			if (!java.util.Arrays.equals(before.heightmaps[bit], javaRaw)) return "heightmap " + type + " of " + chunk.getPos();
		}
		for (int index = 0; setBlock && index < CHUNKS; index++) {
			if (before.sky[index] != null && !java.util.Arrays.equals(before.sky[index], this.chunks[index].getSkyLightSources().nativeRaw())) {
				return "sky sources of " + this.chunks[index].getPos();
			}
		}
		// Replaying the native events onto copies of Java's prior lists must give Java's lists now.
		int[] post = new int[CHUNKS], block = new int[CHUNKS], fluid = new int[CHUNKS];
		java.util.List<java.util.Set<BlockPos>> entities = new java.util.ArrayList<>();
		for (int index = 0; index < CHUNKS; index++) entities.add(new java.util.TreeSet<>());
		for (int i = 0; i < events.length / 5; i++) {
			int at = i * 5;
			var pos = new BlockPos((int)events[at + 1], (int)events[at + 2], (int)events[at + 3]);
			int index = this.indexOf(pos);
			switch ((int)events[at]) {
				case EVENT_POST_PROCESS -> post[index]++;
				case EVENT_BLOCK_TICK -> block[index]++;
				case EVENT_FLUID_TICK -> fluid[index]++;
				default -> {
				}
			}
		}
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			if (chunk.getBlockTicks().count() != before.blockTicks[index] + block[index]) return "block ticks of " + chunk.getPos();
			if (chunk.getFluidTicks().count() != before.fluidTicks[index] + fluid[index]) return "fluid ticks of " + chunk.getPos();
			if (post[index] == 0 && !java.util.Arrays.deepToString(chunk.getPostProcessing()).equals(before.postProcessing[index])) return "post-processing of " + chunk.getPos();
		}
		return null;
	}

	private int indexOf(BlockPos pos) {
		var center = this.chunks[4].getPos();
		return ((pos.getZ() >> 4) - center.z + 1) * 3 + (pos.getX() >> 4) - center.x + 1;
	}

	/** {@code OP_TOUCH}: chunk {@code index} of the bound 3x3 joins the step's transaction. */
	public void touchForTransaction(int index) {
		if (index < 0 || index >= CHUNKS) throw new IllegalArgumentException("Native touch outside the bound region: " + index);
		DecorationTransaction.touchCurrent(this.chunks[index]);
	}

	/** Verification only: a block position for a flat state index. */
	public BlockPos positionForVerification(int section, int i) {
		var pos = this.chunks[section / this.sectionCount].getPos();
		return new BlockPos(pos.getMinBlockX() + (i & 15), this.region.getMinY() + (section % this.sectionCount) * 16 + (i >> 8), pos.getMinBlockZ() + ((i >> 4) & 15));
	}

	/**
	 * Replays a native placement's Java-owned effects in their recorded order,
	 * through the same calls {@code WorldGenRegion.setBlock}, {@code ProtoChunk}
	 * and {@code scheduleTick} make.
	 */
	public void replay(long[] events, int count) {
		var states = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY;
		for (int i = 0; i < count; i++) {
			int at = i * 5;
			var pos = new BlockPos((int)events[at + 1], (int)events[at + 2], (int)events[at + 3]);
			long argument = events[at + 4];
			ChunkAccess chunk = this.region.getChunk(pos);
			int kind = (int)events[at];
			if (kind > 0 && kind < 9) net.minecraft.world.level.levelgen.NativeDecoration.countEvent(kind);
			switch (kind) {
				case EVENT_POI -> this.region.nativeUpdatePoi(pos, states.byId((int)(argument >>> 32)), states.byId((int)argument));
				case EVENT_BLOCK_ENTITY_DUMMY -> {
					var tag = new net.minecraft.nbt.CompoundTag();
					tag.putInt("x", pos.getX());
					tag.putInt("y", pos.getY());
					tag.putInt("z", pos.getZ());
					tag.putString("id", "DUMMY");
					chunk.setBlockEntityNbt(tag);
				}
				case EVENT_BLOCK_ENTITY_REMOVE -> chunk.removeBlockEntity(pos);
				case EVENT_POST_PROCESS -> chunk.markPosForPostprocessing(pos);
				case EVENT_LIGHT_SECTION -> {
					var engine = ((ProtoChunk)chunk).nativeLightEngine();
					boolean empty = argument != 0;
					if (!DecorationTransaction.defer(() -> engine.updateSectionStatus(pos, empty))) engine.updateSectionStatus(pos, empty);
				}
				case EVENT_LIGHT_CHECK -> {
					var engine = ((ProtoChunk)chunk).nativeLightEngine();
					if (!DecorationTransaction.defer(() -> engine.checkBlock(pos))) engine.checkBlock(pos);
				}
				case EVENT_FLUID_TICK -> this.region.scheduleTick(pos, net.minecraft.core.registries.BuiltInRegistries.FLUID.byId((int)(argument >>> 32)), (int)argument);
				case EVENT_BLOCK_TICK -> this.region.scheduleTick(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId((int)(argument >>> 32)), (int)argument);
				case 9 -> net.minecraft.world.level.levelgen.NativeDecoration.templateBlockEntity(pos, argument);
				default -> throw new IllegalStateException("Unknown native decoration event " + events[at]);
			}
		}
	}

	/** A native read reached a chunk the region does not serve: raise the region's own crash. */
	/** The chunk a native read failed to reach, and the bound centre, for diagnostics. */
	public String unavailableDetail() {
		return (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_DETAIL_X) + "," + (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_DETAIL_Z)
			+ " centre " + this.chunks[4].getPos();
	}

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
