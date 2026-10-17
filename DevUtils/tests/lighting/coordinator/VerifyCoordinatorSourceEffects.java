package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/** Portable real enable callbacks, original failures, map replacement and retry. */
public final class VerifyCoordinatorSourceEffects {
    private static final RuntimeException FAILURE = new RuntimeException("original enable callback failure");
    private static final class Sources extends ChunkSkyLightSources {
        final List<String> calls;
        Runnable callback;
        boolean reject;
        Sources(List<String> calls) { super(LevelHeightAccessor.create(0,64)); this.calls=calls; }
        @Override public int getHighestLowestSourceY() {
            calls.add("highest");
            Runnable action=callback;callback=null;if(action!=null)action.run();
            if(reject)throw FAILURE;
            return 0;
        }
    }
    private static final class Layer extends DataLayer {
        final List<String> calls;
        Runnable callback;
        boolean reject;
        Layer(List<String> calls) { this.calls=calls; }
        @Override public boolean isEmpty() {
            calls.add("empty");Runnable action=callback;callback=null;if(action!=null)action.run();return true;
        }
        @Override public void fill(int value) { calls.add("fill:"+value);if(reject)throw FAILURE;super.fill(value); }
    }
    private static void set(Object object,String name,Object value)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;
        }catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static void bounds(Object map,int bottom,int top)throws Exception {
        try { VerifyThreadedLighting.invoke(map,"lowestY",new Class<?>[]{int.class},bottom); }
        catch(NoSuchMethodException frozen) {
            set(map,"currentLowestY",bottom);
            ((it.unimi.dsi.fastutil.longs.Long2IntMap)VerifyThreadedLighting.field(map,"topSections")).defaultReturnValue(bottom);
        }
        try { VerifyThreadedLighting.invoke(map,"putTop",new Class<?>[]{long.class,int.class},0L,top); }
        catch(NoSuchMethodException frozen) {
            ((it.unimi.dsi.fastutil.longs.Long2IntMap)VerifyThreadedLighting.field(map,"topSections")).put(0L,top);
        }
    }
    public static void main(String[] args)throws Exception {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String mode:List.of("replace","highest-throw","fill-throw","nested"))try(var fixture=new VerifyThreadedLighting.Fixture(false,true)) {
            List<String> calls=new ArrayList<>();Sources sources=new Sources(calls);
            set(fixture.world.chunks.get(0L),"skyLightSources",sources);
            Object storage=VerifyThreadedLighting.field(fixture.engine.getLayerListener(LightLayer.SKY),"storage");
            var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");
            long key=SectionPos.asLong(0,2,0);Layer selected=new Layer(calls);DataLayer replacement=new DataLayer(7);
            map.setLayer(key,selected);map.clearCache();
            ((LongSet)VerifyThreadedLighting.field(storage,"changedSections")).add(key);
            bounds(map,9,9);
            sources.callback=()->{try{bounds(map,2,3);}catch(Exception e){throw new RuntimeException(e);}};
            sources.reject=mode.equals("highest-throw");selected.reject=mode.equals("fill-throw");
            if(mode.equals("replace"))selected.callback=()->{map.setLayer(key,replacement);map.clearCache();};
            if(mode.equals("nested"))selected.callback=()->{try{fixture.add(false,()->calls.add("nested-pre"));fixture.transport();fixture.update();}catch(Exception e){throw new RuntimeException(e);}};
            fixture.engine.setLightEnabled(new ChunkPos(0,0),true);fixture.transport();
            System.out.println("SOURCE deferred="+mode+" calls="+calls+" pending="+fixture.pending());
            boolean failed=false;try{fixture.update();}catch(RuntimeException error){if(error!=FAILURE)throw error;failed=true;}
            if(failed!=mode.endsWith("throw"))throw new AssertionError("Original enable failure changed");
            System.out.println("SOURCE result="+mode+" failed="+failed+" pending="+fixture.pending()+" enabled="+fixture.engine.lightOnInColumn(0L)+" calls="+calls+" selected="+selected.get(0,0,0)+" current="+map.getLayer(key).get(0,0,0));
            if(failed){sources.reject=false;selected.reject=false;fixture.update();System.out.println("SOURCE retry="+mode+" pending="+fixture.pending()+" calls="+calls+" selected="+selected.get(0,0,0));}
        }
        for(int mode=0;mode<4;mode++)try(var fixture=new VerifyThreadedLighting.Fixture(false,true)) {
            List<String> calls=new ArrayList<>();set(fixture.world.chunks.get(0L),"skyLightSources",new Sources(calls));
            Object storage=VerifyThreadedLighting.field(fixture.engine.getLayerListener(LightLayer.SKY),"storage");
            var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");
            long key=SectionPos.asLong(0,2,0);DataLayer layer=mode==0?new DataLayer():mode==1?new DataLayer(new byte[2048]):mode==2?new DataLayer(16):new DataLayer(7);
            map.setLayer(key,layer);map.clearCache();((LongSet)VerifyThreadedLighting.field(storage,"changedSections")).add(key);bounds(map,2,3);
            fixture.engine.setLightEnabled(new ChunkPos(0,0),true);fixture.transport();fixture.update();
            System.out.println("SOURCE layer="+mode+" value="+layer.get(0,0,0)+" homogeneous="+layer.isDefinitelyHomogenous()+" alias="+(map.getLayer(key)==layer));
        }
        for(boolean sky:new boolean[]{false,true})try(var fixture=new VerifyThreadedLighting.Fixture(false,sky)) {
            fixture.initialize();fixture.engine.setLightEnabled(new ChunkPos(0,0),false);fixture.transport();fixture.update();
            fixture.world.rejectLookup=true;fixture.engine.propagateLightSources(new ChunkPos(0,0));fixture.transport();
            System.out.println("SOURCE source-deferred sky="+sky+" pending="+fixture.pending()+" enabled="+fixture.engine.lightOnInColumn(0L));
            try{fixture.update();throw new AssertionError("Missing source lookup failure");}catch(IllegalStateException error){if(!"propagation lookup failure".equals(error.getMessage()))throw error;}
            System.out.println("SOURCE source-error sky="+sky+" pending="+fixture.pending()+" enabled="+fixture.engine.lightOnInColumn(0L));
            fixture.world.rejectLookup=false;fixture.update();System.out.println("SOURCE source-retry sky="+sky+" pending="+fixture.pending()+" enabled="+fixture.engine.lightOnInColumn(0L));
        }
    }
}
