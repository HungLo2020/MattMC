package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.*;
import net.minecraft.util.NativeLibraryLoader;

/** Runs one whole-source render conversion in Rust and answers its contextual queries. */
public final class NativeDhRenderConversion {
    public static final int SIZE = 0, POINT = 1, RESOLVE = 2, SOLID = 3, LIQUID = 4,
        OPACITY = 5, SNOW = 6, COLOR = 7, PREVIOUS_MATERIAL = 9, OUTPUT_GET = 10,
        OUTPUT_SET = 11, SEMANTIC_SET = 12, SEMANTIC_MERGE = 13, BLOCK_MATERIAL = 14,
        INVALID_POINT = 15, COLOR_SELECTED = 16, SEMANTIC_SET_UNAVAILABLE = 17, SEMANTIC_MERGE_UNAVAILABLE = 18,
        COLUMN_BEGIN = 19, COLUMN_END = 20;
    @FunctionalInterface public interface Access {
        long call(int operation, int index, long value);
    }
    private static final class Frame {
        Access access;
        Throwable failure;
    }
    private static final class Frames {
        final Frame root = new Frame();
        Frame active;
    }
    // One cleared root per worker. A contextual query can start another
    // conversion on the same thread; it gets a temporary frame.
    private static final ThreadLocal<Frames> FRAMES = ThreadLocal.withInitial(Frames::new);
    private static final MemorySegment CALLBACK;
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_render_convert_source",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    static {
        try {
            var target = MethodHandles.lookup().findStatic(NativeDhRenderConversion.class, "callback",
                MethodType.methodType(long.class, int.class, int.class, long.class, long.class));
            CALLBACK = Linker.nativeLinker().upcallStub(target, FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), Arena.global());
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private NativeDhRenderConversion() {}
    private static long callback(int operation, int index, long value, long failureAddress) {
        Frame frame = FRAMES.get().active;
        try { return frame.access.call(operation, index, value); }
        catch (Throwable e) {
            frame.failure = e;
            // This address belongs to the suspended native callback's stack.
            // Materialize a segment only on failure, never retain it or expose
            // it to contextual code. Successful queries pass primitives only.
            MemorySegment.ofAddress(failureAddress).reinterpret(1).set(ValueLayout.JAVA_BYTE, 0, (byte)1);
            return 0;
        }
    }
    /**
     * Converts every column of {@code source} into {@code output}. The caller
     * keeps all owners open for the call; {@code scratch} holds one tall column.
     */
    public static void convertSource(int flags, int caveY, Access access, MemorySegment source,
        NativeDhRenderSource output, int height, NativeDhRenderSource scratch, MemorySegment materials) {
        Frames frames = FRAMES.get();
        Frame previous = frames.active, current = previous == null ? frames.root : new Frame();
        current.access = access;
        frames.active = current;
        try {
            int result = (int)RUN.invokeExact(flags, caveY, CALLBACK, source, output.borrowOwner(), height,
                scratch.borrowOwner(), materials);
            if (current.failure != null) throw current.failure;
            if (result == -5) throw new IllegalArgumentException("DH full-data column points overlap");
            if (result != 1) throw new IllegalStateException("Rust DH render conversion failed");
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Rust DH render conversion callback failed", e); }
        finally {
            frames.active = previous;
            current.access = null;
            current.failure = null;
            java.lang.ref.Reference.reachabilityFence(output);
            java.lang.ref.Reference.reachabilityFence(scratch);
        }
    }
}
