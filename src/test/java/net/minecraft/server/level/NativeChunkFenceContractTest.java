package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Actual generation-fence producers through send/save native aggregation. */
@Isolated
class NativeChunkFenceContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static CompletableFuture<?> generationFence() {
        var holder = new GenerationChunkHolderContractTest.Holder(0, 0);
        holder.increaseGenerationRefCount();
        return holder.saves.getFirst();
    }
    @Test void actualGenerationFenceCombinationsCreateNativeDependencyOwners() {
        for (boolean save : new boolean[]{false, true}) {
            var route = ChunkSyncDependencyContractTest.route(save);
            CompletableFuture<?> first = generationFence();
            var second = new CompletableFuture<Void>();
            long created = NativeChunkGeneration.rangeStatistics()[1];
            route.add().accept(first);
            assertSame(first, route.get().get());
            route.add().accept(second);
            var combined = route.get().get();
            assertEquals(created + 1, NativeChunkGeneration.rangeStatistics()[1]);
            first.complete(null);
            assertFalse(combined.isDone());
            second.complete(null);
            assertNull(combined.join());
        }
    }
    @Test void nativeFenceObservesRecoveryBeforeItsPairPublishes() {
        for (boolean save : new boolean[]{false, true}) {
            var route = ChunkSyncDependencyContractTest.route(save);
            CompletableFuture<?> first = generationFence();
            var second = new CompletableFuture<Void>();
            route.add().accept(first); route.add().accept(second);
            var combined = route.get().get();
            first.completeExceptionally(new RuntimeException("recovered"));
            first.obtrudeValue(null);
            second.complete(null);
            assertNull(combined.join());
        }
    }
    @Test void publishedNativeInnerErrorSurvivesRecoveryThroughANewFence() {
        for (boolean save : new boolean[]{false, true}) {
            var route = ChunkSyncDependencyContractTest.route(save);
            CompletableFuture<?> first = generationFence();
            var second = new CompletableFuture<Void>(); var third = new CompletableFuture<Void>();
            long created = NativeChunkGeneration.rangeStatistics()[1];
            route.add().accept(first); route.add().accept(second);
            var inner = route.get().get();
            route.add().accept(third);
            var outer = route.get().get();
            assertEquals(created + 2, NativeChunkGeneration.rangeStatistics()[1]);
            RuntimeException failure = new RuntimeException("published inner");
            first.completeExceptionally(failure); second.complete(null);
            assertSame(failure, assertThrows(CompletionException.class, inner::join).getCause());
            first.obtrudeValue(null); third.complete(null);
            assertSame(failure, assertThrows(CompletionException.class, outer::join).getCause());
        }
    }
    @Test void nativeFenceCallsActualStageConversionAndRetainsItsReturnedFuture() {
        var route = ChunkSyncDependencyContractTest.route(true);
        CompletableFuture<?> first = generationFence();
        var actual = new CompletableFuture<Void>(); int[] calls = {0};
        CompletableFuture<Void> supplied = new CompletableFuture<>() {
            @Override public CompletableFuture<Void> toCompletableFuture() { calls[0]++; return actual; }
        };
        route.add().accept(first); route.add().accept(supplied);
        var combined = route.get().get();
        assertEquals(1, calls[0]); first.complete(null); supplied.complete(null);
        assertFalse(combined.isDone()); actual.complete(null); assertNull(combined.join());
    }
    @Test void conversionFailureDoesNotPublishANewFence() {
        for (boolean save : new boolean[]{false, true}) {
            var route = ChunkSyncDependencyContractTest.route(save);
            CompletableFuture<?> first = generationFence();
            RuntimeException failure = new RuntimeException("conversion");
            CompletableFuture<Void> supplied = new CompletableFuture<>() {
                @Override public CompletableFuture<Void> toCompletableFuture() { throw failure; }
            };
            route.add().accept(first);
            long created = NativeChunkGeneration.rangeStatistics()[1];
            assertSame(failure, assertThrows(RuntimeException.class, () -> route.add().accept(supplied)));
            assertSame(first, route.get().get());
            assertEquals(created, NativeChunkGeneration.rangeStatistics()[1]);
        }
    }
    @Test void arbitraryPublicCombinatorsKeepTheirActualFunctionAndExecutorSemantics() {
        CompletableFuture<?> first = generationFence();
        var second = new CompletableFuture<Integer>(); int[] calls = {0};
        long created = NativeChunkGeneration.rangeStatistics()[1];
        var combined = first.thenCombine(second, (value, number) -> { calls[0]++; return "value:" + number; });
        first.complete(null); assertEquals(0, calls[0]); second.complete(43);
        assertEquals("value:43", combined.join()); assertEquals(1, calls[0]);
        assertEquals(created, NativeChunkGeneration.rangeStatistics()[1], "arbitrary CF operations are not a new native future framework");
    }
}
