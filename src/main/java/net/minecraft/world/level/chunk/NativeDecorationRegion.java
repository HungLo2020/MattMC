package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.jetbrains.annotations.Nullable;

/**
 * A retained native region inside the existing FEATURES transaction. Rust
 * owns its writable metadata, dirty state, section leases and ordered effects;
 * Java pins the actual section owners and reconciles their exposed views at
 * public calls and callbacks. Layout: {@code levelgen/decoration/region.rs}.
 * {@link #refresh} imports current VM state after retrying unfinished adoption;
 * {@link #afterPlacement} and {@link #replayPending} expose native changes.
 */
public final class NativeDecorationRegion implements AutoCloseable {
	private static final int VERSION = 2, HEADER = 17, SECTION_WORDS = 3, CHUNKS = 9;
	private static final Cleaner CLEANER = Cleaner.create();
	private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle CLAIM = handle("claim", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle ACK = handle("ack", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
	private static final MethodHandle PROJECTED = handle("projected", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle RECONCILE = handle("reconcile", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle PENDING = handle("pending", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static MethodHandle handle(String suffix, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_decoration_region_" + suffix, descriptor);
	}

	private static final class Release implements Runnable {
		final java.util.concurrent.atomic.AtomicLong pointer = new java.util.concurrent.atomic.AtomicLong();
		@Override public void run() {
			long address = this.pointer.getAndSet(0);
			if (address != 0) {
				try { RELEASE.invokeExact(address); }
				catch (Throwable failure) { throw new IllegalStateException("Native region release failed", failure); }
			}
		}
	}
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
	private final Release release = new Release();
	// Borrowed projections retain this scope. GC cannot release the owner while
	// a projection segment is reachable, even if the region itself is abandoned.
	private final Cleaner.Cleanable cleanable = CLEANER.register(this.arena.scope(), this.release);
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
		int wordCount = HEADER + CHUNKS * sectionCount * SECTION_WORDS + CHUNKS * HEIGHTMAPS.length * this.heightmapWords;
		int outCount = Math.toIntExact(this.outSky() + 2 + CHUNKS * (long)this.skyWords);
		var descriptor = this.arena.allocate(ValueLayout.JAVA_LONG, 3);
		try {
			if ((int)CREATE.invokeExact(wordCount, outCount, descriptor) != 1) throw new IllegalStateException("Native region allocation failed");
			this.release.pointer.set(descriptor.getAtIndex(ValueLayout.JAVA_LONG, 0));
			this.words = MemorySegment.ofAddress(descriptor.getAtIndex(ValueLayout.JAVA_LONG, 1)).reinterpret(wordCount * 8L, this.arena, null);
			this.out = MemorySegment.ofAddress(descriptor.getAtIndex(ValueLayout.JAVA_LONG, 2)).reinterpret(outCount * 8L, this.arena, null);
		} catch (Throwable failure) {
			this.release.pointer.compareAndSet(0, descriptor.getAtIndex(ValueLayout.JAVA_LONG, 0));
			this.cleanable.clean();
			this.arena.close();
			if (failure instanceof RuntimeException runtime) throw runtime;
			if (failure instanceof Error error) throw error;
			throw new IllegalStateException("Native region adoption failed", failure);
		}
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
				if (biome == null || !this.sameBiomeIds(biome.binding)) return false;
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
		try {
			int pending = (int)PENDING.invokeExact(this.owner());
			if ((pending & 1) != 0) this.afterPlacement();
			if ((pending & 2) != 0) this.replayPending();
		} catch (RuntimeException | Error failure) { throw failure; }
		catch (Throwable failure) { throw new IllegalStateException("Native region pending adoption failed", failure); }
		if (setBlock && !this.writeReady) return REFRESH_HEIGHTMAP;
		long present = 0;
		for (int index = 0; index < CHUNKS; index++) {
			var chunk = this.chunks[index];
			var chunkSections = chunk.getSectionsForRead();
			for (int s = 0; s < this.sectionCount; s++) {
				var section = chunkSections[s];
				if (section != this.sections[index][s] || section == null || section.nativeGenerationInput() != this.blocks[index][s]
						|| !(section.getBiomes() instanceof PalettedContainer<?> container) || container.nativeLiveBiomes() != this.biomes[index][s]) {
					int reconciled = this.recapture(index, chunkSections);
					if (reconciled != REFRESHED) return reconciled;
					break;
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

	/** Admit the complete replacement before publishing any pointer or changing
	 * the pins. Cached captures can be shared by another binding, so replace the
	 * arrays rather than mutating a previously published capture. */
	private int recapture(int index, LevelChunkSection[] current) {
		var chunk = this.chunks[index];
		chunk.nativeDecorationCapture = null;
		if (current.length != this.sectionCount) return REFRESH_BLOCKS;
		var sections = current.clone();
		var blocks = new NativeLiveBlockSection[this.sectionCount];
		var counters = new MemorySegment[this.sectionCount];
		var biomes = new NativeLiveBiomeSection<?>[this.sectionCount];
		var captured = new long[this.sectionCount * SECTION_WORDS];
		for (int s = 0; s < this.sectionCount; s++) {
			var section = sections[s];
			if (section == null || (blocks[s] = section.nativeGenerationInput()) == null) return REFRESH_BLOCKS;
			if (!(section.getBiomes() instanceof PalettedContainer<?> container) || container.getClass() != PalettedContainer.class) return REFRESH_BIOMES;
			var biome = container.nativeLiveBiomes();
			if (biome == null || !this.sameBiomeIds(biome.binding)) return REFRESH_BIOMES;
			biomes[s] = biome;
			counters[s] = section.nativeGenerationCounters();
			captured[s * SECTION_WORDS] = blocks[s].stageOwner().address();
			captured[s * SECTION_WORDS + 1] = counters[s].address();
			captured[s * SECTION_WORDS + 2] = biome.owner.address();
		}
		DecorationTransaction.reconcileCurrent(chunk);
		this.sections[index] = sections;
		this.blocks[index] = blocks;
		this.counters[index] = counters;
		this.biomes[index] = biomes;
		MemorySegment.copy(captured, 0, this.words, ValueLayout.JAVA_LONG,
			(HEADER + (long)index * this.sectionCount * SECTION_WORDS) * 8L, captured.length);
		chunk.nativeDecorationCapture = new Captured(sections, blocks, counters, biomes, this.biomeBinding, captured);
		try { RECONCILE.invokeExact(this.owner()); }
		catch (RuntimeException | Error failure) { throw failure; }
		catch (Throwable failure) { throw new IllegalStateException("Native region reconciliation failed", failure); }
		return REFRESHED;
	}

	/** A fresh canonical factory can register a distinct binding for the same
	 * registry. Native biome IDs remain valid only when every holder has the
	 * identical position in both tables; never compare just lengths or IDs. */
	private boolean sameBiomeIds(NativeLiveBiomeSection.Binding<?> binding) {
		if (binding == this.biomeBinding) return true;
		var values = binding.values;
		var expected = this.biomeBinding.values;
		if (values.size() != expected.size()) return false;
		for (int i = 0; i < values.size(); i++) if (values.get(i) != expected.get(i)) return false;
		return true;
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
			this.projectHeightmap(this.chunks[bit / FINAL.length], FINAL[bit % FINAL.length],
				this.outFinal() + 2 + (long)bit * this.heightmapWords);
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
			this.projectHeightmap(this.chunks[bit / HEIGHTMAPS.length], HEIGHTMAPS[bit % HEIGHTMAPS.length],
				primedAt + 1 + (long)bit * this.heightmapWords);
		}
		long far = this.out.getAtIndex(ValueLayout.JAVA_LONG, OUT_FAR_COUNT);
		for (int i = 0; i < Math.min(far, FAR_LOG_ENTRIES); i++) {
			long at = OUT_FAR_LOG + i * 3L;
			this.region.ensureCanWrite(new BlockPos((int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at),
				(int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at + 1), (int)this.out.getAtIndex(ValueLayout.JAVA_LONG, at + 2)));
		}
		if (far > FAR_LOG_ENTRIES) throw new IllegalStateException("Native decoration logged " + far + " far writes");
		if (installPrimed) this.resetAfterSync();
	}

	private void projectHeightmap(ProtoChunk chunk, Heightmap.Types type, long at) {
		var map = chunk.getOrCreateHeightmapUnprimed(type);
		if (map.getClass() == Heightmap.class) {
			long[] raw = map.getRawData();
			if (raw.length == this.heightmapWords) {
				// The canonical setRawData operation copies into this same live
				// array. Preserve every escaped alias without a temporary array.
				MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, at * 8L, raw, 0, raw.length);
				return;
			}
		}
		// Preserve virtual dispatch for an escaped/custom heightmap entry.
		long[] raw = new long[this.heightmapWords];
		MemorySegment.copy(this.out, ValueLayout.JAVA_LONG, at * 8L, raw, 0, raw.length);
		chunk.setHeightmap(type, raw);
	}

	/** After a sync upcall applied Rust's state: clear what {@link #afterPlacement} already applied. */
	public void resetAfterSync() {
		try { PROJECTED.invokeExact(this.owner()); }
		catch (RuntimeException | Error failure) { throw failure; }
		catch (Throwable failure) { throw new IllegalStateException("Native region projection acknowledgement failed", failure); }
	}

	private MemorySegment owner() {
		// Enforce the confined scope's thread and close checks before a downcall.
		this.words.getAtIndex(ValueLayout.JAVA_LONG, 0);
		// The opaque argument must retain/acquire the same scope as words/out.
		// A global address alone can outlive the last Java reference to its owner.
		return MemorySegment.ofAddress(this.release.pointer.get()).reinterpret(1, this.arena, null);
	}

	/** Replays and acknowledges one owned batch. Nested calls claim their own
	 * effects; a failed replay returns only its unfinished suffix to this owner. */
	public void replayPending() {
		try (var scratch = Arena.ofConfined()) {
			var descriptor = scratch.allocate(ValueLayout.JAVA_LONG, 3);
			int completed = 0;
			try {
				CLAIM.invokeExact(this.owner(), descriptor);
				if (descriptor.getAtIndex(ValueLayout.JAVA_LONG, 0) == 0) return;
				int count = Math.toIntExact(descriptor.getAtIndex(ValueLayout.JAVA_LONG, 2));
				var events = MemorySegment.ofAddress(descriptor.getAtIndex(ValueLayout.JAVA_LONG, 1)).reinterpret(count * 40L, scratch, null);
				for (; completed < count; completed++) {
					long at = completed * 5L;
					this.replayEvent(events.getAtIndex(ValueLayout.JAVA_LONG, at), events.getAtIndex(ValueLayout.JAVA_LONG, at + 1),
						events.getAtIndex(ValueLayout.JAVA_LONG, at + 2), events.getAtIndex(ValueLayout.JAVA_LONG, at + 3),
						events.getAtIndex(ValueLayout.JAVA_LONG, at + 4));
				}
			} finally {
				long batch = descriptor.getAtIndex(ValueLayout.JAVA_LONG, 0);
				if (batch != 0) ACK.invokeExact(batch, completed);
			}
		} catch (RuntimeException | Error failure) { throw failure; }
		catch (Throwable failure) { throw new IllegalStateException("Native region effects failed", failure); }
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
		for (int i = 0; i < count; i++) {
			int at = i * 5;
			this.replayEvent(events[at], events[at + 1], events[at + 2], events[at + 3], events[at + 4]);
		}
	}

	private void replayEvent(long event, long x, long y, long z, long argument) {
			var states = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY;
			var pos = new BlockPos((int)x, (int)y, (int)z);
			ChunkAccess chunk = this.region.getChunk(pos);
			int kind = (int)event;
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
				default -> throw new IllegalStateException("Unknown native decoration event " + event);
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
		this.cleanable.clean();
		Reference.reachabilityFence(this.blocks);
		Reference.reachabilityFence(this.biomes);
		Reference.reachabilityFence(this.counters);
	}
}
