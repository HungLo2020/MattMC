package net.minecraft.server.level;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;
import net.minecraft.world.level.material.FluidState;
import org.objenesis.ObjenesisStd;

/** Actual stock threaded engine, original controlled transport, live section-array/height effects. */
public final class ThreadedRequestContracts {
    static final List<String> events=new ArrayList<>();
    static final class Height implements LevelHeightAccessor {
        boolean active; int calls; Runnable action;
        public int getMinY(){return 0;}
        public int getHeight(){
            if(active){events.add("count "+(++calls));if(action!=null)action.run();}
            return 64;
        }
    }
    static final class Section extends LevelChunkSection {
        final String name; boolean fail;
        Section(String name){super(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);this.name=name;}
        @Override public boolean hasOnlyAir(){
            events.add("section "+name+(fail?" throw":""));
            if(fail){fail=false;throw new IllegalStateException("section probe");}
            return false;
        }
    }
    static final class World implements LightChunkGetter,BlockGetter {
        public LightChunk getChunkForLighting(int x,int z){return null;}
        public BlockGetter getLevel(){return this;}
        public BlockState getBlockState(BlockPos p){return Blocks.AIR.defaultBlockState();}
        public FluidState getFluidState(BlockPos p){return Blocks.AIR.defaultBlockState().getFluidState();}
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public int getMinY(){return 0;}
        public int getHeight(){return 64;}
    }
    static final class Fixture implements AutoCloseable {
        final VerifyThreadedLighting.Tasks dispatch=new VerifyThreadedLighting.Tasks(),light=new VerifyThreadedLighting.Tasks();
        final VerifyThreadedLighting.LightExecutor executor=new VerifyThreadedLighting.LightExecutor(light);
        final ChunkTaskDispatcher dispatcher=new ChunkTaskDispatcher(executor,dispatch);
        final ThreadedLevelLightEngine engine=new ThreadedLevelLightEngine(new World(),new ObjenesisStd().newInstance(VerifyThreadedLighting.MapBoundary.class),false,executor,dispatcher);
        void transport(){for(int i=0;i<10000;i++){boolean ran=dispatch.step();ran|=light.step();if(!ran)return;}throw new AssertionError("transport");}
        void update()throws Exception{VerifyThreadedLighting.invoke(engine,"runUpdate",new Class<?>[0]);}
        int pending()throws Exception{Object tasks=VerifyThreadedLighting.field(engine,"lightTasks");return tasks instanceof Collection<?> c?c.size():((Number)VerifyThreadedLighting.invoke(tasks,"pending",new Class<?>[0])).intValue();}
        public void close(){engine.close();dispatcher.close();executor.close();}
    }
    static ProtoChunk chunk(Height height,Section[] sections){return new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,sections,new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),height,null,null);}
    static Section[] sections(){return new Section[]{new Section("0"),new Section("1"),new Section("2"),new Section("3")};}
    static void initialization(boolean throwing)throws Exception{
        events.add("initialization "+throwing);
        try(var f=new Fixture()){
            var h=new Height();var rows=sections();var c=chunk(h,rows);var alias=c.getSections();
            if(throwing)rows[1].fail=true;
            else h.action=()->{if(h.calls==2){alias[1]=new Section("replacement");events.add("replace");}};
            var future=f.engine.initializeLight(c,true);future.thenRun(()->events.add("initialized "+f.engine.lightOnInColumn(SectionPos.getZeroNode(0,0))));
            events.add("before "+future.isDone());f.transport();events.add("pending "+f.pending());h.active=true;
            try{f.update();events.add("first OK");}catch(IllegalStateException e){events.add("first "+e.getMessage());}
            events.add("after-first "+future.isDone()+" "+f.pending());
            if(throwing){f.update();events.add("retry "+future.isDone()+" "+f.pending());}
            h.active=false;
            if(!future.isDone()||f.pending()!=0)throw new AssertionError("unfinished initialization");
            VerifyThreadedLighting.invoke(f.engine,"updateChunkStatus",new Class<?>[]{ChunkPos.class},c.getPos());f.transport();f.update();
            events.add("unloaded "+f.engine.lightOnInColumn(SectionPos.getZeroNode(0,0))+" "+f.pending());
            if(f.engine.lightOnInColumn(SectionPos.getZeroNode(0,0)))throw new AssertionError("column remains enabled");
        }
    }
    static void cancellation()throws Exception{
        events.add("cancellation");
        try(var f=new Fixture()){
            var h=new Height();var c=chunk(h,sections());c.setLightCorrect(true);
            var cancelled=f.engine.lightChunk(c,true);events.add("immediate "+c.isLightCorrect());
            events.add("cancel "+cancelled.cancel(false));f.transport();f.update();events.add("cancelled "+c.isLightCorrect()+" "+cancelled.isCancelled()+" "+f.pending());
            if(c.isLightCorrect()||!cancelled.isCancelled())throw new AssertionError("cancelled POST ran");
            var normal=f.engine.lightChunk(c,true);normal.thenRun(()->events.add("completed "+c.isLightCorrect()));f.transport();f.update();
            events.add("normal "+normal.isDone()+" "+c.isLightCorrect()+" "+f.pending());
            if(!normal.isDone()||!c.isLightCorrect()||f.pending()!=0)throw new AssertionError("normal POST absent");
        }
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();VerifyThreadedLighting.pinClasses();
        initialization(false);initialization(true);cancellation();Files.write(Path.of(args[0]),events);
        System.out.println("REQUEST_CONTRACTS 3 "+events.size());
    }
}
