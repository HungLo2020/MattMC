package net.minecraft.world.level.entity;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.NativeLibraryLoader;

/** Java behavior bindings over Rust-owned ordered membership and tick snapshots. */
final class NativeEntityOrder {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_order_"+name, descriptor);
    }
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle PREPARE=handle("prepare",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle RECEIVER=handle("receiver",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle ADD=handle("add",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE=handle("remove",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle CONTAINS=handle("contains",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle BEGIN=handle("begin",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle END=handle("end",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle ACK=handle("acknowledge",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle STRIDE=handle("node_stride",FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle VIEW_SIZE=handle("view_size",FunctionDescriptor.of(ValueLayout.JAVA_LONG));

    private final Arena scope;
    private final MemorySegment owner,change,iteration,retired;
    private Entity[] bindings=new Entity[16];
    private MemorySegment[] pages=new MemorySegment[0];
    private long cachedTable;

    NativeEntityOrder() {
        try {
            if ((long)STRIDE.invokeExact()!=24L || (long)VIEW_SIZE.invokeExact()!=32L)
                throw new IllegalStateException("Entity order layout mismatch");
            MemorySegment pointer=(MemorySegment)CREATE.invokeExact();
            if (pointer.address()==0) throw new IllegalStateException("Cannot create entity order");
            boolean attached=false;
            try {
                scope=Arena.ofAuto();
                owner=pointer.reinterpret(1,scope,NativeEntityOrder::release);
                attached=true;
                change=scope.allocate(24,8);
                iteration=scope.allocate(32,8);
                retired=scope.allocate(16,8);
            } finally { if (!attached) release(pointer); }
        } catch (Throwable t) { throw failure(t); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable t) { throw failure(t); }
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new IllegalStateException(t);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException propagate(Throwable t) throws E {
        throw (E)t;
    }
    private static void check(int status) {
        if (status==-2) throw new UnsupportedOperationException("Only one concurrent iteration supported");
        if (status!=0) throw new IllegalStateException("Entity order status "+status);
    }
    private int receiver(boolean mutation) {
        try {
            int selected=mutation ? (int)PREPARE.invokeExact(owner) : (int)RECEIVER.invokeExact(owner);
            if(selected<0) check(selected);
            return selected;
        } catch (Throwable t) { throw failure(t); }
    }
    private void clearImmediate() {
        long old=change.get(ValueLayout.JAVA_LONG,8);
        if (old!=0 && change.get(ValueLayout.JAVA_LONG,16)==0) bindings[(int)old]=null;
    }
    void add(Entity entity) {
        try {
            int selected=receiver(true);
            int key=entity.getId();
            check((int)ADD.invokeExact(owner,selected,key,change));
            clearImmediate();
            long token=change.get(ValueLayout.JAVA_LONG,0);
            if(token==0) return; // A delayed put targeted the inactive physical map.
            int slot=(int)token;
            if (slot<0) throw new IllegalStateException("Entity binding exceeds Java array extent");
            if (slot>=bindings.length) bindings=Arrays.copyOf(bindings,Math.max(slot+1,Math.multiplyExact(bindings.length,2)));
            bindings[slot]=entity;
        } catch (Throwable t) { throw NativeEntityOrder.<RuntimeException>propagate(t); }
        finally { Reference.reachabilityFence(this); }
    }
    void remove(Entity entity) {
        try {
            int selected=receiver(true);
            int key=entity.getId();
            check((int)REMOVE.invokeExact(owner,selected,key,change));
            clearImmediate();
        } catch (Throwable t) { throw NativeEntityOrder.<RuntimeException>propagate(t); }
        finally { Reference.reachabilityFence(this); }
    }
    boolean contains(Entity entity) {
        try {
            int selected=receiver(false);
            int key=entity.getId();
            int answer=(int)CONTAINS.invokeExact(owner,selected,key);
            if (answer<0) check(answer);
            return answer==1;
        } catch (Throwable t) { throw NativeEntityOrder.<RuntimeException>propagate(t); }
        finally { Reference.reachabilityFence(this); }
    }
    private void bindPages() {
        long identity=iteration.get(ValueLayout.JAVA_LONG,0);
        int count=Math.toIntExact(iteration.get(ValueLayout.JAVA_LONG,16));
        if (identity==cachedTable && count==pages.length) return;
        MemorySegment rows=iteration.get(ValueLayout.ADDRESS,8).reinterpret(count*8L,scope,null).asReadOnly();
        MemorySegment[] mapped=new MemorySegment[count];
        for (int i=0;i<count;i++) mapped[i]=rows.getAtIndex(ValueLayout.ADDRESS,i).reinterpret(64L*24L,scope,null).asReadOnly();
        pages=mapped;
        cachedTable=identity;
    }
    private void finishIteration() {
        try {
            check((int)END.invokeExact(owner,retired));
            long count=retired.get(ValueLayout.JAVA_LONG,8);
            if (count!=0) {
                MemorySegment tokens=retired.get(ValueLayout.ADDRESS,0).reinterpret(Math.multiplyExact(count,8),scope,null).asReadOnly();
                for (long i=0;i<count;i++) bindings[(int)tokens.getAtIndex(ValueLayout.JAVA_LONG,i)]=null;
            }
            check((int)ACK.invokeExact(owner));
        } catch (Throwable t) { throw failure(t); }
    }
    void forEach(Consumer<Entity> consumer) {
        boolean begun=false;
        Throwable original=null;
        try {
            check((int)BEGIN.invokeExact(owner,iteration));
            begun=true;
            bindPages();
            int index=iteration.get(ValueLayout.JAVA_INT,24);
            while (index!=-1) {
                MemorySegment page=pages[index>>>6];
                long offset=(index&63)*24L;
                long token=page.get(ValueLayout.JAVA_LONG,offset);
                int next=page.get(ValueLayout.JAVA_INT,offset+16);
                consumer.accept(bindings[(int)token]);
                index=next;
            }
        } catch (Throwable t) { original=t; throw NativeEntityOrder.<RuntimeException>propagate(t); }
        finally {
            try {
                if (begun) {
                    try { finishIteration(); }
                    catch (RuntimeException|Error cleanup) {
                        if (original!=null) original.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                }
            } finally { Reference.reachabilityFence(this); }
        }
    }
}
