package net.minecraft.server.level;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

/** Public check producer plus controlled original transport; no task policy model. */
public final class VerifyCoordinatorTaskLifetimes {
    private record Pending(Object tasks, WeakReference<ThreadedLevelLightEngine> engine,
            WeakReference<BlockPos> position, VerifyThreadedLighting.Tasks dispatch,
            VerifyThreadedLighting.Tasks light, VerifyThreadedLighting.LightExecutor executor,
            ChunkTaskDispatcher dispatcher) implements AutoCloseable {
        public void close() { dispatcher.close(); executor.close(); }
    }

    private static Pending enqueue() throws Exception {
        var fixture = new VerifyThreadedLighting.Fixture(false, true);
        var position = new BlockPos(8, 24, 8);
        fixture.engine.checkBlock(position);
        fixture.transport();
        if (fixture.pending() != 1) throw new AssertionError("Deferred check not admitted");
        return new Pending(VerifyThreadedLighting.field(fixture.engine, "lightTasks"),
            new WeakReference<>(fixture.engine), new WeakReference<>(position),
            fixture.dispatch, fixture.light, fixture.executor, fixture.dispatcher);
    }

    private static void update(WeakReference<ThreadedLevelLightEngine> reference) throws Exception {
        ThreadedLevelLightEngine engine = reference.get();
        if (engine == null) throw new AssertionError("Pending command lost its actual engine context");
        VerifyThreadedLighting.invoke(engine, "runUpdate", new Class<?>[0]);
        Reference.reachabilityFence(engine);
    }

    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Pending pending = enqueue()) {
            for (int i = 0; i < 5; i++) { System.gc(); Thread.sleep(20); }
            if (pending.engine.get() == null || pending.position.get() == null)
                throw new AssertionError("Pending check lost an original captured object");
            System.out.println("TASK_LIFETIME pending-engine=true pending-position=true");
            update(pending.engine);
            for (int i = 0; i < 150 && (pending.engine.get() != null || pending.position.get() != null); i++) {
                System.gc(); Thread.sleep(20);
            }
            if (pending.engine.get() != null || pending.position.get() != null)
                throw new AssertionError("Retired command retained engine/position context");
            System.out.println("TASK_LIFETIME retired-engine=false retired-position=false");
            Reference.reachabilityFence(pending.tasks);
        }
    }
}
