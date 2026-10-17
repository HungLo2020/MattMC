package net.minecraft.world.level.lighting;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;

/** Current-only: actual level query/frame/Source Arc after facade and arena GC. */
public final class VerifySkySourceFrameLifetime {
    static MethodHandle handle(String name,FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust",name,descriptor);
    }
    static final MethodHandle COMMAND=handle("mattmc_light_level_command",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
    static final MethodHandle READ=handle("mattmc_light_level_frame_read",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle BIND=handle("mattmc_light_level_frame_bind_source",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
    static final MethodHandle STEP=handle("mattmc_light_level_frame_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    static final MethodHandle END=handle("mattmc_light_level_frame_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle PROBE=handle("mattmc_sky_source_probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle OWNERS=handle("mattmc_sky_source_probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
    static final MethodHandle DROP_PROBE=handle("mattmc_sky_source_probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final class Getter implements LightChunkGetter,BlockGetter {
        public LightChunk getChunkForLighting(int x,int z){return null;}
        public BlockGetter getLevel(){return this;}
        public void onLightUpdate(LightLayer layer,SectionPos pos){}
        public BlockEntity getBlockEntity(BlockPos pos){return null;}
        public BlockState getBlockState(BlockPos pos){return Blocks.AIR.defaultBlockState();}
        public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        public int getMinY(){return 0;}
        public int getHeight(){return 64;}
    }
    record Setup(long probe,List<WeakReference<Object>> weak,long[] snapshot){}
    static Setup capture(long frame,Object[] holder)throws Throwable {
        var source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,64));
        long[] words=source.nativeSnapshot();int bits=source.nativeBits(),per=64/bits;
        words[0]=1; // Column0 is0; reading it instead of the maximum must fail.
        words[255/per]=18L<<((255%per)*bits);source.nativeRestore(words); // actual highest17 incolumn255
        var field=ChunkSkyLightSources.class.getDeclaredField("nativeOwner");field.setAccessible(true);Object owner=field.get(source);
        var anchorField=owner.getClass().getDeclaredField("owner");anchorField.setAccessible(true);Object anchor=anchorField.get(owner);
        long id=source.nativeOwnerForLighting();long probe=(long)PROBE.invokeExact(id);
        try {
            if((int)BIND.invokeExact(frame,MemorySegment.ofAddress(id))!=0)throw new AssertionError("Actual waiting level query rejected Source input");
            if((long)OWNERS.invokeExact(probe)!=2)throw new AssertionError("Base and real frame must each own Source Arc");
            if(holder!=null)holder[0]=source;
            return new Setup(probe,List.of(new WeakReference<>(source),new WeakReference<>(owner),new WeakReference<>(anchor)),words.clone());
        }catch(Throwable failure){DROP_PROBE.invokeExact(probe);throw failure;}
        finally{Reference.reachabilityFence(source);}
    }
    static boolean collected(Setup setup){for(var weak:setup.weak())if(weak.get()!=null)return false;return true;}
    static void run(boolean abandon,boolean retain)throws Throwable {
        var engine=SkyLightEngine.withBindings(new Getter(),new NativeLightBindings());
        NativeLevelLighting level=NativeLevelLighting.create(null,engine);
        if(level==null)throw new AssertionError("Actual native level binding required");
        var map=engine.storage.updatingSectionData;long key=SectionPos.asLong(0,1,0);
        var layer=new DataLayer();map.setLayer(key,layer);map.clearCache();map.lowestY(1);map.putTop(0L,2);
        engine.storage.changedSections.add(key);
        long frame=0;Setup setup=null;
        try(Arena arena=Arena.ofConfined()) {
            MemorySegment output=arena.allocate(7L*8,8);
            frame=(long)COMMAND.invokeExact(level.ownerForTasks(),1,0L,1);
            if(frame==0||(int)READ.invokeExact(frame,output)!=17)throw new AssertionError("Actual highest-source request missing");
            Object[] holder=retain?new Object[1]:null;setup=capture(frame,holder);
            if(retain){for(int i=0;i<5;i++){System.gc();Thread.sleep(20);}if(collected(setup)||(long)OWNERS.invokeExact(setup.probe())!=2)throw new AssertionError("Retained source control lost owner");holder[0]=null;}
            long deadline=System.nanoTime()+5_000_000_000L;
            while((!collected(setup)||(long)OWNERS.invokeExact(setup.probe())!=1)&&System.nanoTime()<deadline){if((long)OWNERS.invokeExact(setup.probe())==0){frame=0;throw new AssertionError("Pending real frame lost Source before last use; unsafe failure cleanup skipped");}System.gc();Thread.sleep(10);}
            if(!collected(setup))throw new AssertionError("Source facade/owner/arena remains reachable");
            if((long)OWNERS.invokeExact(setup.probe())!=1)throw new AssertionError("actual base handle did not retire while pending frame remains");
            if(abandon){END.invokeExact(frame);frame=0;}
            else {
                // A fake scalar0 would fill the real layer15. The captured actual
                // highest17 must leave it0, even after VM/base retirement.
                int action=(int)STEP.invokeExact(frame,0L,MemorySegment.NULL,output);
                if((long)OWNERS.invokeExact(setup.probe())!=0)throw new AssertionError("Source input crosses a later VM effect");
                for(int effects=0;action!=0;effects++) {
                    if(effects>=8)throw new AssertionError("Actual query did not settle");
                    if(action==16)map.nativeMap.drainForCoordinator();
                    else if(action==23) {
                        long count=output.getAtIndex(ValueLayout.JAVA_LONG,5);
                        MemorySegment changes=MemorySegment.ofAddress(output.getAtIndex(ValueLayout.JAVA_LONG,4)).reinterpret(count*4);
                        for(long i=0;i<count;i++)map.nativeMap.refreshForCoordinator(changes.getAtIndex(ValueLayout.JAVA_INT,i));
                    }else throw new AssertionError("Unexpected real source-query effect: "+action);
                    action=(int)STEP.invokeExact(frame,0L,MemorySegment.NULL,output);
                }
            }
            if((long)OWNERS.invokeExact(setup.probe())!=0)throw new AssertionError("Source remains after actual query last use/abandonment");
            if(layer.get(0,0,0)!=0||map.getLayer(key).get(0,0,0)!=0)throw new AssertionError("Real consumer used fake scalar instead of Source payload");
            if(setup.snapshot()[0]!=1||setup.snapshot()[setup.snapshot().length-1]==0)throw new AssertionError("Detached Source snapshot changed");
            System.out.println("SOURCE_FRAME_LIFETIME abandon="+abandon+" retained-control="+retain+" VMcollected=true baseRetired=true actual-payload=true finalNativeOwners=0");
        }finally {
            try{if(frame!=0)END.invokeExact(frame);}finally{if(setup!=null)DROP_PROBE.invokeExact(setup.probe());Reference.reachabilityFence(level);Reference.reachabilityFence(engine);}
        }
    }
    public static void main(String[] args)throws Throwable {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(boolean abandon:new boolean[]{false,true})for(boolean retain:new boolean[]{false,true})run(abandon,retain);
    }
}
