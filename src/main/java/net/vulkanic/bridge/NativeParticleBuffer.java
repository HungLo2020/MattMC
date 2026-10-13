package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.bridge.VulkanicGalBridge.*;

/** Bounded Rust CPU storage for extraction and immutable frame snapshots.
 * Normal handoff copies native records directly; get() projects only for API
 * compatibility and diagnostics. The producer is serialized by its caller. */
public final class NativeParticleBuffer extends AbstractList<WorldParticleQuadRecord> implements AutoCloseable {
    private static final int STRIDE = 72, LIMIT = 262_144, FRAME_LIMIT = 65_536;
    private static final Cleaner CLEANER = Cleaner.create();
    private static MethodHandle bind(String name, FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_particle_buffer_" + name, type);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle GROW = bind("grow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SNAPSHOT = bind("snapshot", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle APPEND = bind("append_layer", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE = bind("encode", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private final Arena arena = Arena.ofShared();
    private final MemorySegment owner;
    private final Cleaner.Cleanable cleanable;
    private final boolean immutable;
    private java.util.function.IntSupplier maximum;
    private MemorySegment records = MemorySegment.NULL;

    public NativeParticleBuffer() { this(create(), false); }
    public NativeParticleBuffer(java.util.function.IntSupplier maximum) { this(); this.maximum = maximum; }
    private NativeParticleBuffer(MemorySegment pointer, boolean immutable) {
        this.immutable = immutable;
        boolean attached = false;
        try {
            if (pointer.address() == 0) throw new IllegalStateException("Cannot allocate native particle storage");
            owner = pointer.reinterpret(16, arena, NativeParticleBuffer::release);
            attached = true;
            refresh();
            // The cleanup action retains the scope, never this wrapper. Explicit
            // frame completion closes snapshots; collection also handles abandoned layers.
            cleanable = CLEANER.register(this, arena::close);
        } catch (Throwable failure) {
            try { arena.close(); } finally { if (!attached && pointer.address() != 0) release(pointer); }
            throw failure(failure);
        }
    }
    private static MemorySegment create() {
        try { return (MemorySegment)CREATE.invokeExact(); } catch (Throwable t) { throw failure(t); }
    }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable t) { throw failure(t); }
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new IllegalStateException("Native particle storage call failed", t);
    }
    private void mutable() { if (immutable) throw new UnsupportedOperationException("sealed particle frame"); }
    private void refresh() {
        records = MemorySegment.ofAddress(owner.get(ValueLayout.JAVA_LONG, 0))
            .reinterpret((long)owner.get(ValueLayout.JAVA_INT, 8) * STRIDE, arena, null);
    }
    @Override public void close() { cleanable.clean(); }
    @Override public int size() {
        try { return owner.get(ValueLayout.JAVA_INT, 12); }
        finally { Reference.reachabilityFence(this); }
    }
    @Override public void clear() { truncate(0); }
    public void truncate(int count) {
        try {
            mutable();
            if (count < 0 || count > size()) throw new IndexOutOfBoundsException(count);
            owner.set(ValueLayout.JAVA_INT, 12, count); modCount++;
        } finally { Reference.reachabilityFence(this); }
    }
    @Override protected void removeRange(int from, int to) {
        if (to != size()) throw new UnsupportedOperationException("particle rollback requires a suffix");
        truncate(from);
    }
    @Override public WorldParticleQuadRecord remove(int index) {
        if (index != size()-1) throw new UnsupportedOperationException("particle rollback requires the last entry");
        var value = get(index); truncate(index); return value;
    }
    public int textureId(int index) { return integer(index, 4); }
    public int surface(int index) { return integer(index, 8); }
    public int materialIndex(int index) { return integer(index, 12); }
    private int integer(int index, int offset) {
        try { Objects.checkIndex(index, size()); return records.get(ValueLayout.JAVA_INT, (long)index*STRIDE+offset); }
        finally { Reference.reachabilityFence(this); }
    }
    public NativeParticleBuffer snapshot() {
        try { return new NativeParticleBuffer((MemorySegment)SNAPSHOT.invokeExact(owner), true); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this); }
    }
    public static List<WorldParticleQuadRecord> immutableCopy(List<WorldParticleQuadRecord> values) {
        return values instanceof NativeParticleBuffer b && b.immutable ? b : List.copyOf(values);
    }
    public static void closeSnapshot(List<WorldParticleQuadRecord> values) {
        if (values instanceof NativeParticleBuffer b && b.immutable) b.close();
    }
    @Override public boolean add(WorldParticleQuadRecord p) {
        var c = p.center(); var q = p.rotation(); var uv = p.uvBounds();
        append(p.textureId(), p.surface().wireValue(), p.materialIndex(), c[0], c[1], c[2],
            q[0], q[1], q[2], q[3], p.size(), uv[0], uv[1], uv[2], uv[3], p.colorArgb(), p.packedLight());
        return true;
    }
    /** Inputs are already validated by the semantic producer, before publication. */
    public void append(int texture, int surface, int order, float x, float y, float z,
        float qx, float qy, float qz, float qw, float scale, float u0, float u1, float v0, float v1, int color, int light) {
        try {
            mutable(); int count = size();
            if (count >= LIMIT || maximum != null && count >= maximum.getAsInt()) throw new IllegalStateException("particle frame capacity exceeded");
            if (count == owner.get(ValueLayout.JAVA_INT, 8)) {
                int ok = (int)GROW.invokeExact(owner, count+1); refresh();
                if (ok == 0) throw new IllegalStateException("particle buffer growth failed");
            }
            var p = records.asSlice((long)count*STRIDE, STRIDE);
            p.set(ValueLayout.JAVA_INT,0,STRIDE); p.set(ValueLayout.JAVA_INT,4,texture);
            p.set(ValueLayout.JAVA_INT,8,surface); p.set(ValueLayout.JAVA_INT,12,order);
            p.set(ValueLayout.JAVA_FLOAT,16,x); p.set(ValueLayout.JAVA_FLOAT,20,y); p.set(ValueLayout.JAVA_FLOAT,24,z);
            p.set(ValueLayout.JAVA_FLOAT,28,qx); p.set(ValueLayout.JAVA_FLOAT,32,qy); p.set(ValueLayout.JAVA_FLOAT,36,qz); p.set(ValueLayout.JAVA_FLOAT,40,qw);
            p.set(ValueLayout.JAVA_FLOAT,44,scale); p.set(ValueLayout.JAVA_FLOAT,48,u0); p.set(ValueLayout.JAVA_FLOAT,52,u1);
            p.set(ValueLayout.JAVA_FLOAT,56,v0); p.set(ValueLayout.JAVA_FLOAT,60,v1);
            p.set(ValueLayout.JAVA_INT,64,color); p.set(ValueLayout.JAVA_INT,68,light);
            owner.set(ValueLayout.JAVA_INT,12,count+1); modCount++;
        } catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this); }
    }
    public int appendLayer(NativeParticleBuffer input, int texture, int surface, int order) {
        try {
            mutable();
            if (maximum != null && (long)size()+input.size()>maximum.getAsInt()) throw new IllegalStateException("particle frame capacity exceeded");
            int result = (int)APPEND.invokeExact(owner, input.owner, texture, surface, order); refresh();
            if (result == -2) throw new IllegalArgumentException("invalid particle quad semantics");
            if (result < 0) throw new IllegalStateException("particle layer capacity or ownership failure");
            modCount++; return result;
        } catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(input); Reference.reachabilityFence(this); }
    }
    @FunctionalInterface public interface Consumer {
        void accept(float x,float y,float z,float qx,float qy,float qz,float qw,float scale,
            float u0,float u1,float v0,float v1,int color,int light);
    }
    public void visit(Consumer consumer) {
        try {
            for(int i=0;i<size();i++) {
                var p=records.asSlice((long)i*STRIDE,STRIDE);
                consumer.accept(p.get(ValueLayout.JAVA_FLOAT,16),p.get(ValueLayout.JAVA_FLOAT,20),p.get(ValueLayout.JAVA_FLOAT,24),
                    p.get(ValueLayout.JAVA_FLOAT,28),p.get(ValueLayout.JAVA_FLOAT,32),p.get(ValueLayout.JAVA_FLOAT,36),p.get(ValueLayout.JAVA_FLOAT,40),
                    p.get(ValueLayout.JAVA_FLOAT,44),p.get(ValueLayout.JAVA_FLOAT,48),p.get(ValueLayout.JAVA_FLOAT,52),p.get(ValueLayout.JAVA_FLOAT,56),p.get(ValueLayout.JAVA_FLOAT,60),
                    p.get(ValueLayout.JAVA_INT,64),p.get(ValueLayout.JAVA_INT,68));
            }
        } finally {Reference.reachabilityFence(this);}
    }
    @Override public WorldParticleQuadRecord get(int index) {
        try {
            Objects.checkIndex(index,size()); var p=records.asSlice((long)index*STRIDE,STRIDE);
            float[] c=new float[3],q=new float[4],uv=new float[4];
            for(int i=0;i<3;i++)c[i]=p.get(ValueLayout.JAVA_FLOAT,16+i*4);
            for(int i=0;i<4;i++){q[i]=p.get(ValueLayout.JAVA_FLOAT,28+i*4);uv[i]=p.get(ValueLayout.JAVA_FLOAT,48+i*4);}
            return new WorldParticleQuadRecord(p.get(ValueLayout.JAVA_INT,4), ParticleSurface.values()[p.get(ValueLayout.JAVA_INT,8)],
                p.get(ValueLayout.JAVA_INT,12),c,q,p.get(ValueLayout.JAVA_FLOAT,44),uv,p.get(ValueLayout.JAVA_INT,64),p.get(ValueLayout.JAVA_INT,68));
        } finally { Reference.reachabilityFence(this); }
    }
    /** Mapping and record construction remain native; only the material prefix crosses. */
    public MemorySegment encode(Arena output, List<WorldMaterialQuadRecord> materials) {
        try {
            int n=size();
            if ((long)n+materials.size()>FRAME_LIMIT) throw new IllegalArgumentException("combined particle/material frame bound exceeded");
            var prefix=output.allocate((materials.size()+1L)*4,4); prefix.set(ValueLayout.JAVA_INT,0,0);
            int compact=0;
            for(int i=0;i<materials.size();i++) {if(!materials.get(i).hasVertexModulation())compact++; prefix.setAtIndex(ValueLayout.JAVA_INT,i+1,compact);}
            var bytes=output.allocate((long)n*STRIDE,4);
            if((int)ENCODE.invokeExact(owner,prefix,materials.size()+1,bytes)==0)throw new IllegalArgumentException("invalid particle collection order");
            return bytes;
        } catch(Throwable t){throw failure(t);}
        finally {Reference.reachabilityFence(this);}
    }
}
