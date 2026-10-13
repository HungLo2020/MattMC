package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Read-only CPU projection of Rust-owned DH height fields, not a Java mirror. */
public final class NativeDhHeightmaps {
    private static final int BYTES = 8 + 512 * Integer.BYTES;
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_heightmaps_from_chunk",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_heightmaps_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final MemorySegment view;
    private NativeDhHeightmaps(MemorySegment pointer) {
        // The view is the batched reclaimer's referent; reads fence this object
        // and native callers fence its holder.
        try { view = pointer.reinterpret(BYTES).asReadOnly(); NativeReclaimer.register(view, pointer, NativeReclaimer.DH_HEIGHTMAPS); }
        catch (Throwable failure) { release(pointer); throw failure; }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot release DH height fields", e); }
    }
    /** Custom reader/shape/cache behavior keeps its original Java callbacks. */
    @Nullable
    public static NativeDhHeightmaps build(ChunkAccess chunk) {
        if (chunk.getClass() != ProtoChunk.class && chunk.getClass() != LevelChunk.class) return null;
        if (chunk instanceof LevelChunk level && level.getLevel().isDebug()) return null;
        int min = chunk.getMinY(), height = chunk.getHeight();
        if (min % 16 != 0 || Math.abs((long)min) > 1_000_000 || height < 16 || height > 4096 || height % 16 != 0) return null;
        if (!NativeCollisionGeometry.ready()) return null;
        var inputs = chunk.nativeDhSections();
        if (inputs == null) return null;
        try {
            for (int index = 0; index < inputs.size(); index++) {
                var owner = inputs.block(index);
                // Preserve public wrapper replacements before native cache reuse.
                if (!inputs.empty(index)) {
                    for (int entry = 0, count = owner.paletteSize(); entry < count; entry++) {
                        BlockState state = owner.paletteValue(entry);
                        var wrapper = BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.get(state);
                        if (wrapper != null && (wrapper.getClass() != BlockStateWrapper.class || wrapper.blockState != state)) return null;
                    }
                }
            }
            MemorySegment pointer = (MemorySegment)CREATE.invokeExact(inputs.stageOwner());
            return pointer.address() == 0 ? null : new NativeDhHeightmaps(pointer);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot build native DH height fields", e); }
        finally { Reference.reachabilityFence(inputs); }
    }
    MemorySegment stageOwner() { return view; }
    public int minHeight() { return read(0); }
    public int maxHeight() { return read(1); }
    public int solid(int x, int z) { checkColumn(x, z); return read(2 + x * 16 + z); }
    public int blocking(int x, int z) { checkColumn(x, z); return read(258 + x * 16 + z); }
    private static void checkColumn(int x, int z) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16) throw new IndexOutOfBoundsException("DH column outside chunk");
    }
    private int read(int index) {
        try { return view.getAtIndex(ValueLayout.JAVA_INT, index); }
        finally { Reference.reachabilityFence(this); }
    }
}
