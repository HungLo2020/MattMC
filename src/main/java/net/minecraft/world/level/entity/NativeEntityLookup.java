package net.minecraft.world.level.entity;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;
import net.minecraft.util.NativeLibraryLoader;

/** Java behavior bindings over Rust's primary visible identity registry. */
final class NativeEntityLookup<T extends EntityAccess> {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor, boolean critical) {
        return critical
            ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_lookup_"+name, descriptor, Linker.Option.critical(true))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_lookup_"+name, descriptor);
    }
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS),false);
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),false);
    private static final MethodHandle VIEW=handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS),true);
    private static final FunctionDescriptor UUID_QUERY=FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG);
    private static final MethodHandle BEGIN_ADD=handle("begin_add",UUID_QUERY,false);
    private static final MethodHandle GET_UUID=handle("get_uuid",UUID_QUERY,true);
    private static final MethodHandle REMOVE_UUID=handle("remove_uuid",UUID_QUERY,false);
    private static final MethodHandle FINISH_ADD=handle("finish_add",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS),false);
    private static final FunctionDescriptor ID_QUERY=FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT);
    private static final MethodHandle GET_ID=handle("get_id",ID_QUERY,true);
    private static final MethodHandle REMOVE_ID=handle("remove_id",ID_QUERY,false);

    private final Arena scope;
    private final MemorySegment owner,header,freed;
    private EntityAccess[] bindings=new EntityAccess[16];
    private MemorySegment rows=MemorySegment.NULL;
    private long arrayRevision;
    private int arrayLength;

    NativeEntityLookup() {
        try {
            MemorySegment pointer=(MemorySegment)CREATE.invokeExact();
            if(pointer.address()==0)throw new IllegalStateException("Cannot create entity lookup");
            boolean attached=false;
            try {
                scope=Arena.ofAuto();
                owner=pointer.reinterpret(1,scope,NativeEntityLookup::release);
                attached=true;
                header=((MemorySegment)VIEW.invokeExact(owner)).reinterpret(32,scope,null).asReadOnly();
                freed=scope.allocate(8,8);
            } finally {if(!attached)release(pointer);}
        } catch(Throwable t){throw failure(t);}
    }
    private static void release(MemorySegment pointer) {
        try {RELEASE.invokeExact(pointer);}catch(Throwable t){throw failure(t);}
    }
    private static RuntimeException failure(Throwable t) {
        if(t instanceof RuntimeException r)return r;
        if(t instanceof Error e)throw e;
        return new IllegalStateException(t);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException propagate(Throwable t) throws E {throw (E)t;}
    @SuppressWarnings("unchecked")
    private T object(long token) {return token==0 ? null : (T)bindings[(int)token];}
    private void clear(long token) {if(token!=0)bindings[(int)token]=null;}
    private long uuid(MethodHandle method,UUID uuid) throws Throwable {
        int present=uuid==null ? 0 : 1;
        long high=uuid==null ? 0 : uuid.getMostSignificantBits();
        long low=uuid==null ? 0 : uuid.getLeastSignificantBits();
        return (long)method.invokeExact(owner,present,high,low);
    }
    /** False means duplicate UUID. UUID ownership commits before virtual getId. */
    boolean add(T entity,UUID uuid) {
        long pending=0;
        try {
            pending=uuid(BEGIN_ADD,uuid);
            if(pending==0)return false;
            int slot=(int)pending;
            if(slot>=bindings.length)bindings=Arrays.copyOf(bindings,Math.max(slot+1,Math.multiplyExact(bindings.length,2)));
            bindings[slot]=entity;
            int id=entity.getId();
            int status=(int)FINISH_ADD.invokeExact(owner,pending,1,id,freed);
            if(status!=0)throw new IllegalStateException("Entity lookup add status "+status);
            pending=0;
            clear(freed.get(ValueLayout.JAVA_LONG,0));
            return true;
        } catch(Throwable t) {throw NativeEntityLookup.<RuntimeException>propagate(t);}
        finally {
            try {
                if(pending!=0) {
                    int status=(int)FINISH_ADD.invokeExact(owner,pending,0,0,freed);
                    if(status!=0)throw new IllegalStateException("Entity lookup abort status "+status);
                    clear(freed.get(ValueLayout.JAVA_LONG,0));
                }
            } catch(Throwable t) {throw failure(t);}
            finally {Reference.reachabilityFence(this);}
        }
    }
    void remove(T entity) {
        try {
            clear(uuid(REMOVE_UUID,entity.getUUID()));
            int id=entity.getId();
            clear((long)REMOVE_ID.invokeExact(owner,id));
        }catch(Throwable t){throw NativeEntityLookup.<RuntimeException>propagate(t);}
        finally{Reference.reachabilityFence(this);}
    }
    T get(int id) {
        try{return object((long)GET_ID.invokeExact(owner,id));}
        catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    T get(UUID uuid) {
        try{return object(uuid(GET_UUID,uuid));}
        catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    int count(){return header.get(ValueLayout.JAVA_INT,28);}
    private void bindRows() {
        long revision=header.get(ValueLayout.JAVA_LONG,16);
        if(revision==arrayRevision)return;
        arrayLength=Math.toIntExact(header.get(ValueLayout.JAVA_LONG,8));
        rows=header.get(ValueLayout.ADDRESS,0).reinterpret(arrayLength*24L,scope,null).asReadOnly();
        arrayRevision=revision;
    }
    Iterable<T> values() {
        return () -> new Iterator<T>() {
            private int next=header.get(ValueLayout.JAVA_INT,24);
            @Override public boolean hasNext(){return next!=-1;}
            @Override public void remove(){throw new UnsupportedOperationException();}
            @Override public T next() {
                if(next==-1)throw new NoSuchElementException();
                bindRows();
                int index=next;
                if(index<0 || index>=arrayLength)throw new ArrayIndexOutOfBoundsException("Index "+index+" out of bounds for length "+arrayLength);
                long offset=index*24L;
                next=rows.get(ValueLayout.JAVA_INT,offset+16);
                T value=object(rows.get(ValueLayout.JAVA_LONG,offset+8));
                Reference.reachabilityFence(NativeEntityLookup.this);
                return value;
            }
        };
    }
}
