package net.minecraft.server.level;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.level.material.FluidState;

/** Frozen-first actual block getter, mutable-position alias and checked/reentrant effects. */
public final class VerifyBlockInputEffects {
    static final Exception MARKER=new Exception("original checked block input failure");
    @SuppressWarnings("unchecked") static <T extends Throwable> RuntimeException sneaky(Throwable failure)throws T {throw (T)failure;}
    static void invoke(Object receiver,Class<?> owner,String name,Class<?>[] types,Object... arguments)throws Throwable {
        Method method=owner.getDeclaredMethod(name,types);method.setAccessible(true);
        try{method.invoke(receiver,arguments);}catch(InvocationTargetException envelope){throw envelope.getCause();}
    }
    static final class Getter implements LightChunkGetter {
        final VerifyThreadedLighting.World world;
        final List<String> calls=new ArrayList<>();
        BlockLightEngine block;
        String mode;
        boolean active,lookupOnce,stateOnce;
        Getter(VerifyThreadedLighting.World world){this.world=world;}
        void nested(){
            calls.add("nested-start");
            try{invoke(block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},BlockPos.asLong(10,24,10));}
            catch(Throwable failure){throw sneaky(failure);}
            calls.add("nested-end");
        }
        public LightChunk getChunkForLighting(int x,int z){
            var chunk=world.getChunkForLighting(x,z);
            if(!active||x!=0||z!=0||chunk==null)return chunk;
            if(!lookupOnce){lookupOnce=true;calls.add("lookup:"+x+":"+z);if(mode.equals("lookup-reenter")||mode.equals("canonical-reenter"))nested();}
            if(mode.equals("canonical-reenter"))return chunk;
            return new LightChunk(){
                public BlockState getBlockState(BlockPos pos){
                    if(!stateOnce){
                        stateOnce=true;
                        calls.add("state:"+pos.getX()+":"+pos.getY()+":"+pos.getZ()+":mutable="+(pos instanceof BlockPos.MutableBlockPos));
                        if(mode.equals("checked"))throw sneaky(MARKER);
                        if(mode.equals("getter-reenter"))nested();
                        if(mode.equals("mutate"))((BlockPos.MutableBlockPos)pos).set(9,24,9);
                        calls.add("after:"+pos.getX()+":"+pos.getY()+":"+pos.getZ());
                    }
                    return chunk.getBlockState(pos);
                }
                public BlockEntity getBlockEntity(BlockPos pos){return chunk.getBlockEntity(pos);}
                public FluidState getFluidState(BlockPos pos){return chunk.getFluidState(pos);}
                public int getMinY(){calls.add("unexpected-height-min");return chunk.getMinY();}
                public int getHeight(){calls.add("unexpected-height");return chunk.getHeight();}
                public void findBlockLightSources(BiConsumer<BlockPos,BlockState> consumer){chunk.findBlockLightSources(consumer);}
                public ChunkSkyLightSources getSkyLightSources(){return chunk.getSkyLightSources();}
            };
        }
        public BlockGetter getLevel(){return world;}
        public void onLightUpdate(LightLayer layer,SectionPos section){world.onLightUpdate(layer,section);}
    }
    static void threadedUpdate(ThreadedLevelLightEngine engine)throws Throwable {
        invoke(engine,ThreadedLevelLightEngine.class,"runUpdate",new Class<?>[0]);
    }
    static void run(String path,String mode)throws Throwable {
        try(var fixture=new VerifyThreadedLighting.Fixture(false,false)){
            if(mode.equals("canonical-reenter"))fixture.world.chunks.get(0L).getSection(1).setBlockState(10,8,10,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),false);
            var getter=new Getter(fixture.world);
            LevelLightEngine level=path.equals("threaded")?new ThreadedLevelLightEngine(getter,(ChunkMap)VerifyThreadedLighting.field(fixture.engine,"chunkMap"),false,fixture.executor,fixture.dispatcher):new LevelLightEngine(getter,true,false);
            if(level instanceof ThreadedLevelLightEngine threaded){
                for(var chunk:fixture.world.chunks.values())threaded.initializeLight(chunk,false);
                fixture.transport();threadedUpdate(threaded);
                for(var chunk:fixture.world.chunks.values())threaded.lightChunk(chunk,false);
                fixture.transport();threadedUpdate(threaded);
            }else{
                for(var chunk:fixture.world.chunks.values())for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())level.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
                level.runLightUpdates();
                for(var chunk:fixture.world.chunks.values()){level.setLightEnabled(chunk.getPos(),true);level.propagateLightSources(chunk.getPos());}
                level.runLightUpdates();
            }
            getter.block=(BlockLightEngine)level.getLayerListener(LightLayer.BLOCK);getter.mode=mode;getter.active=true;
            var target=new BlockPos(8,24,8);Throwable failure=null;
            try{
                if(path.equals("engine"))invoke(getter.block,BlockLightEngine.class,"checkNode",new Class<?>[]{long.class},target.asLong());
                else{level.checkBlock(target);if(level instanceof ThreadedLevelLightEngine threaded){fixture.transport();threadedUpdate(threaded);}else level.runLightUpdates();}
            }catch(Throwable actual){failure=actual;}
            if(!getter.stateOnce&&!mode.equals("canonical-reenter"))throw new AssertionError("Actual custom block getter was not reached");
            if(mode.equals("checked")&&failure!=MARKER)throw new AssertionError("Original checked block Throwable changed",failure);
            if(!mode.equals("checked")&&failure!=null)throw failure;
            if(getter.calls.stream().anyMatch(s->s.startsWith("unexpected-height")))throw new AssertionError("Extra virtual height observation");
            String stored="";
            if(mode.equals("canonical-reenter")){
                if(!getter.calls.contains("nested-end"))throw new AssertionError("Actual canonical lookup reentry did not run");
                Object storage=VerifyThreadedLighting.field(getter.block,"storage");
                var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");
                stored=" stored="+map.getLayer(SectionPos.asLong(0,1,0)).get(8,8,8);
            }
            System.out.println("BLOCK_INPUT path="+path+" mode="+mode+" same-marker="+(failure==MARKER)+" pending="+getter.block.hasLightWork()+" light="+getter.block.getLightValue(target)+stored+" calls="+getter.calls);
        }
    }
    public static void main(String[] args)throws Throwable {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String path:List.of("engine","level","threaded"))for(String mode:List.of("value","checked","getter-reenter","lookup-reenter","mutate","canonical-reenter"))run(path,mode);
    }
}
