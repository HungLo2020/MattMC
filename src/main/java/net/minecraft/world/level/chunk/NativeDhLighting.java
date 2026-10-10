package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** One whole CPU lighting pass; Rust owns block snapshots, queues and immutable outputs. */
public final class NativeDhLighting {
    private NativeDhLighting() {}
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", name, descriptor);
    }
    private static final MethodHandle BUILD = handle("mattmc_dh_lighting_build", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
    private static final MethodHandle TAKE = handle("mattmc_dh_lighting_take", FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT));
    private static final MethodHandle ITERATIONS = handle("mattmc_dh_lighting_iterations", FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("mattmc_dh_lighting_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("mattmc_dh_light_field_view", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle FIELD_RELEASE = handle("mattmc_dh_light_field_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    public record ChunkInput(ChunkAccess chunk, NativeDhHeightmaps heights, @Nullable Field previous, @Nullable NativeDhSources sources) {}
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment descriptors=arena.allocate(9*48,8), owners=arena.allocate(9*256*8,8), order=arena.allocate(9,1), metadata=arena.allocate(10*8,8);
        final ChunkInput[] inputs=new ChunkInput[9];
        final ChunkWrapper[] wrappers=new ChunkWrapper[9];
        final NativeLiveBlockSection[] pins=new NativeLiveBlockSection[9*256];
    }
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    /** -1 declines without writes; normal output is a Frozen-compatible work count. */
    public static int tryLight(IChunkWrapper center, ArrayList<IChunkWrapper> nearby, int sky, boolean block, boolean updateSky) {
        if(center.getClass()!=ChunkWrapper.class || nearby.getClass()!=ArrayList.class || nearby.size()>1024 || sky<0 || sky>15) return -1;
        // Do not invoke a custom wrapper's callbacks before replaying the legacy path.
        for(int i=0;i<nearby.size();i++) if(nearby.get(i)!=null && nearby.get(i).getClass()!=ChunkWrapper.class) return -1;
        var scratch=SCRATCH.get(); MemorySegment batch=MemorySegment.NULL;
        try {
            var canonical=(ChunkWrapper)center;var position=canonical.getChunkPos();scratch.wrappers[4]=canonical;
            int orderCount=0, mask=0;
            for(int i=0;i<nearby.size();i++) {
                var wrapper=(ChunkWrapper)nearby.get(i);if(wrapper==null)continue;
                var pos=wrapper.getChunkPos();long x=(long)pos.getX()-position.getX(), z=(long)pos.getZ()-position.getZ();
                if(x < -1 || x > 1 || z < -1 || z > 1)continue;
                int slot=(int)(4+x+z*3);if((mask&(1<<slot))!=0)continue;
                // A second wrapper for center has separate mutable light authority.
                if(slot==4 && wrapper!=canonical)return -1;
                mask|=1<<slot;scratch.wrappers[slot]=wrapper;scratch.order.setAtIndex(ValueLayout.JAVA_BYTE,orderCount++,(byte)slot);
            }
            int count=0;
            for(int slot=0;slot<9;slot++) {
                var wrapper=scratch.wrappers[slot];if(wrapper==null)continue;
                var input=wrapper.nativeDhLightingInput();if(input==null)return -1;scratch.inputs[count]=input;
                var sections=input.chunk().getSectionsForRead();if(sections.length<1||sections.length>256)return -1;
                long row=count*48L;scratch.descriptors.set(ValueLayout.JAVA_INT,row,slot);
                scratch.descriptors.set(ValueLayout.JAVA_INT,row+4,input.chunk().getMinY());
                scratch.descriptors.set(ValueLayout.JAVA_INT,row+8,sections.length);scratch.descriptors.set(ValueLayout.JAVA_INT,row+12,0);
                var pointers=scratch.owners.asSlice(count*256L*8,sections.length*8L);
                for(int s=0;s<sections.length;s++) {
                    var section=sections[s];if(section==null)return -1;var owner=section.nativeGenerationInput();if(owner==null)return -1;
                    scratch.pins[count*256+s]=owner;
                    // Public wrapper replacements can alter opacity/emission even for air.
                    for(int p=0,n=owner.paletteSize();p<n;p++) {
                        var state=owner.paletteValue(p);var cached=BlockStateWrapper.WRAPPER_BY_BLOCK_STATE.get(state);
                        if(cached!=null&&(cached.getClass()!=BlockStateWrapper.class||cached.blockState!=state))return -1;
                    }
                    pointers.setAtIndex(ValueLayout.ADDRESS,s,owner.stageOwner());
                }
                scratch.descriptors.set(ValueLayout.ADDRESS,row+16,pointers);
                scratch.descriptors.set(ValueLayout.ADDRESS,row+24,input.heights().stageOwner());
                scratch.descriptors.set(ValueLayout.ADDRESS,row+32,input.previous()==null?MemorySegment.NULL:input.previous().owner);
                scratch.descriptors.set(ValueLayout.ADDRESS,row+40,input.sources()==null?MemorySegment.NULL:input.sources().stageOwner());
                count++;
            }
            batch=(MemorySegment)BUILD.invokeExact(scratch.descriptors,count,scratch.order,orderCount,sky,(block?1:0)|(updateSky?2:0));
            if(batch.address()==0)return -1;
            long iterations=(long)ITERATIONS.invokeExact(batch);if(iterations<0||iterations>Integer.MAX_VALUE)throw new IllegalStateException("DH work count overflow");
            var outputs=new Field[9];
            // Complete adoption before publishing any wrapper. Untaken owners are
            // released by the batch; taken owners have independent automatic arenas.
            for(int slot=0;slot<9;slot++)if(scratch.wrappers[slot]!=null) {
                var pointer=(MemorySegment)TAKE.invokeExact(batch,slot);if(pointer.address()==0)throw new IllegalStateException("Missing DH light output");
                outputs[slot]=new Field(pointer,scratch.metadata);
            }
            for(int slot=0;slot<9;slot++)if(outputs[slot]!=null)scratch.wrappers[slot].adoptNativeDhLighting(outputs[slot]);
            return (int)iterations;
        } catch(RuntimeException|Error e) {throw e;}
        catch(Throwable e) {throw new IllegalStateException("Cannot run native DH lighting",e);}
        finally {
            Reference.reachabilityFence(scratch.inputs);Reference.reachabilityFence(scratch.pins);
            Arrays.fill(scratch.inputs,null);Arrays.fill(scratch.wrappers,null);Arrays.fill(scratch.pins,null);
            if(batch.address()!=0)releaseBatch(batch);
        }
    }
    private static void releaseBatch(MemorySegment pointer) {
        try {RELEASE.invokeExact(pointer);}catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
    private static void releaseField(MemorySegment pointer) {
        try {FIELD_RELEASE.invokeExact(pointer);}catch(RuntimeException|Error e){throw e;}catch(Throwable e){throw new IllegalStateException(e);}
    }
    public static final class Field {
        private final MemorySegment owner,blockBytes,skyBytes,blockSections,skySections,sources;
        private final int sections;
        private final int minY;
        private Field(MemorySegment pointer,MemorySegment metadata) {
            Arena arena=Arena.ofAuto();boolean attached=false;
            try {
                int result=(int)VIEW.invokeExact(pointer,metadata);if(result!=1)throw new IllegalStateException("Invalid DH light view");
                owner=pointer.reinterpret(1,arena,NativeDhLighting::releaseField);attached=true;
                long blockLength=metadata.getAtIndex(ValueLayout.JAVA_LONG,1),skyLength=metadata.getAtIndex(ValueLayout.JAVA_LONG,4);
                long sectionCount=metadata.getAtIndex(ValueLayout.JAVA_LONG,6),sourceCount=metadata.getAtIndex(ValueLayout.JAVA_LONG,8);
                if(sectionCount<1||sectionCount>256||blockLength<0||skyLength<0||blockLength>sectionCount*2048||skyLength>sectionCount*2048
                    ||blockLength%2048!=0||skyLength%2048!=0||sourceCount<0||sourceCount>sectionCount*4096)throw new IllegalStateException("DH light view bounds");
                sections=(int)sectionCount;
                blockBytes=metadata.getAtIndex(ValueLayout.ADDRESS,0).reinterpret(blockLength,arena,null).asReadOnly();
                blockSections=metadata.getAtIndex(ValueLayout.ADDRESS,2).reinterpret(sectionCount*4,arena,null).asReadOnly();
                skyBytes=metadata.getAtIndex(ValueLayout.ADDRESS,3).reinterpret(skyLength,arena,null).asReadOnly();
                skySections=metadata.getAtIndex(ValueLayout.ADDRESS,5).reinterpret(sectionCount*4,arena,null).asReadOnly();
                sources=metadata.getAtIndex(ValueLayout.ADDRESS,7).reinterpret(sourceCount*4,arena,null).asReadOnly();
                minY=(int)metadata.getAtIndex(ValueLayout.JAVA_LONG,9);
            } catch(RuntimeException|Error e) {if(!attached)releaseField(pointer);throw e;}
            catch(Throwable e) {if(!attached)releaseField(pointer);throw new IllegalStateException("Cannot adopt DH lights",e);}
        }
        MemorySegment stageOwner(){return owner;}
        public NativeDhSources retainSources(){return NativeDhSources.fromField(this);}
        public int minY(){return minY;}
        public int maxY(){return minY+sections*16;}
        public int light(int x,int y,int z,boolean sky) {
            try {
                if(x<0||x>15||z<0||z>15)throw new IndexOutOfBoundsException("DH light column");
                if(y<minY)return 0;if(y>=maxY())return sky?15:0;
                int index=(y-minY)*256+z*16+x;
                int offset=(sky?skySections:blockSections).getAtIndex(ValueLayout.JAVA_INT,index/4096);
                if(offset<0)return -1-offset;
                int packed=Byte.toUnsignedInt((sky?skyBytes:blockBytes).getAtIndex(ValueLayout.JAVA_BYTE,offset+(index%4096)/2));
                return(packed>>((index&1)*4))&15;
            } finally {Reference.reachabilityFence(this);}
        }
        public int sourceCount(){return(int)(sources.byteSize()/4);}
        public int sourceIndex(int index) {
            try{return sources.getAtIndex(ValueLayout.JAVA_INT,index);}finally{Reference.reachabilityFence(this);}
        }
    }
}
