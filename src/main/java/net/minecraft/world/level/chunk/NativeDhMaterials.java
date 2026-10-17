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

/** One semantic material lease shared by native DH reduction and occlusion; always available. */
public final class NativeDhMaterials implements AutoCloseable {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle FROM_MAP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_from_map",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FROM_FACTS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_from_facts",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_materials_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final Arena arena;
    private final boolean nativeMapInput;
    final MemorySegment owner;
    private NativeDhMaterials(MemorySegment pointer, boolean nativeMapInput) {
        this.nativeMapInput = nativeMapInput;
        arena = Arena.ofConfined(); boolean attached = false;
        try {
            owner = pointer.reinterpret(1, arena, NativeDhMaterials::release); attached = true;
        } catch (RuntimeException | Error e) { if (!attached) release(pointer); arena.close(); throw e; }
    }
    /**
     * The mapping's material facts. Registered block states use Rust's block
     * catalog; otherwise (before the catalog is installed, or for test
     * wrappers) each entry's wrapper supplies {@code getOpacity}, {@code isAir}
     * and {@code isLiquid}. Null only for an empty mapping, which no update reads.
     */
    @Nullable public static NativeDhMaterials create(FullDataPointIdMap mapping) {
        if (mapping == null || mapping.size() == 0) return null;
        var stock = stock(mapping);
        if (stock != null) return stock;
        byte[] facts = new byte[mapping.size() * 3];
        for (int id = 0; id < mapping.size(); id++) {
            var wrapper = mapping.getBlockStateWrapper(id);
            facts[id * 3] = (byte) Math.max(0, Math.min(255, wrapper.getOpacity()));
            facts[id * 3 + 1] = (byte) (wrapper.isAir() ? 1 : 0);
            facts[id * 3 + 2] = (byte) (wrapper.isLiquid() ? 1 : 0);
        }
        try {
            var pointer = (MemorySegment) FROM_FACTS.invokeExact(MemorySegment.ofArray(facts), mapping.size());
            if (pointer.address() == 0) throw new OutOfMemoryError("DH material facts");
            return new NativeDhMaterials(pointer, false);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
    }
    @Nullable private static NativeDhMaterials stock(FullDataPointIdMap mapping) {
        if (!NativeCollisionGeometry.ready()) return null;
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
            if (!(wrapper instanceof BlockStateWrapper stock)) return null;
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
    @Override public void close() { arena.close(); }
}
