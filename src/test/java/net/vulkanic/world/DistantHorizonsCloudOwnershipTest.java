package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DistantHorizonsCloudOwnershipTest {
	@Test
	void rustWholeFrameRouteKeepsVanillaCloudSemanticProducerAvailable() throws Exception {
		Path source = Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
		String text = Files.readString(source);
		assertTrue(
			text.contains("overrideVanillaGraphicsSettings.get()")
				&& text.contains("&& !VulkanicAPI.isVulkanBackendSelected()")
				&& text.contains("&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"DH must not disable vanilla clouds while its private cloud renderer is unavailable to either selected Rust Vulkan ownership path"
		);
	}
}
