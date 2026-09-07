package net.minecraft.client.dev;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Opt-in normal resource reload, with completion and presentation evidence. */
public final class GraphicsAuditResourceReload {
    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_MENU_RELOAD"));
    private static final Sequence ACTIVE = new Sequence();
    private static CompletableFuture<Void> reload;
    private static int observedFrames;
    public static void observe(Minecraft minecraft) {
        if (!ENABLED || observedFrames++ >= 1200 || observedFrames % 60 != 1) return;
        System.out.println("[MattMC graphics audit] reload-observation requested=" + (reload != null)
            + " done=" + (reload != null && reload.isDone()) + " complete=" + ACTIVE.complete()
            + " screen=" + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName())
            + " overlay=" + (minecraft.getOverlay() == null ? "null" : minecraft.getOverlay().getClass().getSimpleName())
            + " titleFadeReady=" + net.minecraft.client.gui.screens.TitleScreen.graphicsAuditTitleScreenFadeComplete());
    }
    private GraphicsAuditResourceReload() {}

    public static final class Sequence {
        public enum Action { RELOAD, WAIT, CAPTURE }
        private boolean requested;
        private int presentations;
        public Action afterPresentation(boolean done, boolean overlayPresent) {
            if (!requested) { requested = true; return Action.RELOAD; }
            if (!done || overlayPresent) { presentations = 0; return Action.WAIT; }
            return ++presentations >= 2 ? Action.CAPTURE : Action.WAIT;
        }
        public boolean complete() { return requested && presentations >= 2; }
    }

    public static boolean prepareCapture(Minecraft minecraft) {
        if (!ENABLED) return true;
        if (reload != null && reload.isDone()) reload.join(); // failures cannot become successful evidence
        var action = ACTIVE.afterPresentation(reload != null && reload.isDone(), minecraft.getOverlay() != null);
        if (action == Sequence.Action.RELOAD) reload = minecraft.reloadResourcePacks();
        return action == Sequence.Action.CAPTURE;
    }

    public static void captureState(com.google.gson.JsonObject state) {
        if (!ENABLED) return;
        state.addProperty("resourceReloadFixture", "normal-reload-complete-presented-v1");
        state.addProperty("resourceReloadComplete", ACTIVE.complete());
    }
}
