package net.minecraft.world.level.entity;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;

/** Behavior bindings over Rust's primary section map and ordered spatial index. */
final class NativeEntitySectionIndex<T extends EntityAccess> {
    private static MethodHandle handle(String name,FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_index_"+name,descriptor);
    }
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW=handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle GET=handle("get",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle FIND=handle("find",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle CHUNK=handle("chunk",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle ADD_KEY=handle("add_key",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle ALLOCATE=handle("allocate",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle FINISH=handle("finish",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle RETIRED=handle("retired",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle INSERT=handle("insert",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE=handle("remove",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle CHUNKS=handle("chunks",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CHUNK_CURSOR=handle("chunk_cursor",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle SPATIAL_CURSOR=handle("spatial_cursor",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle SPATIAL_REOPEN=handle("spatial_reopen",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_VIEW=handle("cursor_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_NEXT=handle("cursor_next",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle QUERY_NEXT=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_index_query_next",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle QUERY_TYPED=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_index_query_typed",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle COLLECT=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_index_collect_non_spectators",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS),Linker.Option.critical(true));
    private static final MethodHandle CURSOR_CLOSE=handle("cursor_close",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle CURSOR_RELEASE=handle("cursor_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final Arena scope;
    private final MemorySegment owner,count,change,bounds,pointer;
    /** Closed spatial cursors, reopened instead of allocating a lease and cleanup per search. */
    private final java.util.ArrayDeque<Cursor> idleSpatial=new java.util.ArrayDeque<>();
    private Object[] bindings=new Object[16];
    private long[] generations=new long[16];
    NativeEntitySectionIndex() {
        try {
            MemorySegment raw=(MemorySegment)CREATE.invokeExact();
            boolean attached=false;
            try {
                scope=Arena.ofAuto();owner=raw.reinterpret(1,scope,NativeEntitySectionIndex::release);attached=true;
                count=((MemorySegment)VIEW.invokeExact(owner)).reinterpret(4,scope,null).asReadOnly();
                change=scope.allocate(16,8);bounds=scope.allocate(48,8);pointer=scope.allocate(ValueLayout.ADDRESS);
            } finally {if(!attached)release(raw);}
        } catch(Throwable t){throw failure(t);}
    }
    private static void release(MemorySegment p){try{RELEASE.invokeExact(p);}catch(Throwable t){throw failure(t);}}
    private static void releaseCursor(MemorySegment p){try{CURSOR_RELEASE.invokeExact(p);}catch(Throwable t){throw failure(t);}}
    private static RuntimeException failure(Throwable t){if(t instanceof RuntimeException r)return r;if(t instanceof Error e)throw e;return new IllegalStateException(t);}
    @SuppressWarnings("unchecked") private static <E extends Throwable> RuntimeException propagate(Throwable t)throws E{throw (E)t;}
    @SuppressWarnings("unchecked") private EntitySection<T> object(long token){return token==0?null:(EntitySection<T>)bindings[(int)token];}
    private void clearRetired() {
        try {
            while(true){long token=(long)RETIRED.invokeExact(owner);if(token==0)return;int slot=(int)token;
                if(slot<generations.length && generations[slot]==token){bindings[slot]=null;generations[slot]=0;}}
        } catch(Throwable t){throw failure(t);}
    }
    private void check(int status) {
        if(status==0)return;
        if(status==2)throw new ArrayIndexOutOfBoundsException("Index "+change.get(ValueLayout.JAVA_INT,8)+" out of bounds for length "+change.get(ValueLayout.JAVA_INT,12));
        throw new IllegalStateException("Section index status "+status);
    }
    int prepare(long key) {
        try {check((int)FIND.invokeExact(owner,key,change));return change.get(ValueLayout.JAVA_INT,8);}
        catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    EntitySection<T> prepared(){return object(change.get(ValueLayout.JAVA_LONG,0));}
    EntitySection<T> get(long key){try{return object((long)GET.invokeExact(owner,key));}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}}
    long chunk(long key){try{return (long)CHUNK.invokeExact(key);}catch(Throwable t){throw failure(t);}}
    void addKey(long key){try{ADD_KEY.invokeExact(owner,key);}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}}
    void insert(long key,int slot,EntitySection<T> section) {
        long pending=0;
        try {
            pending=(long)ALLOCATE.invokeExact(owner);int binding=(int)pending;
            if(binding>=bindings.length){int length=Math.max(binding+1,Math.multiplyExact(bindings.length,2));bindings=Arrays.copyOf(bindings,length);generations=Arrays.copyOf(generations,length);}
            bindings[binding]=section;generations[binding]=pending;
            section.attachNativeIndex(owner,pending);
            check((int)INSERT.invokeExact(owner,key,slot,pending,change));
        } catch(Throwable t){throw NativeEntitySectionIndex.<RuntimeException>propagate(t);}
        finally {try{if(pending!=0)FINISH.invokeExact(owner,pending);}catch(Throwable t){throw failure(t);}finally{clearRetired();Reference.reachabilityFence(this);}}
    }
    void remove(long key){try{check((int)REMOVE.invokeExact(owner,key,change));}catch(Throwable t){throw failure(t);}finally{clearRetired();Reference.reachabilityFence(this);}}
    int count(){return count.get(ValueLayout.JAVA_INT,0);}
    LongSet chunks() {
        try {
            int length=(int)CHUNKS.invokeExact(owner,pointer);if(length<0)throw new IllegalStateException("Cannot project section chunks");
            MemorySegment rows=pointer.get(ValueLayout.ADDRESS,0).reinterpret(length*8L,scope,null).asReadOnly();
            LongSet result=new LongOpenHashSet();for(int i=0;i<length;i++)result.add(rows.get(ValueLayout.JAVA_LONG,i*8L));return result;
        }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    Cursor chunkCursor(long chunkKey) {
        try {
            MemorySegment p=(MemorySegment)CHUNK_CURSOR.invokeExact(owner,chunkKey,bounds);
            if(p.address()==0)throw rangeFailure(bounds.get(ValueLayout.JAVA_LONG,0),bounds.get(ValueLayout.JAVA_LONG,8));
            return new Cursor(p);
        }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    Cursor spatialCursor(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        bounds.set(ValueLayout.JAVA_DOUBLE,0,minX);bounds.set(ValueLayout.JAVA_DOUBLE,8,minY);bounds.set(ValueLayout.JAVA_DOUBLE,16,minZ);
        bounds.set(ValueLayout.JAVA_DOUBLE,24,maxX);bounds.set(ValueLayout.JAVA_DOUBLE,32,maxY);bounds.set(ValueLayout.JAVA_DOUBLE,40,maxZ);
        try {
            Cursor idle;
            synchronized(idleSpatial){idle=idleSpatial.pollFirst();}
            if(idle!=null){
                if((int)SPATIAL_REOPEN.invokeExact(idle.handle,bounds)!=0)throw new IllegalStateException("Cannot reopen section cursor");
                idle.open=true;return idle;
            }
            Cursor cursor=new Cursor((MemorySegment)SPATIAL_CURSOR.invokeExact(owner,bounds),true);cursor.pooled=true;return cursor;
        }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    private static IllegalArgumentException rangeFailure(long from,long to){return new IllegalArgumentException("Start element ("+from+") is larger than end element ("+to+")");}
    final class Cursor implements PrimitiveIterator.OfLong,AutoCloseable {
        private final Arena cursorScope;
        private final MemorySegment handle,view;
        private final ClassInstanceMultiMap.QueryBatch matches;
        private long[] collectionRows;
        private MemorySegment collectionOutput;
        private boolean pooled,open=true;
        Cursor(MemorySegment p) { this(p,false); }
        Cursor(MemorySegment p,boolean spatial) {
            boolean attached=false;
            try {
                if(p.address()==0)throw new IllegalStateException("Cannot create section cursor");
                cursorScope=Arena.ofAuto();handle=p.reinterpret(1,cursorScope,NativeEntitySectionIndex::releaseCursor);attached=true;
                view=((MemorySegment)CURSOR_VIEW.invokeExact(handle)).reinterpret(32,cursorScope,null).asReadOnly();
                matches=spatial?new ClassInstanceMultiMap.QueryBatch():null;
            }catch(Throwable t){throw failure(t);}finally{if(!attached && p.address()!=0)releaseCursor(p);}
        }
        @Override public boolean hasNext(){return view.get(ValueLayout.JAVA_INT,0)!=0;}
        /** Zero status is an exhausted spatial search, whose public consumer sees no key. */
        boolean advance() {
            try {
                int status=(int)CURSOR_NEXT.invokeExact(handle);
                if(status==-2)throw rangeFailure(view.get(ValueLayout.JAVA_LONG,8),view.get(ValueLayout.JAVA_LONG,16));
                if(status<0)throw new IllegalStateException("Section cursor status "+status);
                return status==1;
            }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntitySectionIndex.this);}
        }
        boolean advanceQuery(boolean scanMembers) { return advanceQuery(scanMembers,0); }
        boolean advanceQuery(boolean scanMembers,long typeIdentity) {
            try {
                int status=typeIdentity==0?(int)QUERY_NEXT.invokeExact(handle,scanMembers?1:0,matches.output)
                    :(int)QUERY_TYPED.invokeExact(handle,typeIdentity,matches.output);
                if(status==-2)throw rangeFailure(view.get(ValueLayout.JAVA_LONG,8),view.get(ValueLayout.JAVA_LONG,16));
                if(status==-4)throw new java.util.ConcurrentModificationException();
                if(status==-5)throw new NoSuchElementException();
                if(status<0)throw new IllegalStateException("Section query status "+status);
                matches.begin(status);
                return status>0;
            }catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntitySectionIndex.this);}
        }
        AbortableIterationConsumer.Continuation consume(AABB query,AbortableIterationConsumer<T> consumer) {
            EntitySection<T> section=section();
            if(matches.count>0)return section.consumeNativeBatch(query,consumer,matches);
            if(accessible(section))return section.getEntities(query,consumer);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        }
        <U extends T> AbortableIterationConsumer.Continuation consumeTyped(EntityTypeTest.ClassTest<T,U> type,AABB query,AbortableIterationConsumer<U> consumer) {
            EntitySection<T> section=section();
            if(matches.count>0)return section.consumeNativeTypedBatch(type.getBaseClass(),query,consumer,matches);
            if(accessible(section))return section.getEntities(type,query,consumer);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        }
        void collectNonSpectators(AABB query,T excluded,java.util.function.Predicate<? super T> predicate,java.util.ArrayList<T> result) {
            if(collectionRows==null){collectionRows=new long[258];collectionOutput=MemorySegment.ofArray(collectionRows);}
            AbortableIterationConsumer<T> callback=null;
            try {
                while(true) {
                    int count=(int)COLLECT.invokeExact(handle,collectionOutput);
                    if(count==-2)throw rangeFailure(view.get(ValueLayout.JAVA_LONG,8),view.get(ValueLayout.JAVA_LONG,16));
                    if(count<0)throw new IllegalStateException("Entity collection status "+count);
                    boolean reserved=false;
                    for(int i=0;i<count;i++) {
                        T entity=object(collectionRows[2+2*i]).collectionBinding(collectionRows[3+2*i]);
                        if(entity!=excluded) {
                            if(!reserved) {
                                int capacity=result.size()+count-i;
                                if(capacity>=0)result.ensureCapacity(capacity);
                                reserved=true;
                            }
                            result.add(entity);
                        }
                    }
                    int kind=(int)collectionRows[0];
                    if(kind==0)return;
                    if(kind==1)continue;
                    if(kind==4) {
                        int status=(int)collectionRows[1];
                        if(status==-2)throw rangeFailure(view.get(ValueLayout.JAVA_LONG,8),view.get(ValueLayout.JAVA_LONG,16));
                        throw new IllegalStateException("Entity collection status "+status);
                    }
                    if(callback==null)callback=entity->{
                        if(entity!=excluded && predicate.test(entity))result.add(entity);
                        return AbortableIterationConsumer.Continuation.CONTINUE;
                    };
                    EntitySection<T> section=section();
                    if(kind==3) {
                        long iterator=collectionRows[1];
                        section.continueCollection(query,callback,matches,(int)iterator,(int)(iterator>>>32));
                    } else if(kind==2) {
                        if(section!=null && !section.isEmpty() && section.getStatus().isAccessible())section.getEntities(query,callback);
                    } else throw new IllegalStateException("Entity collection continuation "+kind);
                }
            }catch(Throwable t){throw NativeEntitySectionIndex.<RuntimeException>propagate(t);}
            finally{Reference.reachabilityFence(NativeEntitySectionIndex.this);}
        }
        boolean accessible(EntitySection<T> section) {
            return matches.count!=-1 || (section!=null && !section.isEmpty() && section.getStatus().isAccessible());
        }
        long key(){return view.get(ValueLayout.JAVA_LONG,8);}
        EntitySection<T> section(){return object(view.get(ValueLayout.JAVA_LONG,24));}
        @Override public long nextLong(){if(!hasNext())throw new NoSuchElementException();if(!advance())throw new NoSuchElementException();return key();}
        @Override public void remove(){throw new UnsupportedOperationException();}
        @Override public void close(){
            if(!open)return;
            try{CURSOR_CLOSE.invokeExact(handle);}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(NativeEntitySectionIndex.this);}
            open=false;
            if(pooled)synchronized(idleSpatial){if(idleSpatial.size()<8)idleSpatial.addFirst(this);}
        }
    }
}
