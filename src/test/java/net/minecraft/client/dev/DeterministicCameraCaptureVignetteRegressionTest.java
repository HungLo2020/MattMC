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
}
