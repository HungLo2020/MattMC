package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/** Opt-in test navigation only; does not implement or modify renderer behavior. */
public final class GraphicsAuditRenderingSuspension {
    private static final int REQUESTED_TICKS = Integer.parseInt(
        System.getProperty("mattmc.dev.graphicsAudit.suspendRenderingTicks", "0"));
    private static final Sequence ACTIVE = new Sequence(REQUESTED_TICKS);

    private GraphicsAuditRenderingSuspension() {}

    public static final class Sequence {
        public enum Action { NONE, SUSPEND, RESUME }
        private final int requested;
        private boolean started;
        private int elapsed;
        private boolean complete;

        public Sequence(int requested) {
            if (requested < 0 || requested > 1200) throw new IllegalArgumentException("Invalid suspension tick bound");
            this.requested = requested;
            complete = requested == 0;
        }

        public Action afterTextureTick(boolean ready) {
            if (complete) return Action.NONE;
            if (!started) {
                if (!ready) return Action.NONE;
                started = true;
                return Action.SUSPEND;
            }
            elapsed++;
            if (elapsed == requested) {
                complete = true;
                return Action.RESUME;
            }
            return Action.NONE;
        }

        public int elapsed() { return elapsed; }
        public boolean complete() { return complete; }
    }

    /** Called only after an ordinary eligible texture-manager tick. */
    public static void afterTextureTick(Minecraft minecraft) {
        if (REQUESTED_TICKS == 0) return;
        var action = ACTIVE.afterTextureTick(!minecraft.noRender && minecraft.level != null
            && minecraft.player != null && minecraft.screen == null && minecraft.getOverlay() == null);
        if (action == Sequence.Action.SUSPEND) {
            minecraft.noRender = true;
            System.out.println("[MattMC graphics audit] rendering-suspension started requested_ticks=" + REQUESTED_TICKS);
        } else if (action == Sequence.Action.RESUME) {
            minecraft.noRender = false;
            System.out.println("[MattMC graphics audit] rendering-suspension completed texture_ticks=" + ACTIVE.elapsed());
        }
    }

    public static boolean readyForCapture() { return ACTIVE.complete(); }

    public static void captureState(JsonObject state) {
        if (REQUESTED_TICKS == 0) return;
        state.addProperty("renderingSuspensionFixture", "ordinary-texture-ticks-without-rendering-v1");
        state.addProperty("renderingSuspensionRequestedTicks", REQUESTED_TICKS);
        state.addProperty("renderingSuspensionElapsedTicks", ACTIVE.elapsed());
        state.addProperty("renderingSuspensionComplete", ACTIVE.complete());
    }
}
