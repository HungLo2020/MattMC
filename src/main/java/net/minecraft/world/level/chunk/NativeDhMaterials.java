package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import org.jetbrains.annotations.Nullable;

/** One semantic material lease shared by native DH reduction and occlusion. */
public final class NativeDhMaterials implements AutoCloseable {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle FROM_MAP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_from_map",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final Arena arena;
    private final boolean nativeMapInput;
    final MemorySegment owner, neighbors, result;
    private NativeDhMaterials(MemorySegment pointer, boolean nativeMapInput) {
        this.nativeMapInput = nativeMapInput;
        arena = Arena.ofConfined(); boolean attached = false;
        try {
            owner = pointer.reinterpret(1, arena, NativeDhMaterials::release); attached = true;
            neighbors = arena.allocate(64, 8);
            result = arena.allocate(8, 8);
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); arena.close(); throw e; }
    }
    @Nullable public static NativeDhMaterials create(FullDataPointIdMap mapping) {
        if (mapping == null || mapping.getClass() != FullDataPointIdMap.class) return null;
        if (!NativeCollisionGeometry.ready() || mapping.size() == 0) return null;
        var map = mapping.nativeMaterialMap();
        if (map != null) {
            try {
                var pointer = (MemorySegment) FROM_MAP.invokeExact(map.stageOwner());
                return pointer.address() == 0 ? null : new NativeDhMaterials(pointer, true);
            } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
            finally { Reference.reachabilityFence(map); Reference.reachabilityFence(mapping); }
        }
        int[] states = new int[mapping.size()];
        for (int id = 0; id < states.length; id++) {
            var wrapper = mapping.getBlockStateWrapper(id);
            if (wrapper.getClass() != BlockStateWrapper.class) return null;
            var stock = (BlockStateWrapper) wrapper;
            if (stock == BlockStateWrapper.AIR) states[id] = 0;
            else {
                if (stock.blockState == null) return null;
                int state = Block.getId(stock.blockState);
                if (state < 0 || Block.stateById(state) != stock.blockState) return null;
                states[id] = state;
            }
        }
        try {
            var pointer = (MemorySegment) CREATE.invokeExact(MemorySegment.ofArray(states), states.length);
            return pointer.address() == 0 ? null : new NativeDhMaterials(pointer, false);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(mapping); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    MemorySegment stageRemap(int[] ids) { return arena.allocateFrom(ValueLayout.JAVA_INT, ids); }
    @Override public void close() { arena.close(); }
}
