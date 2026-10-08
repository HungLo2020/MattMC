package net.minecraft.client.dev;

import java.util.ArrayList;
import java.util.List;

/** Bounded observations of existing clocks. Never supplies rendering inputs. */
public final class GraphicsAuditGroundFoilTiming {
    private static final List<String> samples = new ArrayList<>();
    private static final List<Long> observedTicks = new ArrayList<>();
    private static int semanticCount, worldCount;
    private static boolean world;
    private static boolean valid = true;
    private static long frameSequence;
    private static long renderedFrameIndex;
    private GraphicsAuditGroundFoilTiming() {}

    public static boolean enabled() { return Boolean.getBoolean("mattmc.dev.groundItemFoilTiming"); }

    public static synchronized void beginFrame() {
        if (enabled()) beginFrame(GraphicsAuditGroundFoilSources.currentFrameIndex());
    }

    static synchronized void beginFrame(long frame) {
        if (!enabled()) return;
        samples.clear(); observedTicks.clear(); semanticCount = worldCount = 0; valid = !world && frame > 0; world = false; frameSequence++;
        renderedFrameIndex = frame;
    }

    public static synchronized void beginWorld() {
        if (!enabled()) return;
        if (world) valid = false;
        world = true;
    }

    public static synchronized void endWorld() {
        if (!enabled()) return;
        if (!world) valid = false;
        world = false;
    }

    /** Frozen observes the existing value used to set world glint texturing. */
    public static synchronized void observeScaledTicks(long ticks, float scale) {
        if (!enabled() || !world) return;
        if (ticks < 0 || scale != 8F) { valid = false; return; }
        append("{\"provider\":\"frozen-world-state\",\"scaledTicks\":" + ticks + "}", ticks, false);
    }

    /** Current observes each immutable world decal payload as it is queued. */
    public static synchronized void observeSemanticClock(long meshKey, long clock, double speed, float strength) {
        if (!enabled()) return;
        if (meshKey == 0 || clock < 0 || !Double.isFinite(speed) || speed < 0 || speed > 1
                || !Float.isFinite(strength) || strength < 0 || strength > 1) { valid = false; return; }
        append("{\"provider\":\"semantic-world\",\"meshKey\":\"" + Long.toUnsignedString(meshKey)
            + "\",\"clockMillis\":" + clock + ",\"speed\":" + speed + ",\"strength\":" + strength
            + ",\"scaledTicks\":" + (long)(clock * speed * 8.0) + "}", (long)(clock * speed * 8.0), true);
    }

    private static void append(String sample, long ticks, boolean semantic) {
        if (samples.size() >= 16) { valid = false; return; }
        samples.add(sample); observedTicks.add(ticks);
        if (semantic) semanticCount++; else worldCount++;
    }

    /** Select screenshots from observed phases only; never changes clocks or draw input. */
    public static synchronized boolean readyForCapture(int target) {
        if (!enabled()) return true;
        if (target != 10000 && target != 40000) throw new IllegalArgumentException("unsupported ground foil phase");
        if (!valid || world || !((semanticCount == 2 && worldCount == 0)
                || (semanticCount == 0 && worldCount >= 1 && worldCount <= 2))) return false;
        for (long tick : observedTicks) {
            if (!GraphicsAuditGuiFoilTiming.phaseMatches(tick,target) || !pairedPhaseMatches(tick)) return false;
            for (long other : observedTicks) if (!withinWindow(tick,other)) return false;
        }
        return true;
    }

    private static boolean withinWindow(long observed, long other) {
        long distance = Math.floorMod(observed - other,330000L);
        return distance <= 16 || distance >= 330000L - 16;
    }

    static boolean pairedPhaseMatches(long observed) {
        String configured = System.getProperty("mattmc.dev.graphicsAuditGroundFoilPhaseTargets");
        if (configured == null) return true;
        String[] targets = configured.split(",",-1);
        if (targets.length != 2) throw new IllegalArgumentException("ground alignment requires both native clocks");
        for (String value : targets) {
            int target = Integer.parseInt(value);
            if (target < 0 || target >= 330000) throw new IllegalArgumentException("invalid ground alignment target");
            if (observed < 0 || !withinWindow(observed,target)) return false;
        }
        return true;
    }

    public static synchronized String snapshot() {
        return "{\"enabled\":" + enabled() + ",\"complete\":" + (valid && !world)
            + ",\"schema\":\"ground-foil-frame-timing-v1\",\"frameSequence\":" + frameSequence
            + ",\"renderedFrameIndex\":" + renderedFrameIndex + ",\"samples\":[" + String.join(",",samples) + "]}";
    }

    static synchronized void resetForTest() {
        samples.clear(); observedTicks.clear(); semanticCount = worldCount = 0; world = false; valid = true; frameSequence = 0; renderedFrameIndex = 0;
    }
}
