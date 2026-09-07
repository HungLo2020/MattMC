package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Opt-in window input and post-presentation observation; no GPU handles. */
public final class GraphicsAuditFullscreen {
    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_MENU_FULLSCREEN"));
    private static final Sequence ACTIVE = new Sequence();
    private static java.util.Optional<net.blaze3d.platform.VideoMode> savedMode;
    private GraphicsAuditFullscreen() {}

    public static final class Sequence {
        public enum Action { ENTER, EXIT, WAIT, CAPTURE }
        private int phase;
        private int stable;
        private int width;
        private int height;
        private int fullscreenWidth;
        private int fullscreenHeight;
        public Action afterPresentation(boolean requested, boolean attached, int w, int h) {
            if (phase == 0) {
                if (requested || attached || w != 1280 || h != 720)
                    throw new IllegalStateException("Fullscreen fixture requires windowed1280x720");
                phase = 1;
                return Action.ENTER;
            }
            if (phase == 1) {
                if (!requested || !attached || w <= 0 || h <= 0) { stable = 0; return Action.WAIT; }
                if (w != width || h != height) stable = 0;
                width = w;
                height = h;
                if (++stable < 2) return Action.WAIT;
                fullscreenWidth = w;
                fullscreenHeight = h;
                phase = 2;
                stable = 0;
                return Action.EXIT;
            }
            if (phase == 2) {
                if (requested || attached || w != 1280 || h != 720) { stable = 0; return Action.WAIT; }
                if (++stable < 2) return Action.WAIT;
                phase = 3;
            }
            return Action.CAPTURE;
        }
        public boolean complete() { return phase == 3; }
        public int fullscreenWidth() { return fullscreenWidth; }
        public int fullscreenHeight() { return fullscreenHeight; }
    }

    public static boolean prepareCapture(Minecraft minecraft) {
        if (!ENABLED) return true;
        var window = minecraft.getWindow();
        var action = ACTIVE.afterPresentation(window.isFullscreen(),
            GLFW.glfwGetWindowMonitor(window.handle()) != 0L, window.getWidth(), window.getHeight());
        if (action == Sequence.Action.ENTER) {
            var monitor = window.findBestMonitor();
            if (monitor == null) throw new IllegalStateException("Fullscreen fixture needs a monitor");
            savedMode = window.getPreferredFullscreenVideoMode();
            window.setPreferredFullscreenVideoMode(java.util.Optional.of(monitor.getCurrentMode()));
            window.toggleFullScreen();
        } else if (action == Sequence.Action.EXIT) {
            window.toggleFullScreen();
            window.setPreferredFullscreenVideoMode(savedMode);
        }
        return action == Sequence.Action.CAPTURE;
    }

    public static void captureState(com.google.gson.JsonObject state) {
        if (!ENABLED) return;
        state.addProperty("fullscreenFixture", "fullscreen-restore-1280x720-v1");
        state.addProperty("fullscreenComplete", ACTIVE.complete());
        state.addProperty("fullscreenWidth", ACTIVE.fullscreenWidth());
        state.addProperty("fullscreenHeight", ACTIVE.fullscreenHeight());
    }
}
