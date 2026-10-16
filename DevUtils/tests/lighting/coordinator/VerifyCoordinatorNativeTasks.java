package net.minecraft.server.level;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

/** Current-only admission nonvacuity; portable semantic observers stay unchanged. */
public final class VerifyCoordinatorNativeTasks {
    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var fixture = new VerifyThreadedLighting.Fixture(false, true)) {
            BlockPos position = new BlockPos(8, 24, 8);
            fixture.engine.checkBlock(position);
            fixture.transport();
            if (fixture.pending() != 1) throw new AssertionError("Stock check was not deferred");
            Object tasks = VerifyThreadedLighting.field(fixture.engine, "lightTasks");
            Object[] roots = (Object[])VerifyThreadedLighting.field(tasks, "references");
            Object[] contexts = (Object[])VerifyThreadedLighting.field(tasks, "contexts");
            if (roots[0] != position || contexts == null || contexts[0] != fixture.engine)
                throw new AssertionError("Stock check used a callable instead of actual typed capture roots");
            VerifyThreadedLighting.invoke(fixture.engine, "runUpdate", new Class<?>[0]);
            if (fixture.pending() != 0 || roots[0] != null || contexts[0] != null)
                throw new AssertionError("Typed command did not retire its original roots");
            System.out.println("NATIVE_TASK stock-check=typed original-captures=true retired=true");
        }
    }
}
