package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;

/** Whole global-palette save-data repack; Java retains object/registry ownership. */
final class NativePaletteUnpacking {
    private static final Class<?> BLOCK_STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final Class<?> ARRAY_LIST = Arrays.asList(1, 2).getClass();
    private static final Class<?> IMMUTABLE_LIST = List.of(1, 2, 3).getClass();
    private static final Class<?> GUAVA_LIST = ImmutableList.of(1, 2).getClass();
    private static final MethodHandle DECODE = NativeLibraryLoader.downcallHandle(
            "mattmc_rust", "mattmc_palette_unpack_decode", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle(
            "mattmc_rust", "mattmc_palette_encode", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativePaletteUnpacking() {}

    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(1024 * 8, 8);
        final MemorySegment lookup = arena.allocate(65536 * 4, 4);
        final MemorySegment decoded = arena.allocate(8192 * 4, 4);
        final MemorySegment indices = decoded.asSlice(4096 * 4, 4096 * 4);
        final MemorySegment mapping = arena.allocate(4096 * 4, 4);
        final MemorySegment packed = arena.allocate(2048 * 8, 8);
        final int[] ids = new int[4096];
        final int[] mapped = new int[4096];
        boolean inUse;
    }

    /** Null selects the original caller, including all custom/null/malformed palette semantics. */
    static <T> PalettedContainer<T> unpack(Strategy<T> owner, Configuration config, List<T> entries, long[] words) {
        if (owner.getClass() != BLOCK_STRATEGY || owner.globalMap() != Block.BLOCK_STATE_REGISTRY
                || config.getClass() != Configuration.Global.class) return null;
        var listClass = entries.getClass();
        // Unknown lists can expose observable forEach/get callbacks. Leave them untouched.
        if (listClass != ArrayList.class && listClass != ARRAY_LIST
                && listClass != IMMUTABLE_LIST && listClass != GUAVA_LIST) return null;
        int bits = config.bitsInStorage(), count = entries.size(), targetBits = config.bitsInMemory();
        if (bits < 9 || bits > 16 || count < 257 || count > 65536
                || targetBits < 1 || targetBits > 16
                || !config.equals(owner.getConfigurationForPaletteSize(count))) return null;
        // With non-null entries HashMapPalette's byId[i] is exactly entries[i],
        // even for identity aliases. Nulls have different insertion/growth behavior.
        for (int i = 0; i < count; i++) if (entries.get(i) == null) return null;
        // Preserve the exact original storage-length exception and caller error result.
        new SimpleBitStorage(bits, 4096, words);
        var scratch = SCRATCH.get();
        if (scratch.inUse) return null;
        scratch.inUse = true;
        try {
            MemorySegment.copy(MemorySegment.ofArray(words), 0, scratch.words, 0, words.length * 8L);
            int used = (int) DECODE.invokeExact(scratch.words, words.length, bits, count,
                    scratch.lookup, 65536, scratch.decoded, 8192);
            // Original code supplies MissingPaletteEntryException and its first invalid ID.
            if (used < 1) return null;
            MemorySegment.copy(scratch.decoded, 0, MemorySegment.ofArray(scratch.ids), 0, used * 4L);
            var palette = owner.globalPalette();
            var noResize = PaletteResize.<T>noResizeExpected();
            for (int i = 0; i < used; i++)
                scratch.mapped[i] = palette.idFor(entries.get(scratch.ids[i]), noResize);
            MemorySegment.copy(MemorySegment.ofArray(scratch.mapped), 0, scratch.mapping, 0, used * 4L);
            int length = (4096 + 64 / targetBits - 1) / (64 / targetBits);
            int status = (int) ENCODE.invokeExact(scratch.indices, 4096, targetBits,
                    scratch.mapping, used, scratch.packed, length);
            if (status != 0) throw new IllegalStateException("Invalid native palette unpack result: " + status);
            long[] output = new long[length];
            MemorySegment.copy(scratch.packed, 0, MemorySegment.ofArray(output), 0, length * 8L);
            return new PalettedContainer<>(owner, config, new SimpleBitStorage(targetBits, 4096, output), palette);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native palette unpack failed", e);
        } finally {
            scratch.inUse = false;
        }
    }
}
