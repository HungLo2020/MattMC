package net.minecraft.server.level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.objenesis.ObjenesisStd;

/** Unchanged portable observer of actual producer submission, callback and reentry failures. */
public final class ThreadedRequestFailures {
    static final List<String> events = new ArrayList<>();

    static final class MapBoundary extends ChunkMap {
        int calls, failAt;
        private MapBoundary() { super(null,null,null,null,null,null,null,null,null,null,null,0,false); }
        @Override protected IntSupplier getChunkQueueLevel(long key) {
            events.add("priority " + (++calls));
            if (calls == failAt) throw new RejectedExecutionException("request priority");
            return () -> 0;
        }
    }

    static final class Fixture implements AutoCloseable {
        final VerifyThreadedLighting.Tasks dispatch = new VerifyThreadedLighting.Tasks(), light = new VerifyThreadedLighting.Tasks();
        final VerifyThreadedLighting.LightExecutor executor = new VerifyThreadedLighting.LightExecutor(light);
        final ChunkTaskDispatcher dispatcher = new ChunkTaskDispatcher(executor, dispatch);
        final MapBoundary map = new ObjenesisStd().newInstance(MapBoundary.class);
        final ThreadedLevelLightEngine engine = new ThreadedLevelLightEngine(new ThreadedRequestContracts.World(), map, false, executor, dispatcher);
        void transport() {
            for (int i=0; i<10000; i++) {
                boolean ran=dispatch.step(); ran|=light.step();
                if (!ran) return;
            }
            throw new AssertionError("transport did not settle");
        }
        void update() throws Exception { VerifyThreadedLighting.invoke(engine,"runUpdate",new Class<?>[0]); }
        int pending() throws Exception {
            Object tasks=VerifyThreadedLighting.field(engine,"lightTasks");
            return tasks instanceof java.util.Collection<?> c ? c.size() : ((Number)VerifyThreadedLighting.invoke(tasks,"pending",new Class<?>[0])).intValue();
        }
        public void close() { engine.close(); dispatcher.close(); executor.close(); }
    }

    static final class ProbeChunk extends ProtoChunk {
        Runnable whenCorrect;
        ProbeChunk() {
            super(new ChunkPos(0,0),UpgradeData.EMPTY,ThreadedRequestContracts.sections(),
                new net.minecraft.world.ticks.ProtoChunkTicks<>(),new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                new ThreadedRequestContracts.Height(),null,null);
        }
        @Override public void setLightCorrect(boolean value) {
            super.setLightCorrect(value);
            events.add("correct " + value);
            if (value && whenCorrect != null) whenCorrect.run();
        }
    }

    static void rejectedSubmission(boolean initialize, int failAt) throws Exception {
        events.add("submission " + initialize + " " + failAt);
        try (var f=new Fixture()) {
            var chunk=new ProbeChunk(); f.map.failAt=failAt;
            try {
                if (initialize) f.engine.initializeLight(chunk,true); else f.engine.lightChunk(chunk,true);
                throw new AssertionError("missing rejection");
            } catch (RejectedExecutionException expected) { events.add("rejected " + expected.getMessage()); }
            f.transport(); events.add("pending " + f.pending()); f.update();
            events.add("after " + f.pending() + " " + chunk.isLightCorrect());
            if (f.pending()!=0 || chunk.isLightCorrect()) throw new AssertionError("failed submission committed POST");
        }
    }

    static void throwingPost() throws Exception {
        events.add("throwing POST");
        try (var f=new Fixture()) {
            var chunk=new ProbeChunk();
            chunk.whenCorrect=()->{events.add("callback throw");throw new IllegalStateException("POST probe");};
            var failed=f.engine.lightChunk(chunk,true); f.transport(); f.update();
            events.add("future " + failed.isCompletedExceptionally() + " " + f.pending() + " " + chunk.isLightCorrect());
            try { failed.join(); throw new AssertionError("missing exceptional completion"); }
            catch (java.util.concurrent.CompletionException expected) { events.add("cause " + expected.getCause().getClass().getSimpleName() + " " + expected.getCause().getMessage()); }
            chunk.whenCorrect=null;
            var retry=f.engine.lightChunk(chunk,true); f.transport(); f.update();
            events.add("retry " + retry.isDone() + " " + f.pending() + " " + chunk.isLightCorrect());
        }
    }

    static void reenteredPost() throws Exception {
        events.add("reentered POST");
        try (var f=new Fixture()) {
            var chunk=new ProbeChunk();
            var nested=new ArrayList<CompletableFuture<ChunkAccess>>();
            chunk.whenCorrect=()->{
                chunk.whenCorrect=null;
                events.add("nested submit");
                var future=f.engine.lightChunk(chunk,true);nested.add(future);
                future.thenRun(()->events.add("nested complete " + chunk.isLightCorrect()));
            };
            var outer=f.engine.lightChunk(chunk,true);outer.thenRun(()->events.add("outer complete " + chunk.isLightCorrect()));
            f.transport();f.update();events.add("first " + outer.isDone() + " " + nested.getFirst().isDone() + " " + f.pending());
            f.transport();f.update();events.add("second " + outer.isDone() + " " + nested.getFirst().isDone() + " " + f.pending());
            if (!outer.isDone() || !nested.getFirst().isDone() || f.pending()!=0) throw new AssertionError("reentered request unfinished");
        }
    }

    static void nullSectionPrefix() throws Exception {
        events.add("null section");
        try (var f=new Fixture()) {
            var chunk=new ProbeChunk();chunk.getSections()[1]=null;
            var future=f.engine.initializeLight(chunk,true);f.transport();
            try {f.update();throw new AssertionError("missing section failure");}
            catch (NullPointerException expected) {events.add("NPE " + expected.getMessage());}
            events.add("after failure " + future.isDone() + " " + f.pending());
            chunk.getSections()[1]=ThreadedRequestContracts.sections()[1];f.update();
            events.add("after retry " + future.isDone() + " " + f.pending());
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();VerifyThreadedLighting.pinClasses();
        for (boolean initialize : new boolean[]{true,false}) for (int failAt : new int[]{1,2}) rejectedSubmission(initialize,failAt);
        throwingPost();reenteredPost();nullSectionPrefix();
        Files.write(Path.of(args[0]),events);
        System.out.println("REQUEST_FAILURE_CONTRACTS 7 " + events.size());
    }
}
