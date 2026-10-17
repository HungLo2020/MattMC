package net.minecraft.server.level;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.*;

/** Frozen-first inherited Source getters, checked failure and same-engine reentry. */
public final class VerifySourceInputEffects {
    static final Exception MARKER=new Exception("original checked Source input failure");
    @SuppressWarnings("unchecked") static <T extends Throwable> RuntimeException sneaky(Throwable failure)throws T {throw (T)failure;}
    static class Parent extends ChunkSkyLightSources {
        final List<String> calls=new ArrayList<>();
        Runnable callback;
        boolean reject;
        Parent(){super(LevelHeightAccessor.create(0,64));}
        @Override public int getLowestSourceY(int x,int z){
            calls.add("low:"+x+":"+z);Runnable action=callback;callback=null;if(action!=null)action.run();
            if(reject)throw sneaky(MARKER);
            return Integer.MAX_VALUE;
        }
        // Present only as a subclass method on Frozen. It must never replace
        // the actual inherited getter with a nativeRaw transport operation.
        public long[] nativeRaw(){throw new AssertionError("Unexpected nativeRaw dispatch in Source query");}
    }
    static final class Inherited extends Parent {}
    interface Call {void run()throws Throwable;}
    static void replace(ProtoChunk chunk,ChunkSkyLightSources source)throws Exception {
        Field field=ChunkAccess.class.getDeclaredField("skyLightSources");field.setAccessible(true);field.set(chunk,source);
    }
    static void update(VerifyThreadedLighting.Fixture fixture)throws Throwable {
        Method method=ThreadedLevelLightEngine.class.getDeclaredMethod("runUpdate");method.setAccessible(true);
        try{method.invoke(fixture.engine);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    static LevelLightEngine initialize(VerifyThreadedLighting.World world) {
        var level=new LevelLightEngine(world,false,true);
        for(var chunk:world.chunks.values())for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())level.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
        level.runLightUpdates();
        for(var chunk:world.chunks.values()){level.setLightEnabled(chunk.getPos(),true);level.propagateLightSources(chunk.getPos());}
        level.runLightUpdates();return level;
    }
    static void run(String path,String mode)throws Throwable {
        try(var fixture=new VerifyThreadedLighting.Fixture(false,true)) {
            LevelLightEngine level;
            if(path.equals("threaded")){fixture.initialize();level=fixture.engine;}
            else level=initialize(fixture.world);
            SkyLightEngine sky=(SkyLightEngine)level.getLayerListener(LightLayer.SKY);
            var source=new Inherited();replace(fixture.world.chunks.get(0L),source);
            Call drive=path.equals("threaded")?()->update(fixture):path.equals("engine")?()->sky.runLightUpdates():()->level.runLightUpdates();
            if(mode.equals("nested"))source.callback=()->{source.calls.add("nested-start");try{drive.run();}catch(Throwable failure){throw sneaky(failure);}source.calls.add("nested-end");};
            source.reject=mode.equals("checked");
            if(path.equals("engine"))sky.checkBlock(new BlockPos(8,24,8));else level.checkBlock(new BlockPos(8,24,8));
            if(path.equals("threaded"))fixture.transport();
            Throwable failure=null;try{drive.run();}catch(Throwable actual){failure=actual;}
            if(source.calls.isEmpty())throw new AssertionError("Actual inherited Source getter did not run");
            if(mode.equals("checked")&&failure!=MARKER)throw new AssertionError("Original checked Source Throwable identity changed",failure);
            if(mode.equals("nested")&&!source.calls.contains("nested-start"))throw new AssertionError("Reentry callback did not run");
            var data=sky.getDataLayerData(SectionPos.of(0,1,0));int pixel=data==null?-1:data.get(8,8,8);
            System.out.println("SOURCE_INPUT path="+path+" mode="+mode+" same-marker="+(failure==MARKER)+" error="+(failure==null?"none":failure.getClass().getName()+":"+failure.getMessage())+" pending="+sky.hasLightWork()+" pixel="+pixel+" calls="+source.calls);
        }
    }
    public static void main(String[] args)throws Throwable {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String path:List.of("engine","level","threaded"))for(String mode:List.of("value","checked","nested"))run(path,mode);
    }
}
