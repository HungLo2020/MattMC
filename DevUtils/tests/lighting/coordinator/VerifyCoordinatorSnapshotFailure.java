package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/** Actual private metadata-binding failure, not heap exhaustion or a Frozen bug. */
public final class VerifyCoordinatorSnapshotFailure {
    private static final RuntimeException FAILURE=new RuntimeException("original private snapshot metadata failure");
    private static int attempts;
    private static MemorySegment failMetadata(MemorySegment owner){attempts++;throw FAILURE;}
    public static void main(String[] args)throws Exception{
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try(var fixture=new VerifyThreadedLighting.Fixture(false,true)){
            fixture.initialize();var section=SectionPos.of(0,1,0);var old=new ArrayList<DataLayer>();var before=new ArrayList<Integer>();var replacements=new ArrayList<DataLayer>();Object bindings=null;
            for(var channel:LightLayer.values()){
                var layer=fixture.engine.getLayerListener(channel).getDataLayerData(section);old.add(layer);before.add(layer.get(0,0,0));
                Object storage=VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel),"storage");
                var map=(DataLayerStorageMap<?>)VerifyThreadedLighting.field(storage,"updatingSectionData");
                bindings=VerifyThreadedLighting.field(VerifyThreadedLighting.field(map,"nativeMap"),"bindings");
                var replacement=new DataLayer(channel==LightLayer.BLOCK?6:9);replacements.add(replacement);map.setLayer(section.asLong(),replacement);map.clearCache();
                ((LongSet)VerifyThreadedLighting.field(storage,"changedSections")).add(section.asLong());
                ((LongSet)VerifyThreadedLighting.field(storage,"sectionsAffectedByLightUpdates")).add(section.asLong());
            }
            var future=fixture.engine.waitForPendingTasks(0,0);fixture.transport();
            var field=bindings.getClass().getDeclaredField("metadata");field.setAccessible(true);Object original=field.get(bindings);
            MethodHandle fail=MethodHandles.lookup().findStatic(VerifyCoordinatorSnapshotFailure.class,"failMetadata",MethodType.methodType(MemorySegment.class,MemorySegment.class));
            boolean same=false;
            try{
                field.set(bindings,fail);
                try{fixture.update();throw new AssertionError("Missing actual private metadata lookup failure");}
                catch(RuntimeException error){if(error!=FAILURE)throw error;same=true;}
            }finally{field.set(bindings,original);}
            if(attempts!=1)throw new AssertionError("The original private binding lookup did not fail once");
            boolean visibleOld=true,oldStable=true;int i=0;
            for(var channel:LightLayer.values()){
                visibleOld&=fixture.engine.getLayerListener(channel).getDataLayerData(section)==old.get(i);
                oldStable&=old.get(i).get(0,0,0)==before.get(i);i++;
            }
            if(!visibleOld||!oldStable||future.isDone()||fixture.pending()!=1)throw new AssertionError("Failed adoption changed visible roots or POST prefix");
            System.out.println("SNAPSHOT_FAILURE same="+same+" attempts="+attempts+" pending="+fixture.pending()+" done="+future.isDone()+" visible-old="+visibleOld+" old-stable="+oldStable);
            fixture.update();boolean aliases=true;i=0;
            for(var channel:LightLayer.values()){
                aliases&=fixture.engine.getLayerListener(channel).getDataLayerData(section)==replacements.get(i);
                oldStable&=old.get(i).get(0,0,0)==before.get(i);i++;
            }
            if(!aliases||!oldStable||!future.isDone()||fixture.pending()!=0)throw new AssertionError("Original adoption retry or retained snapshot changed");
            System.out.println("SNAPSHOT_FAILURE retry pending="+fixture.pending()+" done="+future.isDone()+" aliases="+aliases+" old-stable="+oldStable);
        }
    }
}
