package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded semantic IDs and saved words go directly to their Rust live owner. */
final class NativeSavedPaletteLoading {
    private static final Class<?> BLOCK_STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final Class<?> BIOME_STRATEGY = Strategy.createForBiomes(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final Set<Class<?>> LISTS = Set.of(ArrayList.class, Arrays.asList(1).getClass(),
        List.of(1).getClass(), List.of(1,2,3).getClass(), ImmutableList.of(1).getClass(), ImmutableList.of(1,2).getClass());
    private static final FunctionDescriptor LOAD = FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
    private static final MethodHandle BLOCKS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_saved_blocks_load", LOAD, Linker.Option.critical(true));
    private static final MethodHandle BIOMES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_saved_biomes_load", LOAD, Linker.Option.critical(true));
    private static final ThreadLocal<int[]> IDS = ThreadLocal.withInitial(() -> new int[256]);
    static final long[] EMPTY_WORDS = new long[0];
    private NativeSavedPaletteLoading() {}

    /** Null leaves the original loader in charge, using its already consumed words. */
    static <T> PalettedContainer<T> unpack(Strategy<T> strategy, List<T> entries, long[] words) {
        boolean blocks = strategy.getClass() == BLOCK_STRATEGY && strategy.globalMap() == Block.BLOCK_STATE_REGISTRY;
        var binding = !blocks && strategy.getClass() == BIOME_STRATEGY ? NativeLiveBiomeSection.binding(strategy) : null;
        if (!blocks && binding == null || !LISTS.contains(entries.getClass())) return null;
        int count = entries.size(), limit = blocks ? Block.BLOCK_STATE_REGISTRY.size() : binding.values.size();
        if (count < 1 || count > 65536 || limit < (blocks ? 257 : 9) || limit > 65535 || words.length > 1024) return null;
        int[] ids = IDS.get();
        if (ids.length < count) { ids = new int[Integer.highestOneBit(count - 1) << 1]; IDS.set(ids); }
        for (int i=0;i<count;i++) {
            var value = entries.get(i);
            int id;
            if (blocks) {
                if (value == null || value.getClass() != BlockState.class) return null;
                id = Block.getId((BlockState)value);
                if (Block.BLOCK_STATE_REGISTRY.byId(id) != value) return null;
            } else id = binding.id(value);
            if (id < 0) return null;
            ids[i] = id;
        }
        try {
            var pointer = (MemorySegment)(blocks ? BLOCKS : BIOMES).invokeExact(MemorySegment.ofArray(words), words.length,
                MemorySegment.ofArray(ids).asSlice(0, count * 4L), count, limit, strategy.globalPaletteBitsInMemory);
            if (pointer.address() == 0) return null;
            return blocks ? new PalettedContainer<>(strategy, NativeLiveBlockSection.adoptOwned(pointer))
                : new PalettedContainer<>(strategy, NativeLiveBiomeSection.adoptOwned(pointer, binding));
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot load saved native palette", failure); }
    }
}
