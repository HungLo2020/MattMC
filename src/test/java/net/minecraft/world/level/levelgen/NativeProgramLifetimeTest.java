package net.minecraft.world.level.levelgen;

import java.lang.foreign.MemorySegment;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The cell program memory a noise fill binds outlives every snapshot rebuild
 * until the fill releases it. Rust keeps only the address, so a replaced
 * snapshot whose arena became unreachable would be freed under the traversal.
 * The oracle is reachability of the bound segment's arena scope, observed
 * through a weak reference; it does not depend on a crash. */
class NativeProgramLifetimeTest {
    static Holder<NoiseGeneratorSettings> overworld;

    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
        overworld = NativeNoiseFillTest.settings.stream()
            .filter(holder -> holder.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
    }

    @AfterAll static void close() {
        NativeNoiseFill.traversalHook = null;
        NativeNoiseFillTest.close();
    }

    static RandomState random(long seed) {
        return RandomState.create(overworld.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
    }

    static NativeDensityProgram cellProgram(RandomState random) {
        NativeChunkNoise template = random.nativeChunkNoise(overworld.value());
        assertNotNull(template, "overworld compiles to native chunk noise");
        return template.cellProgram;
    }

    /** What a thread that raced to a stale snapshot, or a staleness rebuild, does. */
    static void replaceSnapshot(NativeDensityProgram program) {
        try {
            Field snapshot = NativeDensityProgram.class.getDeclaredField("snapshot");
            snapshot.setAccessible(true);
            snapshot.set(program, null);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        program.memory();
    }

    /** A weak view of the arena behind the program's current memory; no strong
     * reference to the segment stays in the caller's frame. */
    static WeakReference<Object> arena(NativeDensityProgram program) {
        return new WeakReference<>(program.memory().scope());
    }

    static boolean collected(WeakReference<Object> arena) {
        for (int attempt = 0; attempt < 40; attempt++) {
            System.gc();
            if (arena.get() == null) return true;
            try { Thread.sleep(5); } catch (InterruptedException error) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /** A rebuild that loses the race to a fresh snapshot keeps it. */
    @Test void rebuildKeepsAFreshSnapshot() throws Exception {
        NativeDensityProgram program = cellProgram(random(11));
        MemorySegment bound = program.memory();
        Method rebuild = NativeDensityProgram.class.getDeclaredMethod("rebuild");
        rebuild.setAccessible(true);
        rebuild.invoke(program);
        assertSame(bound, program.memory(), "a fresh snapshot is never replaced");
    }

    /** A snapshot replaced mid-traversal stays allocated until the fill ends,
     * and the fill matches Java's loop. */
    @Test void boundProgramOutlivesARebuildDuringTraversal() {
        RandomState random = random(-7_340_013_412_337L);
        NativeDensityProgram program = cellProgram(random);
        WeakReference<Object> arena = arena(program);
        AtomicInteger runs = new AtomicInteger();
        AtomicReference<Boolean> retained = new AtomicReference<>();
        NativeNoiseFill.traversalHook = () -> {
            if (runs.getAndIncrement() != 0) return;
            replaceSnapshot(program);
            retained.set(!collected(arena));
        };
        ChunkPos pos = new ChunkPos(37, -91);
        NativeNoiseFillTest.Filled filled;
        try {
            long traversals = NativeNoiseFill.TRAVERSALS.get();
            filled = NativeNoiseFillTest.fill(overworld, random, pos, true);
            assertEquals(1, NativeNoiseFill.TRAVERSALS.get() - traversals, "the fill ran the Rust traversal");
        } finally {
            NativeNoiseFill.traversalHook = null;
        }
        assertTrue(runs.get() > 0, "the hook ran mid-traversal");
        assertEquals(Boolean.TRUE, retained.get(), "the bound program's arena stays reachable while Rust traverses it");
        assertTrue(collected(arena), "once released, the replaced snapshot's arena is reclaimable");
        NativeNoiseFillTest.compare(NativeNoiseFillTest.fill(overworld, random(-7_340_013_412_337L), pos, false), filled, "rebuilt mid-traversal");
    }
}
