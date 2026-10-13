package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.*;
import net.minecraft.util.NativeLibraryLoader;

/** Synchronous contextual facts/publication adapter for Rust column policy. */
public final class NativeDhRenderConversion {
    public static final int SIZE = 0, POINT = 1, RESOLVE = 2, SOLID = 3, LIQUID = 4,
        OPACITY = 5, SNOW = 6, COLOR = 7, PREVIOUS_MATERIAL = 9, OUTPUT_GET = 10,
        OUTPUT_SET = 11, SEMANTIC_SET = 12, SEMANTIC_MERGE = 13, BLOCK_MATERIAL = 14,
        INVALID_POINT = 15, COLOR_SELECTED = 16, SEMANTIC_SET_UNAVAILABLE = 17, SEMANTIC_MERGE_UNAVAILABLE = 18;
    @FunctionalInterface public interface Access {
        long call(int operation, int index, long value, long extra);
        default MemorySegment inputOwner() { return MemorySegment.NULL; }
        default MemorySegment outputOwner() { return MemorySegment.NULL; }
        default long outputWindow() { return 0; }
        default MemorySegment materialsOwner() { return MemorySegment.NULL; }
    }
    private static final class Frame {
        Access access;
        Throwable failure;
    }
    private static final class Frames {
        final Frame root = new Frame();
        Frame active;
    }
    // One cleared root per worker, not a new ThreadLocal entry per column.
    // Nested calls get temporary frames; no unbounded depth cache is retained.
    private static final ThreadLocal<Frames> FRAMES = ThreadLocal.withInitial(Frames::new);
    private static final MemorySegment CALLBACK;
    private static final MethodHandle RUN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_render_convert",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
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
        try { return frame.access.call(operation, index, value, 0); }
        catch (Throwable e) {
            frame.failure = e;
            // This address belongs to the suspended native callback's stack.
            // Materialize a segment only on failure, never retain it or expose
            // it to contextual code. Successful queries pass primitives only.
            MemorySegment.ofAddress(failureAddress).reinterpret(1).set(ValueLayout.JAVA_BYTE, 0, (byte)1);
            return 0;
        }
    }
    public static void convert(int flags, int caveY, Access access) {
        Frames frames = FRAMES.get();
        Frame previous = frames.active, current = previous == null ? frames.root : new Frame();
        current.access = access;
        frames.active = current;
        try {
            int result = (int)RUN.invokeExact(flags, caveY, CALLBACK, access.inputOwner(), access.outputOwner(), access.outputWindow(), access.materialsOwner());
            if (current.failure != null) throw current.failure;
            if (result != 1) throw new IllegalStateException("Rust DH column conversion failed");
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Rust DH column conversion callback failed", e); }
        finally {
            frames.active = previous;
            current.access = null;
            current.failure = null;
        }
    }
}
