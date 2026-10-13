package net.vulkanic.world;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.HashSet;
import net.minecraft.util.NativeLibraryLoader;

/** One conversion's Rust material facts and its tall-column scratch. Close
 * before returning the completed render source so no full-data or level
 * binding is retained. */
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
    public static NativeDhConversionScope create(FullDataSourceV2 source) {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(Math.max(1, source.mapping.size()));
            if (pointer.address() == 0) throw new OutOfMemoryError("DH conversion material scope");
            return new NativeDhConversionScope(pointer);
        } catch (Throwable e) { throw failure(e); }
    }
    /** Material facts depend on the avoided-block sets; new sets clear them. */
    public MemorySegment borrow(HashSet<IBlockStateWrapper> ignored, HashSet<IBlockStateWrapper> caves,
        HashSet<IBlockStateWrapper> snow) {
        if (this.ignored != ignored || this.caves != caves || this.snow != snow) {
            try { CLEAR.invokeExact(owner); } catch (Throwable e) { throw failure(e); }
            this.ignored = ignored; this.caves = caves; this.snow = snow;
        }
        return owner;
    }
    /** One 4,096-point window for a column taller than the render source. */
    public NativeDhRenderSource scratch() {
        if (!owner.scope().isAlive()) throw new IllegalStateException("DH conversion scope is closed");
        if (scratch == null) scratch = new NativeDhRenderSource(1);
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
