package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Shared Rust bindings to mutable section owners; no copied block/counter data. */
final class NativeChunkInputs {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_inputs_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_inputs_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle WITH_BIOMES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_inputs_with_biome_inputs",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final class Scratch {
        final MemorySegment pairs = Arena.ofAuto().allocate(256 * 16L, 8);
        final NativeLiveBlockSection[] blocks = new NativeLiveBlockSection[256];
        final MemorySegment[] counts = new MemorySegment[256];
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final VarHandle COUNTS = ValueLayout.JAVA_LONG.varHandle();
    private final MemorySegment owner;
    // Compatibility identities for detecting container/section replacement.
    // The mutable state is exclusively in the Rust owners, not these bindings.
    private final NativeLiveBlockSection[] blocks;
    private final MemorySegment[] counts;
    @Nullable private final NativeBiomeChunkInputs biomes;
    private final int minY;
    private NativeChunkInputs(MemorySegment pointer, Scratch scratch, int count, int minY) {
        boolean attached = false;
        try {
            blocks = Arrays.copyOf(scratch.blocks, count);
            counts = Arrays.copyOf(scratch.counts, count);
            biomes = null;
            this.minY = minY;
            owner = adopt(pointer);
            attached = true;
        } finally { if (!attached) release(pointer); }
    }
    private NativeChunkInputs(MemorySegment pointer, NativeChunkInputs base, NativeBiomeChunkInputs biomes) {
        boolean attached = false;
        try {
            blocks = base.blocks; counts = base.counts; minY = base.minY;
            this.biomes = biomes;
            owner = adopt(pointer);
            attached = true;
        } finally { if (!attached) release(pointer); }
    }
    /** Biome compatibility admission remains exclusive to consumers needing it. */
    @Nullable NativeChunkInputs bindBiomes(ChunkAccess chunk) {
        NativeBiomeChunkInputs binding;
        try { binding = chunk.nativeBiomeSections(null); }
        catch (NativeBiomeChunkInputs.CaptureFailure rejected) { return null; }
        if (binding == null || binding.size() != blocks.length) return null;
        if (biomes == binding) return this;
        try {
            var pointer = (MemorySegment)WITH_BIOMES.invokeExact(owner, binding.stageOwner());
            return pointer.address() == 0 ? null : new NativeChunkInputs(pointer, this, binding);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot bind native biome inputs", e); }
        finally { Reference.reachabilityFence(binding); Reference.reachabilityFence(this); }
    }

    /**
     * Binds to a release scope's arena when one is open (the inputs pin its
     * sections). Otherwise the owner segment is the batched reclaimer's
     * referent: every native caller fences this object across its call.
     */
    private static MemorySegment adopt(MemorySegment pointer) {
        var release = NativeReleaseScope.current();
        if (release != null) return pointer.reinterpret(1, release, NativeChunkInputs::release);
        var owner = pointer.reinterpret(1);
        NativeReclaimer.register(owner, pointer, NativeReclaimer.CHUNK_INPUTS);
        return owner;
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable e) { throw new IllegalStateException("Cannot release chunk inputs", e); }
    }
    /** Immutable bindings publish through a volatile cache; original mutation exclusion still applies. */
    @Nullable static NativeChunkInputs bind(ChunkAccess chunk, @Nullable NativeChunkInputs previous) {
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return null;
        if (chunk instanceof LevelChunk level && level.getLevel().isDebug()) return null;
        int min = chunk.getMinY(), height = chunk.getHeight();
        if (min % 16 != 0 || Math.abs((long)min) > 1_000_000 || height < 16 || height > 4096 || height % 16 != 0) return null;
        var sections = chunk.getSectionsForRead();
        if (sections.length != height / 16) return null;
        var scratch = SCRATCH.get();
        try {
            boolean reuse = previous != null && previous.minY == min && previous.blocks.length == sections.length;
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i]; if (section == null) return null;
                var block = section.nativeGenerationInput(); if (block == null) return null;
                var count = section.nativeGenerationCounters();
                scratch.blocks[i] = block; scratch.counts[i] = count;
                if (reuse && (previous.blocks[i] != block || previous.counts[i].address() != count.address())) reuse = false;
            }
            if (reuse) return previous;
            for (int i = 0; i < sections.length; i++) {
                scratch.pairs.setAtIndex(ValueLayout.ADDRESS, i * 2L, scratch.blocks[i].stageOwner());
                scratch.pairs.setAtIndex(ValueLayout.ADDRESS, i * 2L + 1, scratch.counts[i]);
            }
            var pointer = (MemorySegment)CREATE.invokeExact(scratch.pairs, sections.length, min);
            return pointer.address() == 0 ? null : new NativeChunkInputs(pointer, scratch, sections.length, min);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot bind native chunk inputs", e); }
        finally {
            Reference.reachabilityFence(scratch.blocks); Reference.reachabilityFence(scratch.counts);
            Reference.reachabilityFence(sections); Reference.reachabilityFence(previous);
            Arrays.fill(scratch.blocks, null); Arrays.fill(scratch.counts, null);
        }
    }
    MemorySegment stageOwner() { return owner; }
    int size() { return blocks.length; }
    int minY() { return minY; }
    NativeLiveBlockSection block(int index) { return blocks[index]; }
    @SuppressWarnings("unchecked")
    NativeLiveBiomeSection<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biome(int index) {
        return biomes.biome(index);
    }
    boolean empty(int index) { return (short)(long)COUNTS.getAcquire(counts[index], 0L) == 0; }
}
