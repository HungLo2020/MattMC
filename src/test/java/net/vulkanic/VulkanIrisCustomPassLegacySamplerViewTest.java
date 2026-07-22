package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanIrisCustomPassLegacySamplerViewTest {

	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
	private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

	private static String readSource(String relativePath) throws IOException {
		return Files.readString(SRC_MAIN_JAVA.resolve(relativePath));
	}

	@Test
	void programSamplersFallsBackToLegacySamplerBindingWhenNoGpuTextureViewExists() throws IOException {
		String source = readSource("net/irisshaders/iris/gl/program/ProgramSamplers.java");

		assertTrue(source.contains("TextureTracker.INSTANCE.getTextureView(textureId)"),
			"ProgramSamplers should still prefer tracked GpuTextureViews when one is available");
		assertTrue(source.contains("renderPass instanceof GlRenderPass glRenderPass"),
			"ProgramSamplers should recognize Vulkan-backed GlRenderPass instances for legacy fallback binding");
		assertTrue(source.contains("glRenderPass.bindLegacySampler"),
			"ProgramSamplers should bind a legacy sampler resource when only a raw texture handle is known");
	}

	@Test
	void glRenderPassSupportsLegacySamplerResourceBinding() throws IOException {
		String source = readSource("net/blaze3d/opengl/GlRenderPass.java");

		assertTrue(source.contains("public boolean bindLegacySampler(String string, int textureId)"),
			"GlRenderPass should expose a dedicated legacy sampler binding path for Vulkan custom passes");
		assertTrue(source.contains("this.encoder.createLegacySamplerResourceView(textureId)"),
			"GlRenderPass should recover descriptor resource views from legacy texture handles through GlCommandEncoder");
	}

	@Test
	void vulkanBackendExposesLegacyTextureViewRecovery() throws IOException {
		String apiSource = readSource("net/vulkanic/VulkanicAPI.java");
		String backendSource = readSource("net/vulkanic/backends/vulkan/VulkanBackend.java");

		assertTrue(apiSource.contains("createManagedLegacyTextureView(int legacyTextureHandle)"),
			"VulkanicAPI should expose legacy texture view recovery for custom-pass descriptor binding");
		assertTrue(backendSource.contains("createManagedLegacyTextureView(int legacyTextureHandle)"),
			"VulkanBackend should implement legacy texture view recovery");
		assertTrue(backendSource.contains("spine.createManagedTextureViewForLegacyTexture"),
			"VulkanBackend should route legacy handle recovery through native managed image-view creation");
		assertTrue(backendSource.contains("Skipping Vulkan legacy sampler view recovery for texture {} with unsupported VkFormat 0x{}"),
			"VulkanBackend should fail open when the temporary wrapper cannot represent a legacy Vulkan texture format");
		assertTrue(backendSource.contains("return null;"),
			"Unsupported legacy sampler formats should leave the custom-pass sampler unbound instead of crashing the client");
	}

	@Test
	void fullRangeLegacySamplerViewsReuseDefaultImageView() throws IOException {
		String backendSource = readSource("net/vulkanic/backends/vulkan/VulkanBackend.java");

		assertTrue(backendSource.contains("boolean canUseDefaultView = baseMipLevel == 0 && mipLevelCount == legacyTexture.mipLevels"),
			"Full-range legacy sampler views should use the already-owned default VkImageView");
		assertTrue(backendSource.contains("legacyTexture.defaultViewHandle"),
			"Default legacy image views should remain the sampled descriptor identity for full-range views");
		assertTrue(!backendSource.contains("boolean forceOwnedView = (texture.usage() & VulkanicTexture.USAGE_RENDER_ATTACHMENT) == 0"),
			"Texture wrapper usage must not force per-draw owned VkImageViews that descriptor planning remaps away");
	}
}
