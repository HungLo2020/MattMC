package net.minecraft.world.level.lighting;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** A genuine custom source may throw a checked object through an unchecked signature. */
public final class VerifyLiveSkyCheckedFailure {
    static final IOException MARKER=new IOException("original custom sky failure");
    @SuppressWarnings("unchecked")
    static <T extends Throwable> int fail() throws T {throw (T)MARKER;}
    static final class Sources extends ChunkSkyLightSources {
        Sources() {super(LevelHeightAccessor.create(0,64));}
        @Override public int getLowestSourceY(int x,int z) {return VerifyLiveSkyCheckedFailure.<RuntimeException>fail();}
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        Field output=VerifyLightCoordinator.class.getDeclaredField("out");output.setAccessible(true);
        try(var quiet=new PrintWriter(OutputStream.nullOutputStream())) {
            output.set(null,quiet);Class<?> type=Class.forName(VerifyLightCoordinator.class.getName()+"$Fixture");
            var constructor=type.getDeclaredConstructor(boolean.class);constructor.setAccessible(true);
            Object fixture=constructor.newInstance(true),world=VerifyLiveSkySourceContracts.field(fixture,"world");
            var chunks=(Map<Long,ChunkAccess>)VerifyLiveSkySourceContracts.field(world,"chunks");
            Field source=ChunkAccess.class.getDeclaredField("skyLightSources");source.setAccessible(true);
            source.set(chunks.get(ChunkPos.asLong(0,0)),new Sources());
            var storage=new SkyLightSectionStorage((LightChunkGetter)world) {
                @Override protected int getTopSectionY(long column) {return 4;}
                @Override protected int getBottomSectionY() {return 3;}
            };
            var layer=new DataLayer(0);storage.updatingSectionData.setLayer(SectionPos.asLong(0,3,0),layer);
            storage.updatingSectionData.clearCache();var engine=new SkyLightEngine((LightChunkGetter)world,storage);
            Throwable actual=null;
            try {engine.propagateLightSources(new ChunkPos(0,0));}catch(Throwable failure) {actual=failure;}
            var queue=VerifyLiveSkySourceContracts.queue(engine);
            System.out.println("CHECKED_SKY_FAILURE original="+(actual==MARKER)+" type="+(actual==null?"none":actual.getClass().getName())+" words="+queue.size()+" pixel="+layer.get(0,15,0));
            if(actual!=MARKER||!queue.isEmpty()||layer.get(0,15,0)!=0)throw new AssertionError("Original checked source failure was replaced",actual);
            java.lang.ref.Reference.reachabilityFence(fixture);java.lang.ref.Reference.reachabilityFence(engine);
        }
    }
}
