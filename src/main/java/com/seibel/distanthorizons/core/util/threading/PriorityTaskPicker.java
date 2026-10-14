package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Divides DH work across bounded, rate-limited pools without losing rejected work. */
public class PriorityTaskPicker
{
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();
    private static final long INITIAL_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(25);
    private static final long MAX_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final ScheduledThreadPoolExecutor RETRY_EXECUTOR = createRetryExecutor();

    private final ArrayList<Executor> executors = new ArrayList<>();
    /** Protects queue ownership, admission, pool replacement, and terminal transitions. */
    private final ReentrantLock taskPickerLock = new ReentrantLock();
    private final AtomicInteger occupiedThreadsRef = new AtomicInteger();
    private boolean shutDown;
    private boolean picking;
    private boolean pickRequested;
    private ScheduledFuture<?> retryFuture;
    private long retryWakeNanos;
    private long retryGeneration;

    private static ScheduledThreadPoolExecutor createRetryExecutor()
    {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "DH task admission retry");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    public Executor createExecutor(String name)
    {
        this.taskPickerLock.lock();
        try
        {
            if (this.shutDown) throw new RejectedExecutionException("PriorityTaskPicker shutdown.");
            Executor executor = new Executor(this, name);
            this.executors.add(executor);
            return executor;
        }
        finally { this.taskPickerLock.unlock(); }
    }

    /**
     * Only one caller dispatches at a time. Other callers leave a request instead of losing
     * their wakeup. Pool admission and user callbacks happen outside the bookkeeping lock,
     * including when an executor starts its command synchronously.
     */
    private void tryStartNextTask()
    {
        this.taskPickerLock.lock();
        try
        {
            if (this.shutDown) return;
            if (this.picking)
            {
                this.pickRequested = true;
                return;
            }
            this.picking = true;
        }
        finally { this.taskPickerLock.unlock(); }

        boolean released = false;
        try
        {
            while (true)
            {
                List<Executor> ordered;
                this.taskPickerLock.lock();
                try
                {
                    this.pickRequested = false;
                    ordered = new ArrayList<>(this.executors);
                    ordered.sort(Comparator.comparingLong(executor -> executor.totalRuntimeNanos.get()));
                }
                finally { this.taskPickerLock.unlock(); }

                boolean progressed = false;
                int batchSize = Math.max(1, Config.Common.MultiThreading.numberOfThreads.get() / 2);
                for (Executor executor : ordered)
                {
                    for (int count = 0; count < batchSize; count++)
                    {
                        TrackedRunnable task = null;
                        RateLimitedThreadPoolExecutor pool = null;
                        ShutdownPlan stopped = null;
                        this.taskPickerLock.lock();
                        try
                        {
                            if (this.shutDown || executor.shutDown) break;
                            if (executor.threadPoolExecutor.isShutdown())
                            {
                                stopped = executor.stopLocked(false);
                            }
                            else if (this.occupiedThreadsRef.get() < Config.Common.MultiThreading.numberOfThreads.get()
                                    && (executor.retryAfterNanos == 0 || System.nanoTime() - executor.retryAfterNanos >= 0))
                            {
                                task = executor.taskQueue.pollFirst();
                                if (task != null)
                                {
                                    task.state = TaskState.ADMITTED;
                                    executor.admittedTasks.add(task);
                                    this.occupiedThreadsRef.incrementAndGet();
                                    executor.runningTasksRef.incrementAndGet();
                                    pool = executor.threadPoolExecutor;
                                }
                            }
                        }
                        finally { this.taskPickerLock.unlock(); }
                        if (stopped != null) stopped.finish(false);
                        if (task == null) break;
                        progressed = true;
                        this.dispatch(executor, pool, task);
                    }
                }

                this.taskPickerLock.lock();
                try
                {
                    if (this.shutDown || (!progressed && !this.pickRequested))
                    {
                        this.picking = false;
                        released = true;
                        this.scheduleRetryLocked();
                        return;
                    }
                }
                finally { this.taskPickerLock.unlock(); }
            }
        }
        finally
        {
            if (!released)
            {
                this.taskPickerLock.lock();
                try
                {
                    this.picking = false;
                    this.scheduleRetryLocked();
                }
                finally { this.taskPickerLock.unlock(); }
            }
        }
    }

