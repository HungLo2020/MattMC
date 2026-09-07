package net.minecraft.client.dev;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Diagnostic handshake only: never advances clocks, renders, or presents. */
public final class GraphicsAuditPresentedFrameWait {
    private GraphicsAuditPresentedFrameWait() {}

    static void await(BooleanSupplier acknowledged, LongSupplier clock, Runnable pause, long budgetNanos) {
        if (budgetNanos <= 0) throw new IllegalArgumentException("capture wait budget must be positive");
        long start = clock.getAsLong();
        while (!acknowledged.getAsBoolean()) {
            if (clock.getAsLong() - start >= budgetNanos) {
                throw new IllegalStateException("timed out waiting for the presented diagnostic frame capture");
            }
            pause.run();
        }
    }
}
