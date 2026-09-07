package net.minecraft.client.dev;

/** Capture scheduling only; never changes a game tick, texture, or renderer. */
public final class GraphicsAuditPhaseWait {
    private int attempts;
    public boolean observe(boolean ready) {
        if (ready) { attempts = 0; return true; }
        if (++attempts > 512) throw new IllegalStateException("animation capture phase was not reached within 512 rendered observations");
        return false;
    }
    public static boolean cycleBoundary(long producedTick, long duration) {
        return producedTick > 0 && duration > 0 && producedTick % duration == 0;
    }
    public static long requestedPhase(long duration) {
        final long phase;
        try {
            phase = Long.parseLong(System.getProperty("mattmc.dev.graphicsAuditMagmaCapturePhase", "0"));
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("animation capture phase must be an integer", invalid);
        }
        if (duration <= 0 || phase < 0 || phase >= duration) {
            throw new IllegalStateException("animation capture phase must be inside the declared cycle");
        }
        return phase;
    }
    public static boolean phaseMatches(long producedTick, long duration, long phase) {
        return producedTick > 0 && duration > 0 && phase >= 0 && phase < duration
            && producedTick % duration == phase;
    }
}
