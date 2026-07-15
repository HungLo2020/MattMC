package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.quickplay.QuickPlay;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development-only status/screenshot hook for automated runClient validation.
 *
 * <p>This class is inert unless {@code -Dmattmc.dev.runCapture=true} is set.
 * The PowerShell capture script uses it after quick-play starts a local world:
 * the hook waits until the client has a level, player, connection, no blocking
 * overlay/screen, and a few rendered frames, then emits a status JSON file and
 * one internal screenshot. Normal launches do not set the system property.</p>
 */
public final class RunCaptureAutomation {
    private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-RunCapture");

    private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.runCapture")
            || Boolean.parseBoolean(System.getenv("MATTMC_DEV_RUN_CAPTURE"));
    private static final String REQUESTED_WORLD = getSetting(
            "mattmc.dev.runCapture.world",
            "MATTMC_DEV_RUN_CAPTURE_WORLD",
            ""
    );
    private static final Path STATUS_PATH = getPath(
            "mattmc.dev.runCapture.status",
            "MATTMC_DEV_RUN_CAPTURE_STATUS"
    );
    private static final Path SCREENSHOT_PATH = getPath(
            "mattmc.dev.runCapture.screenshot",
            "MATTMC_DEV_RUN_CAPTURE_SCREENSHOT"
    );
    private static final int MIN_RENDERED_FRAMES = getInt(
            "mattmc.dev.runCapture.minRenderedFrames",
            "MATTMC_DEV_RUN_CAPTURE_MIN_RENDERED_FRAMES",
            8
    );

    private static int renderedFrames;
    private static boolean wroteWaiting;
    private static boolean quickPlayAttempted;
    private static boolean screenshotRequested;
    private static boolean completed;

    private RunCaptureAutomation() {
    }

    public static void afterRender(Minecraft minecraft) {
        if (!ENABLED || completed) {
            return;
        }

        try {
            if (!hasUsableWorld(minecraft)) {
                renderedFrames = 0;
                if (tryStartWorldLoad(minecraft)) {
                    return;
                }
                if (!wroteWaiting) {
                    writeStatus(minecraft, "waiting_for_world", "client has not reached a usable world render state", null);
                    wroteWaiting = true;
                }
                return;
            }

            renderedFrames++;
            if (renderedFrames < MIN_RENDERED_FRAMES) {
                writeStatus(minecraft, "rendering", "waiting for stable rendered frames", null);
                return;
            }

            if (screenshotRequested) {
                return;
            }

            screenshotRequested = true;
            if (SCREENSHOT_PATH == null) {
                completed = true;
                writeStatus(minecraft, "rendered", "usable rendered state reached", null);
                return;
            }

            Files.createDirectories(SCREENSHOT_PATH.getParent());
            writeStatus(minecraft, "capturing_screenshot", "usable rendered state reached", null);
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), nativeImage -> {
                try (nativeImage) {
                    nativeImage.writeToFile(SCREENSHOT_PATH);
                    completed = true;
                    writeStatus(minecraft, "rendered", "usable rendered state reached", SCREENSHOT_PATH);
                    LOGGER.info("Automated run capture screenshot written to {}", SCREENSHOT_PATH);
                } catch (Throwable throwable) {
                    completed = true;
                    writeStatus(minecraft, "failed", throwable.toString(), null);
                    LOGGER.error("Automated run capture screenshot write failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            completed = true;
            writeStatus(minecraft, "failed", throwable.toString(), null);
            LOGGER.error("Automated run capture failed", throwable);
        }
    }

    private static boolean hasUsableWorld(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.getConnection() != null
                && minecraft.getOverlay() == null
                && minecraft.screen == null
                && worldMatches(minecraft);
    }

    private static boolean worldMatches(Minecraft minecraft) {
        if (REQUESTED_WORLD.isBlank()) {
            return true;
        }

        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return false;
        }

        return REQUESTED_WORLD.equals(server.getWorldData().getLevelName());
    }

    private static boolean tryStartWorldLoad(Minecraft minecraft) {
        if (quickPlayAttempted || isBlank(REQUESTED_WORLD) || minecraft.level != null) {
            return false;
        }

        quickPlayAttempted = true;
        writeStatus(minecraft, "loading_world", "requesting singleplayer quick-play world load", null);
        LOGGER.info("Automated run capture loading singleplayer world '{}'", REQUESTED_WORLD);
        QuickPlay.connect(minecraft, new GameConfig.QuickPlaySinglePlayerData(REQUESTED_WORLD));
        return true;
    }

    private static void writeStatus(Minecraft minecraft, String status, String reason, Path screenshotPath) {
        if (STATUS_PATH == null) {
            return;
        }

        try {
            Files.createDirectories(STATUS_PATH.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("status", status);
            json.addProperty("reason", reason);
            json.addProperty("expectedWorld", REQUESTED_WORLD);
            json.addProperty("actualWorld", actualWorldName(minecraft));
            json.addProperty("backend", VulkanicAPI.getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT));
            json.addProperty("dimension", minecraft.level == null ? null : minecraft.level.dimension().location().toString());
            json.addProperty("renderedFrames", renderedFrames);
            json.addProperty("player", minecraft.player == null ? null : minecraft.player.position().toString());
            json.addProperty("screenshot", screenshotPath == null ? null : screenshotPath.toString());
            Files.writeString(STATUS_PATH, json.toString());
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to write automated run capture status to {}", STATUS_PATH, throwable);
        }
    }

    private static String actualWorldName(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        return server == null ? null : server.getWorldData().getLevelName();
    }

    private static Path getPath(String property, String environmentVariable) {
        String value = getSetting(property, environmentVariable, "");
        if (isBlank(value)) {
            return null;
        }
        return Path.of(value);
    }

    private static int getInt(String property, String environmentVariable, int fallback) {
        String value = getSetting(property, environmentVariable, "");
        if (isBlank(value)) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            LOGGER.warn("Invalid integer setting {} / {}: {}", property, environmentVariable, value);
            return fallback;
        }
    }

    private static String getSetting(String property, String environmentVariable, String fallback) {
        String propertyValue = System.getProperty(property);
        if (!isBlank(propertyValue)) {
            return propertyValue;
        }

        String environmentValue = System.getenv(environmentVariable);
        if (!isBlank(environmentValue)) {
            return environmentValue;
        }

        return fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
