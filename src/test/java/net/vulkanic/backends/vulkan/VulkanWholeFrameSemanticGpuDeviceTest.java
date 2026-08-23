package net.vulkanic.backends.vulkan;

import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanWholeFrameSemanticGpuDeviceTest {
	@Test
	void storesBoundedStartupUniformBytesWithoutAGpuHandle() {
		VulkanWholeFrameSemanticGpuDevice device = new VulkanWholeFrameSemanticGpuDevice();
		GpuBuffer buffer = device.createBuffer(() -> "startup-uniform", 128, 8);
		CommandEncoder encoder = device.createCommandEncoder();
		encoder.writeToBuffer(buffer.slice(2, 4), ByteBuffer.wrap(new byte[] {3, 5, 7, 11}));

		try (GpuBuffer.MappedView mapped = encoder.mapBuffer(buffer, true, false)) {
			ByteBuffer bytes = mapped.data();
			assertEquals(0, bytes.get(0));
			assertEquals(3, bytes.get(2));
			assertEquals(11, bytes.get(5));
		}
		assertFalse(buffer.isClosed());
		buffer.close();
		assertThrows(IllegalStateException.class, () -> encoder.mapBuffer(buffer, true, false));
	}

	@Test
	void tracksTextureMetadataButRejectsJavaRendering() {
		VulkanWholeFrameSemanticGpuDevice device = new VulkanWholeFrameSemanticGpuDevice();
		GpuTexture texture = device.createTexture("semantic-target", 15, TextureFormat.RGBA8, 32, 16, 1, 1);
		GpuTextureView view = device.createTextureView(texture);

		assertEquals(32, view.getWidth(0));
		assertEquals(16, view.getHeight(0));
		assertThrows(
			UnsupportedOperationException.class,
			() -> device.createCommandEncoder().createRenderPass(() -> "illegal-java-render", view, OptionalInt.empty())
		);
	}

	@Test
	void apiRenderPassAndNativeTerrainSeamsFailClosedForWholeFrameMode() {
		RustGalVulkanWholeFrameMode.markVulkanBackendSelected();
		try {
			assertThrows(
				IllegalStateException.class,
				() -> VulkanicAPI.createRenderPass(() -> "bypassed-java-pass", null, OptionalInt.empty())
			);
			assertThrows(
				IllegalStateException.class,
				VulkanicAPI::createNativeTerrainCommandEncoder
			);
			RustGalVulkanWholeFrameMode.activateRustPresentation();
			assertThrows(IllegalStateException.class, VulkanicAPI::getCommandContext);
		} finally {
			RustGalVulkanWholeFrameMode.deactivateRustPresentation();
			RustGalVulkanWholeFrameMode.clearVulkanBackendSelection();
		}
	}
}
