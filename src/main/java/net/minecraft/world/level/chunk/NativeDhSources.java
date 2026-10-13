package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.NativeBlockRegistry;
import org.jetbrains.annotations.Nullable;

/** Rust-owned first emitter enumeration, shared by hashing, beacons and lighting. */
public final class NativeDhSources {
    private static MethodHandle handle(String name,FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust",name,signature);
    }
    private static final MethodHandle BUILD=handle("mattmc_dh_sources_from_chunk",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle FROM_FIELD=handle("mattmc_dh_light_field_sources",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle VIEW=handle("mattmc_dh_sources_view",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE=handle("mattmc_dh_sources_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final class Scratch {
        final Arena arena=Arena.ofAuto();final MemorySegment metadata=arena.allocate(3*8,8);
    }
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private final MemorySegment owner,positions;
    private final int minY;
    private NativeDhSources(MemorySegment pointer,MemorySegment metadata) {
        boolean attached=false;
        try {
            int result=(int)VIEW.invokeExact(pointer,metadata);if(result!=1)throw new IllegalStateException("Invalid emitter CPU view");
            // The owner segment is the batched reclaimer's referent; reads fence this object.
            owner=pointer.reinterpret(1);NativeReclaimer.register(owner,pointer,NativeReclaimer.DH_SOURCES);attached=true;
            long count=metadata.getAtIndex(ValueLayout.JAVA_LONG,1);if(count<0||count>1048576)throw new IllegalStateException("Emitter count bound");
            positions=metadata.getAtIndex(ValueLayout.ADDRESS,0).reinterpret(count*4).asReadOnly();minY=(int)metadata.getAtIndex(ValueLayout.JAVA_LONG,2);
        } catch(RuntimeException|Error e){if(!attached)release(pointer);throw e;}
        catch(Throwable e){if(!attached)release(pointer);throw new IllegalStateException("Cannot adopt native emitters",e);}
    }
    @Nullable public static NativeDhSources build(ChunkAccess chunk) {
        if(chunk.getClass()!=ProtoChunk.class && chunk.getClass()!=LevelChunk.class)return null;
        if(chunk instanceof LevelChunk level && level.getLevel().isDebug())return null;
        if(!NativeBlockRegistry.ready())return null;
        int min=chunk.getMinY(),height=chunk.getHeight();if(min%16!=0||Math.abs((long)min)>1_000_000||height<16||height>4096||height%16!=0)return null;
        var inputs=chunk.nativeDhSections();if(inputs==null)return null;var scratch=SCRATCH.get();
        try {
            var pointer=(MemorySegment)BUILD.invokeExact(inputs.stageOwner());
            return pointer.address()==0?null:new NativeDhSources(pointer,scratch.metadata);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException("Cannot enumerate native emitters",e);}
        finally{Reference.reachabilityFence(inputs);}
    }
    @Nullable static NativeDhSources fromField(NativeDhLighting.Field field) {
        try {
            var pointer=(MemorySegment)FROM_FIELD.invokeExact(field.stageOwner());if(pointer.address()==0)return null;
            return new NativeDhSources(pointer,SCRATCH.get().metadata);
        }catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
        finally{Reference.reachabilityFence(field);}
    }
    private static void release(MemorySegment pointer) {
        try{RELEASE.invokeExact(pointer);}catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
    MemorySegment stageOwner(){return owner;}
    public int minY(){return minY;}
    public int count(){return(int)(positions.byteSize()/4);}
    public int index(int i){try{return positions.getAtIndex(ValueLayout.JAVA_INT,i);}finally{Reference.reachabilityFence(this);}}
}
