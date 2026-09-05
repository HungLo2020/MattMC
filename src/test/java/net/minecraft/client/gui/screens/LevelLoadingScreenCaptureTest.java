package net.minecraft.client.gui.screens;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the diagnostic-only loading-screen capture boundary. */
class LevelLoadingScreenCaptureTest {
	@Test
	void acknowledgedExternalCaptureIsTheOnlyOptInReasonToHoldLevelLoadingScreen() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LevelLoadingScreen.java"));
		assertTrue(source.contains("Boolean.getBoolean(\"mattmc.dev.levelLoadingScreenCapture\")"));
		assertTrue(source.contains("this.loadTracker.isLevelReady() && !this.awaitingLevelLoadingScreenCapture()"),
			"the real screen may wait only for the capture acknowledgement when explicitly enabled");
		assertTrue(source.contains("capture_request_level_loading_screen.json"));
		assertTrue(source.contains("\\\"captureKind\\\": \\\"level-loading-screen\\\""));
		assertTrue(source.contains("Minecraft.getInstance().getOverlay() != null"),
			"the fixture must not capture a loading-overlay transition as the level-loading UI");
		assertTrue(source.contains("\\\"overlayPresent\\\": false"),
			"the receipt must prove that no Mojang overlay was composited above the screen");
		assertTrue(source.contains("levelLoadingScreenCaptureGridColors")
			&& source.contains("renderCapturedChunks(guiGraphics"),
			"the external capture must retain the emitted grid snapshot until acknowledgement rather than race a later tracker state");
		assertTrue(source.contains("Files.isRegularFile(this.levelLoadingScreenCaptureAck)"),
			"the capture hold must retire from the runner acknowledgement, not a timer or renderer state");
	}
}
