package net.vulkanic.world;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.HashSet;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.NativeCollisionGeometry;

/** One conversion's bounded Rust material facts and reusable native scratch.
 * Contextual/custom inputs keep their ordered queries. Close before returning
 * the completed render source so no full-data or level binding is retained. */
public final class NativeDhConversionScope implements AutoCloseable {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_conversion_materials_" + name, descriptor);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CLEAR = bind("clear", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle COLORS_CHANGED = bind("colors_changed", FunctionDescriptor.ofVoid());
    /** Reload notification invalidates scoped native colors without retaining a world. */
    public static void colorsChanged() {
        try { COLORS_CHANGED.invokeExact(); } catch (Throwable e) { throw failure(e); }
    }
    private final Arena arena;
    private final MemorySegment owner;
    private NativeDhRenderSource scratch;
    private HashSet<IBlockStateWrapper> ignored, caves, snow;
    private NativeDhConversionScope(MemorySegment pointer) {
        arena = Arena.ofConfined();
        try { owner = pointer.reinterpret(1, arena, NativeDhConversionScope::release); }
        catch (Throwable e) { arena.close(); release(pointer); throw failure(e); }
    }
    public static NativeDhConversionScope create(IClientLevelWrapper level, FullDataSourceV2 source) {
        if (level.getClass() != ClientLevelWrapper.class || source.getClass() != FullDataSourceV2.class
            || source.mapping.getClass() != FullDataPointIdMap.class || !NativeCollisionGeometry.ready()) return null;
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(source.mapping.size());
            return pointer.address() == 0 ? null : new NativeDhConversionScope(pointer);
        } catch (Throwable e) { throw failure(e); }
    }
    public MemorySegment borrow(HashSet<IBlockStateWrapper> ignored, HashSet<IBlockStateWrapper> caves,
        HashSet<IBlockStateWrapper> snow) {
        if (ignored.getClass() != HashSet.class || caves.getClass() != HashSet.class || snow.getClass() != HashSet.class)
            return MemorySegment.NULL;
        if (this.ignored != ignored || this.caves != caves || this.snow != snow) {
            try { CLEAR.invokeExact(owner); } catch (Throwable e) { throw failure(e); }
            this.ignored = ignored; this.caves = caves; this.snow = snow;
        }
        return owner;
    }
    /** Ordinary full columns fit one 4,096-point scratch window. Oversized or
     * custom columns retain their public-list path. No point array crosses Java. */
    public NativeDhRenderSource temporaryOutput(int length) {
        if (!owner.scope().isAlive()) throw new IllegalStateException("DH conversion scope is closed");
        if (length < 0 || length > 4096) return null;
        if (scratch == null) scratch = new NativeDhRenderSource(1);
        NativeDhRenderSource.span(scratch, 0, length, 0, 0L);
        return scratch;
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw failure(e); }
    }
    @Override public void close() {
        arena.close();
        if (scratch != null) { scratch.close(); scratch = null; }
        ignored = null; caves = null; snow = null;
    }
    private static RuntimeException failure(Throwable e) {
        if (e instanceof Error error) throw error;
        return e instanceof RuntimeException error ? error : new IllegalStateException("DH conversion material scope", e);
    }
}
