package net.minecraft.server.level;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.material.FluidState;
import org.objenesis.ObjenesisStd;

/** A genuine live-height callback observes retirement after the original null import. */
public final class ThreadedUnloadCustody {
    static final class World implements LightChunkGetter, BlockGetter {
        Runnable callback;
        int heightReads;
        public LightChunk getChunkForLighting(int x,int z) { return null; }
        public BlockGetter getLevel() { return this; }
        public BlockState getBlockState(BlockPos position) { return Blocks.AIR.defaultBlockState(); }
        public FluidState getFluidState(BlockPos position) { return Blocks.AIR.defaultBlockState().getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos position) { return null; }
        public int getMinY() { return 0; }
        public int getHeight() {
            if (callback!=null && ++heightReads==2) callback.run();
            return 64;
        }
    }

    @SuppressWarnings("unchecked")
    static WeakReference<DataLayer> queueOnly(ThreadedLevelLightEngine engine) throws Exception {
        Object listener=engine.getLayerListener(LightLayer.BLOCK);
        Object storage=VerifyThreadedLighting.field(listener,"storage");
        var queued=(Map<Long,DataLayer>)VerifyThreadedLighting.field(storage,"queuedSections");
        var layer=new DataLayer(7);
        synchronized (queued) { queued.put(SectionPos.asLong(0,-1,0),layer); }
        return new WeakReference<>(layer);
    }

    static boolean collected(WeakReference<?> weak) {
        for (int i=0;i<200;i++) {
            System.gc();
            if (weak.get()==null) return true;
            try { Thread.sleep(5); }
            catch (InterruptedException failure) { throw new RuntimeException(failure); }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();VerifyThreadedLighting.pinClasses();
        var events=new ArrayList<String>();
        var world=new World();
        var dispatch=new VerifyThreadedLighting.Tasks();var light=new VerifyThreadedLighting.Tasks();
        try (var executor=new VerifyThreadedLighting.LightExecutor(light);
             var dispatcher=new ChunkTaskDispatcher(executor,dispatch);
             var engine=new ThreadedLevelLightEngine(world,new ObjenesisStd().newInstance(VerifyThreadedLighting.MapBoundary.class),false,executor,dispatcher)) {
            var weak=queueOnly(engine);
            System.gc();if (weak.get()==null) throw new AssertionError("actual queued map lost its entry");
            world.callback=()->{
                boolean gone=collected(weak);events.add("next live height released="+gone);
                if (!gone) throw new AssertionError("queued VM root survived null import until next live height effect");
            };
            engine.updateChunkStatus(new ChunkPos(0,0));
            for (int i=0;i<10000;i++) {boolean ran=dispatch.step();ran|=light.step();if (!ran) break;}
            VerifyThreadedLighting.invoke(engine,"runUpdate",new Class<?>[0]);
            if (events.size()!=1) throw new AssertionError("live height boundary was not observed");
            if (!collected(weak)) throw new AssertionError("unload retains queued layer");
            java.lang.ref.Reference.reachabilityFence(engine);
        }
        Files.write(Path.of(args[0]),events);
        System.out.println("UNLOAD_CUSTODY_CONTRACT 1");
    }
}
