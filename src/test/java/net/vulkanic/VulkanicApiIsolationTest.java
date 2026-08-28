package net.vulkanic;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicApiIsolationTest {
	@Test
	void defaultUniformBindingIsFencedBeforeCompatibilityStateReads() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int method = source.indexOf("public static void bindDefaultUniforms(RenderPass renderPass)");
		int guard = source.indexOf("Java Vulkan default-uniform binding is disabled", method);
		int stateRead = source.indexOf("getProjectionMatrixBuffer()", method);
		assertTrue(method >= 0 && guard > method && stateRead > guard,
			"selected Vulkan must reject Java default-uniform binding before reading compatibility state");
	}

	@Test
	void outputTargetOverridesAreFencedFromJavaGpuViews() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int color = source.indexOf("public static void setOutputColorTextureOverride(");
		int colorGuard = source.indexOf("Java Vulkan color-target override is disabled", color);
		int colorStore = source.indexOf("outputColorTextureOverride = gpuTextureView", color);
		int depth = source.indexOf("public static void setOutputDepthTextureOverride(");
		int depthGuard = source.indexOf("Java Vulkan depth-target override is disabled", depth);
		int depthStore = source.indexOf("outputDepthTextureOverride = gpuTextureView", depth);
		assertTrue(color >= 0 && colorGuard > color && colorStore > colorGuard
			&& depth >= 0 && depthGuard > depth && depthStore > depthGuard,
			"selected Vulkan must reject Java color/depth target views before storing them");
	}
}
