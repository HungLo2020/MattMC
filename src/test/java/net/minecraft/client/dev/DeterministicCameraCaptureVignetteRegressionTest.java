package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicCameraCaptureVignetteRegressionTest {
	@Test
	void deterministicCaptureForcesVignetteBrightnessInsteadOfCapturingRuntimeState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(source.contains("mattmc.dev.deterministicCameraCapture.vignetteBrightness"),
			"Deterministic capture must expose an explicit fixed vignette brightness for OpenGL/Vulkan geometry parity");
		assertTrue(source.contains("minecraft.gui.vignetteBrightness = FIXED_VIGNETTE_BRIGHTNESS;"),
			"Deterministic capture must not preserve backend-dependent runtime vignette brightness");
		assertTrue(source.contains("stabilizeGuiState(minecraft);"),
			"Deterministic capture must stabilize GUI state before pose screenshots and shader-input diagnostics");
	}

	@Test
	void deterministicCaptureCanExerciseRustGalGuiInventoryRecovery() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(source.contains("mattmc.dev.deterministicCameraCapture.rustGalGuiScreenCycle"),
			"Deterministic capture must expose a bounded inventory open/close regression cycle for Rust GAL GUI recovery");
		assertTrue(source.contains("new InventoryScreen(minecraft.player)"),
			"The regression cycle must open the real inventory screen instead of simulating only metadata");
		assertTrue(source.contains("minecraft.setScreen(null)"),
			"The regression cycle must close inventory and return to gameplay HUD before the screenshot");
		assertTrue(source.contains("cyclesCompleted"),
			"The deterministic artifact must record screen-cycle state for capture validation");
	}
}
