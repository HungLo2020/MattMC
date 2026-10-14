package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Headless regression coverage for picker admission, rejection, and pool retirement. */
@Timeout(10)
class PriorityTaskPickerRecoveryTest {
    private final List<PriorityTaskPicker.Executor> executors = new ArrayList<>();
    private final List<RateLimitedThreadPoolExecutor> pools = new ArrayList<>();
    private final List<CountDownLatch> releases = new ArrayList<>();
    private PriorityTaskPicker picker;
    private int oldThreads;
    private double oldRatio;

    @BeforeAll
    static void initializeConfigInRuntimeOrder() {
        // AbstractModInitializer initializes the preset handler before nested Config classes.
        ThreadPresetConfigEventHandler.INSTANCE.toString();
    }

    @BeforeEach
    void setUp() {
        oldThreads = Config.Common.MultiThreading.numberOfThreads.get();
        oldRatio = Config.Common.MultiThreading.threadRunTimeRatio.get();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(2);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(1.0);
        picker = new PriorityTaskPicker();
    }

    @AfterEach
    void tearDown() throws Exception {
        releases.forEach(CountDownLatch::countDown);
        for (PriorityTaskPicker.Executor executor : executors) {
            executor.shutdownNow();
            Config.Common.MultiThreading.numberOfThreads.removeListener(executor);
        }
        for (RateLimitedThreadPoolExecutor pool : pools) {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
        picker.shutdownNow();
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(oldThreads);
        Config.Common.MultiThreading.threadRunTimeRatio.setWithoutFiringEvents(oldRatio);
    }

    @Test
    void transientRejectionRetriesWithoutAnotherSubmission() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RateLimitedThreadPoolExecutor pool = pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                if (attempts.getAndIncrement() == 0) throw new RejectedExecutionException("transient");
                super.execute(command);
            }
        });
        PriorityTaskPicker.Executor executor = executor(pool);
        AtomicInteger runs = new AtomicInteger();
        Future<?> future = executor.submit(runs::incrementAndGet);
        future.get(2, TimeUnit.SECONDS);
        assertEquals(1, runs.get());
        assertEquals(2, attempts.get());
        awaitIdle(executor);
        assertEquals(1, executor.getCompletedTaskCount());
    }

    @Test
    void rejectionKeepsTheHeadAheadOfLaterSubmissions() throws Exception {
        AtomicBoolean reject = new AtomicBoolean(true);
        RateLimitedThreadPoolExecutor pool = pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(8)) {
            @Override public void execute(Runnable command) {
                if (reject.get()) throw new RejectedExecutionException("temporarily full");
                super.execute(command);
            }
        });
        PriorityTaskPicker.Executor executor = executor(pool);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int value = i;
            futures.add(executor.submit(() -> order.add(value)));
        }
        reject.set(false);
        futures.add(executor.submit(() -> order.add(3)));
        for (Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
        assertEquals(List.of(0, 1, 2, 3), order);
        awaitIdle(executor);
        assertEquals(4, executor.getCompletedTaskCount());
    }

    @Test
    void permanentLiveRejectionIsRateBoundedAndShutdownCancelsItsFuture() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                attempts.incrementAndGet();
                throw new RejectedExecutionException("still full");
            }
        }));
        Future<?> future = executor.submit(() -> fail("permanently rejected task ran"));
        Thread.sleep(150);
        assertTrue(attempts.get() >= 2, "live work must get an independent retry");
        assertTrue(attempts.get() <= 12, "retry must not spin");
        assertFalse(future.isDone());
        executor.shutdownNow();
        assertTrue(future.isCancelled(), "shutdown must terminate the polled future");
        assertEquals(0, executor.getRunningTaskCount());
        assertEquals(0, executor.getCompletedTaskCount());
    }

    @Test
    void shutdownDiscardsPickerQueuedFutures() throws Exception {
        Config.Common.MultiThreading.numberOfThreads.setWithoutFiringEvents(1);
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = release();
        Future<?> active = executor.submit(() -> { entered.countDown(); await(release); });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        Future<?> waiting = executor.submit(() -> fail("discarded work ran"));
        assertEquals(1, executor.getQueueSize());
        executor.shutdown();
        assertTrue(waiting.isCancelled());
        assertEquals(0, executor.getQueueSize());
        release.countDown();
        active.get(2, TimeUnit.SECONDS);
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void configurationCannotResurrectShutdownExecutor() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        executor.shutdownNow();
        executor.onConfigValueSet();
        pools.add(currentPool(executor));
        assertTrue(executor.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
    }

    @Test
    void terminationIncludesAStillRunningRetiredPool() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = release();
        Future<?> active = executor.submit(() -> { entered.countDown(); await(release); });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        executor.onConfigValueSet();
        pools.add(currentPool(executor));
        executor.shutdown();
        assertFalse(executor.isTerminated(), "retired worker still owns a task");
        assertFalse(executor.awaitTermination(30, TimeUnit.MILLISECONDS));
        release.countDown();
        active.get(2, TimeUnit.SECONDS);
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(executor.isTerminated());
    }

    @Test
    void admissionIsAccountedBeforeAnInlineWorkerCanComplete() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) { command.run(); }
        }));
        AtomicInteger runningInside = new AtomicInteger(-1);
        executor.execute(() -> runningInside.set(executor.getRunningTaskCount()));
        assertEquals(1, runningInside.get());
        assertEquals(0, executor.getRunningTaskCount());
        assertEquals(1, executor.getCompletedTaskCount());
    }

    @Test
    void realBoundedPoolRecoversAfterRateLimiterCooldown() throws Exception {
        CountDownLatch cooldownEntered = new CountDownLatch(1);
        CountDownLatch releaseCooldown = release();
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicInteger rejections = new AtomicInteger();
        RateLimitedThreadPoolExecutor pool = pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override protected void afterExecute(Runnable task, Throwable failure) {
                if (first.compareAndSet(true, false)) {
                    cooldownEntered.countDown();
                    await(releaseCooldown);
                }
                super.afterExecute(task, failure);
            }
        });
        pool.setRejectedExecutionHandler((task, rejectedPool) -> {
            rejections.incrementAndGet();
            throw new RejectedExecutionException("bounded queue during cooldown");
        });
        PriorityTaskPicker.Executor executor = executor(pool);
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        assertTrue(cooldownEntered.await(2, TimeUnit.SECONDS));
        Future<Integer> queued = executor.submit(() -> 1);
        Future<Integer> rejected = executor.submit(() -> 2);
        assertTrue(rejections.get() > 0, "exercise the actual bounded ThreadPoolExecutor rejection");
        releaseCooldown.countDown();
        assertEquals(1, queued.get(2, TimeUnit.SECONDS));
        assertEquals(2, rejected.get(2, TimeUnit.SECONDS));
        awaitIdle(executor);
        assertEquals(3, executor.getCompletedTaskCount());
    }

    @Test
    void shutdownDuringAdmissionDiscardsExactlyOnceOutsideTheLock() throws Exception {
        CountDownLatch admission = new CountDownLatch(1);
        CountDownLatch releaseAdmission = release();
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                admission.countDown();
                await(releaseAdmission);
                throw new RejectedExecutionException("shutdown raced admission");
            }
        }));
        AtomicInteger discards = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Throwable> callbackFailure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>(() -> fail("discarded command ran"), null);
        Thread submitter = daemon(() -> executor.execute(task, () -> {
            try {
                assertTrue(executor.isShutdown());
                assertFalse(pickerLock().isHeldByCurrentThread());
                assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
                discards.incrementAndGet();
            } catch (Throwable failure) { callbackFailure.set(failure); }
        }));
        submitter.start();
        assertTrue(admission.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(task), executor.shutdownNow());
        assertTrue(task.isCancelled());
        releaseAdmission.countDown();
        submitter.join(2000);
        assertFalse(submitter.isAlive());
        assertNull(callbackFailure.get());
        assertEquals(1, discards.get());
        assertEquals(0, executor.getQueueSize());
        assertEquals(0, executor.getRunningTaskCount());
        assertEquals(0, executor.getCompletedTaskCount());
        assertEquals(0, occupied());
    }

    @Test
    void replacementDuringRejectedAdmissionRetriesOnTheNewPool() throws Exception {
        CountDownLatch admission = new CountDownLatch(1);
        CountDownLatch releaseAdmission = release();
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                admission.countDown();
                await(releaseAdmission);
                throw new RejectedExecutionException("retired during admission");
            }
        }));
        AtomicInteger runs = new AtomicInteger();
        java.util.concurrent.FutureTask<Integer> task = new java.util.concurrent.FutureTask<>(runs::incrementAndGet);
        Thread submitter = daemon(() -> executor.execute(task));
        submitter.start();
        assertTrue(admission.await(2, TimeUnit.SECONDS));
        executor.onConfigValueSet();
        pools.add(currentPool(executor));
        releaseAdmission.countDown();
        submitter.join(2000);
        assertFalse(submitter.isAlive());
        assertEquals(1, task.get(2, TimeUnit.SECONDS));
        awaitIdle(executor);
        assertEquals(1, runs.get());
        assertEquals(1, executor.getCompletedTaskCount());
        assertEquals(0, occupied());
        assertFalse(executor.isShutdown());
    }

    @Test
    void shutdownNowInterruptsRetiredWorkersAndCancelsTheirQueuedOwners() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = release();
        Future<?> active = executor.submit(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        Future<?> waitingInOldPool = executor.submit(() -> fail("retired queued command ran"));
        assertEquals(2, executor.getRunningTaskCount());
        executor.onConfigValueSet();
        pools.add(currentPool(executor));
        executor.shutdownNow();
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertTrue(waitingInOldPool.isCancelled());
        active.get(2, TimeUnit.SECONDS);
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(0, occupied());
    }

    @Test
    void removeAndClearNotifyOwnersExactlyOnceAndPermitReentrantSubmission() throws Exception {
        AtomicBoolean reject = new AtomicBoolean(true);
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(2)) {
            @Override public void execute(Runnable command) {
                if (reject.get()) throw new RejectedExecutionException("full");
                super.execute(command);
            }
        }));
        AtomicInteger discards = new AtomicInteger();
        AtomicBoolean callbackHeldLock = new AtomicBoolean();
        java.util.concurrent.CompletableFuture<Void> replacementDone = new java.util.concurrent.CompletableFuture<>();
        Runnable removed = () -> fail("removed command ran");
        executor.execute(removed, () -> {
            callbackHeldLock.set(pickerLockUnchecked().isHeldByCurrentThread());
            discards.incrementAndGet();
            executor.execute(() -> replacementDone.complete(null));
        });
        executor.remove(removed);
        executor.remove(removed);
        assertEquals(1, discards.get());
        assertFalse(callbackHeldLock.get());
        Runnable cleared = () -> fail("cleared command ran");
        executor.execute(cleared, discards::incrementAndGet);
        // Remove only the new owner, leaving the reentrantly submitted replacement to run.
        executor.remove(cleared);
        reject.set(false);
        replacementDone.get(2, TimeUnit.SECONDS);
        awaitIdle(executor);
        assertEquals(2, discards.get());
        assertEquals(1, executor.getCompletedTaskCount());
        executor.clearQueue();
        executor.shutdownNow();
        assertEquals(2, discards.get());
    }

    @Test
    void throwingDiscardCallbackCannotStrandOtherOwners() throws Exception {
        PriorityTaskPicker.Executor executor = rejectingExecutor();
        AtomicInteger discards = new AtomicInteger();
        executor.execute(() -> {}, () -> { discards.incrementAndGet(); throw new IllegalStateException("expected test callback failure"); });
        executor.execute(() -> {}, discards::incrementAndGet);
        Future<?> future = executor.submit(() -> {});
        executor.clearQueue();
        assertEquals(2, discards.get());
        assertTrue(future.isCancelled());
        assertEquals(0, executor.getQueueSize());
        executor.clearQueue();
        executor.shutdownNow();
        assertEquals(2, discards.get());
    }

    @Test
    void ownedFutureHelpersTerminateOnClearAndImmediateShutdown() throws Exception {
        PriorityTaskPicker.Executor executor = rejectingExecutor();
        var supplied = executor.supplyAsync(() -> 42);
        var runnable = executor.runAsync(() -> fail("discarded helper ran"));
        executor.clearQueue();
        assertTrue(supplied.isCancelled());
        assertTrue(runnable.isCancelled());
        executor.shutdownNow();
        assertTrue(executor.supplyAsync(() -> 43).isCancelled());
        assertTrue(executor.runAsync(() -> {}).isCancelled());
        java.util.concurrent.FutureTask<Void> direct = new java.util.concurrent.FutureTask<>(() -> {}, null);
        assertThrows(RejectedExecutionException.class, () -> executor.execute(direct));
        assertTrue(direct.isCancelled());
    }

    @Test
    void cancelledOwnedFutureNeverStartsItsSupplier() throws Exception {
        AtomicBoolean reject = new AtomicBoolean(true);
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(2)) {
            @Override public void execute(Runnable command) {
                if (reject.get()) throw new RejectedExecutionException("full");
                super.execute(command);
            }
        }));
        AtomicInteger runs = new AtomicInteger();
        var result = executor.supplyAsync(runs::incrementAndGet);
        assertTrue(result.cancel(false));
        assertEquals(0, executor.getQueueSize(), "owned cancellation removes the queued command");
        reject.set(false);
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        awaitIdle(executor);
        assertEquals(0, runs.get());
        assertTrue(result.isCancelled());
    }

    @Test
    void submissionWhileDispatcherIsBusyCannotLoseTheWakeup() throws Exception {
        CountDownLatch admission = new CountDownLatch(1);
        CountDownLatch releaseAdmission = release();
        AtomicBoolean first = new AtomicBoolean(true);
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                if (first.compareAndSet(true, false)) {
                    admission.countDown();
                    await(releaseAdmission);
                }
                command.run();
            }
        }));
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        Thread submitter = daemon(() -> executor.execute(() -> order.add(1)));
        submitter.start();
        assertTrue(admission.await(2, TimeUnit.SECONDS));
        Future<?> next = executor.submit(() -> order.add(2));
        releaseAdmission.countDown();
        submitter.join(2000);
        assertFalse(submitter.isAlive());
        next.get(2, TimeUnit.SECONDS);
        awaitIdle(executor);
        assertEquals(List.of(1, 2), order);
        assertEquals(2, executor.getCompletedTaskCount());
        assertEquals(0, occupied());
    }

    @Test
    void newSubmissionsCannotBypassRejectionBackoff() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                attempts.incrementAndGet();
                throw new RejectedExecutionException("full");
            }
        }));
        long started = System.nanoTime();
        for (int i = 0; i < 500; i++) executor.execute(() -> fail("rejected work ran"));
        long elapsed = System.nanoTime() - started;
        assertTrue(attempts.get() <= 2 + elapsed / TimeUnit.MILLISECONDS.toNanos(25),
                "new arrivals must not cause a rejection loop");
        assertEquals(500, executor.getQueueSize());
        executor.clearQueue();
        assertEquals(0, executor.getQueueSize());
        assertEquals(0, occupied());
    }

    @Test
    void rejectedExecutorDoesNotBlockAnotherExecutor() throws Exception {
        PriorityTaskPicker.Executor rejected = rejectingExecutor();
        Future<?> pending = rejected.submit(() -> fail("rejected command ran"));
        PriorityTaskPicker.Executor healthy = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        assertEquals(19, healthy.submit(() -> 19).get(2, TimeUnit.SECONDS));
        assertFalse(pending.isDone());
        rejected.shutdownNow();
        assertTrue(pending.isCancelled());
        awaitIdle(healthy);
        assertEquals(0, occupied());
    }

    @Test
    void terminatedBackingPoolCancelsPendingRetriesPermanently() throws Exception {
        PriorityTaskPicker.Executor executor = rejectingExecutor();
        Future<?> task = executor.submit(() -> {});
        currentPool(executor).shutdownNow();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!task.isDone() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(task.isCancelled());
        assertTrue(executor.isShutdown());
        executor.onConfigValueSet();
        assertTrue(executor.isShutdown());
        assertEquals(0, occupied());
    }

    @Test
    void shutdownNowReturnsOriginalCommandsFromBothQueues() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = release();
        executor.execute(() -> { entered.countDown(); await(release); });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        AtomicInteger discarded = new AtomicInteger();
        Runnable inPool = () -> fail("pool queue task ran");
        Runnable inPicker = () -> fail("picker queue task ran");
        executor.execute(inPool, discarded::incrementAndGet);
        executor.execute(inPicker, discarded::incrementAndGet);
        List<Runnable> returned = executor.shutdownNow();
        assertEquals(2, returned.size());
        assertTrue(returned.contains(inPool));
        assertTrue(returned.contains(inPicker));
        assertEquals(2, discarded.get());
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(0, occupied());
    }

    private PriorityTaskPicker.Executor rejectingExecutor() throws Exception {
        return executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) { throw new RejectedExecutionException("full"); }
        }));
    }

    private java.util.concurrent.locks.ReentrantLock pickerLock() throws Exception {
        Field field = PriorityTaskPicker.class.getDeclaredField("taskPickerLock");
        field.setAccessible(true);
        return (java.util.concurrent.locks.ReentrantLock) field.get(picker);
    }

    private java.util.concurrent.locks.ReentrantLock pickerLockUnchecked() {
        try { return pickerLock(); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private int occupied() throws Exception {
        Field field = PriorityTaskPicker.class.getDeclaredField("occupiedThreadsRef");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(picker)).get();
    }

    @Test
    void discardingAdmissionsWakesAnotherQueueWithoutAWorkerCompletion() throws Exception {
        PriorityTaskPicker.Executor held = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(2)) {
            @Override public void execute(Runnable command) { /* accepted, but not yet started */ }
        }));
        Future<?> first = held.submit(() -> {});
        Future<?> second = held.submit(() -> {});
        assertEquals(2, occupied());
        PriorityTaskPicker.Executor healthy = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1))));
        Future<Integer> waiting = healthy.submit(() -> 71);
        assertEquals(1, healthy.getQueueSize());
        held.shutdownNow();
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        assertEquals(71, waiting.get(2, TimeUnit.SECONDS));
        awaitIdle(healthy);
        assertEquals(0, occupied());
    }

    @Test
    void pickerShutdownIsVisibleToEveryDiscardCallback() throws Exception {
        PriorityTaskPicker.Executor first = rejectingExecutor();
        PriorityTaskPicker.Executor second = rejectingExecutor();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        first.execute(() -> {}, () -> {
            try {
                assertTrue(first.isShutdown());
                assertTrue(second.isShutdown());
                assertFalse(pickerLockUnchecked().isHeldByCurrentThread());
                assertThrows(RejectedExecutionException.class, () -> picker.createExecutor("must-not-reopen"));
                callbacks.incrementAndGet();
            } catch (Throwable thrown) { failure.set(thrown); }
        });
        Future<?> secondOwner = second.submit(() -> {});
        picker.shutdownNow();
        assertNull(failure.get());
        assertEquals(1, callbacks.get());
        assertTrue(secondOwner.isCancelled());
        assertEquals(0, occupied());
    }

    @Test
    void lateRejectionFromAnInlineExecutorCannotReplayCompletedWork() throws Exception {
        PriorityTaskPicker.Executor executor = executor(pool(new RateLimitedThreadPoolExecutor(1, PriorityTaskPickerRecoveryTest::daemon, new ArrayBlockingQueue<>(1)) {
            @Override public void execute(Runnable command) {
                command.run();
                throw new RejectedExecutionException("command has already run");
            }
        }));
        AtomicInteger runs = new AtomicInteger();
        assertEquals(1, executor.submit(runs::incrementAndGet).get(2, TimeUnit.SECONDS));
        assertEquals(1, runs.get());
        assertEquals(1, executor.getCompletedTaskCount());
        assertEquals(0, executor.getQueueSize());
        assertEquals(0, occupied());
    }

    private PriorityTaskPicker.Executor executor(RateLimitedThreadPoolExecutor pool) throws Exception {
        PriorityTaskPicker.Executor executor = picker.createExecutor("recovery-test");
        pools.add(currentPool(executor));
        currentPool(executor).shutdownNow();
        Field field = PriorityTaskPicker.Executor.class.getDeclaredField("threadPoolExecutor");
        field.setAccessible(true);
        field.set(executor, pool);
        executors.add(executor);
        return executor;
    }

    private static RateLimitedThreadPoolExecutor currentPool(PriorityTaskPicker.Executor executor) throws Exception {
        Field field = PriorityTaskPicker.Executor.class.getDeclaredField("threadPoolExecutor");
        field.setAccessible(true);
        return (RateLimitedThreadPoolExecutor) field.get(executor);
    }

    private RateLimitedThreadPoolExecutor pool(RateLimitedThreadPoolExecutor pool) {
        pools.add(pool);
        return pool;
    }

    private CountDownLatch release() {
        CountDownLatch latch = new CountDownLatch(1);
        releases.add(latch);
        return latch;
    }

    private static Thread daemon(Runnable command) {
        Thread thread = new Thread(command, "picker-recovery-test");
        thread.setDaemon(true);
        return thread;
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void awaitIdle(PriorityTaskPicker.Executor executor) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (executor.getRunningTaskCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(0, executor.getRunningTaskCount());
    }
}
