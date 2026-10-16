package net.minecraft.server.level;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;

/** Actual VM capture roots; Rust owns commands, live membership, cursors and admission.
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
    private static final MethodHandle APPEND_CHECK=handle("append_check",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle APPEND_RETAIN=handle("append_retain",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle APPEND_IMPORT=handle("append_import",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    private static final MethodHandle APPEND_STATUS=handle("append_status",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final MethodHandle APPEND_LEVEL=handle("append_level",FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG));
    private static final VarHandle INT=ValueLayout.JAVA_INT.varHandle(),LONG=ValueLayout.JAVA_LONG.varHandle();
    private static final Object NULL_TASK=new Object();
    private static final java.lang.invoke.MethodHandle BEGIN_LEVEL=net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_light_tasks_begin_level",
        java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG,java.lang.foreign.ValueLayout.ADDRESS,java.lang.foreign.ValueLayout.ADDRESS));
    private final MemorySegment owner,header,metadata;
    private Object[] references=new Object[16];
    // Actual original capture roots for native commands. Native rows, not this
    // array, own command type/membership/order; callable rows need no context.
    private Object[] contexts;
    private Object[] payloads;
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
        if(value==-20)throw new OutOfMemoryError("Native lighting table allocation failed");
        if(value<=-20)throw new IllegalStateException("Native light coordinator rejected operation: "+value);
        if(value==-2)throw new IndexOutOfBoundsException("Index (0) is greater than or equal to list size (0)");
        if(value<0)throw new IllegalStateException("Native threaded-light operation failed: "+value);
    }
    boolean appendCheck(MemorySegment level,long pos,Object engine,Object position) {
        try {
            long row=(long)LONG.getAcquire(header,8L);
            if(row<=0||row>Integer.MAX_VALUE-8)throw new OutOfMemoryError("Too many threaded-light callbacks");
            int index=(int)row-1;
            if(index>=references.length)references=Arrays.copyOf(references,(int)Math.min(Integer.MAX_VALUE-8L,Math.max(row,references.length*2L)));
            if(contexts==null)contexts=new Object[references.length];
            else if(contexts.length<references.length)contexts=Arrays.copyOf(contexts,references.length);
            if(references[index]!=null||contexts[index]!=null)throw new IllegalStateException("Threaded-light row was not retired");
            references[index]=position;contexts[index]=engine;
            long result=(long)APPEND_CHECK.invokeExact(owner,level,pos,row);
            if(result<0){references[index]=null;contexts[index]=null;check(result);}
            return result!=0;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(level);Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(this);}
    }
    private long pin(Object engine,Object position,Object payload){
        long row=(long)LONG.getAcquire(header,8L);
        if(row<=0||row>Integer.MAX_VALUE-8)throw new OutOfMemoryError("Too many threaded-light callbacks");
        int index=(int)row-1;
        if(index>=references.length)references=Arrays.copyOf(references,(int)Math.min(Integer.MAX_VALUE-8L,Math.max(row,references.length*2L)));
        if(contexts==null)contexts=new Object[references.length];
        else if(contexts.length<references.length)contexts=Arrays.copyOf(contexts,references.length);
        if(payloads==null&&payload!=null)payloads=new Object[references.length];
        else if(payloads!=null&&payloads.length<references.length)payloads=Arrays.copyOf(payloads,references.length);
        if(references[index]!=null||contexts[index]!=null||(payloads!=null&&payloads[index]!=null))throw new IllegalStateException("Threaded-light row was not retired");
        references[index]=position;contexts[index]=engine;
        if(payloads!=null)payloads[index]=payload;
        return row;
    }
    private void unpin(long row){
        int index=(int)row-1;references[index]=null;
        if(contexts!=null&&index<contexts.length)contexts[index]=null;
        if(payloads!=null&&index<payloads.length)payloads[index]=null;
    }
    boolean appendRetain(MemorySegment level,long key,boolean value,Object engine,Object position){
        try{
            long row=pin(engine,position,null);
            long result=(long)APPEND_RETAIN.invokeExact(owner,level,key,value?1:0,row);
            if(result<0){unpin(row);check(result);}return result!=0;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(this);}
    }
    boolean appendImport(MemorySegment level,int channel,long key,Object engine,Object position,Object payload){
        try{
            long row=pin(engine,position,payload);
            long result=(long)APPEND_IMPORT.invokeExact(owner,level,channel,key,row);
            if(result<0){unpin(row);check(result);}return result!=0;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(payload);Reference.reachabilityFence(this);}
    }
    boolean appendStatus(MemorySegment level,long key,boolean empty,Object engine,Object position){
        try{
            long row=pin(engine,position,null);
            long result=(long)APPEND_STATUS.invokeExact(owner,level,key,empty?1:0,row);
            if(result<0){unpin(row);check(result);}return result!=0;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(this);}
    }
    boolean appendLevel(MemorySegment level,int kind,long key,boolean value,Object engine,Object position){
        try{
            long row=pin(engine,position,null);
            long result=(long)APPEND_LEVEL.invokeExact(owner,level,kind,key,value?1:0,row);
            if(result<0){unpin(row);check(result);}return result!=0;
        }catch(Throwable failure){throw rethrow(failure);}
        finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(this);}
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
            unpin(row);
        }
    }
    void runUpdate(Runnable propagation) { runUpdate(propagation,MemorySegment.NULL,null); }
    void runUpdate(Runnable propagation,MemorySegment level,ThreadedLevelLightEngine context) {
        long cursor;
        try{cursor=level.address()==0?change(1,0,0):(long)BEGIN_LEVEL.invokeExact(owner,level);}
        catch(Throwable failure){throw rethrow(failure);}
        check(cursor);
        try {
            while(true) {
                long command=change(2,cursor,0);retire();check(command);
                if(command==0)return;
                if(command==1)propagation.run();
                else if((command&(1L<<62))!=0){
                    long row=(command&((1L<<60)-1))-1;
                    ThreadedLevelLightEngine engine;
                    Object position=null,payload=null;
                    if(row==0&&(command&(1L<<60))!=0){
                        if(context==null)throw new IllegalStateException("Missing native propagation context");
                        engine=context;
                    }else{
                        if(row<=0||row>references.length||contexts==null||row>contexts.length
                                ||!(contexts[(int)row-1] instanceof ThreadedLevelLightEngine))throw new IllegalStateException("Missing native light VM context");
                        engine=(ThreadedLevelLightEngine)contexts[(int)row-1];
                        position=references[(int)row-1];payload=payloads!=null&&row<=payloads.length?payloads[(int)row-1]:null;
                    }
                    try{
                        if((command&(1L<<60))!=0)engine.resumeNativeContext(owner,cursor);
                        else engine.applyNativeImport(owner,cursor,(int)((command>>>61)&1),(net.minecraft.world.level.chunk.DataLayer)payload);
                    }
                    finally{Reference.reachabilityFence(engine);Reference.reachabilityFence(position);Reference.reachabilityFence(payload);}
                }else {
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
            try{check(change(3,cursor,0));}finally{Reference.reachabilityFence(propagation);Reference.reachabilityFence(context);Reference.reachabilityFence(level);Reference.reachabilityFence(this);}
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
