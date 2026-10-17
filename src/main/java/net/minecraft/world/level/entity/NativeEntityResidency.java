package net.minecraft.world.level.entity;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongPredicate;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.util.NativeLibraryLoader;

/** Bounded Java views/callback dispatch over primary Rust entity residency. */
final class NativeEntityResidency {
    private static MethodHandle handle(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_entity_residency_"+name,type);}
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW=handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle UUID_OP=handle("uuid",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle MAP=handle("map",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    private static final MethodHandle SET=handle("set",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle POLICY=handle("policy",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle STORE=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_entity_residency_store_action",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT),Linker.Option.critical(true));
    private static final MethodHandle LOADED=handle("loaded_keys",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_CREATE=handle("cursor_create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CURSOR_VIEW=handle("cursor_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_STEP=handle("cursor_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle CURSOR_CLOSE=handle("cursor_close",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_RELEASE=handle("cursor_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final Visibility[] VISIBILITIES=Visibility.values();
    private final Arena scope;
    private final MemorySegment owner,counts,pointer;
    private final Long2ObjectMap<Visibility> visibility=new EnumView<>(0,Visibility.values());
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> loads=new EnumView<>(1,PersistentEntitySectionManager.ChunkLoadStatus.values());
    private final LongSet ticking=new SetView(0),unloads=new SetView(1);
    final KnownUuids knownUuids=new KnownUuids();
    NativeEntityResidency(){
        try{
            MemorySegment p=(MemorySegment)CREATE.invokeExact();boolean attached=false;
            try{scope=Arena.ofAuto();owner=p.reinterpret(1,scope,NativeEntityResidency::release);attached=true;counts=((MemorySegment)VIEW.invokeExact(owner)).reinterpret(20,scope,null).asReadOnly();pointer=scope.allocate(ValueLayout.ADDRESS);}
            finally{if(!attached)release(p);}
        }catch(Throwable t){throw failure(t);}
    }
    private static RuntimeException failure(Throwable t){if(t instanceof RuntimeException r)return r;if(t instanceof Error e)throw e;return new IllegalStateException(t);}
    private static void release(MemorySegment p){try{RELEASE.invokeExact(p);}catch(Throwable t){throw failure(t);}}
    private static void releaseCursor(MemorySegment p){try{CURSOR_RELEASE.invokeExact(p);}catch(Throwable t){throw failure(t);}}
    Long2ObjectMap<Visibility> visibility(){return visibility;}
    Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> loads(){return loads;}
    LongSet ticking(){return ticking;}
    LongSet unloads(){return unloads;}
    private int policy(int action,long key){try{return (int)POLICY.invokeExact(owner,action,key);}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}}
    Visibility clientVisibility(long key){return VISIBILITIES[policy(0,key)];}
    boolean needsLoad(long key){return policy(1,key)!=0;}
    boolean shouldUnload(long key){return policy(2,key)!=0;}
    static int storeAction(PersistentEntitySectionManager.ChunkLoadStatus captured,boolean empty){
        int status=captured==null?-1:captured.ordinal(),flag=empty?1:0;
        try{return (int)STORE.invokeExact(status,flag);}catch(Throwable t){throw failure(t);}
    }
    void addLoadedKeys(LongSet destination){
        try{
            int count=(int)LOADED.invokeExact(owner,pointer);if(count<0)throw new IllegalStateException("Cannot project loaded entity chunks");
            MemorySegment rows=pointer.get(ValueLayout.ADDRESS,0).reinterpret(count*8L,scope,null).asReadOnly();
            for(int i=0;i<count;i++)destination.add(rows.get(ValueLayout.JAVA_LONG,i*8L));
        }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    final class KnownUuids {
        private boolean operation(UUID id,int action){
            int present=id==null?0:1;long high=id==null?0:id.getMostSignificantBits(),low=id==null?0:id.getLeastSignificantBits();
            try{int result=(int)UUID_OP.invokeExact(owner,action,present,high,low);if(result<0)throw new IllegalStateException("Residency UUID status "+result);return result!=0;}
            catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntityResidency.this);}
        }
        boolean add(UUID id){return operation(id,1);}
        boolean remove(UUID id){return operation(id,2);}
        boolean contains(UUID id){return operation(id,0);}
        int size(){return counts.get(ValueLayout.JAVA_INT,0);}
    }
    /** Internal enum views preserve the original interface call-site failure semantics. */
    private final class EnumView<V extends Enum<V>> extends AbstractLong2ObjectMap<V> {
        private final int id;private final V[] values;
        EnumView(int id,V[] values){this.id=id;this.values=values;}
        private int operation(int action,long key,int value){
            try{int result=(int)MAP.invokeExact(owner,id,action,key,value);if(result < -1)throw new IllegalStateException("Residency map status "+result);return result;}
            catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntityResidency.this);}
        }
        private V decode(int value){return value==-1?null:values[value];}
        private int encode(V value){return value==null?-1:value.ordinal();}
        @Override public V get(long key){return decode(operation(0,key,0));}
        @Override public V put(long key,V value){return decode(operation(1,key,encode(value)));}
        @Override public V remove(long key){return decode(operation(2,key,0));}
        @Override public boolean containsKey(long key){return operation(3,key,0)!=0;}
        @Override public int size(){return counts.get(ValueLayout.JAVA_INT,(id+1)*4L);}
        @Override public V defaultReturnValue(){return decode(operation(4,0,0));}
        @Override public void defaultReturnValue(V value){operation(5,0,encode(value));}
        @Override public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet(){throw new UnsupportedOperationException("internal residency view does not export entries");}
    }
    private final class SetView extends AbstractLongSet {
        private final int id;SetView(int id){this.id=id;}
        private boolean operation(int action,long key){try{int result=(int)SET.invokeExact(owner,id,action,key);if(result<0)throw new IllegalStateException("Residency set status "+result);return result!=0;}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntityResidency.this);}}
        @Override public boolean add(long key){return operation(1,key);}
        @Override public boolean remove(long key){return operation(2,key);}
        @Override public boolean contains(long key){return operation(0,key);}
        @Override public int size(){return counts.get(ValueLayout.JAVA_INT,(id+3)*4L);}
        @Override public void clear(){operation(3,0);}
        @Override public LongIterator iterator(){return new Cursor(id);}
        @Override public boolean removeIf(LongPredicate predicate){
            Objects.requireNonNull(predicate);boolean changed=false;
            try(Cursor cursor=new Cursor(id)){while(cursor.hasNext()){long key=cursor.nextLong();if(predicate.test(key)){cursor.remove();changed=true;}}}
            return changed;
        }
    }
    private final class Cursor implements LongIterator,AutoCloseable {
        private final Arena cursorScope;private final MemorySegment handle,view;
        Cursor(int set){
            MemorySegment p=MemorySegment.NULL;boolean attached=false;
            try{p=(MemorySegment)CURSOR_CREATE.invokeExact(owner,set);if(p.address()==0)throw new IllegalStateException("Cannot create residency cursor");cursorScope=Arena.ofAuto();handle=p.reinterpret(1,cursorScope,NativeEntityResidency::releaseCursor);attached=true;view=((MemorySegment)CURSOR_VIEW.invokeExact(handle)).reinterpret(24,cursorScope,null).asReadOnly();}
            catch(Throwable t){throw failure(t);}finally{if(!attached && p.address()!=0)releaseCursor(p);Reference.reachabilityFence(NativeEntityResidency.this);}
        }
        @Override public boolean hasNext(){return view.get(ValueLayout.JAVA_INT,0)!=0;}
        private void step(int remove){
            try{
                int status=(int)CURSOR_STEP.invokeExact(handle,remove);
                switch(status){
                    case 0->{}
                    case 1->throw new NoSuchElementException();
                    case 2->throw new IllegalStateException();
                    case 3->throw new ArrayIndexOutOfBoundsException("Index "+view.get(ValueLayout.JAVA_INT,16)+" out of bounds for length "+view.get(ValueLayout.JAVA_INT,20));
                    case 4->throw new NullPointerException("Cannot invoke \"it.unimi.dsi.fastutil.longs.LongArrayList.getLong(int)\" because \"this.wrapped\" is null");
                    case 5->throw new IndexOutOfBoundsException("Index ("+view.get(ValueLayout.JAVA_INT,16)+") is greater than or equal to list size ("+view.get(ValueLayout.JAVA_INT,20)+")");
                    default->throw new IllegalStateException("Residency cursor status "+status);
                }
            }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntityResidency.this);}
        }
        @Override public long nextLong(){step(0);return view.get(ValueLayout.JAVA_LONG,8);}
        @Override public void remove(){step(1);}
        @Override public void close(){try{CURSOR_CLOSE.invokeExact(handle);}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntityResidency.this);}}
    }
}
