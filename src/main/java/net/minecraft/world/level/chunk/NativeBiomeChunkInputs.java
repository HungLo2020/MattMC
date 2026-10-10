package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Shared immutable biome bindings; no block admission or copied biome values. */
final class NativeBiomeChunkInputs {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_biome_inputs_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_biome_inputs_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final class Scratch {
        final MemorySegment pointers = Arena.ofAuto().allocate(1024 * 8L, 8);
        final NativeLiveBiomeSection<?>[] owners = new NativeLiveBiomeSection<?>[1024];
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private final MemorySegment owner;
    private final NativeLiveBiomeSection<?>[] owners;
    private NativeBiomeChunkInputs(MemorySegment pointer, Scratch scratch, int count) {
        boolean attached = false;
        try {
            owners = Arrays.copyOf(scratch.owners, count);
            owner = pointer.reinterpret(1, Arena.ofAuto(), NativeBiomeChunkInputs::release);
            attached = true;
        } finally { if (!attached) release(pointer); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (Throwable failure) { throw failure(failure); }
    }
    static final class CaptureFailure extends RuntimeException {}
    @Nullable static NativeBiomeChunkInputs bind(ChunkAccess chunk, @Nullable NativeBiomeChunkInputs previous,
        @Nullable NativeLiveBiomeSection.Binding<?> expected) {
        if (chunk.getClass() != LevelChunk.class && chunk.getClass() != ProtoChunk.class) return null;
        var sections = chunk.getSectionsForRead();
        var scratch = SCRATCH.get();
        try {
            boolean reuse = previous != null && previous.owners.length == sections.length;
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                if (section == null) return null;
                if (expected == null && section.getClass() != LevelChunkSection.class) return null;
                var values = section.getBiomes(); // World admission originally probes this before the section-class check.
                if (values == null || section.getClass() != LevelChunkSection.class || values.getClass() != PalettedContainer.class) return null;
                var input = ((PalettedContainer<?>)values).nativeLiveBiomes();
                if (input == null || (expected != null && input.binding != expected)) return null;
                if (i < scratch.owners.length) scratch.owners[i] = input;
                if (reuse && previous.owners[i] != input) reuse = false;
            }
            if (sections.length > scratch.owners.length) throw new CaptureFailure();
            if (reuse) return previous;
            for (int i = 0; i < sections.length; i++) scratch.pointers.setAtIndex(ValueLayout.ADDRESS, i, scratch.owners[i].owner);
            var pointer = (MemorySegment)CREATE.invokeExact(scratch.pointers, sections.length);
            if (pointer.address() == 0) throw new CaptureFailure();
            return new NativeBiomeChunkInputs(pointer, scratch, sections.length);
        } catch (Throwable failure) { throw failure(failure); }
        finally {
            Reference.reachabilityFence(sections); Reference.reachabilityFence(previous); Reference.reachabilityFence(scratch.owners);
            Arrays.fill(scratch.owners, null);
        }
    }
    MemorySegment stageOwner() { return owner; }
    int size() { return owners.length; }
    @SuppressWarnings("unchecked")
    NativeLiveBiomeSection<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biome(int index) {
        return (NativeLiveBiomeSection<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>)owners[index];
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException exception) return exception;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Cannot bind chunk biome inputs", failure);
    }
}
