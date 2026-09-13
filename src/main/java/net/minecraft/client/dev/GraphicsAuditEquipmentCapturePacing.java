package net.minecraft.client.dev;

/** Capture-only wait before a natural clock read; never supplies a clock sample. */
final class GraphicsAuditEquipmentCapturePacing {
    static long delayNanos(long observedTicks, long target) {
        if (observedTicks < 0 || target < 0 || target >= 330000) return 0;
        long remaining = Math.floorMod(target - Math.floorMod(observedTicks, 330000L), 330000L);
        return remaining <= 200 ? remaining * 250_000L : 0;
    }
}
