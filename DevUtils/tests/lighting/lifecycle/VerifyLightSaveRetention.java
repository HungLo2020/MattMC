package net.minecraft.world.level.lighting;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

/** Actual active-column retention and release. Header bytes count requested
 * table payload only; RSS also includes VM, allocator and unrelated runtime. */
public final class VerifyLightSaveRetention {
    private static Object field(Object object, String name) throws Exception {
        for(Class<?> type=object.getClass(); type!=null; type=type.getSuperclass()) {
            try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch(NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static int count(MemorySegment header) {
        return (int)ValueLayout.JAVA_INT.varHandle().getAcquire(header,80L);
    }
    private static void observe(String phase, MemorySegment header) throws Exception {
        long bytes=(long)ValueLayout.JAVA_LONG.varHandle().getAcquire(header,72L);
        String status=Files.readString(Path.of("/proc/self/status"));
        String rss=status.lines().filter(s->s.startsWith("VmRSS:")).findFirst().orElseThrow();
        String swap=status.lines().filter(s->s.startsWith("VmSwap:")).findFirst().orElseThrow();
        System.out.println("RETENTION "+phase+" saved="+count(header)+" requested_table_payload_bytes="+bytes+" "+rss+" "+swap);
    }
    private static WeakReference<DataLayer> retire(LevelLightEngine engine, int x) {
        var target=SectionPos.of(x,1,0); var neighbor=SectionPos.of(x+1,1,0);
        var layer=new DataLayer(9);
        engine.setLightEnabled(new ChunkPos(x,0),true);
        engine.updateSectionStatus(neighbor,false);
        engine.queueSectionData(LightLayer.BLOCK,target,layer); engine.runLightUpdates();
        engine.updateSectionStatus(neighbor,true); engine.runLightUpdates();
        if(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(target)!=null
            ||engine.dataLayerForSave(LightLayer.BLOCK,target)!=layer)throw new AssertionError("Actual save identity lost");
        return new WeakReference<>(layer);
    }
    private static void scene(int columns) throws Exception {
        var source=new LightChunkGetter() {
            public LightChunk getChunkForLighting(int x,int z) { return null; }
            public BlockGetter getLevel() { return EmptyBlockGetter.INSTANCE; }
        };
        var engine=new LevelLightEngine(source,true,false);
        var coordinator=field(field(engine.getLayerListener(LightLayer.BLOCK),"storage"),"coordinator");
        var header=(MemorySegment)field(coordinator,"metadata"); observe("initial",header);
        var layers=new ArrayList<WeakReference<DataLayer>>();
        for(int i=0;i<columns;i++)layers.add(retire(engine,i*4));
        System.gc(); Thread.sleep(100);
        if(count(header)<columns||layers.stream().anyMatch(w->w.get()==null))throw new AssertionError("Active column did not retain its real identity");
        observe("active",header);
        for(int i=0;i<columns;i++)engine.setLightEnabled(new ChunkPos(i*4,0),false);
        if(count(header)!=0)throw new AssertionError("Column retirement retained save entries");
        long deadline=System.nanoTime()+10_000_000_000L;
        while(layers.stream().anyMatch(w->w.get()!=null)&&System.nanoTime()<deadline) { System.gc(); Thread.sleep(50); }
        if(layers.stream().anyMatch(w->w.get()!=null))throw new AssertionError("Retired layers remain rooted");
        observe("disabled",header);
        java.lang.ref.Reference.reachabilityFence(engine);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        long before=NativeLightCoordinator.liveForVerification(); scene(Integer.parseInt(args[0]));
        long deadline=System.nanoTime()+10_000_000_000L;
        while(NativeLightCoordinator.liveForVerification()!=before&&System.nanoTime()<deadline) { System.gc(); Thread.sleep(50); }
        if(NativeLightCoordinator.liveForVerification()!=before)throw new AssertionError("Native owner survived final engine release");
        System.out.println("RETENTION_FINAL native_owner_delta=0");
    }
}
