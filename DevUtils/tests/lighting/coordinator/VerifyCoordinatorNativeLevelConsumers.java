package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;

/** Current-only actual native parent projection and task update consumer. */
public final class VerifyCoordinatorNativeLevelConsumers {
    public static void main(String[] args)throws Exception{
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        try(var fixture=new VerifyThreadedLighting.Fixture(false,true)){
            fixture.initialize();Object level=VerifyThreadedLighting.field(fixture.engine,"nativeLevel");
            MemorySegment projection=(MemorySegment)VerifyThreadedLighting.field(level,"projection");
            if(projection.get(ValueLayout.JAVA_INT,0)!=12)throw new AssertionError("Actual idle level projection is not published");
            var old=ThreadedLevelLightEngine.class.getDeclaredField("propagation");old.setAccessible(true);
            boolean[] called={false};old.set(fixture.engine,(Runnable)()->{called[0]=true;throw new AssertionError("Old Java level orchestration was invoked");});
            boolean[] observed={false};fixture.engine.checkBlock(new BlockPos(8,24,8));
            fixture.add(false,()->{
                if((projection.get(ValueLayout.JAVA_INT,0)&3)!=3||!fixture.engine.hasLightWork())throw new AssertionError("Native PRE check did not publish both actual level work bits");
                observed[0]=true;
            });fixture.transport();
            if(projection.get(ValueLayout.JAVA_INT,0)!=12||fixture.engine.hasLightWork())throw new AssertionError("Native work committed before PRE");
            fixture.update();
            if(!observed[0]||called[0]||fixture.pending()!=0||fixture.engine.hasLightWork()||projection.get(ValueLayout.JAVA_INT,0)!=12)
                throw new AssertionError("Actual native level did not complete its task pass");
            for(var channel:LightLayer.values()){
                Object storage=VerifyThreadedLighting.field(fixture.engine.getLayerListener(channel),"storage");
                ((LongSet)VerifyThreadedLighting.field(storage,"changedSections")).add(SectionPos.asLong(0,1,0));
                ((LongSet)VerifyThreadedLighting.field(storage,"sectionsAffectedByLightUpdates")).add(SectionPos.asLong(0,1,0));
            }
            if((projection.get(ValueLayout.JAVA_INT,0)&3)!=0||(projection.get(ValueLayout.JAVA_INT,0)&12)!=0)
                throw new AssertionError("Changed-only publication was confused with public pending work");
            fixture.update();
            if(called[0]||projection.get(ValueLayout.JAVA_INT,0)!=12)throw new AssertionError("Native pass skipped required publication or used old orchestration");
            System.out.println("NATIVE_LEVEL actual-projection=true native-update-consumer=true changed-only-publication=true legacy-runnable-unused=true");
        }
    }
}
