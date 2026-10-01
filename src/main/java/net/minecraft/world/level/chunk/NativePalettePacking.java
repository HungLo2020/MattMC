package net.minecraft.world.level.chunk;

import net.minecraft.util.BitStorage;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.stream.LongStream;

/** Locked block-section save packing. Java owns object identities; Rust owns the bulk loops. */
final class NativePalettePacking {
    private static final int SECTION_SIZE = 4096;
    private static final MethodHandle COMPACT =
            NativeLibraryLoader.downcallHandle(
                    "mattmc_rust",
                    "mattmc_palette_compact",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE =
            NativeLibraryLoader.downcallHandle(
                    "mattmc_rust",
                    "mattmc_palette_encode",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));
    private static final MemorySegment LOCAL_LABELS = createLocalLabels();

    private static MemorySegment createLocalLabels() {
        var labels = Arena.ofAuto().allocate(256 * 4, 4);
        for (int i = 0; i < 256; i++) labels.setAtIndex(ValueLayout.JAVA_INT, i, i);
        return labels.asReadOnly();
    }

    private static final Class<?> BLOCK_STRATEGY =
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativePalettePacking() {}

    private static final class BlockLabels {
        static final int COUNT = Block.BLOCK_STATE_REGISTRY.size();
        static final MemorySegment VALUES = create();

        private static MemorySegment create() {
            var labels = Arena.ofAuto().allocate(Math.max(COUNT, 1) * 4L, 4);
            var identities = new IdentityHashMap<Object, Integer>();
            for (int i = 0; i < COUNT; i++) {
                var state = Block.BLOCK_STATE_REGISTRY.byId(i);
                if (state == null)
                    labels.setAtIndex(ValueLayout.JAVA_INT, i, COUNT); // rejected if encountered
                else {
                    var label = identities.get(state);
                    if (label == null) {
                        label = identities.size();
                        identities.put(state, label);
                    }
                    labels.setAtIndex(ValueLayout.JAVA_INT, i, label);
                }
            }
            return labels.asReadOnly();
        }
    }

    private static final class Scratch {
        final MemorySegment words = Arena.ofAuto().allocate(2048 * 8, 8);
        final MemorySegment lookup = Arena.ofAuto().allocate(32768 * 4, 4);
        final MemorySegment output = Arena.ofAuto().allocate(8192 * 4, 4);
        final MemorySegment indices = output.asSlice(SECTION_SIZE * 4L, SECTION_SIZE * 4L);
        final MemorySegment packed = Arena.ofAuto().allocate(2048 * 8, 8);
        boolean inUse;
    }

    /** Called only while the source container is locked. Null selects the original path. */
    static <T> PalettedContainerRO.PackedData<T> pack(
            BitStorage storage, Palette<T> palette, Strategy<T> owner, Strategy<T> target) {
        if (storage == null
                || palette == null
                || target == null
                || target.getClass() != BLOCK_STRATEGY
                || owner == null
                || owner.getClass() != BLOCK_STRATEGY
                || target.entryCount() != SECTION_SIZE) return null;
        if (storage.getClass() != SimpleBitStorage.class
                && storage.getClass() != ZeroBitStorage.class) return null;
        if (storage.getSize() != SECTION_SIZE || storage.getBits() > 16) return null;
        boolean global = palette.getClass() == GlobalPalette.class;
        if (!global
                && palette.getClass() != SingleValuePalette.class
                && palette.getClass() != LinearPalette.class
                && palette.getClass() != HashMapPalette.class) return null;
        if (global
                && (owner.globalMap() != Block.BLOCK_STATE_REGISTRY
                        || palette != owner.globalPalette())) return null;
        int count = palette.getSize();
        if (count < 1 || count > (global ? 32768 : 256)) return null;
        if (global && count != BlockLabels.COUNT) return null;
        var scratch = SCRATCH.get();
        if (scratch.inUse) return null;
        scratch.inUse = true;
        try {
            return evaluate(storage, palette, target, global, count, scratch);
        } finally {
            scratch.inUse = false;
        }
    }

    private static <T> PalettedContainerRO.PackedData<T> evaluate(
            BitStorage storage,
            Palette<T> palette,
            Strategy<T> target,
            boolean global,
            int count,
            Scratch scratch) {
        // Compact used source IDs first. Do not inspect unused palette entries.
        MemorySegment labels = global ? BlockLabels.VALUES : LOCAL_LABELS;
        var raw = storage.getRaw();
        MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, raw.length * 8L);
        int unique;
        try {
            unique =
                    (int)
                            COMPACT.invokeExact(
                                    scratch.words,
                                    raw.length,
                                    storage.getBits(),
                                    labels,
                                    count,
                                    scratch.lookup,
                                    count,
                                    scratch.output,
                                    8192);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native palette compaction failed", e);
        }
        if (unique < 1) return null;

        var entries = new ArrayList<T>(unique);
        var identities = !global && unique > 1 ? new IdentityHashMap<T, Integer>() : null;
        boolean aliases = false;
        for (int i = 0; i < unique; i++) {
            int id = scratch.output.getAtIndex(ValueLayout.JAVA_INT, i);
            T value;
            try {
                value = palette.valueFor(id);
            } catch (MissingPaletteEntryException e) {
                return null;
            }
            if (value == null) return null;
            int dense = entries.size();
            if (identities != null) {
                var existing = identities.get(value);
                if (existing != null) {
                    dense = existing;
                    aliases = true;
                } else {
                    identities.put(value, dense);
                    entries.add(value);
                }
                // The order has already been consumed at i; reuse it for alias remapping.
                scratch.output.setAtIndex(ValueLayout.JAVA_INT, i, dense);
            } else entries.add(value);
        }
        int bits = target.getConfigurationForPaletteSize(entries.size()).bitsInStorage();
        Optional<LongStream> data;
        if (bits == 0) data = Optional.empty();
        else {
            int length = (SECTION_SIZE + 64 / bits - 1) / (64 / bits);
            int status;
            try {
                status =
                        (int)
                                ENCODE.invokeExact(
                                        scratch.indices,
                                        SECTION_SIZE,
                                        bits,
                                        scratch.output,
                                        aliases ? unique : 0,
                                        scratch.packed,
                                        length);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Native palette encoding failed", e);
            }
            if (status < 0) return null;
            long[] packed = new long[length];
            MemorySegment.copy(scratch.packed, 0, MemorySegment.ofArray(packed), 0, length * 8L);
            data = Optional.of(java.util.Arrays.stream(packed));
        }
        return new PalettedContainerRO.PackedData<>(entries, data, bits);
    }
}
