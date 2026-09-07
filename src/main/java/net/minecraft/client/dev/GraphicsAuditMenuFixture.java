package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Opt-in test navigation only; never changes draw data or GPU state. */
public final class GraphicsAuditMenuFixture {
    public enum Decision { WAIT, OPEN_OPTIONS, OPEN_VIDEO, CAPTURE }

    private static final GraphicsAuditMenuFixture ACTIVE = new GraphicsAuditMenuFixture(
        "true".equalsIgnoreCase(System.getenv("MATTMC_TITLE_SCREEN_CAPTURE"))
            ? System.getenv("MATTMC_CAPTURE_MENU_SCREEN") : null);
    private final boolean options;
    private final boolean video;
    private boolean opened;
    private int presentedFrames;
    private static final ResizeSequence RESIZE = "true".equalsIgnoreCase(System.getenv("MATTMC_TITLE_SCREEN_CAPTURE"))
        && "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_MENU_RESIZE"))
        ? new ResizeSequence() : null;
    private static final MaximizeSequence MAXIMIZE = RESIZE != null
        && "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_MENU_MAXIMIZE"))
        ? new MaximizeSequence() : null;

    /** Observes window state after presentation; never changes renderer state. */
    public static final class MaximizeSequence {
        public enum Action { WAIT, MAXIMIZE, RESTORE, SET_FINAL_SIZE, CAPTURE }
        private int phase;
        private int stableFrames;
        private int observedWidth;
        private int observedHeight;

        public Action afterPresentation(boolean maximized, int width, int height) {
            if (phase == 0) { phase = 1; return Action.MAXIMIZE; }
            if (phase == 1) {
                if (!maximized || width <= 0 || height <= 0) {
                    stableFrames = 0;
                    return Action.WAIT;
                }
                if (width != observedWidth || height != observedHeight) stableFrames = 0;
                observedWidth = width;
                observedHeight = height;
                if (++stableFrames < 2) return Action.WAIT;
                phase = 2;
                stableFrames = 0;
                return Action.RESTORE;
            }
            if (phase == 2) {
                if (maximized) return Action.WAIT;
                phase = 3;
                return Action.SET_FINAL_SIZE;
            }
            if (phase == 3) {
                if (maximized || width != 1280 || height != 720) {
                    stableFrames = 0;
                    return Action.WAIT;
                }
                if (++stableFrames < 2) return Action.WAIT;
                phase = 4;
            }
            return Action.CAPTURE;
        }
        public boolean complete() { return phase == 4; }
        public int observedWidth() { return observedWidth; }
        public int observedHeight() { return observedHeight; }
    }

    /** Test input only: each ordinary window resize must be presented twice. */
    public static final class ResizeSequence {
        private static final int[][] SIZES = {{640, 480}, {1600, 900}, {1280, 720}};
        private int step;
        private boolean requested;
        private int stableFrames;

        public int[] afterPresentation(int width, int height) {
            if (complete()) return null;
            if (!requested) {
                requested = true;
                return SIZES[step].clone();
            }
            if (width != SIZES[step][0] || height != SIZES[step][1]) {
                stableFrames = 0;
                return null;
            }
            if (++stableFrames < 2) return null;
            step++;
            stableFrames = 0;
            requested = false;
            return complete() ? null : afterPresentation(width, height);
        }

        public boolean complete() { return step == SIZES.length; }
        public int completedSteps() { return step; }
    }

    public GraphicsAuditMenuFixture(String requested) {
        if (requested != null && !requested.equals("title") && !requested.equals("options") && !requested.equals("video")) {
            throw new IllegalArgumentException("Unsupported graphics audit menu: " + requested);
        }
        options = "options".equals(requested);
        video = "video".equals(requested);
    }

    public Decision afterPresentation(String screen) {
        if (!options && !video) return "TitleScreen".equals(screen) ? Decision.CAPTURE : Decision.WAIT;
        if (!opened && "TitleScreen".equals(screen)) {
            opened = true;
            return video ? Decision.OPEN_VIDEO : Decision.OPEN_OPTIONS;
        }
        if (!opened || !(video ? "SodiumOptionsGUI" : "OptionsScreen").equals(screen)) {
            presentedFrames = 0;
            return Decision.WAIT;
        }
        return ++presentedFrames >= 2 ? Decision.CAPTURE : Decision.WAIT;
    }

    public static boolean isRequestedScreen() {
        return (ACTIVE.options && Minecraft.getInstance().screen instanceof OptionsScreen)
            || (ACTIVE.video && Minecraft.getInstance().screen instanceof net.sodium.client.gui.SodiumOptionsGUI);
    }

    /** Observed state of the presented fixture; no settings or rendering are changed. */
    public static com.google.gson.JsonObject captureState() {
        Minecraft minecraft = Minecraft.getInstance();
        var state = new com.google.gson.JsonObject();
        state.addProperty("schema", "mattmc-presented-menu-state-v1");
        state.addProperty("worldPresent", minecraft.level != null);
        state.addProperty("guiScale", minecraft.getWindow().getGuiScale());
        state.addProperty("menuBlur", minecraft.options.menuBackgroundBlurriness().get());
        state.addProperty("panoramaTheme", minecraft.options.panoramaTheme().get().getSerializedName());
        state.addProperty("hideSplashTexts", minecraft.options.hideSplashTexts().get());
        GraphicsAuditVideoTabs.captureState(state);
        GraphicsAuditGuiScale.captureState(state);
        GraphicsAuditFullscreen.captureState(state);
        GraphicsAuditResourceReload.captureState(state);
        if ("true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_MENU_RESOURCES"))) {
            state.add("resourceScope", GraphicsAuditMenuResources.capture(
                minecraft.getResourceManager(), minecraft.options.languageCode));
        }
        if (RESIZE != null) {
            state.addProperty("resizeFixture", "640x480-1600x900-1280x720-v1");
            state.addProperty("resizeCompletedSteps", RESIZE.completedSteps());
            state.addProperty("resizeComplete", RESIZE.complete());
        }
        if (MAXIMIZE != null) {
            state.addProperty("maximizeFixture", "maximize-restore-1280x720-v1");
            state.addProperty("maximizeComplete", MAXIMIZE.complete());
            state.addProperty("maximizedWidth", MAXIMIZE.observedWidth());
            state.addProperty("maximizedHeight", MAXIMIZE.observedHeight());
        }
        return state;
    }

    /** Invoked only after ordinary presentation, with the startup fade finished. */
    public static boolean prepareCapture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return false;
        Decision decision = ACTIVE.afterPresentation(minecraft.screen.getClass().getSimpleName());
        if (decision == Decision.OPEN_OPTIONS) {
            minecraft.setScreen(new OptionsScreen(minecraft.screen, minecraft.options));
        }
        if (decision == Decision.OPEN_VIDEO) {
            minecraft.setScreen(net.sodium.client.gui.SodiumOptionsGUI.createScreen(minecraft.screen));
        }
        if (decision != Decision.CAPTURE) return false;
        if (!GraphicsAuditVideoTabs.prepareCapture(minecraft)) return false;
        if (!GraphicsAuditGuiScale.prepareCapture(minecraft)) return false;
        if (!GraphicsAuditFullscreen.prepareCapture(minecraft)) return false;
        if (!GraphicsAuditResourceReload.prepareCapture(minecraft)) return false;
        if (RESIZE != null) {
            if (!ACTIVE.options) throw new IllegalStateException("Resize fixture requires Options");
            var window = minecraft.getWindow();
            int[] next = RESIZE.afterPresentation(window.getWidth(), window.getHeight());
            if (next != null) {
                System.out.println("[MattMC graphics audit] menu-resize-request width=" + next[0] + " height=" + next[1]
                    + " completedSteps=" + RESIZE.completedSteps());
                window.setWindowed(next[0], next[1]);
            }
            if (!RESIZE.complete()) return false;
        }
        if (MAXIMIZE != null) {
            var window = minecraft.getWindow();
            boolean maximized = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(
                window.handle(), org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED) == org.lwjgl.glfw.GLFW.GLFW_TRUE;
            var action = MAXIMIZE.afterPresentation(maximized, window.getWidth(), window.getHeight());
            if (action != MaximizeSequence.Action.WAIT) {
                System.out.println("[MattMC graphics audit] menu-maximize action=" + action
                    + " maximized=" + maximized + " extent=" + window.getWidth() + "x" + window.getHeight());
            }
            switch (action) {
                case MAXIMIZE -> org.lwjgl.glfw.GLFW.glfwMaximizeWindow(window.handle());
                case RESTORE -> org.lwjgl.glfw.GLFW.glfwRestoreWindow(window.handle());
                case SET_FINAL_SIZE -> window.setWindowed(1280, 720);
                default -> { }
            }
            return action == MaximizeSequence.Action.CAPTURE;
        }
        return true;
    }
}
