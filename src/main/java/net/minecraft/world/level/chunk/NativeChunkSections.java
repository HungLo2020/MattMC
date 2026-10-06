package net.minecraft.world.level.chunk;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NativeNbtRegionAccess;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.jetbrains.annotations.Nullable;

/** Rust encoding of a chunk's {@code sections} list for saving
 * ({@code storage/chunk/}): packing each section's block states and biomes as
 * {@link PalettedContainer}'s codec does, their palettes, light layers and
 * {@code Y}, as NBT tape in {@code CompoundTag}'s key order. Java supplies the
 * raw containers and, once, each block state's encoded compound. Lives beside
 * chunk storage to borrow its package-private palette data. */
public final class NativeChunkSections {
    private static final MethodHandle VOCABULARY = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_sections_vocabulary",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_sections_encode",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    // Tests compare both routes in one JVM; production reads the property once.
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.storage.javaChunkSections");
    // Chunks whose sections Rust encoded, so tests can prove the route.
    public static final AtomicLong CHUNKS = new AtomicLong();

    private NativeChunkSections() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Every block state's canonical label (equal for identical states), its
     * {@code BlockState.CODEC} compound as list-element tape, and the block
     * strategy's storage bits per palette size. Built on first use. */
    private static final class Vocabulary {
        static final int[] LABELS;
        static final long HANDLE;
        @Nullable
        static final Strategy<BlockState> STRATEGY;

        static {
            int count = Block.BLOCK_STATE_REGISTRY.size();
            int[] labels = new int[count];
            long handle = 0;
            Strategy<BlockState> strategy = null;
            try {
                strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
                var identities = new IdentityHashMap<BlockState, Integer>();
                var fragments = new ByteArrayOutputStream();
                var offsets = new java.util.ArrayList<Integer>();
                offsets.add(0);
                for (int id = 0; id < count; id++) {
                    BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                    if (state == null || state.getClass() != BlockState.class) throw new IllegalStateException("Unsupported state " + id);
                    Integer label = identities.get(state);
                    if (label == null) {
                        label = identities.size();
                        identities.put(state, label);
                        fragments.write(NativeNbtRegionAccess.elementTape(BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow()));
                        offsets.add(fragments.size());
                    }
                    labels[id] = label;
                }
                byte[] bits = new byte[4097];
                for (int size = 1; size <= 4096; size++) bits[size] = (byte)strategy.getConfigurationForPaletteSize(size).bitsInStorage();
                byte[] blob = fragments.toByteArray();
                int[] ends = offsets.stream().mapToInt(Integer::intValue).toArray();
                try (Arena arena = Arena.ofConfined()) {
                    handle = (long)VOCABULARY.invokeExact(arena.allocateFrom(ValueLayout.JAVA_INT, labels), labels.length,
                        arena.allocateFrom(ValueLayout.JAVA_BYTE, blob), blob.length, arena.allocateFrom(ValueLayout.JAVA_INT, ends), ends.length,
                        arena.allocateFrom(ValueLayout.JAVA_BYTE, bits), bits.length);
                }
            } catch (Throwable error) {
                handle = 0;
            }
            LABELS = labels;
            HANDLE = handle;
            STRATEGY = strategy;
        }
    }

    /** Growable native buffers, reused per thread. */
    private static final class Scratch {
        int[] ints = new int[4096];
        long[] longs = new long[8192];
        byte[] bytes = new byte[2048 * 64];
        char[] chars = new char[512];
        int intCount, longCount, byteCount, charCount;
        final IdentityHashMap<Holder<Biome>, Integer> biomes = new IdentityHashMap<>();
        final List<String> names = new java.util.ArrayList<>();
        Arena arena = Arena.ofConfined();
        MemorySegment out = this.arena.allocate(256 * 1024, 8);
        @Nullable
        Strategy<Holder<Biome>> biomeStrategy;
        @Nullable
        Strategy<BlockState> blockStrategy;
        byte[] biomeBits = new byte[65];

        void reset() {
            this.intCount = this.longCount = this.byteCount = this.charCount = 0;
            this.biomes.clear();
            this.names.clear();
        }

        void addInt(int value) {
            if (this.intCount == this.ints.length) this.ints = java.util.Arrays.copyOf(this.ints, this.ints.length * 2);
            this.ints[this.intCount++] = value;
        }

        void addLongs(long[] values) {
            if (this.longCount + values.length > this.longs.length) {
                this.longs = java.util.Arrays.copyOf(this.longs, Math.max(this.longs.length * 2, this.longCount + values.length));
            }
            System.arraycopy(values, 0, this.longs, this.longCount, values.length);
            this.longCount += values.length;
        }