    private void dispatch(Executor executor, RateLimitedThreadPoolExecutor pool, TrackedRunnable task)
    {
        try
        {
            pool.execute(task);
            this.taskPickerLock.lock();
            try
            {
                executor.retryDelayNanos = INITIAL_RETRY_NANOS;
                executor.retryAfterNanos = 0;
            }
            finally { this.taskPickerLock.unlock(); }
        }
        catch (RejectedExecutionException rejection)
        {
            boolean discarded = false;
            ShutdownPlan stopped = null;
            this.taskPickerLock.lock();
            try
            {
                // A synchronous/custom executor may already have run the task. Never replay it.
                if (task.state != TaskState.ADMITTED) return;
                if (this.shutDown || executor.shutDown)
                {
                    discarded = task.discardLocked();
                }
                else if (pool == executor.threadPoolExecutor && pool.isShutdown())
                {
                    discarded = task.discardLocked();
                    stopped = executor.stopLocked(false);
                }
                else
                {
                    task.releaseAdmissionLocked();
                    task.state = TaskState.QUEUED;
                    executor.taskQueue.addFirst(task);
                    executor.retryAfterNanos = System.nanoTime() + executor.retryDelayNanos;
                    executor.retryDelayNanos = Math.min(MAX_RETRY_NANOS, executor.retryDelayNanos * 2);
                }
            }
            finally { this.taskPickerLock.unlock(); }
            if (stopped != null) stopped.finish(false);
            if (discarded) task.notifyDiscard();
        }
        catch (RuntimeException | Error failure)
        {
            boolean discarded;
            this.taskPickerLock.lock();
            try { discarded = task.discardLocked(); }
            finally { this.taskPickerLock.unlock(); }
            if (discarded) task.notifyDiscard();
            throw failure;
        }
    }

