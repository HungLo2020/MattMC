package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.world.DistantHorizonsSemanticCollector;

/** Build-local CPU lease. Rust owns quad storage and topology; no GPU objects. */
final class NativeDhQuadBuilder implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, descriptor, Linker.Option.critical(true));
    }
    private static final MethodHandle CREATE = bind("mattmc_dh_quads_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = bind("mattmc_dh_quads_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ADD = bind("mattmc_dh_quads_add", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle MERGE = bind("mattmc_dh_quads_merge", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle COPY = bind("mattmc_dh_quads_copy", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle COUNT = bind("mattmc_dh_quads_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final EDhDirection[] DIRECTIONS = EDhDirection.values();
    private static final class Lease implements Runnable {
        MemorySegment pointer;
        Lease(MemorySegment pointer) { this.pointer = pointer; }
        public synchronized void run() {
            if (pointer.address() == 0) return;
            try { RELEASE.invokeExact(pointer); }
            catch (Throwable e) { throw failure(e); }
            finally { pointer = MemorySegment.NULL; }
        }
    }
    private final Lease lease;
    private final Cleaner.Cleanable cleanable;
    NativeDhQuadBuilder(boolean transparency) {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(transparency ? 1 : 0);
            if (pointer.address() == 0) throw new IllegalStateException("Cannot allocate DH quad owner");
            lease = new Lease(pointer);
            try { cleanable = CLEANER.register(this, lease); }
            catch (Throwable e) { lease.run(); throw e; }
        } catch (Throwable e) { throw failure(e); }
    }
    private static RuntimeException failure(Throwable error) {
        if (error instanceof Error e) throw e;
        return error instanceof RuntimeException e ? e : new IllegalStateException("Native DH quad operation failed", error);
    }
    private MemorySegment pointer() {
        if (lease.pointer.address() == 0) throw new IllegalStateException("DH quad builder is closed");
        return lease.pointer;
    }
    private static long policy() {
        return (DistantHorizonsSemanticCollector.usesExactMaterialTopologyBuild() ? 1L << 49 : 0)
            | (Config.Client.Advanced.Debugging.showOverlappingQuadErrors.get() ? 1L << 50 : 0)
            | (Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get() != 0 ? 1L << 51 : 0);
    }
    void add(EDhDirection direction, short x, short y, short z, short width, short height,
             int color, byte material, byte sky, byte block, int identity, byte variant, long position, boolean premerge) {
        int directionIndex = direction.ordinal();
        if (width == 0 || height == 0) throw new IllegalArgumentException("Size 0 quad!");
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative sized quad!");
        long geometry = (x & 0xffffL) | (y & 0xffffL) << 16 | (z & 0xffffL) << 32 | (width & 0xffffL) << 48;
        long attributes = (height & 0xffffL) | (long)directionIndex << 16 | (material & 0xffL) << 24
            | (sky & 0xffL) << 32 | (block & 0xffL) << 40 | (premerge ? 1L << 48 : 0)
            | (premerge ? policy() : 0L) | (variant & 0xffL) << 56;
        synchronized (lease) {
            try { if ((int)ADD.invokeExact(pointer(), geometry, attributes, color, identity, position) != 1)
                throw new IllegalStateException("Native DH quad admission failed"); }
            catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    void merge() {
        synchronized (lease) {
            try { if ((int)MERGE.invokeExact(pointer(), policy()) != 1) throw new IllegalStateException("Native DH merge failed"); }
            catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    int count(int field) {
        synchronized (lease) {
            try { return (int)COUNT.invokeExact(pointer(), field); }
            catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    /** Copy before contextual Java callbacks: reentrant mutation cannot invalidate these rows. */
    List<BufferQuad> snapshot(int layer, int direction) {
        synchronized (lease) {
            try {
                int count = (int)COPY.invokeExact(pointer(), layer, direction, MemorySegment.NULL, 0);
                if (count < 0) throw new IllegalStateException("Invalid DH quad projection");
                if (count == 0) return List.of();
                byte[] bytes = new byte[Math.multiplyExact(count, 32)];
                if ((int)COPY.invokeExact(pointer(), layer, direction, MemorySegment.ofArray(bytes), count) != count)
                    throw new IllegalStateException("Incomplete DH quad projection");
                var rows = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                return new AbstractList<>() {
                    public int size() { return count; }
                    public BufferQuad get(int index) {
                        java.util.Objects.checkIndex(index, count);
                        int p = index * 32;
                        var quad = new BufferQuad(rows.getShort(p), rows.getShort(p+2), rows.getShort(p+4),
                            (short)1, (short)1, rows.getInt(p+16), rows.get(p+11), rows.get(p+12), rows.get(p+13),
                            DIRECTIONS[rows.get(p+10)], rows.getInt(p+20), rows.get(p+15), rows.getLong(p+24));
                        quad.hasError = rows.get(p+14) != 0;
                        // Frozen's compound short additions can wrap after a valid admission.
                        quad.widthEastWest = rows.getShort(p+6);
                        quad.widthNorthSouthOrUpDown = rows.getShort(p+8);
                        return quad;
                    }
                };
            } catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    public void close() { cleanable.clean(); }
}
