package net.sodium.api.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class MemoryIntrinsics {
    /**
     * Copies the number of bytes specified by {@param length} between off-heap buffers {@param src} and {@param dst}.
     * <p>
     * WARNING: This function makes no attempt to verify that the parameters are correct. If you pass invalid pointers
     * or read/write memory outside a buffer, the JVM will likely crash!
     *
     * @param src The source pointer to begin copying from
     * @param dst The destination pointer to begin copying into
     * @param length The number of bytes to copy
     */
    public static void copyMemory(long src, long dst, int length) {
        // Use the Foreign Function & Memory API (standard since Java 22)
        // This is the modern, safe replacement for sun.misc.Unsafe.copyMemory
        MemorySegment srcSegment = MemorySegment.ofAddress(src).reinterpret(length);
        MemorySegment dstSegment = MemorySegment.ofAddress(dst).reinterpret(length);
        dstSegment.copyFrom(srcSegment);
    }
}
