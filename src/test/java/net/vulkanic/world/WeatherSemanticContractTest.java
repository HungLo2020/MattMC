package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeatherSemanticContractTest {
	@Test
	void weatherAdmissionChecksCombinedPendingMaterialCapacityBeforePublication() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int weather = source.indexOf("public static void enqueueWorldWeather(");
		int capacity = source.indexOf("MAX_RUST_WORLD_MATERIAL_QUADS - PENDING_MATERIAL_QUADS.size()", weather);
		int publish = source.indexOf("PENDING_MATERIAL_QUADS.addAll(quads)", weather);
		assertTrue(weather >= 0 && capacity > weather && publish > capacity,
			"weather must check combined Rust material capacity before publishing its quad batch");
	}

	@Test
	void weatherAdmissionRejectsNonFiniteColumnHeightsBeforeGeometryExpansion() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int weather = source.indexOf("public static void enqueueWorldWeather(");
		int rain = source.indexOf("!Float.isFinite(column.topY())", weather);
		int snow = source.indexOf("!Float.isFinite(column.topY())", rain + 1);
		assertTrue(weather >= 0 && rain > weather && snow > rain,
			"both rain and snow semantic columns must reject non-finite copied heights before expansion");
	}

	@Test
	void weatherAdmissionRejectsNullCopiedColumnsBeforeFieldAccess() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int weather = source.indexOf("public static void enqueueWorldWeather(");
		int nullCheck = source.indexOf("column == null", weather);
		int rain = source.indexOf("malformed rain column semantics", nullCheck);
		int snow = source.indexOf("malformed snow column semantics", rain + 1);
		assertTrue(weather >= 0 && nullCheck > weather && rain > nullCheck && snow > rain,
			"rain and snow loops must reject null copied columns before dereferencing semantic fields");
	}

	@Test
	void rustWholeFrameShellExtractsWeatherWithoutCallingJavaWeatherRenderer() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int shell = source.indexOf("renderRustVulkanWholeFrameShell");
		int enqueue = source.indexOf("enqueueRustGalWeatherForWholeFrame", shell);
		assertTrue(shell >= 0 && enqueue > shell,
			"Rust whole-frame shell must copy weather semantics through LevelRenderer before submission");
		String shellSource = source.substring(shell, source.indexOf("\n\t}", enqueue));
		assertTrue(!shellSource.contains("weatherEffectRenderer.render"),
			"Rust whole-frame shell must not invoke Java weather rendering");
	}

	@Test
	void rustWholeFrameSkyExtractionIsOrderedBeforeSubmissionAndJavaSkyDrawsAreFenced() throws Exception {
		String gameRenderer = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int shell = gameRenderer.indexOf("renderRustVulkanWholeFrameShell");
		int sky = gameRenderer.indexOf("enqueueRustGalSkyForWholeFrame", shell);
		assertTrue(shell >= 0 && sky > shell,
			"Rust whole-frame shell must extract sky semantics before submitting the frame");

		String skyRenderer = Files.readString(Path.of(
			"src/main/java/net/minecraft/client/renderer/SkyRenderer.java"));
		int draw = skyRenderer.indexOf("public void renderSunMoonAndStars");
		int fence = skyRenderer.indexOf("ensureJavaSkyRenderingAvailable();", draw);
		assertTrue(draw >= 0 && fence > draw,
			"all Java celestial drawing must be fenced when Rust owns presentation");
	}

	@Test
	void skyAdmissionRejectsMissingCopiedSkyTypeBeforeFieldUse() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		int method = source.indexOf("public static void enqueueWorldSky(SkyRenderState state, boolean visible, @Nullable Camera camera)");
		int guard = source.indexOf("state.skyType == null", method);
		int lock = source.indexOf("synchronized (LOCK)", guard);
		assertTrue(method >= 0 && guard > method && lock > guard,
			"sky admission must reject a missing copied sky type before synchronized publication");
	}
}
