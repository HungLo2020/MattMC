package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Portable original pending/run/cache/publication and nested native consumer effects. */
public final class VerifyCoordinatorLevelConsumers {
    private static final RuntimeException FAILURE=new RuntimeException("original level lookup failure");
    private static final class Getter implements LightChunkGetter {
        final VerifyThreadedLighting.World world;
        final List<String> calls=new ArrayList<>();
        boolean capture,reject;
        Runnable lookup,notification;
        Getter(VerifyThreadedLighting.World world){this.world=world;}
        public LightChunk getChunkForLighting(int x,int z){
            if(capture)calls.add("chunk:"+x+":"+z);
            Runnable action=lookup;lookup=null;if(action!=null)action.run();
            if(reject)throw FAILURE;
            return world.getChunkForLighting(x,z);
        }
        public BlockGetter getLevel(){return world;}
        public void onLightUpdate(LightLayer layer,SectionPos section){
            if(capture)calls.add("notify:"+layer+":"+section.asLong());
            Runnable action=notification;notification=null;if(action!=null)action.run();
            world.onLightUpdate(layer,section);
        }
    }
    private static LevelLightEngine initialize(Getter source,boolean sky){
        var level=new LevelLightEngine(source,true,sky);
        for(var chunk:source.world.chunks.values())for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())
            level.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
        level.runLightUpdates();
        for(var chunk:source.world.chunks.values()){level.setLightEnabled(chunk.getPos(),true);level.propagateLightSources(chunk.getPos());}
        level.runLightUpdates();source.calls.clear();source.capture=true;return level;
    }
    private static void print(String stage,LevelLightEngine level,Getter source,int result){
        System.out.println("LEVEL_CONSUMER "+stage+" result="+result+" pending="+level.hasLightWork()+" calls="+source.calls);
        source.calls.clear();
    }
    private static Object storage(LevelLightEngine level,LightLayer layer)throws Exception{
        return VerifyThreadedLighting.field(level.getLayerListener(layer),"storage");
    }
    public static void main(String[] args)throws Exception{
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        System.out.println("LEVEL_CONSUMER empty="+LevelLightEngine.EMPTY.runLightUpdates()+":"+LevelLightEngine.EMPTY.hasLightWork());
        for(boolean sky:new boolean[]{false,true}){
            var source=new Getter(new VerifyThreadedLighting.World());var level=initialize(source,sky);
            print("idle:"+sky,level,source,level.runLightUpdates());
            level.checkBlock(new BlockPos(8,24,8));print("queued:"+sky,level,source,-1);
            source.reject=true;
            try{level.runLightUpdates();throw new AssertionError("Missing original level lookup failure");}
            catch(RuntimeException failure){if(failure!=FAILURE)throw failure;print("error:"+sky,level,source,-1);}
            source.reject=false;print("retry:"+sky,level,source,level.runLightUpdates());
            print("idle-again:"+sky,level,source,level.runLightUpdates());
            level.checkBlock(new BlockPos(8,24,8));print("after-clear:"+sky,level,source,level.runLightUpdates());
            List<DataLayer> old=new ArrayList<>();List<DataLayer> replacements=new ArrayList<>();
            for(var channel:sky?LightLayer.values():new LightLayer[]{LightLayer.BLOCK}){
                var section=SectionPos.of(0,1,0);old.add(level.getLayerListener(channel).getDataLayerData(section));
                Object storage=storage(level,channel);var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");
                var replacement=new DataLayer(6);replacements.add(replacement);map.setLayer(section.asLong(),replacement);map.clearCache();
                ((LongSet)VerifyThreadedLighting.field(storage,"changedSections")).add(section.asLong());
                ((LongSet)VerifyThreadedLighting.field(storage,"sectionsAffectedByLightUpdates")).add(section.asLong());
            }
            print("publication-only-pending:"+sky,level,source,-1);
            print("publication-only-run:"+sky,level,source,level.runLightUpdates());
            int i=0;for(var channel:sky?LightLayer.values():new LightLayer[]{LightLayer.BLOCK}){
                var current=level.getLayerListener(channel).getDataLayerData(SectionPos.of(0,1,0));
                System.out.println("LEVEL_CONSUMER published:"+sky+":"+channel+" alias="+(current==replacements.get(i))+" old-distinct="+(current!=old.get(i))+" value="+current.get(0,0,0));i++;
            }
        }
        for(String mode:List.of("lookup-reentry","notify-reentry")){
            var source=new Getter(new VerifyThreadedLighting.World());var level=initialize(source,true);
            Runnable nested=()->{source.calls.add("nested-start");level.checkBlock(new BlockPos(-8,24,-8));int count=level.runLightUpdates();source.calls.add("nested-end:"+count);};
            if(mode.equals("lookup-reentry"))source.lookup=nested;else {
                source.notification=nested;
                for(var channel:LightLayer.values())((LongSet)VerifyThreadedLighting.field(storage(level,channel),"sectionsAffectedByLightUpdates")).add(SectionPos.asLong(0,1,0));
            }
            level.checkBlock(new BlockPos(8,24,8));
            try{print(mode,level,source,level.runLightUpdates());}
            catch(RuntimeException error){System.out.println("LEVEL_CONSUMER "+mode+" error="+error.getClass().getSimpleName()+":"+error.getMessage()+" pending="+level.hasLightWork()+" calls="+source.calls);}
            if(source.lookup!=null||source.notification!=null)throw new AssertionError("Original reentry effect was not reached: "+mode);
        }
        try(var fixture=new VerifyThreadedLighting.Fixture(false,true)){
            fixture.initialize();List<String> calls=new ArrayList<>();boolean[] first={true};
            fixture.engine.checkBlock(new BlockPos(8,24,8));fixture.add(false,()->{calls.add("pre");if(first[0]){first[0]=false;fixture.world.rejectLookup=true;}});fixture.add(true,()->calls.add("post"));fixture.transport();
            try{fixture.update();throw new AssertionError("Missing original threaded propagation error");}
            catch(IllegalStateException error){if(!"propagation lookup failure".equals(error.getMessage()))throw error;}
            System.out.println("LEVEL_CONSUMER threaded-error pending="+fixture.pending()+" calls="+calls);
            fixture.world.rejectLookup=false;fixture.update();System.out.println("LEVEL_CONSUMER threaded-retry pending="+fixture.pending()+" calls="+calls);
        }
    }
}