        void addBytes(byte[] values) {
            if (this.byteCount + values.length > this.bytes.length) {
                this.bytes = java.util.Arrays.copyOf(this.bytes, Math.max(this.bytes.length * 2, this.byteCount + values.length));
            }
            System.arraycopy(values, 0, this.bytes, this.byteCount, values.length);
            this.byteCount += values.length;
        }
    }

    /** The tape of {@code writeTag("sections", sections)} as
     * {@code SerializableChunkData.write()} builds the list, or null for input
     * Rust does not model (the caller then encodes with Java). */
    @Nullable
    public static byte[] encode(List<SerializableChunkData.SectionData> sections, PalettedContainerFactory factory) {
        if (!enabled || Vocabulary.HANDLE == 0 || factory.blockStatesStrategy().entryCount() != 4096
            || factory.biomeStrategy().entryCount() != 64) return null;
        Scratch scratch = SCRATCH.get();
        if (scratch.blockStrategy != factory.blockStatesStrategy()) {
            // The vocabulary's storage bits must be this factory's.
            if (!sameBlockBits(factory.blockStatesStrategy())) return null;
            scratch.blockStrategy = factory.blockStatesStrategy();
        }
        scratch.reset();
        if (scratch.biomeStrategy != factory.biomeStrategy()) {
            for (int size = 1; size <= 64; size++) scratch.biomeBits[size] = (byte)factory.biomeStrategy().getConfigurationForPaletteSize(size).bitsInStorage();
            scratch.biomeStrategy = factory.biomeStrategy();
        }
        try {
            if (!sections(sections, scratch)) return null;
        } catch (RuntimeException error) {
            // Java's encoding meets the same input and reports it.
            return null;
        }
        return run(scratch, sections.size());
    }

    private static boolean sameBlockBits(Strategy<BlockState> strategy) {
        if (strategy == Vocabulary.STRATEGY) return true;
        if (Vocabulary.STRATEGY == null || strategy.getClass() != Vocabulary.STRATEGY.getClass()) return false;
        for (int size = 1; size <= 4096; size++) {
            if (strategy.getConfigurationForPaletteSize(size).bitsInStorage() != Vocabulary.STRATEGY.getConfigurationForPaletteSize(size).bitsInStorage()) {
                return false;
            }
        }
        return true;
    }

    /** Appends each section as {@code SerializableChunkData.write()} visits it. */
    private static boolean sections(List<SerializableChunkData.SectionData> sections, Scratch scratch) {
        for (SerializableChunkData.SectionData data : sections) {
            LevelChunkSection section = data.chunkSection();
            int flags = (section != null ? 1 : 0) | (data.blockLight() != null ? 2 : 0) | (data.skyLight() != null ? 4 : 0);
            scratch.addInt(data.y());
            scratch.addInt(flags);
            if (section != null) {
                if (section.getClass() != LevelChunkSection.class || !blockStates(section.getStates(), scratch)) return false;
                if (!(section.getBiomes() instanceof PalettedContainer<Holder<Biome>> biomes) || !biomes(biomes, scratch)) return false;
            }
            if (data.blockLight() != null) scratch.addBytes(data.blockLight().getData());
            if (data.skyLight() != null) scratch.addBytes(data.skyLight().getData());
        }
        return true;
    }

    /** Storage words and bits of a modelled container, or null. */
    @Nullable
    private static long[] words(PalettedContainer.Data<?> data, int entries) {
        var storage = data.storage();
        if (storage.getSize() != entries) return null;
        if (storage.getClass() == ZeroBitStorage.class) return new long[0];
        if (storage.getClass() != SimpleBitStorage.class || storage.getBits() < 1 || storage.getBits() > 32) return null;
        return storage.getRaw();
    }

    private static boolean modelledPalette(Palette<?> palette) {
        return palette.getClass() == SingleValuePalette.class || palette.getClass() == LinearPalette.class
            || palette.getClass() == HashMapPalette.class || palette.getClass() == GlobalPalette.class;
    }

    private static boolean blockStates(PalettedContainer<BlockState> container, Scratch out) {
        if (container.getClass() != PalettedContainer.class) return false;
        // pack() reads under the container's lock.
        container.acquire();
        try {
            return blockStatesLocked(container, out);
        } finally {
            container.release();
        }
    }

    private static boolean blockStatesLocked(PalettedContainer<BlockState> container, Scratch out) {
        var data = container.dataForNativeScan();
        long[] words = words(data, 4096);
        var palette = data.palette();
        if (words == null || !modelledPalette(palette)) return false;
        int size = palette.getSize();
        if (palette.getClass() == GlobalPalette.class) {
            if (container.registryForNativeScan() != Block.BLOCK_STATE_REGISTRY || size != Vocabulary.LABELS.length) return false;
            out.addInt(1);
            out.addInt(data.storage().getBits());
            out.addInt(size);
            out.addInt(words.length);
        } else {
            if (size < 1 || size > 4096) return false;
            out.addInt(0);
            out.addInt(data.storage().getBits());
            out.addInt(size);
            out.addInt(words.length);
            for (int i = 0; i < size; i++) {
                BlockState state = palette.valueFor(i);
                if (state == null || state.getClass() != BlockState.class) return false;
                int id = Block.BLOCK_STATE_REGISTRY.getId(state);
                if (id < 0 || id >= Vocabulary.LABELS.length) return false;
                out.addInt(Vocabulary.LABELS[id]);
            }
        }
        out.addLongs(words);
        return true;
    }

