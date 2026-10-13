package net.minecraft.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.NativeEntityGeometry;
import net.minecraft.world.phys.AABB;

/** Behavior bindings over native section members; all membership arrays are Rust-owned. */
final class NativeClassInstanceStorage<T> {
    private static MethodHandle handle(String name,FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_members_"+name,descriptor);
    }
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW=handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle VISIBILITY_VIEW=handle("visibility_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle VISIBILITY=handle("visibility",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle ATTACH_INDEX=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_index_attach_members",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle NEW_GROUP=handle("new_group",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle ALLOCATE=handle("allocate",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle FINISH=handle("finish_binding",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS,ValueLayout.JAVA_LONG));
    private static final MethodHandle RETIRED=handle("retired",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle ADD=handle("add",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle CAPTURE=handle("capture",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE_CAPTURE=handle("release_capture",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle REMOVE=handle("remove",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final MethodHandle INTERSECTIONS=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_section_members_intersections",
        FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,
            ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.ADDRESS,ValueLayout.JAVA_INT),Linker.Option.critical(true));
    private static final MemorySegment BOUNDS_REVISION=boundsRevisionView();
    private static MemorySegment boundsRevisionView() {
        try {
            var call=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_entity_geometry_bounds_revision",FunctionDescriptor.of(ValueLayout.ADDRESS));
            return ((MemorySegment)call.invokeExact()).reinterpret(8).asReadOnly();
        }catch(Throwable t){throw failure(t);}
    }
    private static final java.lang.invoke.VarHandle REVISION=ValueLayout.JAVA_LONG.varHandle();
    private static long boundsRevision(){return (long)REVISION.getAcquire(BOUNDS_REVISION,0L);}
    private static final ThreadLocal<ClassInstanceMultiMap.QueryBatch> MATCHES=ThreadLocal.withInitial(ClassInstanceMultiMap.QueryBatch::new);
    private final Arena scope;
    private final MemorySegment owner,visibility;
    private Object[] bindings=new Object[16];
    private long[] generations=new long[16];
    NativeClassInstanceStorage() {
        try {
            MemorySegment pointer=(MemorySegment)CREATE.invokeExact();
            if(pointer.address()==0)throw new IllegalStateException("Cannot create section members");
            boolean attached=false;
            try {
                scope=Arena.ofAuto();owner=pointer.reinterpret(1,scope,NativeClassInstanceStorage::release);attached=true;
                visibility=((MemorySegment)VISIBILITY_VIEW.invokeExact(owner)).reinterpret(4,scope,null).asReadOnly();
            }
            finally {if(!attached)release(pointer);}
        }catch(Throwable t){throw failure(t);}
    }
    private static void release(MemorySegment owner) {try{RELEASE.invokeExact(owner);}catch(Throwable t){throw failure(t);}}
    int sectionVisibility(){return visibility.get(ValueLayout.JAVA_INT,0);}
    int updateSectionVisibility(int value){
        try{return (int)VISIBILITY.invokeExact(owner,value);}catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    void attachSectionIndex(MemorySegment index,long token){
        try{if((int)ATTACH_INDEX.invokeExact(index,token,owner)!=0)throw new IllegalStateException("Cannot attach section membership");}
        catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    private static RuntimeException failure(Throwable t) {
        if(t instanceof RuntimeException r)return r;
        if(t instanceof Error e)throw e;
        return new IllegalStateException(t);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException propagate(Throwable t) throws E {throw (E)t;}
    private void clearRetired() {
        try {
            while(true) {
                long token=(long)RETIRED.invokeExact(owner);if(token==0)return;
                int slot=(int)token;
                if(slot<generations.length && generations[slot]==token){bindings[slot]=null;generations[slot]=0;}
            }
        }catch(Throwable t){throw failure(t);}
    }
    @SuppressWarnings("unchecked") private T object(long token){return token==0?null:(T)bindings[(int)token];}
    long bind(T object,NativeEntityGeometry.Body geometry) {
        long token=0;
        try {
            token=(long)ALLOCATE.invokeExact(owner);int slot=(int)token;
            if(slot>=bindings.length){int size=Math.max(slot+1,Math.multiplyExact(bindings.length,2));bindings=Arrays.copyOf(bindings,size);generations=Arrays.copyOf(generations,size);}
            bindings[slot]=object;generations[slot]=token;
            if(geometry!=null)geometry.attachSectionBinding(owner,token);
            return token;
        }catch(Throwable t){
            if(token!=0)finish(token);
            throw failure(t);
        }finally{Reference.reachabilityFence(this);}
    }
    void finish(long token) {
        try{FINISH.invokeExact(owner,token);}catch(Throwable t){throw failure(t);}
        finally{clearRetired();Reference.reachabilityFence(this);}
    }
    Group root(){return new Group(0);}
    Group newGroup() {
        try{int id=(int)NEW_GROUP.invokeExact(owner);if(id<0)throw new IllegalStateException("Cannot create typed section members");return new Group(id);}
        catch(Throwable t){throw failure(t);}finally{Reference.reachabilityFence(this);}
    }
    private static void check(int status,MemorySegment result) {
        if(status==0)return;
        int index=result.get(ValueLayout.JAVA_INT,0),capacity=result.get(ValueLayout.JAVA_INT,12);
        if(status==2)throw new ArrayIndexOutOfBoundsException("Index "+index+" out of bounds for length "+capacity);
        if(status==1) {
            int length=result.get(ValueLayout.JAVA_INT,8),destination=result.get(ValueLayout.JAVA_INT,4);
            if(index<0)throw new ArrayIndexOutOfBoundsException("arraycopy: source index "+index+" out of bounds for object array["+capacity+"]");
            if(destination<0)throw new ArrayIndexOutOfBoundsException("arraycopy: destination index "+destination+" out of bounds for object array["+capacity+"]");
            if(length<0)throw new ArrayIndexOutOfBoundsException("arraycopy: length "+length+" is negative");
            if((long)index+length>capacity)throw new ArrayIndexOutOfBoundsException("arraycopy: last source index "+((long)index+length)+" out of bounds for object array["+capacity+"]");
            throw new ArrayIndexOutOfBoundsException("arraycopy: last destination index "+((long)destination+length)+" out of bounds for object array["+capacity+"]");
        }
        throw new IllegalStateException("Section member status "+status);
    }
    final class Group extends AbstractList<T> {
        private final int id;
        private final MemorySegment header;
        private MemorySegment rows=MemorySegment.NULL;
        private int revision,capacity;
        Group(int id) {
            this.id=id;
            try{header=((MemorySegment)VIEW.invokeExact(owner,id)).reinterpret(24,scope,null).asReadOnly();}
            catch(Throwable t){throw failure(t);}
        }
        @Override public int size(){return header.get(ValueLayout.JAVA_INT,12);}
        @Override public Object[] toArray() {
            int count=size();
            Object[] result=new Object[count];
            bindRows();copyInto(result,Math.min(count,capacity));return result;
        }
        @Override @SuppressWarnings("unchecked") public <E> E[] toArray(E[] a) {
            int targetLength=a.length,count=size();
            boolean grow=targetLength<count;
            if(grow)a=(E[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(),count);
            bindRows();
            if(!grow && (count<0 || count>capacity)) {
                // Reproduce ArrayList's VM bounds check before any element store.
                System.arraycopy(new Object[capacity],0,a,0,count);
            }
            copyInto(a,Math.min(count,capacity));
            if(!grow && targetLength>count)a[count]=null;
            return a;
        }
        private void copyInto(Object[] target,int count) {
            for(int i=0;i<count;i++) {
                T value=object(rows.get(ValueLayout.JAVA_LONG,i*8L));
                try {target[i]=value;}
                catch(ArrayStoreException mismatch) {
                    // VM arraycopy reports the original failure and leaves the
                    // same compatible prefix in the caller's destination.
                    System.arraycopy(new Object[]{value},0,target,i,1);
                    throw mismatch;
                }
            }
            Reference.reachabilityFence(NativeClassInstanceStorage.this);
        }
        int modifications(){return header.get(ValueLayout.JAVA_INT,16);}
        private void bindRows() {
            int next=header.get(ValueLayout.JAVA_INT,20);if(revision==next)return;
            capacity=header.get(ValueLayout.JAVA_INT,8);
            rows=header.get(ValueLayout.ADDRESS,0).reinterpret(capacity*8L,scope,null).asReadOnly();revision=next;
        }
        private long token(int index) {bindRows();return rows.get(ValueLayout.JAVA_LONG,index*8L);}
        @Override public T get(int index) {Objects.checkIndex(index,size());return object(token(index));}
        void append(long token) {
            try(Arena scratch=Arena.ofConfined()) {
                MemorySegment result=scratch.allocate(16,4);
                int status=(int)ADD.invokeExact(owner,id,token,result);check(status,result);
            }catch(Throwable t){throw failure(t);}finally{clearRetired();Reference.reachabilityFence(NativeClassInstanceStorage.this);}
        }
        void copyMatching(Group source,Class<?> type) {
            int count=source.size();int expected=source.modifications();
            for(int i=0;i<count;i++){
                long token=source.token(i);if(type.isInstance(object(token)))append(token);
            }
            if(source.modifications()!=expected)throw new ConcurrentModificationException();
        }
        @Override public boolean remove(Object sought) {
            try(Captured captured=new Captured(id)) {
                for(int i=0;i<captured.size;i++){
                    T value=object(captured.token(i));
                    if(sought==null?value==null:sought.equals(value)) {
                        int status=(int)REMOVE.invokeExact(owner,id,captured.lease,i,captured.change);check(status,captured.change);return true;
                    }
                }
                return false;
            }catch(Throwable t){throw NativeClassInstanceStorage.<RuntimeException>propagate(t);}finally{Reference.reachabilityFence(NativeClassInstanceStorage.this);}
        }
        @Override public boolean contains(Object sought) {
            try(Captured captured=new Captured(id)) {
                for(int i=0;i<captured.size;i++) {
                    T value=object(captured.token(i));
                    if(sought==null?value==null:sought.equals(value))return true;
                }
                return false;
            }
        }
        AbortableIterationConsumer.Continuation forEachIntersecting(AABB query,BiPredicate<? super T,AABB> bounds,AbortableIterationConsumer<? super T> abortableIterationConsumer) {
            // Preserve empty/null and custom callback failure timing.
            if(query==null) {
                for(T value:this)if(bounds.test(value,query) && abortableIterationConsumer.accept(value).shouldAbort())return AbortableIterationConsumer.Continuation.ABORT;
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
            if(isEmpty())return AbortableIterationConsumer.Continuation.CONTINUE;
            var reusable=MATCHES.get();var matches=reusable.busy?new ClassInstanceMultiMap.QueryBatch():reusable;matches.busy=true;
            try{return forEachIntersecting(query,bounds,abortableIterationConsumer,matches,0,modifications());}
            finally{matches.busy=false;}
        }

        AbortableIterationConsumer.Continuation forEachIntersecting(AABB query,BiPredicate<? super T,AABB> bounds,AbortableIterationConsumer<? super T> abortableIterationConsumer,ClassInstanceMultiMap.QueryBatch matches,int count,int expected) {
            int cursor=0,consumed=0,scannedUntil=count==0?0:(int)matches.rows[1];
            long revision=count==0?-1:matches.rows[0];
            try {
                while(cursor!=size()) {
                    if(expected!=modifications())throw new ConcurrentModificationException();
                    if(consumed==count || revision!=boundsRevision()) {
                        count=(int)INTERSECTIONS.invokeExact(owner,id,cursor,expected,query.minX,query.minY,query.minZ,query.maxX,query.maxY,query.maxZ,matches.output,128);
                        if(count==-2)throw new ConcurrentModificationException();
                        if(count==-3)throw new NoSuchElementException();
                        if(count<0)throw new IllegalStateException("Invalid section query");
                        if(count==0)return AbortableIterationConsumer.Continuation.CONTINUE;
                        revision=matches.rows[0];scannedUntil=(int)matches.rows[1];consumed=0;
                    }
                    long next=matches.rows[2+consumed++];
                    int index=(int)(next>>>32)&Integer.MAX_VALUE;cursor=index+1;
                    T value=object(next);
                    if((next<0 || bounds.test(value,query)) && abortableIterationConsumer.accept(value).shouldAbort())return AbortableIterationConsumer.Continuation.ABORT;
                    // Only skip a prefetched rejected tail if callbacks left it valid.
                    if(consumed==count && revision==boundsRevision() && expected==modifications())cursor=scannedUntil;
                }
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }catch(Throwable t){throw NativeClassInstanceStorage.<RuntimeException>propagate(t);}
            finally{Reference.reachabilityFence(NativeClassInstanceStorage.this);}
        }

        @Override public Iterator<T> iterator() {
            return new Iterator<>() {
                int cursor;final int expected=modifications();
                @Override public boolean hasNext(){return cursor!=size();}
                @Override public T next(){
                    if(expected!=modifications())throw new ConcurrentModificationException();
                    int index=cursor;if(index>=size())throw new NoSuchElementException();
                    bindRows();if(index>=capacity)throw new ConcurrentModificationException();
                    cursor=index+1;return object(token(index));
                }
                @Override public void remove(){throw new UnsupportedOperationException();}
                @Override public void forEachRemaining(Consumer<? super T> action){
                    Objects.requireNonNull(action);int count=size(),i=cursor;if(i>=count)return;
                    try(Captured captured=new Captured(id)){
                        if(i>=captured.capacity)throw new ConcurrentModificationException();
                        while(i<count && modifications()==expected)action.accept(object(captured.token(i++)));
                        cursor=i;
                        if(modifications()!=expected)throw new ConcurrentModificationException();
                    }
                }
            };
        }
        @Override public void forEach(Consumer<? super T> action){
            Objects.requireNonNull(action);int expected=modifications();
            try(Captured captured=new Captured(id)){
                for(int i=0;i<captured.size && expected==modifications();i++)action.accept(object(captured.token(i)));
                if(expected!=modifications())throw new ConcurrentModificationException();
            }
        }
        @Override public Spliterator<T> spliterator(){return new MemberSpliterator(0,-1,0);}
        final class MemberSpliterator implements Spliterator<T> {
            int index,fence,expected;
            MemberSpliterator(int index,int fence,int expected){this.index=index;this.fence=fence;this.expected=expected;}
            int fence(){if(fence<0){expected=modifications();fence=size();}return fence;}
            @Override public Spliterator<T> trySplit(){int hi=fence(),lo=index,mid=(lo+hi)>>>1;if(lo>=mid)return null;index=mid;return new MemberSpliterator(lo,mid,expected);}
            @Override public long estimateSize(){return fence()-index;}
            @Override public int characteristics(){return ORDERED|SIZED|SUBSIZED;}
            @Override public boolean tryAdvance(Consumer<? super T> action){
                Objects.requireNonNull(action);int hi=fence(),i=index;if(i>=hi)return false;index=i+1;action.accept(object(token(i)));if(expected!=modifications())throw new ConcurrentModificationException();return true;
            }
            @Override public void forEachRemaining(Consumer<? super T> action){
                Objects.requireNonNull(action);
                try(Captured captured=new Captured(id)){
                    int hi=fence;if(hi<0){expected=modifications();hi=captured.size;}
                    int i=index;index=hi;
                    if(i<0 || hi>captured.capacity)throw new ConcurrentModificationException();
                    for(;i<hi;i++)action.accept(object(captured.token(i)));
                    if(expected!=modifications())throw new ConcurrentModificationException();
                }
            }
        }
    }
    final class Captured implements AutoCloseable {
        final Arena scratch=Arena.ofConfined();
        final MemorySegment lease,rows,change;
        final int capacity,size;
        Captured(int group){
            MemorySegment pending=MemorySegment.NULL;
            try{
                MemorySegment result=scratch.allocate(24,8);int status=(int)CAPTURE.invokeExact(owner,group,result);if(status!=0)throw new IllegalStateException("Cannot retain section member array");
                pending=result.get(ValueLayout.ADDRESS,0);
                capacity=result.get(ValueLayout.JAVA_INT,16);size=result.get(ValueLayout.JAVA_INT,20);
                rows=result.get(ValueLayout.ADDRESS,8).reinterpret(capacity*8L,scratch,null).asReadOnly();change=scratch.allocate(16,4);
                lease=pending;pending=MemorySegment.NULL;
            }catch(Throwable t){
                if(pending.address()!=0)try{RELEASE_CAPTURE.invokeExact(pending);}catch(Throwable cleanup){t.addSuppressed(cleanup);}
                scratch.close();clearRetired();throw failure(t);
            }
        }
        long token(int index){
            if(index<0 || index>=capacity)throw new ArrayIndexOutOfBoundsException("Index "+index+" out of bounds for length "+capacity);
            return rows.get(ValueLayout.JAVA_LONG,index*8L);
        }
        @Override public void close(){try{RELEASE_CAPTURE.invokeExact(lease);}catch(Throwable t){throw failure(t);}finally{scratch.close();clearRetired();Reference.reachabilityFence(NativeClassInstanceStorage.this);}}
    }
}
