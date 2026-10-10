package net.minecraft.world.level.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;
import net.sodium.client.world.BitStorageExtension;
import net.sodium.client.world.PalettedContainerROExtension;
import org.jetbrains.annotations.Nullable;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T>, PalettedContainerROExtension<T> {
	private static final int MIN_PALETTE_BITS = 0;
	private volatile PalettedContainer.Data<T> data;
	@Nullable private volatile NativeLiveBlockSection nativeBlocks;
	@Nullable private volatile NativeLiveBiomeSection<T> nativeBiomes;
	private boolean escapedBiomeSingleton;
	NativeLiveBiomeSection<T> nativeLiveBiomes() {
        var biomes = this.nativeBiomes;
        if (biomes != null && biomes.compatibilityEscaped()) { this.materializeForMutation(); return null; }
        return biomes;
    }

	/** Test corruption and explicit compatibility writes transfer ownership first. */
	@VisibleForTesting
	Data<T> dataForCompatibilityMutation() { this.materializeForMutation(); return this.data; }

	private T currentPaletteValue(int id) {
		var live = this.nativeBlocks;
		if (live != null) return (T)live.paletteValue(id);
		var biomes = this.nativeLiveBiomes();
		return biomes != null ? biomes.paletteValue(id) : this.data.palette.valueFor(id);
	}

	NativeLiveBlockSection nativeLiveBlocks() { return this.nativeBlocks; }

	private Data<T> readData() {
		for (;;) {
			var live = this.nativeBlocks;
			if (live != null) return live.legacy(this.strategy);
			var biomes = this.nativeLiveBiomes();
			if (biomes != null) return biomes.legacy(this.strategy);
			var value = this.data;
			if (value != null) return value;
		}
	}

	private void adoptNative() {
		if (this.getClass() != PalettedContainer.class
			|| (this.escapedBiomeSingleton && this.data.storage.getBits() == 0)) return;
		var live = NativeLiveBlockSection.adopt(this.strategy, this.data);
		if (live != null) { this.nativeBlocks = live; this.data = null; }
		else { var biomes = NativeLiveBiomeSection.adopt(this.strategy, this.data);
			if (biomes != null) { this.nativeBiomes = biomes; this.data = null; } }
	}

	private void materializeForMutation() {
		var live = this.nativeBlocks;
		if (live != null) { this.data = live.legacy(this.strategy); this.nativeBlocks = null; }
		var biomes = this.nativeBiomes;
		if (biomes != null) { this.escapedBiomeSingleton = biomes.bits() == 0; var data = biomes.legacyForMutation(this.strategy); this.data = data; this.nativeBiomes = null; }
	}

	private static int nativeStateId(Object value) {
		if (value == null || value.getClass() != net.minecraft.world.level.block.state.BlockState.class) return -1;
		var state = (net.minecraft.world.level.block.state.BlockState)value;
		int id = net.minecraft.world.level.block.Block.getId(state);
		return net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(id) == state ? id : -1;
	}
	private final Strategy<T> strategy;
	private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer");

	// A coherent temporary export for legacy consumers; native bulk readers use the owner.
	// Mutating this export cannot change a native section; transfer ownership first.
	Data<T> dataForNativeScan() { return this.readData(); }

	Strategy<T> strategyForNativeSnapshot() { return this.strategy; }
	net.minecraft.core.IdMap<T> registryForNativeScan() { return this.strategy.globalMap(); }

	public void acquire() {
		this.threadingDetector.checkAndLock();
	}

	public void release() {
		this.threadingDetector.checkAndUnlock();
	}

	public static <T> Codec<PalettedContainer<T>> codecRW(Codec<T> codec, Strategy<T> strategy, T object) {
		PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = PalettedContainer::unpack;
		return codec(codec, strategy, object, unpacker);
	}

	public static <T> Codec<PalettedContainerRO<T>> codecRO(Codec<T> codec, Strategy<T> strategy, T object) {
		PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (strategyx, packedData) -> unpack(strategyx, packedData)
			.map(palettedContainer -> palettedContainer);
		return codec(codec, strategy, object, unpacker);
	}

	private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(
		Codec<T> codec, Strategy<T> strategy, T object, PalettedContainerRO.Unpacker<T, C> unpacker
	) {
		return RecordCodecBuilder.<PalettedContainerRO.PackedData<T>>create(
				instance -> instance.group(
						codec.mapResult(ExtraCodecs.orElsePartial(object)).listOf().fieldOf("palette").forGetter(pd -> pd.paletteEntries()),
						Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(pd -> pd.storage())
					)
					.apply(instance, (palette, storage) -> new PalettedContainerRO.PackedData<T>(palette, storage))
				)
				.comapFlatMap((PalettedContainerRO.PackedData<T> packedData) -> unpacker.read(strategy, packedData), ro -> ro.pack(strategy));
	}

	PalettedContainer(Strategy<T> strategy, Configuration configuration, BitStorage bitStorage, Palette<T> palette) {
		this.strategy = strategy;
		this.data = new PalettedContainer.Data<>(configuration, bitStorage, palette);
		this.adoptNative();
	}

	private PalettedContainer(PalettedContainer<T> palettedContainer) {
		this.strategy = palettedContainer.strategy;
		var live = palettedContainer.nativeBlocks;
		if (live != null) this.nativeBlocks = live.copy();
		else if (palettedContainer.nativeLiveBiomes() != null) this.nativeBiomes = palettedContainer.nativeLiveBiomes().copy();
		else {
			this.data = palettedContainer.readData().copy();
			this.escapedBiomeSingleton = palettedContainer.escapedBiomeSingleton;
			// A transferred single palette still aliases its original and copies.
			if (!(NativeLiveBiomeSection.binding(this.strategy) != null && this.data.storage.getBits() == 0)) this.adoptNative();
		}
	}

	public PalettedContainer(T object, Strategy<T> strategy) {
		this.strategy = strategy;
		this.data = this.createOrReuseData(null, 0);
		this.data.palette.idFor(object, this);
		this.adoptNative();
	}

	private PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> data, int i) {
		Configuration configuration = this.strategy.getConfigurationForBitCount(i);
		if (data != null && configuration.equals(data.configuration())) {
			return data;
		} else {
			BitStorage bitStorage = (BitStorage)(configuration.bitsInMemory() == 0
				? new ZeroBitStorage(this.strategy.entryCount())
				: new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount()));
			Palette<T> palette = configuration.createPalette(this.strategy, List.of());
			return new PalettedContainer.Data<>(configuration, bitStorage, palette);
		}
	}

	/** Whether this is still a fresh single-value container holding only {@code value}. */
	boolean isUntouched(T value) {
		var live = this.nativeBlocks;
		if (live != null) return live.isSingle(value);
		var biomes = this.nativeLiveBiomes();
		if (biomes != null) return biomes.bits() == 0 && biomes.get(0) == value;
		PalettedContainer.Data<T> data = this.readData();
		return data.palette instanceof SingleValuePalette<T> && data.storage instanceof ZeroBitStorage && data.palette.valueFor(0) == value;
	}

	/** This container's exact state for a native stage that continues its writes:
	 * palette kind (0 single, 1 linear, 2 hash map, 3 global), storage bits,
	 * palette ids in id order and the raw storage (the container's own words:
	 * copy them before it changes); null for other palettes or storage. */
	@Nullable
	GeneratedState exportGenerated(java.util.function.ToIntFunction<T> ids) {
		PalettedContainer.Data<T> data = this.readData();
		Class<?> type = data.palette.getClass();
		int kind = type == SingleValuePalette.class ? 0 : type == LinearPalette.class ? 1 : type == HashMapPalette.class ? 2 : type == GlobalPalette.class ? 3 : -1;
		if (kind < 0) return null;
		int bits;
		long[] raw;
		if (data.storage.getClass() == ZeroBitStorage.class) {
			bits = 0;
			raw = new long[0];
		} else if (data.storage.getClass() == SimpleBitStorage.class) {
			bits = data.storage.getBits();
			raw = data.storage.getRaw();
		} else {
			return null;
		}
		int[] palette = new int[kind == 3 ? 0 : data.palette.getSize()];
		for (int i = 0; i < palette.length; i++) palette[i] = ids.applyAsInt(data.palette.valueFor(i));
		return new GeneratedState(kind, bits, palette, raw);
	}

	record GeneratedState(int kind, int bits, int[] palette, long[] raw) {}

	/** Global palette bits of a 64-entry container whose strategy a native biome
	 * fill replays (zero bits, then the 1..3 bit linear palettes, then the global
	 * palette); -1 for other containers and strategies. */
	int generatedBiomeGlobalBits() {
		Strategy<T> strategy = this.strategy;
		if (this.getClass() != PalettedContainer.class || strategy.entryCount() != 64
			|| strategy.getConfigurationForBitCount(0) != Strategy.ZERO_BITS || strategy.getConfigurationForBitCount(1) != Strategy.ONE_BIT_LINEAR
			|| strategy.getConfigurationForBitCount(2) != Strategy.TWO_BITS_LINEAR || strategy.getConfigurationForBitCount(3) != Strategy.THREE_BITS_LINEAR
			|| !(strategy.getConfigurationForBitCount(4) instanceof Configuration.Global global) || global.bitsInStorage() != 4) {
			return -1;
		}
		return global.bitsInMemory();
	}

	IdMap<T> globalIds() {
		return this.strategy.globalMap();
	}

	/** Storage bits of this strategy's global palette. */
	int globalPaletteBits() {
		return this.strategy.getConfigurationForBitCount(32).bitsInMemory();
	}

	/** Installs the palette and storage that replaying a write sequence into this
	 * fresh container produces, as computed by the native NOISE fill. */
	void installGenerated(int requestedBits, List<T> entries, long[] raw) {
		Configuration configuration = this.strategy.getConfigurationForBitCount(requestedBits);
		BitStorage bitStorage = configuration.bitsInMemory() == 0
			? new ZeroBitStorage(this.strategy.entryCount())
			: new SimpleBitStorage(configuration.bitsInMemory(), this.strategy.entryCount(), raw);
		if (this.nativeBiomes != null) { this.nativeBiomes.invalidate(); this.nativeBiomes = null; }
		this.data = new PalettedContainer.Data<>(configuration, bitStorage, configuration.createPalette(this.strategy, entries));
		this.nativeBlocks = null;
		this.adoptNative();
	}

	/** Canonical stage result already constructed and owned entirely in Rust. */
	void installNativeGenerated(NativeLiveBlockSection live) {
		this.nativeBlocks = live;
		this.data = null;
	}

	@Override
	public int onResize(int i, T object) {
		var live = this.nativeBlocks;
		if (i == 0 && live != null && live.bits() == 0) {
			return live.get(0) == object ? 0 : PaletteResize.<T>noResizeExpected().onResize(1, object);
		}
		var biomes = this.nativeLiveBiomes();
		if (i == 0 && biomes != null && biomes.bits() == 0)
			return biomes.get(0) == object ? 0 : PaletteResize.<T>noResizeExpected().onResize(1, object);
		this.materializeForMutation();
		PalettedContainer.Data<T> data = this.readData();
		PalettedContainer.Data<T> data2 = this.createOrReuseData(data, i);
		if (this.getClass() != PalettedContainer.class || !NativePaletteResize.copy(this.strategy, data, data2)) {
			data2.copyFrom(data.palette, data.storage);
		}
		this.data = data2;
		return data2.palette.idFor(object, PaletteResize.noResizeExpected());
	}

	public T getAndSet(int i, int j, int k, T object) {
		this.acquire();

		Object var5;
		try {
			var5 = this.getAndSet(this.strategy.getIndex(i, j, k), object);
		} finally {
			this.release();
		}

		return (T)var5;
	}

	public T getAndSetUnchecked(int i, int j, int k, T object) {
		return this.getAndSet(this.strategy.getIndex(i, j, k), object);
	}

	/** Exact standard containers only; a declined native policy mutates nothing. */
	@Nullable
	T trySetWithCounters(int x, int y, int z, T value, boolean locked, NativeSectionCounters counters) {
		if (this.getClass() != PalettedContainer.class) return null;
		var live = this.nativeBlocks;
		int id = live == null ? -1 : nativeStateId(value);
		if (id < 0) return null;
		int index = this.strategy.getIndex(x, y, z);
		if (index < 0 || index >= 4096) return null;
		if (locked) this.acquire();
		try {
			int old = live.writeWithCounters(index, id, counters);
			return old < 0 ? null : (T)net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(old);
		} finally { if (locked) this.release(); }
	}

	private T getAndSet(int i, T object) {
		var live = this.nativeBlocks;
		int id = live == null ? -1 : nativeStateId(object);
		if (live != null && (i < 0 || i >= 4096) && live.bits() == 0 && live.get(0) == object)
			org.apache.commons.lang3.Validate.inclusiveBetween(0L, 4095L, i);
		if (id >= 0 && i >= 0 && i < 4096) return (T)net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(live.write(i, id));
		var biomes = this.nativeLiveBiomes();
		int biomeId = biomes == null ? -1 : biomes.id(object);
		if (biomeId >= 0 && i >= 0 && i < 64) return biomes.write(i, biomeId);
		this.materializeForMutation();
		int j = this.data.palette.idFor(object, this);
		int k = this.data.storage.getAndSet(i, j);
		return this.data.palette.valueFor(k);
	}

	public void set(int i, int j, int k, T object) {
		this.acquire();

		try {
			this.set(this.strategy.getIndex(i, j, k), object);
		} finally {
			this.release();
		}
	}

	private void set(int i, T object) {
		var live = this.nativeBlocks;
		int id = live == null ? -1 : nativeStateId(object);
		if (live != null && (i < 0 || i >= 4096) && live.bits() == 0 && live.get(0) == object)
			org.apache.commons.lang3.Validate.inclusiveBetween(0L, 4095L, i);
		if (id >= 0 && i >= 0 && i < 4096) { live.write(i, id); return; }
		var biomes = this.nativeLiveBiomes();
		int biomeId = biomes == null ? -1 : biomes.id(object);
		if (biomeId >= 0 && i >= 0 && i < 64) { biomes.write(i, biomeId); return; }
		this.materializeForMutation();
		int j = this.data.palette.idFor(object, this);
		this.data.storage.set(i, j);
	}

	@Override
	public T get(int i, int j, int k) {
		return this.get(this.strategy.getIndex(i, j, k));
	}

	protected T get(int i) {
		var live = this.nativeBlocks;
		if (live != null) return (T)live.get(i);
		var biomes = this.nativeLiveBiomes();
		if (biomes != null) return biomes.get(i);
		PalettedContainer.Data<T> data = this.readData();
		return data.palette.valueFor(data.storage.get(i));
	}

	@Override
	public void getAll(Consumer<T> consumer) {
		var sourceData = this.readData();
		Palette<T> palette = sourceData.palette();
		if (this.getClass() == PalettedContainer.class && sourceData.storage.getClass() == SimpleBitStorage.class) {
			var ids = NativePaletteDistinct.scan(sourceData.storage);
			if (ids != null) {
				try (ids) {
					for (int i = 0; i < ids.size; i++) consumer.accept(palette.valueFor(ids.entry(i)));
				}
				return;
			}
		}
		IntSet intSet = new IntArraySet();
		sourceData.storage.getAll(intSet::add);
		intSet.forEach(i -> consumer.accept(palette.valueFor(i)));
	}

	public void read(FriendlyByteBuf friendlyByteBuf) {
		this.acquire();

		try {
			int i = friendlyByteBuf.readByte();
			var live = this.nativeBlocks;
			if (i == 0 && live != null && live.bits() == 0) {
				int id = friendlyByteBuf.readVarInt();
				net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byIdOrThrow(id);
				live.readSingle(id);
				return;
			}
			var biomes = this.nativeLiveBiomes();
			if (i == 0 && biomes != null && biomes.bits() == 0) {
				int id = friendlyByteBuf.readVarInt();
				this.strategy.globalMap().byIdOrThrow(id);
				biomes.readSingle(id); return;
			}
			boolean fresh = (live != null || biomes != null) && !this.strategy.getConfigurationForBitCount(i)
				.equals(this.strategy.getConfigurationForBitCount(live != null ? live.requestedBits() : biomes.requestedBits()));
			if (!fresh) this.materializeForMutation();
			PalettedContainer.Data<T> data = this.createOrReuseData(fresh ? null : this.data, i);
			data.palette.read(friendlyByteBuf, this.strategy.globalMap());
			friendlyByteBuf.readFixedSizeLongArray(data.storage.getRaw());
			if (this.nativeBiomes != null) { this.nativeBiomes.invalidate(); this.nativeBiomes = null; }
			this.data = data;
			this.nativeBlocks = null;
			this.adoptNative();
		} finally {
			this.release();
		}
	}

	@Override
	public void write(FriendlyByteBuf friendlyByteBuf) {
		this.acquire();

		try {
			this.readData().write(friendlyByteBuf, this.strategy.globalMap());
		} finally {
			this.release();
		}
	}

	@VisibleForTesting
	public static <T> DataResult<PalettedContainer<T>> unpack(Strategy<T> strategy, PalettedContainerRO.PackedData<T> packedData) {
		List<T> list = packedData.paletteEntries();
		int i = strategy.entryCount();
		Configuration configuration = strategy.getConfigurationForPaletteSize(list.size());
		int j = configuration.bitsInStorage();
		if (packedData.bitsPerEntry() != -1 && j != packedData.bitsPerEntry()) {
			return DataResult.error(() -> "Invalid bit count, calculated " + j + ", but container declared " + packedData.bitsPerEntry());
		} else {
			BitStorage bitStorage;
			Palette<T> palette;
			if (configuration.bitsInMemory() == 0) {
				palette = configuration.createPalette(strategy, list);
				bitStorage = new ZeroBitStorage(i);
			} else {
				Optional<LongStream> optional = packedData.storage();
				if (optional.isEmpty()) {
					return DataResult.error(() -> "Missing values for non-zero storage");
				}

				long[] ls = ((LongStream)optional.get()).toArray();

				try {
					if (!configuration.alwaysRepack() && configuration.bitsInMemory() == j) {
						palette = configuration.createPalette(strategy, list);
						bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, ls);
					} else {
						var loaded = NativePaletteUnpacking.unpack(strategy, configuration, list, ls);
						if (loaded != null) return DataResult.success(loaded);
						Palette<T> palette2 = new HashMapPalette<>(j, list);
						SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
						Palette<T> palette3 = configuration.createPalette(strategy, list);
						int[] is = reencodeContents(simpleBitStorage, palette2, palette3);
						palette = palette3;
						bitStorage = new SimpleBitStorage(configuration.bitsInMemory(), i, is);
					}
				} catch (SimpleBitStorage.InitializationException var14) {
					return DataResult.error(() -> "Failed to read PalettedContainer: " + var14.getMessage());
				}
			}

			return DataResult.success(new PalettedContainer<>(strategy, configuration, bitStorage, palette));
		}
	}

	@Override
	public PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy) {
		this.acquire();

		PalettedContainerRO.PackedData var14;
		try {
			var sourceData = this.readData();
			if (this.getClass() == PalettedContainer.class) {
				var packed = NativePalettePacking.pack(sourceData.storage, sourceData.palette, this.strategy, strategy);
				if (packed != null) return packed;
			}
			BitStorage bitStorage = sourceData.storage;
			Palette<T> palette = sourceData.palette;
			HashMapPalette<T> hashMapPalette = new HashMapPalette<>(bitStorage.getBits());
			int i = strategy.entryCount();
			int[] is = reencodeContents(bitStorage, palette, hashMapPalette);
			Configuration configuration = strategy.getConfigurationForPaletteSize(hashMapPalette.getSize());
			int j = configuration.bitsInStorage();
			Optional<LongStream> optional;
			if (j != 0) {
				SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
				optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
			} else {
				optional = Optional.empty();
			}

			var14 = new PalettedContainerRO.PackedData(hashMapPalette.getEntries(), optional, j);
		} finally {
			this.release();
		}

		return var14;
	}

	private static <T> int[] reencodeContents(BitStorage bitStorage, Palette<T> palette, Palette<T> palette2) {
		int[] is = new int[bitStorage.getSize()];
		bitStorage.unpack(is);
		PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();
		int i = -1;
		int j = -1;

		for (int k = 0; k < is.length; k++) {
			int l = is[k];
			if (l != i) {
				i = l;
				j = palette2.idFor(palette.valueFor(l), paletteResize);
			}

			is[k] = j;
		}

		return is;
	}

	@Override
	public int getSerializedSize() {
		return this.readData().getSerializedSize(this.strategy.globalMap());
	}

	@Override
	public int bitsPerEntry() {
		var live = this.nativeBlocks;
		var biomes = this.nativeLiveBiomes();
		return live != null ? live.bits() : biomes != null ? biomes.bits() : this.readData().storage().getBits();
	}

	@Override
	public boolean maybeHas(Predicate<T> predicate) {
		var live = this.nativeBlocks;
		if (live != null) return live.maybeHas(state -> predicate.test((T)state));
		var biomes = this.nativeLiveBiomes();
		return biomes != null ? biomes.maybeHas(predicate) : this.data.palette.maybeHas(predicate);
	}

	@Override
	public PalettedContainer<T> copy() {
		return new PalettedContainer<>(this);
	}

	@Override
	public PalettedContainer<T> recreate() {
		return new PalettedContainer<>(this.currentPaletteValue(0), this.strategy);
	}

	@Override
	public void count(PalettedContainer.CountConsumer<T> countConsumer) {
		var live = this.nativeBlocks;
		if (live != null) {
			if (live.paletteSize() == 1) { countConsumer.accept((T)live.paletteValue(0), 4096); return; }
			var counts = NativePaletteHistogram.scan(live);
			if (counts != null) {
				try (counts) {
					for (int i = 0; i < counts.size; i++) {
						long entry = counts.entry(i);
						countConsumer.accept(this.currentPaletteValue((int)entry), (int)(entry >>> 32));
					}
				}
				return;
			}
		}
		var sourceData = this.readData();
		if (sourceData.palette.getSize() == 1) {
			countConsumer.accept(sourceData.palette.valueFor(0), sourceData.storage.getSize());
		} else {
			if (this.getClass() == PalettedContainer.class) {
				var counts = NativePaletteHistogram.scan(sourceData.storage);
				if (counts != null) {
					try (counts) {
						for (int i = 0; i < counts.size; i++) {
							long entry = counts.entry(i);
							countConsumer.accept(this.currentPaletteValue((int)entry), (int)(entry >>> 32));
						}
					}
					return;
				}
			}
			Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
			sourceData.storage.getAll(i -> int2IntOpenHashMap.addTo(i, 1));
			int2IntOpenHashMap.int2IntEntrySet().forEach(entry -> countConsumer.accept(this.currentPaletteValue(entry.getIntKey()), entry.getIntValue()));
		}
	}

	@FunctionalInterface
	public interface CountConsumer<T> {
		void accept(T object, int i);
	}

	public record Data<T>(Configuration configuration, BitStorage storage, Palette<T> palette) {

		public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
			PaletteResize<T> paletteResize = PaletteResize.noResizeExpected();

			for (int i = 0; i < bitStorage.getSize(); i++) {
				T object = palette.valueFor(bitStorage.get(i));
				this.storage.set(i, this.palette.idFor(object, paletteResize));
			}
		}

		public int getSerializedSize(IdMap<T> idMap) {
			return 1 + this.palette.getSerializedSize(idMap) + this.storage.getRaw().length * 8;
		}

		public void write(FriendlyByteBuf friendlyByteBuf, IdMap<T> idMap) {
			friendlyByteBuf.writeByte(this.storage.getBits());
			this.palette.write(friendlyByteBuf, idMap);
			friendlyByteBuf.writeFixedSizeLongArray(this.storage.getRaw());
		}

		public PalettedContainer.Data<T> copy() {
			return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
		}
	}
	
	// Sodium: PalettedContainerROExtension implementation
	@Override
	public void sodium$unpack(T[] values) {
		java.util.Objects.requireNonNull(this.strategy);

		if (values.length != this.strategy.entryCount()) {
			throw new IllegalArgumentException("Array is wrong size");
		}

		PalettedContainer.Data<T> data = java.util.Objects.requireNonNull(this.readData(), "PalettedContainer must have data");

		BitStorageExtension storage = (BitStorageExtension) data.storage();
		storage.sodium$unpack(values, data.palette());
	}

	@Override
	public void sodium$unpack(T[] values, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		java.util.Objects.requireNonNull(this.strategy);

		if (values.length != this.strategy.entryCount()) {
			throw new IllegalArgumentException("Array is wrong size");
		}

		PalettedContainer.Data<T> data = java.util.Objects.requireNonNull(this.readData(), "PalettedContainer must have data");

		BitStorage storage = data.storage();
		Palette<T> palette = data.palette();

		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					int localBlockIndex = this.strategy.getIndex(x, y, z);

					int paletteIndex = storage.get(localBlockIndex);
					T paletteValue = palette.valueFor(paletteIndex);

					values[localBlockIndex] = paletteValue;
				}
			}
		}
	}

	@Override
	public PalettedContainerRO<T> sodium$copy() {
		return this.copy();
	}
}
