package net.vulkanic.world;

import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Rust owns render-column reduction; Java only checks the storage windows. */
public final class NativeDhRenderReduction {
    private static final int LIMIT = 4096;
    private static final MethodHandle REDUCE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_render_reduce",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.critical(true));
    private NativeDhRenderReduction() {}

    /**
     * Reduces {@code source} into {@code output}. Overlapping input points are
     * rejected as in Frozen: an {@link IllegalArgumentException} with the
     * destination untouched.
     */
    public static void reduce(IColumnDataView source, int[] sourceMaterials, ColumnArrayView output, int[] outputMaterials) {
        if (!NativeDhRenderSource.canonical(output.data) || output.offset < 0 || output.size < 0 || output.size > LIMIT
            || output.offset > output.data.size() - output.size || (sourceMaterials == null) != (outputMaterials == null))
            throw new IllegalArgumentException("Unsupported DH render column output window");
        MemorySegment input = MemorySegment.NULL;
        int count = 0;
        // The original ordinary zero-target path does not read the source.
        if (sourceMaterials != null || output.verticalSize > 0) {
            if (!(source instanceof ColumnArrayView view) || !NativeDhRenderSource.canonical(view.data)
                || view.offset < 0 || view.size < 0 || view.size > LIMIT || view.offset > view.data.size() - view.size)
                throw new IllegalArgumentException("Unsupported DH render column input window");
            count = view.size;
            input = NativeDhRenderSource.borrow(view.data, view.offset, count);
        }
        if (sourceMaterials != null && (sourceMaterials.length < count || outputMaterials.length < output.size))
            throw new IllegalArgumentException("Semantic material arrays must cover their render-data views");
        var destination = NativeDhRenderSource.borrow(output.data, output.offset, output.size);
        var materials = sourceMaterials == null ? MemorySegment.NULL : MemorySegment.ofArray(sourceMaterials);
        var resultMaterials = outputMaterials == null ? MemorySegment.NULL : MemorySegment.ofArray(outputMaterials);
        int result;
        try { result = (int)REDUCE.invokeExact(input, count, materials, output.verticalSize, destination, output.size, resultMaterials); }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot reduce native DH render column", e); }
        finally { java.lang.ref.Reference.reachabilityFence(source); java.lang.ref.Reference.reachabilityFence(output); }
        if (result != 1) throw new IllegalArgumentException("DH render column points overlap or exceed the output");
    }
}
