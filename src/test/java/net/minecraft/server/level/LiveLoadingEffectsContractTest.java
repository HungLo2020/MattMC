package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises live manager queries against the independent original graph. */
class LiveLoadingEffectsContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final class Manager extends DistanceManager {
        final List<String> effects = new ArrayList<>();
        final ChunkHolder holder = org.mockito.Mockito.mock(ChunkHolder.class);
        int level = ChunkLevel.MAX_LEVEL + 1;
        boolean removed;
        Runnable queryEffect;
        Manager(TicketStorage storage) {
            super(storage, Runnable::run, Runnable::run);
            org.mockito.Mockito.when(holder.getTicketLevel()).thenAnswer(call -> {
                effects.add("holder:" + level);
                return level;
            });
        }
        protected boolean isChunkToRemove(long position) {
            effects.add("removed:" + position + ":" + removed);
            return removed;
        }
        protected ChunkHolder getChunk(long position) {
            effects.add("chunk:" + position);
            Runnable effect = queryEffect;
            queryEffect = null;
            if (effect != null) effect.run();
            return position == 0 ? holder : null;
        }
        protected ChunkHolder updateChunkScheduling(long position, int next, ChunkHolder current, int previous) {
            effects.add("schedule:" + position + ":" + previous + ":" + next);
            if (position == 0) level = next;
            return current;
        }
    }

    record Trace(List<String> effects, int remaining, int level, boolean identity) {}

    static Trace run(boolean nativeGraph, boolean removed, boolean failing, boolean reentry) {
        TicketStorage storage = new TicketStorage();
        Manager manager = new Manager(storage);
        LoadingChunkTracker candidate = nativeGraph ? new LoadingChunkTracker(manager, storage) : null;
        JavaLoadingChunkTracker original = nativeGraph ? null : new JavaLoadingChunkTracker(manager, storage);
        java.util.function.IntSupplier run = () -> nativeGraph ? candidate.runDistanceUpdates(5) : original.runDistanceUpdates(5);
        java.util.function.IntConsumer update = level -> {
            if (nativeGraph) candidate.update(0, level, false); else original.update(0, level, false);
        };
        manager.level = 32; // Holder state changed independently of graph notifications.
        manager.removed = removed;
        IllegalStateException marker = new IllegalStateException("manager query");
        manager.effects.clear();
        if (failing || reentry) manager.queryEffect = () -> {
            manager.effects.add(failing ? "throw" : "nested");
            if (failing) throw marker;
            if (nativeGraph) candidate.update(ChunkPos.asLong(2, 1), 34, true);
            else original.update(ChunkPos.asLong(2, 1), 34, true);
            manager.effects.add("nested-return");
        };
        Throwable failure = null;
        int remaining = -1;
        try { update.accept(34); remaining = run.getAsInt(); }
        catch (Throwable error) { failure = error; }
        manager.effects.add("retry");
        remaining += run.getAsInt();
        return new Trace(List.copyOf(manager.effects), remaining, manager.level, failure == marker);
    }

    @Test void liveHolderLevelsAndRemovalQueriesMatchOriginal() {
        assertEquals(run(false, false, false, false), run(true, false, false, false));
        assertEquals(run(false, true, false, false), run(true, true, false, false));
    }
    @Test void actualManagerFailureKeepsIdentityPrefixAndRetry() {
        Trace expected = run(false, false, true, false);
        assertTrue(expected.identity());
        assertEquals(expected, run(true, false, true, false));
    }
    @Test void managerQueryMayReenterTheSameGraph() {
        assertEquals(run(false, false, false, true), run(true, false, false, true));
    }
}
