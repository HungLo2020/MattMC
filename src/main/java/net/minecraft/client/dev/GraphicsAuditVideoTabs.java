package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.sodium.client.gui.SodiumOptionsGUI;
import net.sodium.client.gui.options.OptionPage;
import net.sodium.client.gui.widgets.FlatButtonWidget;

/** Opt-in UI input and observation only. Never accesses GPU or Iris state. */
public final class GraphicsAuditVideoTabs {
    private static final Sequence ACTIVE = new Sequence();
    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getenv("MATTMC_CAPTURE_VIDEO_TABS"));
    private GraphicsAuditVideoTabs() {}
    private static String label(String page) {
        return Component.translatable("general".equals(page) ? "stat.generalButton"
            : "sodium.options.pages." + page).getString();
    }

    public static final class Sequence {
        private static final String[] PAGES = {"quality", "performance", "advanced", "general"};
        private int step;
        private int presentations;
        private boolean clicked;
        public String requestedPage() { return complete() ? null : PAGES[step]; }
        public boolean needsClick() { return !complete() && !clicked; }
        public void clicked(boolean accepted) {
            if (!accepted || !needsClick()) throw new IllegalStateException("Video tab input was not accepted");
            clicked = true;
        }
        public void presented(String page) {
            if (complete()) return;
            if (!clicked || !PAGES[step].equals(page)) { presentations = 0; return; }
            if (++presentations == 2) { step++; clicked = false; presentations = 0; }
        }
        public boolean complete() { return step == PAGES.length; }
        public int completedSteps() { return step; }
    }

    public static boolean prepareCapture(Minecraft minecraft) {
        if (!ENABLED) return true;
        if (!(minecraft.screen instanceof SodiumOptionsGUI screen)) {
            throw new IllegalStateException("Video tab fixture requires Video Settings");
        }
        // Read-only observation of UI selection, not a renderer/runtime field.
        final OptionPage page;
        try {
            var field = SodiumOptionsGUI.class.getDeclaredField("currentPage");
            field.setAccessible(true);
            page = (OptionPage) field.get(screen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot observe selected video tab", exception);
        }
        String requested = ACTIVE.requestedPage();
        String label = requested == null ? "" : label(requested);
        ACTIVE.presented(page != null && page.getName().getString().equals(label) ? requested : null);
        if (ACTIVE.complete()) return true;
        if (ACTIVE.needsClick()) {
            String nextLabel = label(ACTIVE.requestedPage());
            FlatButtonWidget target = null;
            for (var child : screen.children()) {
                if (child instanceof FlatButtonWidget button && button.getLabel().getString().equals(nextLabel)) {
                    if (target != null) throw new IllegalStateException("Ambiguous video tab");
                    target = button;
                }
            }
            if (target == null) throw new IllegalStateException("Missing video tab: " + nextLabel);
            var rect = target.getRectangle();
            ACTIVE.clicked(target.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                rect.left() + rect.width() / 2.0, rect.top() + rect.height() / 2.0,
                new net.minecraft.client.input.MouseButtonInfo(0, 0)), false));
        }
        return false;
    }

    public static void captureState(com.google.gson.JsonObject state) {
        if (!ENABLED) return;
        state.addProperty("videoTabsFixture", "quality-performance-advanced-general-v1");
        state.addProperty("videoTabsCompletedSteps", ACTIVE.completedSteps());
        state.addProperty("videoTabsComplete", ACTIVE.complete());
    }
}
