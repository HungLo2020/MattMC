package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import net.vulkanic.world.NativeDhRenderSource;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
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
    private static final MethodHandle BOX = bind("mattmc_dh_box_add", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle BOX_SOURCE = bind("mattmc_dh_box_add_source", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    // Not critical: a whole section can take milliseconds and must not hold off GC.
    private static final MethodHandle SECTION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_section_build",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    static final int SECTION_PARAMS = 27;
    static final int MAX_INTERNED = 4096;
    /** Per-thread native staging for section builds; holds numbers only, never source pointers between calls. */
    private static final class SectionScratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment params = arena.allocate(SECTION_PARAMS * 8L, 8);
        final MemorySegment neighbors = arena.allocate(4 * 8L, 8);
        final MemorySegment minX = arena.allocate(64 * 2L, 8);
        final MemorySegment minZ = arena.allocate(64 * 2L, 8);
        final MemorySegment order = arena.allocate(MAX_INTERNED * 4L, 8);
        MemorySegment flagColors;
    }
    private static final ThreadLocal<SectionScratch> SECTION_SCRATCH = ThreadLocal.withInitial(SectionScratch::new);
    private static final Class<?> EMPTY_SPANS = List.of().getClass();
    private static final Class<?> SMALL_SPANS = List.of(0).getClass();
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
    static long policy() {
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
    private static boolean canonical(ColumnArrayView v) {
        return v == null || v.data != null && NativeDhRenderSource.canonical(v.data)
            && v.size >= 0 && v.size <= 4096 && v.offset >= 0 && v.offset <= v.data.size()-v.size;
    }
    private static MemorySegment borrow(ColumnArrayView v) {
        return v == null || v.size == 0 ? MemorySegment.NULL
            : NativeDhRenderSource.borrow(v.data, v.offset, v.size);
    }
    private static int length(ColumnArrayView v) { return v == null ? -1 : v.size; }
    private static NativeDhRenderSource commonSource(ColumnArrayView[] neighbors) {
        NativeDhRenderSource common = null;
        for (int d = 0; d < 4; d++) if (neighbors[d] != null) {
            if (!(neighbors[d].data instanceof NativeDhRenderSource source) || common != null && common != source) return null;
            common = source;
        }
        return common;
    }
    private static long window(ColumnArrayView v) { return v == null ? -1L : (v.offset & 0xffffffffL) | (long)v.size << 32; }
    /** Complete admission precedes native mutation. Replay shades are bounded immutable CPU facts. */
    boolean box(short width, short height, short x, short y, short z, int color, byte material,
        int identity, byte variant, long position, List<ColumnRenderSource.SemanticMaterialSpan> spans,
        byte sky, byte block, long top, long bottom, ColumnArrayView[] neighbors, boolean[] same,
        int caveY, boolean transparency, boolean fakeOcean, int shadeFacts, float[] replayShades) {
        if (neighbors == null || neighbors.length < 4 || same == null || same.length < 4
            || spans == null || spans.getClass() != EMPTY_SPANS && spans.getClass() != SMALL_SPANS
            || spans.size() > 4096 || shadeFacts < 0 || replayShades != null && replayShades.length != 6) return false;
        for (int d=0;d<4;d++) if (!canonical(neighbors[d])) return false;
        MemorySegment intervals=MemorySegment.NULL;
        int spanCount=spans.size();
        if (spanCount!=0) {
            long[] words=new long[spanCount*3];
            for(int i=0;i<spanCount;i++) {
                var v=spans.get(i);words[i*3]=(v.minY() & 0xffffffffL) | (long)v.maxY()<<32;
                words[i*3+1]=(v.materialId() & 0xffffffffL) | (v.variantState() & 0xffL)<<32;
                words[i*3+2]=v.variantPosition();
            }
            intervals=MemorySegment.ofArray(words);
        }
        long geometry=(x & 0xffffL) | (y & 0xffffL)<<16 | (z & 0xffffL)<<32 | (width & 0xffffL)<<48;
        long attributes=(height & 0xffffL) | (material & 0xffL)<<24 | (sky & 0xffL)<<32
            | (block & 0xffL)<<40 | policy() | (variant & 0xffL)<<56;
        int flags=(transparency?1:0) | (fakeOcean?2:0) | shadeFacts<<8;
        for(int d=0;d<4;d++) if(same[d]) flags|=1<<(d+2);
        long shade01=0,shade23=0,shade45=0;
        if(replayShades!=null) {
            flags|=1<<16;
            shade01=(Float.floatToRawIntBits(replayShades[0]) & 0xffffffffL) | (long)Float.floatToRawIntBits(replayShades[1])<<32;
            shade23=(Float.floatToRawIntBits(replayShades[2]) & 0xffffffffL) | (long)Float.floatToRawIntBits(replayShades[3])<<32;
            shade45=(Float.floatToRawIntBits(replayShades[4]) & 0xffffffffL) | (long)Float.floatToRawIntBits(replayShades[5])<<32;
        }
        var source=commonSource(neighbors);
        synchronized(lease) {
            if(lease.pointer.address()==0) return false;
            try {
                int result;
                if(source!=null) result=(int)BOX_SOURCE.invokeExact(pointer(),geometry,attributes,color,identity,position,top,bottom,
                    source.borrowOwner(),window(neighbors[0]),window(neighbors[1]),window(neighbors[2]),window(neighbors[3]),
                    caveY,flags,intervals,spanCount,shade01,shade23,shade45);
                else {
                    var north=borrow(neighbors[0]);var south=borrow(neighbors[1]);var west=borrow(neighbors[2]);var east=borrow(neighbors[3]);
                    result=(int)BOX.invokeExact(pointer(),geometry,attributes,color,identity,position,top,bottom,
                        north,length(neighbors[0]),south,length(neighbors[1]),west,length(neighbors[2]),east,length(neighbors[3]),
                        caveY,flags,intervals,spanCount,shade01,shade23,shade45);
                }
                if(result==-2) throw new IllegalArgumentException("Size 0 quad!");
                if(result==-3) throw new IllegalArgumentException("Negative sized quad!");
                if(result!=1) throw new IllegalStateException("Native DH box admission failed");
                return true;
            } catch(Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(neighbors); Reference.reachabilityFence(this); }
        }
    }
    /**
     * Builds every box of one render section in Rust. Sources must stay open
     * for the call; neighbors are indexed by compass index and may be null.
     * Returns the center source's semantic identities in builder first-use order.
     */
    int[] section(long[] params, NativeDhRenderSource center, NativeDhRenderSource[] neighbors,
        short[] minX, short[] minZ, int[] flagColors, long[] spans, long[] contributors, int internCapacity) {
        if (params.length != SECTION_PARAMS || neighbors.length != 4 || minX.length != 64 || minZ.length != 64
            || internCapacity < 0 || internCapacity > MAX_INTERNED || flagColors != null && flagColors.length != 64 * 64)
            throw new IllegalArgumentException("Invalid DH section build input");
        var scratch = SECTION_SCRATCH.get();
        MemorySegment.copy(params, 0, scratch.params, ValueLayout.JAVA_LONG, 0, SECTION_PARAMS);
        MemorySegment.copy(minX, 0, scratch.minX, ValueLayout.JAVA_SHORT, 0, 64);
        MemorySegment.copy(minZ, 0, scratch.minZ, ValueLayout.JAVA_SHORT, 0, 64);
        MemorySegment colors = MemorySegment.NULL;
        if (flagColors != null) {
            if (scratch.flagColors == null) scratch.flagColors = scratch.arena.allocate(64 * 64 * 4L, 8);
            MemorySegment.copy(flagColors, 0, scratch.flagColors, ValueLayout.JAVA_INT, 0, 64 * 64);
            colors = scratch.flagColors;
        }
        try (Arena call = Arena.ofConfined()) {
            MemorySegment spanTable = spans == null ? MemorySegment.NULL : call.allocateFrom(ValueLayout.JAVA_LONG, spans);
            MemorySegment contributorTable = contributors == null ? MemorySegment.NULL : call.allocateFrom(ValueLayout.JAVA_LONG, contributors);
            synchronized (lease) { try {
                // A closed source reports size 0; never pass a released owner to Rust.
                long expected = params[0] * 64 * 64;
                if (center.size() != expected) throw new IllegalStateException("DH render source closed during build");
                for (int d = 0; d < 4; d++) {
                    var neighbor = neighbors[d];
                    if (neighbor != null && neighbor.size() != params[2 + d] * 64 * 64)
                        throw new IllegalStateException("DH render source closed during build");
                    scratch.neighbors.setAtIndex(ValueLayout.ADDRESS, d, neighbor == null ? MemorySegment.NULL : neighbor.borrowOwner());
                }
                int result = (int)SECTION.invokeExact(pointer(), scratch.params, center.borrowOwner(), scratch.neighbors,
                    scratch.minX, scratch.minZ, colors, spanTable, spans == null ? 0 : spans.length,
                    contributorTable, contributors == null ? 0 : contributors.length, scratch.order, internCapacity);
                if (result == -2) throw new IllegalArgumentException("Size 0 quad!");
                if (result == -3) throw new IllegalArgumentException("Negative sized quad!");
                if (result == -4) throw new IllegalArgumentException("Negative y size for a render data point");
                if (result < 1) throw new IllegalStateException("Native DH section build failed");
                return scratch.order.asSlice(0, (result - 1) * 4L).toArray(ValueLayout.JAVA_INT);
            } catch (Throwable e) { throw failure(e); }
            finally {
                for (int d = 0; d < 4; d++) scratch.neighbors.setAtIndex(ValueLayout.ADDRESS, d, MemorySegment.NULL);
                Reference.reachabilityFence(center); Reference.reachabilityFence(neighbors); Reference.reachabilityFence(this);
            } }
        }
    }
    int count(int field) {
        synchronized (lease) {
            try { return (int)COUNT.invokeExact(pointer(), field); }
            catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    NativeDhPacketPlan packetPlan(int route, int grass) {
        synchronized (lease) {
            try { return NativeDhPacketPlan.create(pointer(), route, grass); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    /** Inspection copy of one retained list as 32-byte native rows (tests and diagnostics only). */
    byte[] copyRows(int layer, int direction) {
        synchronized (lease) {
            try {
                int count = (int)COPY.invokeExact(pointer(), layer, direction, MemorySegment.NULL, 0);
                if (count < 0) throw new IllegalStateException("Invalid DH quad projection");
                byte[] bytes = new byte[Math.multiplyExact(count, 32)];
                if (count > 0 && (int)COPY.invokeExact(pointer(), layer, direction, MemorySegment.ofArray(bytes), count) != count)
                    throw new IllegalStateException("Incomplete DH quad projection");
                return bytes;
            } catch (Throwable e) { throw failure(e); }
            finally { Reference.reachabilityFence(this); }
        }
    }
    public void close() { cleanable.clean(); }
}
