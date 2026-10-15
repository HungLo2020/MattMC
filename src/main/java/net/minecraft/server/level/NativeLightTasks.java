package net.minecraft.server.level;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;

/** Callable pins only; Rust owns live membership, batch cursors and admission.
 * Changes/retired outputs use the original consecutive-executor exclusion.
 * Atomic pending/scheduled projections can also be read by the main thread. */
final class NativeLightTasks {
    private static MethodHandle handle(String suffix, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_light_tasks_" + suffix, descriptor);
    }
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle DESTROY=handle("destroy",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle HEADER=handle("header",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CHANGE=handle("change",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle SCHEDULE=handle("schedule",FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle LIVE=handle("live",FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final VarHandle INT=ValueLayout.JAVA_INT.varHandle(),LONG=ValueLayout.JAVA_LONG.varHandle();
    private static final Object NULL_TASK=new Object();
    private final MemorySegment owner,header,metadata;
    private Object[] references=new Object[16];
    private MemorySegment retiredView=MemorySegment.NULL;
    private long retiredAddress;

    NativeLightTasks() {
        MemorySegment pointer=MemorySegment.NULL;boolean adopted=false;
        try {
            pointer=(MemorySegment)CREATE.invokeExact();
            if(pointer.address()==0)throw new IllegalStateException("No threaded-light owner");
            Arena arena=Arena.ofAuto();owner=pointer.reinterpret(1,arena,NativeLightTasks::destroy);adopted=true;
            header=((MemorySegment)HEADER.invokeExact(pointer)).reinterpret(24,arena,null).asReadOnly();
            metadata=arena.allocate(16,8);
        }catch(Throwable failure){if(!adopted&&pointer.address()!=0)destroy(pointer);throw rethrow(failure);}
    }
    private static RuntimeException rethrow(Throwable failure) {
        if(failure instanceof Error error)throw error;
        if(failure instanceof RuntimeException exception)return exception;
        return new IllegalStateException("Native threaded lighting failed",failure);
    }
    private static void destroy(MemorySegment pointer) {
        try{DESTROY.invokeExact(pointer);}catch(Throwable failure){throw rethrow(failure);}
    }
    int pending(){try{return (int)INT.getAcquire(header,0L);}finally{Reference.reachabilityFence(this);}}
    boolean scheduled(){try{return (int)INT.getAcquire(header,4L)!=0;}finally{Reference.reachabilityFence(this);}}
    long nativeAllocationBytes(){try{return (long)LONG.getAcquire(header,16L);}finally{Reference.reachabilityFence(this);}}
    static long liveAllocations(){try{return (long)LIVE.invokeExact();}catch(Throwable failure){throw rethrow(failure);}}
    private long change(int op,long a,long b) {
        try{return (long)CHANGE.invokeExact(owner,op,a,b,metadata);}
        catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(this);}
    }
    private static void check(long value) {
        if(value==-2)throw new IndexOutOfBoundsException("Index (0) is greater than or equal to list size (0)");
        if(value<0)throw new IllegalStateException("Native threaded-light operation failed: "+value);
    }
    boolean append(boolean post,Runnable task) {
        try {
            long row=(long)LONG.getAcquire(header,8L);
            if(row<=0||row>Integer.MAX_VALUE-8)throw new OutOfMemoryError("Too many threaded-light callbacks");
            int index=(int)row-1;
            if(index>=references.length)references=Arrays.copyOf(references,(int)Math.min(Integer.MAX_VALUE-8L,Math.max(row,references.length*2L)));
            if(references[index]!=null)throw new IllegalStateException("Threaded-light row was not retired");
            references[index]=task==null?NULL_TASK:task;
            long result=change(0,post?1:0,row);
            if(result<0){references[index]=null;check(result);}
            return result!=0;
        }finally{Reference.reachabilityFence(this);}
    }
    private void retire() {
        long count=metadata.getAtIndex(ValueLayout.JAVA_LONG,1);
        if(count<0||count>references.length)throw new IllegalStateException("Invalid threaded-light retirement count");
        if(count==0)return;
        long address=metadata.getAtIndex(ValueLayout.JAVA_LONG,0);
        if(address!=retiredAddress||count*8L>retiredView.byteSize()){
            retiredView=MemorySegment.ofAddress(address).reinterpret(count*8L).asReadOnly();retiredAddress=address;
        }
        for(long i=0;i<count;i++) {
            long row=retiredView.getAtIndex(ValueLayout.JAVA_LONG,i);
            if(row<=0||row>references.length||references[(int)row-1]==null)throw new IllegalStateException("Invalid threaded-light retired row");
            references[(int)row-1]=null;
        }
    }
    void runUpdate(Runnable propagation) {
        long cursor=change(1,0,0);check(cursor);
        try {
            while(true) {
                long command=change(2,cursor,0);retire();check(command);
                if(command==0)return;
                if(command==1)propagation.run();
                else {
                    long row=command-1;
                    if(row<=0||row>references.length||references[(int)row-1]==null)throw new IllegalStateException("Invalid threaded-light callback row");
                    Object reference=references[(int)row-1];
                    Runnable task=reference==NULL_TASK?null:(Runnable)reference;
                    task.run();
                }
            }
        }finally{
            // Ending this cursor does not remove pending tasks or reset admission.
            // Keep the callable table/owner pinned across all nested Java effects.
            try{check(change(3,cursor,0));}finally{Reference.reachabilityFence(this);}
        }
    }
    boolean requestSchedule(boolean observedWork){return observedWork && schedule(true,false)!=0;}
    void finishSchedule(){schedule(false,true);}
    private int schedule(boolean work,boolean finish) {
        try {
            int result=(int)SCHEDULE.invokeExact(owner,work?1:0,finish?1:0);
            if(result<0)throw new IllegalStateException("Native threaded-light admission failed");return result;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(this);}
    }
    int pinnedCount(){int count=0;for(Object reference:references)if(reference!=null)count++;return count;}
    int referenceCapacity(){return references.length;}
}
