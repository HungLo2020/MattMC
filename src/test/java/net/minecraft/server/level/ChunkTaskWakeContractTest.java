package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Drives ChunkMap's actual dispatcher adapter with controlled engine effects. */
class ChunkTaskWakeContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final class Fixture {
        final ChunkMap map = mock(ChunkMap.class, CALLS_REAL_METHODS);
        final ChunkGenerationTask task = mock(ChunkGenerationTask.class);
        final GenerationChunkHolder center = mock(GenerationChunkHolder.class);
        final List<Runnable> submitted = new ArrayList<>();
        final List<IntSupplier> priorities = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        int priority = 33;
        Fixture() throws Exception {
            when(center.getPos()).thenReturn(new ChunkPos(4, -7));
            when(center.getQueueLevel()).thenAnswer(call -> priority);
            when(task.getCenter()).thenAnswer(call -> { trace.add("center"); return center; });
            ChunkTaskDispatcher dispatcher = mock(ChunkTaskDispatcher.class);
            doAnswer(call -> {
                trace.add("submit");
                assertEquals(ChunkPos.asLong(4, -7), (long)call.getArgument(1));
                submitted.add(call.getArgument(0));
                priorities.add(call.getArgument(2));
                return null;
            }).when(dispatcher).submit(any(Runnable.class), anyLong(), any(IntSupplier.class));
            var field = ChunkMap.class.getDeclaredField("worldgenTaskDispatcher");
            field.setAccessible(true);
            field.set(map, dispatcher);
        }
        void start() throws Throwable {
            var method = ChunkMap.class.getDeclaredMethod("runGenerationTask", ChunkGenerationTask.class);
            method.setAccessible(true);
            try { method.invoke(map, task); }
            catch (InvocationTargetException exception) { throw exception.getCause(); }
        }
    }

    @Test void pendingCompletionWakesTheSameTaskAndReadsTheLiveQueuePriority() throws Throwable {
        Fixture f = new Fixture();
        CompletableFuture<Void> waiting = new CompletableFuture<>();
        doReturn(waiting).doReturn(null).when(f.task).runUntilWait();
        f.start();
        assertEquals(List.of("center", "submit"), f.trace);
        f.priority = 7;
        assertEquals(7, f.priorities.getFirst().getAsInt());
        f.submitted.getFirst().run();
        assertEquals(1, f.submitted.size());
        waiting.complete(null);
        assertEquals(List.of("center", "submit", "center", "submit"), f.trace);
        f.submitted.getLast().run();
        assertEquals(2, f.submitted.size());
        verify(f.task, times(2)).runUntilWait();
    }

    @Test void alreadyCompletedWaitCanSubmitTheWakeInline() throws Throwable {
        Fixture f = new Fixture();
        doReturn(CompletableFuture.completedFuture(null)).when(f.task).runUntilWait();
        f.start();
        f.submitted.getFirst().run();
        assertEquals(List.of("center", "submit", "center", "submit"), f.trace);
        assertEquals(2, f.submitted.size());
    }

    @Test void exceptionalWaitDoesNotScheduleANormalWake() throws Throwable {
        Fixture f = new Fixture();
        CompletableFuture<Void> waiting = new CompletableFuture<>();
        doReturn(waiting).when(f.task).runUntilWait();
        f.start();
        f.submitted.getFirst().run();
        waiting.completeExceptionally(new RuntimeException("failed prerequisite"));
        assertEquals(1, f.submitted.size());
    }

    @Test void customRegistrationRetainsItsActualCallbackAndRepeatedWakeEffects() throws Throwable {
        Fixture f = new Fixture();
        List<Runnable> callbacks = new ArrayList<>();
        CompletableFuture<Void> waiting = new CompletableFuture<>() {
            @Override public CompletableFuture<Void> thenRun(Runnable action) {
                callbacks.add(action);
                return new CompletableFuture<>();
            }
        };
        doReturn(waiting).when(f.task).runUntilWait();
        f.start();
        f.submitted.getFirst().run();
        assertEquals(1, callbacks.size());
        assertEquals(1, f.submitted.size());
        callbacks.getFirst().run();
        callbacks.getFirst().run();
        assertEquals(3, f.submitted.size(), "custom callbacks retain their original repeated submission effects");
    }

    @Test void registrationFailureKeepsItsOriginalIdentityAndSubmissionPrefix() throws Throwable {
        Fixture f = new Fixture();
        RuntimeException failure = new RuntimeException("registration");
        CompletableFuture<Void> waiting = new CompletableFuture<>() {
            @Override public CompletableFuture<Void> thenRun(Runnable action) { throw failure; }
        };
        doReturn(waiting).when(f.task).runUntilWait();
        f.start();
        assertSame(failure, assertThrows(RuntimeException.class, () -> f.submitted.getFirst().run()));
        assertEquals(List.of("center", "submit"), f.trace);
    }
}
