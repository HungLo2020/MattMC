package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.core.Holder;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A chunk's fresh sections in one native allocation (Rust {@code world/level/chunk/slab.rs}).
 *
 * <p>A fresh section created on its own makes a block owner, a biome owner and
 * counters, each a separate Java owner with its own cleanup. Every item here is
 * a segment scoped to one automatic arena per chunk, so holding any item keeps
 * the whole slab alive and the slab is released, once, when none is reachable.
 * The sections are otherwise identical to {@link LevelChunkSection#LevelChunkSection(PalettedContainerFactory)}:
 * single-value native containers of the factory's defaults and zeroed counters.
 * Copies, generation installs and network reads replace items with ordinary owners.
 */
public final class NativeChunkSlab {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_slab_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_slab_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    /** {@code -Dmattmc.chunk.sectionSlabs=false} creates every fresh section on its own (control runs). */
    private static final boolean ENABLED = !"false".equals(System.getProperty("mattmc.chunk.sectionSlabs"));

    private static final java.util.concurrent.atomic.AtomicLong CREATED = new java.util.concurrent.atomic.AtomicLong();

    private NativeChunkSlab() {}

    /** Slabs created so far (diagnostics and tests). */
    public static long created() { return CREATED.get(); }

    /** {@code count} fresh sections for {@code factory}, or null when its containers would not be native. */
    static @Nullable LevelChunkSection[] fresh(PalettedContainerFactory factory, int count) {
        if (!ENABLED || count < 1 || count > 4096 || factory.getClass() != PalettedContainerFactory.class) return null;
        int[] block = NativeLiveBlockSection.freshParameters(factory.blockStatesStrategy(), factory.defaultBlockState());
        int[] biome = NativeLiveBiomeSection.freshParameters(factory.biomeStrategy(), factory.defaultBiome());
        var binding = NativeLiveBiomeSection.binding(factory.biomeStrategy());
        if (block == null || biome == null || binding == null) return null;
        var arena = Arena.ofAuto();
        MemorySegment slab;
        long[] items = new long[3 * count];
        try (var scratch = Arena.ofConfined()) {
            var output = scratch.allocate(ValueLayout.JAVA_LONG, 3L * count);
            slab = (MemorySegment) CREATE.invokeExact(count, block[0], block[1], block[2], block[3],
                biome[0], biome[1], biome[2], biome[3], output);
            MemorySegment.copy(output, ValueLayout.JAVA_LONG, 0, items, 0, items.length);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        if (slab.address() == 0) return null;
        CREATED.incrementAndGet();
        // Every item segment below carries this scope; the slab is released once none is reachable.
        slab.reinterpret(1, arena, NativeChunkSlab::release);
        var sections = new LevelChunkSection[count];
        for (int i = 0; i < count; i++) {
            var blocks = NativeLiveBlockSection.adoptBorrowed(MemorySegment.ofAddress(items[i]).reinterpret(1, arena, null));
            var biomes = NativeLiveBiomeSection.adoptBorrowed(MemorySegment.ofAddress(items[count + i]).reinterpret(1, arena, null), binding);
            var counters = new NativeSectionCounters(MemorySegment.ofAddress(items[2 * count + i]).reinterpret(8, arena, null), true);
            sections[i] = new LevelChunkSection(counters,
                new PalettedContainer<BlockState>(factory.blockStatesStrategy(), blocks),
                new PalettedContainer<Holder<Biome>>(factory.biomeStrategy(), biomes));
        }
        return sections;
    }

    private static void release(MemorySegment slab) {
        try { RELEASE.invokeExact(slab); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
