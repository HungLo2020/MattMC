package net.minecraft.server.level;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;

/** Current-only proof that public enable/source producers use actual typed rows. */
public final class VerifyCoordinatorNativeSources {
    public static void main(String[] args)throws Exception {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(int kind=1;kind<=2;kind++)try(var fixture=new VerifyThreadedLighting.Fixture(false,true)) {
            fixture.initialize();fixture.engine.setLightEnabled(new ChunkPos(0,0),false);fixture.transport();fixture.update();
            ChunkPos position=new ChunkPos(0,0);
            if(kind==1)fixture.engine.setLightEnabled(position,true);else fixture.engine.propagateLightSources(position);
            fixture.transport();Object tasks=VerifyThreadedLighting.field(fixture.engine,"lightTasks");
            Object[] references=(Object[])VerifyThreadedLighting.field(tasks,"references"),contexts=(Object[])VerifyThreadedLighting.field(tasks,"contexts");
            int row=-1;for(int i=0;i<references.length;i++)if(references[i]==position){if(row!=-1)throw new AssertionError("Duplicate capture root");row=i;}
            if(fixture.pending()!=1||row<0||contexts[row]!=fixture.engine||fixture.engine.lightOnInColumn(0L))
                throw new AssertionError("Enable/source did not retain its actual arguments until PRE");
            fixture.update();
            if(!fixture.engine.lightOnInColumn(0L)||fixture.pending()!=0||references[row]!=null||contexts[row]!=null)
                throw new AssertionError("Enable/source did not commit actual state and retire original capture roots at POST");
            System.out.println("NATIVE_SOURCE kind="+kind+" typed=true deferred=true committed=true retired=true");
        }
    }
}
