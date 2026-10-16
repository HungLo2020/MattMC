package net.minecraft.world.level.chunk.status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Same-source publication contracts. Real constructor/entity/IO effects are
 * separately covered by the server probe; hooks here are controlled leaves. */
class ChunkPublicationContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final class ObservedProto extends ProtoChunk {
        final List<String> trace = new ArrayList<>();
        RuntimeException dirtyFailure;
        ObservedProto() {
            super(new ChunkPos(0, 0), UpgradeData.EMPTY, new LevelChunkSection[]{mock(LevelChunkSection.class)},
                new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), LevelHeightAccessor.create(0, 16), mock(PalettedContainerFactory.class), null);
        }
        @Override public void markUnsaved() {
            trace.add("dirty:" + getPersistedStatus());
            if (dirtyFailure != null) throw dirtyFailure;
            super.markUnsaved();
        }
        @Override public void setBelowZeroRetrogen(BelowZeroRetrogen retrogen) {
            trace.add("retrogen:" + getPersistedStatus() + ":" + (retrogen == null));
            super.setBelowZeroRetrogen(retrogen);
        }
    }

    static WorldGenContext context(Executor executor) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        return new WorldGenContext(level, null, null, null, executor, mock(LevelChunk.UnsavedListener.class));
    }

    static ChunkStep step(ChunkStatus target, ChunkStatusTask task) {
        var original = ChunkPyramid.LOADING_PYRAMID.getStepTo(target);
        return new ChunkStep(target, original.directDependencies(), original.accumulatedDependencies(), original.blockStateWriteRadius(), task);
    }

    @Test void persistedStatusCommitsBeforeDirtyFailure() {
        ObservedProto chunk = new ObservedProto();
        assertSame(ChunkStatus.EMPTY, chunk.getPersistedStatus());
        RuntimeException failure = new RuntimeException("dirty");
        chunk.dirtyFailure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, () -> chunk.setPersistedStatus(ChunkStatus.CARVERS)));
        assertSame(ChunkStatus.CARVERS, chunk.getPersistedStatus());
        assertEquals(List.of("dirty:minecraft:carvers"), chunk.trace);
        assertFalse(chunk.isUnsaved());
    }

    @Test void retrogenClearSeesNewStatusAndPrecedesDirtyNotification() {
        ObservedProto chunk = new ObservedProto();
        BelowZeroRetrogen retrogen = mock(BelowZeroRetrogen.class);
        when(retrogen.targetStatus()).thenReturn(ChunkStatus.NOISE);
        chunk.setBelowZeroRetrogen(retrogen);
        chunk.trace.clear();
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        assertEquals(List.of("retrogen:minecraft:carvers:true", "dirty:minecraft:carvers"), chunk.trace);
        assertNull(chunk.getBelowZeroRetrogen());
        assertTrue(chunk.isUnsaved());
    }

    @Test void nullStatusRetainsTheOriginalAssignedPrefix() {
        ObservedProto chunk = new ObservedProto();
        chunk.setPersistedStatus(null);
        assertNull(chunk.getPersistedStatus());
        assertEquals(List.of("dirty:null"), chunk.trace);
        BelowZeroRetrogen retrogen = mock(BelowZeroRetrogen.class);
        chunk.setBelowZeroRetrogen(retrogen);
        chunk.trace.clear();
        assertThrows(NullPointerException.class, () -> chunk.setPersistedStatus(null));
        assertNull(chunk.getPersistedStatus());
        assertSame(retrogen, chunk.getBelowZeroRetrogen());
        assertTrue(chunk.trace.isEmpty());
    }

    @Test void foreignStatusAssignmentRetainsIdentityWithoutQueryingItsIndex() {
        ObservedProto chunk = new ObservedProto();
        ChunkStatus foreign = new ChunkStatus(ChunkStatus.CARVERS,
                java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class), ChunkType.PROTOCHUNK) {
            @Override public int getIndex() { throw new AssertionError("assignment must not query a foreign status index"); }
            @Override public String getName() { return "test:foreign"; }
        };
        chunk.setPersistedStatus(foreign);
        assertSame(foreign, chunk.getPersistedStatus());
        assertEquals(List.of("dirty:test:foreign"), chunk.trace);
        assertTrue(chunk.isUnsaved());
    }

    @Test void foreignStatusStoreCommitsBeforeThrowingVirtualRetrogenComparison() {
        ObservedProto chunk = new ObservedProto();
        RuntimeException failure = new RuntimeException("foreign retrogen comparison");
        ChunkStatus foreign = new ChunkStatus(ChunkStatus.CARVERS,
                java.util.EnumSet.noneOf(net.minecraft.world.level.levelgen.Heightmap.Types.class), ChunkType.PROTOCHUNK) {
            @Override public String getName() { return "test:foreign_retrogen"; }
            @Override public boolean isOrAfter(ChunkStatus other) {
                assertSame(this, chunk.getPersistedStatus(), "store precedes the foreign virtual comparison");
                throw failure;
            }
        };
        BelowZeroRetrogen retrogen = mock(BelowZeroRetrogen.class);
        chunk.setBelowZeroRetrogen(retrogen);
        chunk.trace.clear();
        assertSame(failure, assertThrows(RuntimeException.class, () -> chunk.setPersistedStatus(foreign)));
        assertSame(foreign, chunk.getPersistedStatus());
        assertSame(retrogen, chunk.getBelowZeroRetrogen());
        assertTrue(chunk.trace.isEmpty());
        assertFalse(chunk.isUnsaved());
    }

    @Test void incompleteStagePublishesOnlyAfterWorkCompletes() {
        ObservedProto chunk = new ObservedProto();
        CompletableFuture<ChunkAccess> work = new CompletableFuture<>();
        var result = step(ChunkStatus.BIOMES, (context, step, cache, input) -> work).apply(context(Runnable::run), null, chunk);
        assertNotSame(work, result);
        assertFalse(result.isDone());
        assertSame(ChunkStatus.EMPTY, chunk.getPersistedStatus());
        work.complete(chunk);
        assertSame(chunk, result.join());
        assertSame(ChunkStatus.BIOMES, chunk.getPersistedStatus());
        assertEquals(List.of("dirty:minecraft:biomes"), chunk.trace);
    }

    @Test void failedStageDoesNotPublishStatus() {
        ObservedProto chunk = new ObservedProto();
        RuntimeException failure = new RuntimeException("work");
        var result = step(ChunkStatus.BIOMES, (context, step, cache, input) -> CompletableFuture.failedFuture(failure))
            .apply(context(Runnable::run), null, chunk);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertSame(ChunkStatus.EMPTY, chunk.getPersistedStatus());
        assertTrue(chunk.trace.isEmpty());
    }

    @Test void failedStatusEffectKeepsStatusButFailsTheStageFuture() {
        ObservedProto chunk = new ObservedProto();
        RuntimeException failure = new RuntimeException("status dirty effect");
        chunk.dirtyFailure = failure;
        var result = step(ChunkStatus.BIOMES, (context, step, cache, input) -> CompletableFuture.completedFuture(chunk))
            .apply(context(Runnable::run), null, chunk);
        assertTrue(result.isCompletedExceptionally());
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertSame(ChunkStatus.BIOMES, chunk.getPersistedStatus());
    }

    @Test void alreadyGeneratedInputRetainsTheExactWorkFuture() {
        ObservedProto chunk = new ObservedProto();
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        chunk.trace.clear();
        CompletableFuture<ChunkAccess> work = new CompletableFuture<>();
        var result = step(ChunkStatus.BIOMES, (context, step, cache, input) -> work).apply(context(Runnable::run), null, chunk);
        assertSame(work, result);
        work.complete(chunk);
        assertSame(chunk, result.join());
        assertTrue(chunk.trace.isEmpty());
    }

    static final class FullFixture {
        final ImposterProtoChunk input = mock(ImposterProtoChunk.class);
        final LevelChunk output = mock(LevelChunk.class);
        final GenerationChunkHolder holder = mock(GenerationChunkHolder.class);
        final StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(0, 0, 0, (x, z) -> holder);
        final List<String> trace = new ArrayList<>();
        String failAt;
        RuntimeException failure;
        Runnable afterPostLoad = () -> {};
        FullFixture() {
            when(input.getPos()).thenReturn(new ChunkPos(0, 0));
            when(input.getWrapped()).thenReturn(output);
            doAnswer(call -> { effect("status"); return null; }).when(output).setFullStatus(any());
            doAnswer(call -> { effect("post"); afterPostLoad.run(); return null; }).when(output).runPostLoad();
            doAnswer(call -> { assertEquals(Boolean.TRUE, call.getArgument(0)); effect("loaded"); return null; }).when(output).setLoaded(anyBoolean());
            doAnswer(call -> { effect("entities"); return null; }).when(output).registerAllBlockEntitiesAfterLevelLoad();
            doAnswer(call -> { effect("ticks"); return null; }).when(output).registerTickContainerInLevel(any());
            doAnswer(call -> { effect("listener"); return null; }).when(output).setUnsavedListener(any());
        }
        void effect(String name) { trace.add(name); if (name.equals(failAt)) throw failure; }
        CompletableFuture<ChunkAccess> run(Executor executor) {
            return ChunkStatusTasks.full(context(executor), ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.FULL), cache, input);
        }
    }

    @Test void loadedFullPublicationRunsTheExactEngineEffectOrder() {
        FullFixture fixture = new FullFixture();
        assertSame(fixture.output, fixture.run(Runnable::run).join());
        assertEquals(List.of("status", "post", "loaded", "entities", "ticks", "listener"), fixture.trace);
        verify(fixture.holder, never()).replaceProtoChunk(any());
    }

    @Test void everyFullHookFailureKeepsItsPrefixAndNeverSucceeds() {
        List<String> order = List.of("status", "post", "loaded", "entities", "ticks", "listener");
        for (int index = 0; index < order.size(); index++) {
            FullFixture fixture = new FullFixture();
            fixture.failAt = order.get(index);
            fixture.failure = new RuntimeException("hook:" + fixture.failAt);
            var result = fixture.run(Runnable::run);
            assertTrue(result.isCompletedExceptionally());
            assertSame(fixture.failure, assertThrows(CompletionException.class, result::join).getCause());
            assertEquals(order.subList(0, index + 1), fixture.trace);
        }
    }

    @Test void queuedExecutorKeepsPublicationPendingUntilActualExecution() {
        FullFixture fixture = new FullFixture();
        List<Runnable> queued = new ArrayList<>();
        var result = fixture.run(queued::add);
        assertFalse(result.isDone());
        assertTrue(fixture.trace.isEmpty());
        assertEquals(1, queued.size());
        queued.removeFirst().run();
        assertSame(fixture.output, result.join());
        assertEquals(List.of("status", "post", "loaded", "entities", "ticks", "listener"), fixture.trace);
    }

    @Test void executorRejectionThrowsSynchronouslyBeforePublicationEffects() {
        FullFixture fixture = new FullFixture();
        RejectedExecutionException failure = new RejectedExecutionException("executor");
        assertSame(failure, assertThrows(RejectedExecutionException.class, () -> fixture.run(task -> { throw failure; })));
        assertTrue(fixture.trace.isEmpty());
    }

    @Test void postLoadCanReenterPublicationWithoutLosingEitherPrefix() {
        FullFixture fixture = new FullFixture();
        boolean[] entered = {false};
        fixture.afterPostLoad = () -> {
            if (!entered[0]) {
                entered[0] = true;
                assertSame(fixture.output, fixture.run(Runnable::run).join());
            }
        };
        assertSame(fixture.output, fixture.run(Runnable::run).join());
        assertEquals(List.of("status", "post", "status", "post", "loaded", "entities", "ticks", "listener", "loaded", "entities", "ticks", "listener"), fixture.trace);
    }

    /** Constructor interception controls engine leaves, while the actual stock
     * full method chooses the fresh path and sequences publication. Real
     * construction and post-load entity work remain server-probe obligations. */
    static final class FreshFullFixture {
        final ObservedProto input = new ObservedProto();
        final GenerationChunkHolder holder = mock(GenerationChunkHolder.class);
        final StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(0, 0, 0, (x, z) -> holder);
        final WorldGenContext context = context(Runnable::run);
        final List<String> trace = new ArrayList<>();
        final RuntimeException failure = new RuntimeException("fresh publication effect");
        String failAt;
        LevelChunk output;
        ImposterProtoChunk replacement;
        CompletableFuture<ChunkAccess> result;

        void effect(String name) { trace.add(name); if (name.equals(failAt)) throw failure; }

        void run() {
            doAnswer(call -> {
                assertSame(replacement, call.getArgument(0));
                effect("replace");
                return null;
            }).when(holder).replaceProtoChunk(any());
            try (var constructors = mockConstruction(LevelChunk.class, (chunk, construction) -> {
                assertSame(context.level(), construction.arguments().get(0));
                assertSame(input, construction.arguments().get(1));
                assertNotNull(construction.arguments().get(2), "original post-load callback must be retained");
                output = chunk;
                effect("construct");
                doAnswer(call -> { effect("status"); return null; }).when(chunk).setFullStatus(any());
                doAnswer(call -> { effect("post"); return null; }).when(chunk).runPostLoad();
                doAnswer(call -> { assertEquals(Boolean.TRUE, call.getArgument(0)); effect("loaded"); return null; }).when(chunk).setLoaded(anyBoolean());
                doAnswer(call -> { effect("entities"); return null; }).when(chunk).registerAllBlockEntitiesAfterLevelLoad();
                doAnswer(call -> { assertSame(context.level(), call.getArgument(0)); effect("ticks"); return null; }).when(chunk).registerTickContainerInLevel(any());
                doAnswer(call -> { assertSame(context.unsavedListener(), call.getArgument(0)); effect("listener"); return null; }).when(chunk).setUnsavedListener(any());
            }); var imposters = mockConstruction(ImposterProtoChunk.class, (imposter, construction) -> {
                assertSame(output, construction.arguments().get(0));
                assertEquals(Boolean.FALSE, construction.arguments().get(1));
                replacement = imposter;
                effect("imposter");
            })) {
                result = ChunkStatusTasks.full(context, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL), cache, input);
                assertEquals(1, constructors.constructed().size());
                assertEquals(1, imposters.constructed().size());
            }
        }
    }

    @Test void freshFullConstructsAndReplacesBeforePublishingEngineEffects() {
        FreshFullFixture fixture = new FreshFullFixture();
        fixture.run();
        assertSame(fixture.output, fixture.result.join());
        assertEquals(List.of("construct", "imposter", "replace", "status", "post", "loaded", "entities", "ticks", "listener"), fixture.trace);
    }

    @Test void freshReplacementFailurePreventsEveryLaterFullEffect() {
        FreshFullFixture fixture = new FreshFullFixture();
        fixture.failAt = "replace";
        fixture.run();
        assertSame(fixture.failure, assertThrows(CompletionException.class, fixture.result::join).getCause());
        assertEquals(List.of("construct", "imposter", "replace"), fixture.trace);
    }

    @Test void freshPostLoadFailureKeepsReplacementButNeverSucceeds() {
        FreshFullFixture fixture = new FreshFullFixture();
        fixture.failAt = "post";
        fixture.run();
        assertSame(fixture.failure, assertThrows(CompletionException.class, fixture.result::join).getCause());
        assertEquals(List.of("construct", "imposter", "replace", "status", "post"), fixture.trace);
        verify(fixture.holder).replaceProtoChunk(fixture.replacement);
    }
}
