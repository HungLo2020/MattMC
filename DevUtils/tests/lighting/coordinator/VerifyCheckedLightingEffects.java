package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Observe genuine checked effects; reflection unwraps only its own invocation envelope. */
public final class VerifyCheckedLightingEffects {
    static final IOException MARKER = new IOException("original checked lighting effect");
    @SuppressWarnings("unchecked")
    static <T extends Throwable> void fail() throws T { throw (T)MARKER; }
    static String failure(Throwable actual) {
        return "original=" + (actual == MARKER) + " type=" + (actual == null ? "none" : actual.getClass().getName())
            + " causeOriginal=" + (actual != null && actual.getCause() == MARKER);
    }
    static final class Getter implements LightChunkGetter {
        final VerifyThreadedLighting.World world = new VerifyThreadedLighting.World();
        final List<String> calls = new ArrayList<>();
        boolean rejectLookup, rejectNotify;
        public LightChunk getChunkForLighting(int x, int z) {
            if (rejectLookup) { calls.add("lookup:" + x + ":" + z); VerifyCheckedLightingEffects.<RuntimeException>fail(); }
            return world.getChunkForLighting(x, z);
        }
        public BlockGetter getLevel() { return world; }
        public void onLightUpdate(LightLayer layer, SectionPos section) {
            if (rejectNotify) { calls.add("notify:" + layer + ":" + section.asLong()); VerifyCheckedLightingEffects.<RuntimeException>fail(); }
            world.onLightUpdate(layer, section);
        }
    }
    static LevelLightEngine initialize(Getter source) {
        var level = new LevelLightEngine(source, true, false);
        for (var chunk : source.world.chunks.values()) for (int y = 0; y < 4; y++)
            if (!chunk.getSection(y).hasOnlyAir()) level.updateSectionStatus(SectionPos.of(chunk.getPos(), y), false);
        level.runLightUpdates();
        for (var chunk : source.world.chunks.values()) { level.setLightEnabled(chunk.getPos(), true); level.propagateLightSources(chunk.getPos()); }
        level.runLightUpdates();
        return level;
    }
    static void level(boolean notification) throws Exception {
        var source = new Getter(); var level = initialize(source);
        if (notification) {
            Object storage = VerifyThreadedLighting.field(level.getLayerListener(LightLayer.BLOCK), "storage");
            long key = SectionPos.asLong(0, 1, 0);
            ((LongSet)VerifyThreadedLighting.field(storage, "changedSections")).add(key);
            ((LongSet)VerifyThreadedLighting.field(storage, "sectionsAffectedByLightUpdates")).add(key);
            source.rejectNotify = true;
        } else { level.checkBlock(new BlockPos(8, 24, 8)); source.rejectLookup = true; }
        Throwable actual = null;
        try { level.runLightUpdates(); } catch (Throwable error) { actual = error; }
        if (actual == null || source.calls.isEmpty()) throw new AssertionError("Checked effect was not reached");
        System.out.println("CHECKED_LEVEL " + (notification ? "notify " : "lookup ") + failure(actual)
            + " pending=" + level.hasLightWork() + " prefix=" + source.calls);
        source.rejectLookup = source.rejectNotify = false;
        int count = level.runLightUpdates();
        System.out.println("CHECKED_LEVEL retry " + notification + " count=" + count + " pending=" + level.hasLightWork());
    }
    static Throwable update(ThreadedLevelLightEngine engine) throws Exception {
        var method = ThreadedLevelLightEngine.class.getDeclaredMethod("runUpdate"); method.setAccessible(true);
        try { method.invoke(engine); return null; }
        catch (InvocationTargetException envelope) { return envelope.getCause(); }
    }
    static void requestPost() throws Exception {
        ThreadedRequestFailures.events.clear();
        try (var f = new ThreadedRequestFailures.Fixture()) {
            var chunk = new ThreadedRequestFailures.ProbeChunk();
            chunk.whenCorrect = () -> VerifyCheckedLightingEffects.<RuntimeException>fail();
            var future = f.engine.lightChunk(chunk, true); f.transport();
            Throwable outside = update(f.engine); if (outside != null) throw new AssertionError("POST escaped its future", outside);
            Throwable actual = null;
            try { future.join(); } catch (java.util.concurrent.CompletionException envelope) { actual = envelope.getCause(); }
            if (actual == null) throw new AssertionError("Missing checked POST failure");
            System.out.println("CHECKED_REQUEST post " + failure(actual) + " pending=" + f.pending()
                + " correct=" + chunk.isLightCorrect() + " prefix=" + ThreadedRequestFailures.events);
            chunk.whenCorrect = null; var retry = f.engine.lightChunk(chunk, true); f.transport();
            outside = update(f.engine); if (outside != null) throw new AssertionError("Retry escaped", outside);
            System.out.println("CHECKED_REQUEST post-retry done=" + retry.isDone() + " pending=" + f.pending() + " correct=" + chunk.isLightCorrect());
        }
    }
    static void requestPre() throws Exception {
        ThreadedRequestContracts.events.clear();
        try (var f = new ThreadedRequestContracts.Fixture()) {
            var height = new ThreadedRequestContracts.Height();
            var chunk = ThreadedRequestContracts.chunk(height, ThreadedRequestContracts.sections());
            var future = f.engine.initializeLight(chunk, true); f.transport();
            height.active = true; height.action = () -> VerifyCheckedLightingEffects.<RuntimeException>fail();
            Throwable actual = update(f.engine);
            if (actual == null || height.calls == 0) throw new AssertionError("Missing checked PRE height effect");
            System.out.println("CHECKED_REQUEST pre " + failure(actual) + " done=" + future.isDone() + " pending=" + f.pending()
                + " prefix=" + ThreadedRequestContracts.events);
            height.active = false; height.action = null;
            Throwable outside = update(f.engine); if (outside != null) throw new AssertionError("PRE retry failed", outside);
            System.out.println("CHECKED_REQUEST pre-retry done=" + future.isDone() + " pending=" + f.pending());
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); VerifyThreadedLighting.pinClasses();
        switch (args[0]) {
            case "level-lookup" -> level(false);
            case "level-notify" -> level(true);
            case "request-post" -> requestPost();
            case "request-pre" -> requestPre();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