    private static boolean biomes(PalettedContainer<Holder<Biome>> container, Scratch out) {
        if (container.getClass() != PalettedContainer.class) return false;
        container.acquire();
        try {
            return biomesLocked(container, out);
        } finally {
            container.release();
        }
    }

    private static boolean biomesLocked(PalettedContainer<Holder<Biome>> container, Scratch out) {
        var data = container.dataForNativeScan();
        long[] words = words(data, 64);
        var palette = data.palette();
        if (words == null || !modelledPalette(palette)) return false;
        int size = palette.getSize();
        if (size < 1 || size > 4096) return false;
        out.addInt(data.storage().getBits());
        out.addInt(size);
        out.addInt(words.length);
        IdMap<Holder<Biome>> registry = palette.getClass() == GlobalPalette.class ? container.registryForNativeScan() : null;
        for (int i = 0; i < size; i++) {
            Holder<Biome> holder = registry != null ? registry.byId(i) : palette.valueFor(i);
            int label = biome(holder, out);
            if (label < 0) return false;
            out.addInt(label);
        }
        out.addLongs(words);
        return true;
    }

    /** The holder's name index, as the biome codec encodes it ({@code holderByNameCodec}). */
    private static int biome(@Nullable Holder<Biome> holder, Scratch scratch) {
        if (!(holder instanceof Holder.Reference<Biome> reference)) return -1;
        Integer known = scratch.biomes.get(holder);
        if (known != null) return known;
        int label = scratch.names.size();
        scratch.names.add(reference.key().location().toString());
        scratch.biomes.put(holder, label);
        return label;
    }

    @Nullable
    private static byte[] run(Scratch scratch, int sectionCount) {
        // Header: section count, biome count and each name's length, then the sections.
        List<String> names = scratch.names;
        int[] all = new int[2 + names.size() + scratch.intCount];
        all[0] = sectionCount;
        all[1] = names.size();
        int chars = 0;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            all[2 + i] = name.length();
            if (scratch.chars.length < chars + name.length()) {
                scratch.chars = java.util.Arrays.copyOf(scratch.chars, Math.max(scratch.chars.length * 2, chars + name.length()));
            }
            name.getChars(0, name.length(), scratch.chars, chars);
            chars += name.length();
        }
        scratch.charCount = chars;
        System.arraycopy(scratch.ints, 0, all, 2 + names.size(), scratch.intCount);
        try (Arena arena = Arena.ofConfined()) {
            var ints = arena.allocateFrom(ValueLayout.JAVA_INT, all);
            var longs = arena.allocate(Math.max(1, scratch.longCount) * 8L, 8);
            MemorySegment.copy(MemorySegment.ofArray(scratch.longs), 0, longs, 0, scratch.longCount * 8L);
            var bytes = arena.allocate(Math.max(1, scratch.byteCount), 1);
            MemorySegment.copy(MemorySegment.ofArray(scratch.bytes), 0, bytes, 0, scratch.byteCount);
            var units = arena.allocate(Math.max(1, scratch.charCount) * 2L, 2);
            MemorySegment.copy(MemorySegment.ofArray(scratch.chars), 0, units, 0, scratch.charCount * 2L);
            var bits = arena.allocateFrom(ValueLayout.JAVA_BYTE, scratch.biomeBits);
            var length = arena.allocate(8, 8);
            while (true) {
                int status = (int)ENCODE.invokeExact(Vocabulary.HANDLE, ints, all.length, longs, scratch.longCount, bytes, scratch.byteCount,
                    units, scratch.charCount, bits, scratch.biomeBits.length, scratch.out, scratch.out.byteSize(), length);
                long needed = length.get(ValueLayout.JAVA_LONG, 0);
                if (status == 0) {
                    CHUNKS.incrementAndGet();
                    return scratch.out.asSlice(0, needed).toArray(ValueLayout.JAVA_BYTE);
                }
                if (status == 1) {
                    scratch.arena.close();
                    scratch.arena = Arena.ofConfined();
                    scratch.out = scratch.arena.allocate(Math.max(needed, scratch.out.byteSize() * 2), 8);
                    continue;
                }
                if (status == -2) return null;
                throw new IllegalStateException("Native chunk sections rejected their input: " + status);
            }
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Native chunk section encoding failed", error);
        }
    }
}
