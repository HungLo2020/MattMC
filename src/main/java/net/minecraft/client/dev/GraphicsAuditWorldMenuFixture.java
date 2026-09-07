package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Opt-in deterministic test navigation. Does not implement rendering. */
public final class GraphicsAuditWorldMenuFixture {
    private GraphicsAuditWorldMenuFixture() {}
    private static boolean requested() {
        return Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.optionsMenu");
    }

    /** True withholds capture while waiting for or performing menu navigation. */
    public static boolean afterRender(Minecraft minecraft) {
        if (!GraphicsAuditRenderingSuspension.readyForCapture()) return true;
        if (!requested() || minecraft.screen instanceof OptionsScreen) return false;
        // Pausing too early freezes the tick-driven vignette away from its
        // target. Observe the existing readiness contract before navigating;
        // never relax the capture's brightness gate or modify the brightness.
        boolean settled = minecraft.gui.vignetteBrightnessSettledForDeterministicCapture(minecraft.getCameraEntity());
        if (!shouldOpen(true, minecraft.level != null && minecraft.player != null,
            minecraft.getOverlay() != null, minecraft.screen != null, settled)) return true;
        minecraft.setScreen(new OptionsScreen(new PauseScreen(true), minecraft.options));
        System.out.println("[MattMC graphics audit] world-menu opened OptionsScreen");
        return true;
    }

    static boolean shouldOpen(boolean requested, boolean worldReady, boolean overlay, boolean screen, boolean settled) {
        return requested && worldReady && !overlay && !screen && settled;
    }

    public static JsonObject receipt(Minecraft minecraft) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "mattmc-world-menu-fixture-v1");
        result.addProperty("requested", requested());
        result.addProperty("worldPresent", minecraft.level != null);
        GraphicsAuditRenderingSuspension.captureState(result);
        result.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
        return result;
    }
}