    /** One delayed wake per picker; submissions cannot bypass an executor's backoff. */
    private void scheduleRetryLocked()
    {
        long earliest = Long.MAX_VALUE;
        if (!this.shutDown && this.occupiedThreadsRef.get() < Config.Common.MultiThreading.numberOfThreads.get())
        {
            for (Executor executor : this.executors)
            {
                if (!executor.shutDown && !executor.taskQueue.isEmpty() && executor.retryAfterNanos != 0)
                    earliest = Math.min(earliest, executor.retryAfterNanos);
            }
        }
        if (earliest == Long.MAX_VALUE)
        {
            if (this.retryFuture != null) this.retryFuture.cancel(false);
            this.retryFuture = null;
            this.retryGeneration++;
            return;
        }
        if (this.retryFuture != null && this.retryWakeNanos <= earliest) return;
        if (this.retryFuture != null) this.retryFuture.cancel(false);
        this.retryWakeNanos = earliest;
        long generation = ++this.retryGeneration;
        this.retryFuture = RETRY_EXECUTOR.schedule(() -> {
            this.taskPickerLock.lock();
            try
            {
                if (generation != this.retryGeneration) return;
                this.retryFuture = null;
            }
            finally { this.taskPickerLock.unlock(); }
            this.tryStartNextTask();
        }, Math.max(0, earliest - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    /** Stops admission, discards unstarted tasks, and interrupts current and retired pools. */
    public void shutdownNow()
    {
        LOGGER.info("Shutting down PriorityTaskPicker thread pool...");
        List<ShutdownPlan> plans = new ArrayList<>();
        this.taskPickerLock.lock();
        try
        {
            this.shutDown = true;
            for (Executor executor : this.executors) plans.add(executor.stopLocked(true));
            this.scheduleRetryLocked();
        }
        finally { this.taskPickerLock.unlock(); }
        for (ShutdownPlan plan : plans) plan.finish(true);
        try
        {
            for (Executor executor : this.executors) executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** A named work queue sharing the picker's global admission budget. */
    public static class Executor extends AbstractExecutorService implements IConfigListener
    {
        private final PriorityTaskPicker parentTaskPicker;
        private final String name;
        private final Deque<TrackedRunnable> taskQueue = new ArrayDeque<>();
        private final Set<TrackedRunnable> admittedTasks = new HashSet<>();
        private final AtomicInteger runningTasksRef = new AtomicInteger();
        private final AtomicInteger completedTasksRef = new AtomicInteger();
        private final AtomicLong totalRuntimeNanos = new AtomicLong();
        private final RollingAverage runTimeInMsRollingAverage = new RollingAverage(200);
        private RateLimitedThreadPoolExecutor threadPoolExecutor;
        private final List<RateLimitedThreadPoolExecutor> retiredPools = new ArrayList<>();
        private boolean shutDown;
        private long retryAfterNanos;
        private long retryDelayNanos = INITIAL_RETRY_NANOS;

        public Executor(PriorityTaskPicker parentTaskPicker, String name)
        {
            this.parentTaskPicker = Objects.requireNonNull(parentTaskPicker);
            this.name = Objects.requireNonNull(name);
            this.threadPoolExecutor = this.createThreadPool();
            Config.Common.MultiThreading.numberOfThreads.addListener(this);
        }

        private RateLimitedThreadPoolExecutor createThreadPool()
        {
            return new RateLimitedThreadPoolExecutor(
                    Config.Common.MultiThreading.numberOfThreads.get(),
                    new DhThreadFactory(this.name, Config.Common.MultiThreading.threadPriority.get(), false),
                    new ArrayBlockingQueue<>(Runtime.getRuntime().availableProcessors()));
        }

        @Override
        public void onConfigValueSet()
        {
            RateLimitedThreadPoolExecutor oldPool;
            this.parentTaskPicker.taskPickerLock.lock();
            try
            {
                if (this.shutDown || this.parentTaskPicker.shutDown) return;
                oldPool = this.threadPoolExecutor;
                this.threadPoolExecutor = this.createThreadPool();
                this.retiredPools.removeIf(ThreadPoolExecutor::isTerminated);
                this.retiredPools.add(oldPool);
                this.retryAfterNanos = 0;
                this.retryDelayNanos = INITIAL_RETRY_NANOS;
            }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
            oldPool.shutdown();
            this.parentTaskPicker.tryStartNextTask();
        }

        @Override
        public void execute(@NotNull Runnable command) { this.execute(command, () -> {}); }

        /**
         * The discard callback runs exactly once if this command never starts, including
         * immediate rejection, remove, clear, and shutdown. It runs outside scheduler locks.
         * Future commands are also cancelled. Opaque CompletableFuture async commands do not
         * expose their owner; use runAsync/supplyAsync or an explicit callback for those owners.
         */
        public void execute(@NotNull Runnable command, @NotNull Runnable onDiscard)
        {
            TrackedRunnable task = new TrackedRunnable(this, Objects.requireNonNull(command), Objects.requireNonNull(onDiscard));
            boolean rejected;
            ShutdownPlan stopped = null;
            this.parentTaskPicker.taskPickerLock.lock();
            try
            {
                if (!this.shutDown && this.threadPoolExecutor.isShutdown()) stopped = this.stopLocked(false);
                rejected = this.shutDown || this.parentTaskPicker.shutDown;
                if (rejected) task.discardLocked();
                else this.taskQueue.addLast(task);
            }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
            if (stopped != null) stopped.finish(false);
            if (rejected)
            {
                task.notifyDiscard();
                throw new RejectedExecutionException("Thread pool [" + this.name + "] shutdown.");
            }
            this.parentTaskPicker.tryStartNextTask();
        }

        public <T> CompletableFuture<T> supplyAsync(@NotNull Supplier<T> supplier)
        {
            Objects.requireNonNull(supplier);
            CompletableFuture<T> result = new CompletableFuture<>();
            Runnable command = () -> {
                if (result.isDone()) return;
                try { result.complete(supplier.get()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            };
            result.whenComplete((value, failure) -> {
                if (result.isCancelled()) this.remove(command);
            });
            try
            {
                this.execute(command, () -> result.cancel(false));
            }
            catch (RejectedExecutionException rejected) { result.cancel(false); }
            return result;
        }

        public CompletableFuture<Void> runAsync(@NotNull Runnable command)
        {
            Objects.requireNonNull(command);
            return this.supplyAsync(() -> { command.run(); return null; });
        }

        /** Removes picker-queued occurrences of the identical command and notifies each owner. */
        public void remove(@NotNull Runnable command)
        {
            List<TrackedRunnable> discarded = new ArrayList<>();
            this.parentTaskPicker.taskPickerLock.lock();
            try
            {
                this.taskQueue.removeIf(task -> {
                    if (task.command != command) return false;
                    if (task.discardLocked()) discarded.add(task);
                    return true;
                });
                this.parentTaskPicker.scheduleRetryLocked();
            }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
            discarded.forEach(TrackedRunnable::notifyDiscard);
        }

        /** @deprecated Submit through execute so admission is always accounted for. */
        @Deprecated
        public void runTask(@NotNull Runnable command) { this.execute(command); }

        public int getQueueSize()
        {
            this.parentTaskPicker.taskPickerLock.lock();
            try { return this.taskQueue.size(); }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
        }
        public int getPoolSize() { return Config.Common.MultiThreading.numberOfThreads.get(); }
        public int getRunningTaskCount() { return this.runningTasksRef.get(); }
        public int getCompletedTaskCount() { return this.completedTasksRef.get(); }
        public double getAverageRunTimeInMs()
        {
            this.parentTaskPicker.taskPickerLock.lock();
            try { return this.runTimeInMsRollingAverage.getAverage(); }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
        }

        public void clearQueue()
        {
            List<TrackedRunnable> discarded = new ArrayList<>();
            this.parentTaskPicker.taskPickerLock.lock();
            try
            {
                this.discardQueuedLocked(discarded);
                this.parentTaskPicker.scheduleRetryLocked();
            }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
            discarded.forEach(TrackedRunnable::notifyDiscard);
        }

        private void discardQueuedLocked(List<TrackedRunnable> discarded)
        {
            TrackedRunnable task;
            while ((task = this.taskQueue.pollFirst()) != null)
                if (task.discardLocked()) discarded.add(task);
        }

        private ShutdownPlan stopLocked(boolean immediate)
        {
            boolean removeListener = !this.shutDown;
            this.shutDown = true;
            this.retryAfterNanos = 0;
            List<TrackedRunnable> discarded = new ArrayList<>();
            this.discardQueuedLocked(discarded);
            if (immediate)
            {
                for (TrackedRunnable task : new ArrayList<>(this.admittedTasks))
                    if (task.discardLocked()) discarded.add(task);
            }
            this.parentTaskPicker.scheduleRetryLocked();
            return new ShutdownPlan(this, this.poolsLocked(), discarded, removeListener);
        }

        private List<RateLimitedThreadPoolExecutor> poolsLocked()
        {
            List<RateLimitedThreadPoolExecutor> pools = new ArrayList<>(this.retiredPools);
            pools.add(this.threadPoolExecutor);
            return pools;
        }

        /** Stops admission and discards picker-queued work; already admitted work may finish. */
        @Override
        public void shutdown() { this.stop(false); }
        @Override
        public @NotNull List<Runnable> shutdownNow() { return this.stop(true); }

        private List<Runnable> stop(boolean immediate)
        {
            ShutdownPlan plan;
            this.parentTaskPicker.taskPickerLock.lock();
            try { plan = this.stopLocked(immediate); }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
            return plan.finish(immediate);
        }

        @Override
        public boolean isShutdown()
        {
            this.parentTaskPicker.taskPickerLock.lock();
            try { return this.shutDown; }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
        }
        @Override
        public boolean isTerminated()
        {
            this.parentTaskPicker.taskPickerLock.lock();
            try { return this.shutDown && this.poolsLocked().stream().allMatch(ThreadPoolExecutor::isTerminated); }
            finally { this.parentTaskPicker.taskPickerLock.unlock(); }
        }
        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException
        {
            long remaining = unit.toNanos(timeout);
            long start = System.nanoTime();
            while (!this.isTerminated())
            {
                List<RateLimitedThreadPoolExecutor> pools;
                this.parentTaskPicker.taskPickerLock.lock();
                try { pools = this.poolsLocked(); }
                finally { this.parentTaskPicker.taskPickerLock.unlock(); }
                for (RateLimitedThreadPoolExecutor pool : pools)
                {
                    if (!pool.isTerminated() && (remaining <= 0 || !pool.awaitTermination(remaining, TimeUnit.NANOSECONDS)))
                        return false;
                    remaining = unit.toNanos(timeout) - (System.nanoTime() - start);
                }
                if (remaining <= 0) return this.isTerminated();
            }
            return true;
        }
    }

    /** All terminal state is visible before pool shutdown or any owner callback. */
    private static class ShutdownPlan
    {
        final Executor executor;
        final List<RateLimitedThreadPoolExecutor> pools;
        final List<TrackedRunnable> discarded;
        final boolean removeListener;

        ShutdownPlan(Executor executor, List<RateLimitedThreadPoolExecutor> pools,
                     List<TrackedRunnable> discarded, boolean removeListener)
        {
            this.executor = executor;
            this.pools = pools;
            this.discarded = discarded;
            this.removeListener = removeListener;
        }

        List<Runnable> finish(boolean immediate)
        {
            if (this.removeListener) Config.Common.MultiThreading.numberOfThreads.removeListener(this.executor);
            for (RateLimitedThreadPoolExecutor pool : this.pools)
            {
                if (immediate) pool.shutdownNow();
                else pool.shutdown();
            }
            List<Runnable> commands = new ArrayList<>();
            for (TrackedRunnable task : this.discarded)
            {
                commands.add(task.command);
                task.notifyDiscard();
            }
            // Discarding admitted work frees shared capacity without a worker completion.
            this.executor.parentTaskPicker.tryStartNextTask();
            return commands;
        }
    }

    private enum TaskState { QUEUED, ADMITTED, RUNNING, FINISHED, DISCARDED }

    private static class TrackedRunnable implements Runnable
    {
        final Executor executor;
        final Runnable command;
        final Runnable onDiscard;
        TaskState state = TaskState.QUEUED;

        TrackedRunnable(Executor executor, Runnable command, Runnable onDiscard)
        {
            this.executor = executor;
            this.command = command;
            this.onDiscard = onDiscard;
        }

        void releaseAdmissionLocked()
        {
            this.executor.admittedTasks.remove(this);
            this.executor.runningTasksRef.decrementAndGet();
            this.executor.parentTaskPicker.occupiedThreadsRef.decrementAndGet();
        }

        boolean discardLocked()
        {
            if (this.state != TaskState.QUEUED && this.state != TaskState.ADMITTED) return false;
            if (this.state == TaskState.ADMITTED) this.releaseAdmissionLocked();
            this.state = TaskState.DISCARDED;
            return true;
        }

        void notifyDiscard()
        {
            try
            {
                if (this.command instanceof Future<?>) ((Future<?>) this.command).cancel(false);
            }
            catch (Throwable failure) { LOGGER.error("Unable to cancel discarded task", failure); }
            try { this.onDiscard.run(); }
            catch (Throwable failure) { LOGGER.error("Discard callback failed", failure); }
        }

        @Override
        public void run()
        {
            PriorityTaskPicker picker = this.executor.parentTaskPicker;
            picker.taskPickerLock.lock();
            try
            {
                if (this.state != TaskState.ADMITTED) return;
                this.state = TaskState.RUNNING;
            }
            finally { picker.taskPickerLock.unlock(); }
            long started = System.nanoTime();
            try { this.command.run(); }
            finally
            {
                long elapsed = System.nanoTime() - started;
                picker.taskPickerLock.lock();
                try
                {
                    this.state = TaskState.FINISHED;
                    this.releaseAdmissionLocked();
                    this.executor.completedTasksRef.incrementAndGet();
                    this.executor.totalRuntimeNanos.addAndGet(elapsed);
                    this.executor.runTimeInMsRollingAverage.add(TimeUnit.NANOSECONDS.toMillis(elapsed));
                }
                finally { picker.taskPickerLock.unlock(); }
                picker.tryStartNextTask();
            }
        }
    }
}
