package net.vulkanic.world;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DistantHorizonsRouteSelectionOwnershipTest {
	@AfterEach
	void resetCollector() {
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		DistantHorizonsSemanticCollector.resetForTest();
	}

	@Test
	void pendingVisibleSegmentsCannotBecomeRustConsumedWorkBeforeRouteSelection() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			501L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer()),
			List.of(),
			List.of(),
			List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate update =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(update);
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(501L).opaqueSegments());
		assertFalse(DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot().selected());
		assertEquals(
			List.of(),
			DistantHorizonsSemanticCollector.consumeVisibleSegments(),
			"visibility discovery alone must never become submitted Rust work before explicit route selection"
		);
	}

	@Test
	void rustWholeFrameDhHookCannotFallThroughToJavaRenderPasses() throws Exception {
		Path source = Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
		String lodRenderer = Files.readString(source);
		int renderStart = lodRenderer.indexOf("public void render(RenderParams renderParams");
		int renderBodyEnd = lodRenderer.indexOf("private void renderLodPass", renderStart);
		String renderBody = lodRenderer.substring(renderStart, renderBodyEnd);
		assertTrue(renderBody.contains("boolean rustWholeFrame"));
		assertTrue(renderBody.contains("if (rustWholeFrame)"));
		assertTrue(renderBody.indexOf("if (rustWholeFrame)") < renderBody.indexOf("this.renderLodPass(renderParams, profiler, false)"));
		assertTrue(renderBody.contains("renderDeferred"));
		assertTrue(renderBody.contains("Deferred DH passes are Java framebuffer/pipeline work"));
	}

	@Test
	void selectedVulkanWithoutRustAdmissionCannotFallThroughToJavaLodOrUploads() throws Exception {
		String lod = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
		int render = lod.indexOf("public void render(RenderParams renderParams");
		int renderGuard = lod.indexOf("boolean rustPresenterActive = VulkanicAPI.isVulkanBackendSelected()", render);
		int renderJavaPass = lod.indexOf("this.renderLodPass(renderParams, profiler, false)", render);
		assertTrue(render >= 0 && renderGuard > render && renderGuard < renderJavaPass,
			"selected Vulkan must be fenced before DH can enter its Java LOD pass");
		assertTrue(lod.indexOf("if (rustPresenterActive && !rustWholeFrame)", render) > renderGuard,
			"DH must also fence the pre-selection Rust presenter shell before its Java LOD pass");

		int deferred = lod.indexOf("public void renderDeferred(");
		int deferredGuard = lod.indexOf("boolean rustPresenterActive = VulkanicAPI.isVulkanBackendSelected()", deferred);
		int deferredJavaPass = lod.indexOf("this.renderLodPass(renderParams, profiler, true)", deferred);
		assertTrue(deferred >= 0 && deferredGuard > deferred && deferredGuard < deferredJavaPass,
			"selected Vulkan must be fenced before DH deferred Java rendering");
		assertTrue(lod.indexOf("if (rustPresenterActive && !rustWholeFrame)", deferred) > deferredGuard,
			"DH deferred rendering must fence the pre-selection Rust presenter shell");

		String proxy = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/GLProxy.java"));
		assertTrue(proxy.contains("boolean vulkanBackend = VulkanicAPI.isVulkanBackendSelected()")
			&& proxy.contains("|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();"),
			"DH GLProxy capability probing must treat the Rust presenter shell as Vulkan ownership");
		int threadCheck = proxy.indexOf("public static boolean runningOnRenderThread()");
		int threadVulkan = proxy.indexOf("|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()", threadCheck);
		assertTrue(threadCheck >= 0 && threadVulkan > threadCheck,
			"DH render-thread checks must use the backend-neutral Vulkan thread seam during handoff");
		int upload = proxy.indexOf("public static void runLodUploadRenderThreadTasks()");
		int uploadGuard = proxy.indexOf("VulkanicAPI.isVulkanBackendSelected()", upload);
		int uploadTask = proxy.indexOf("runRenderThreadTasks(VULKAN_LOD_UPLOAD_TASK_BUDGET_NANOS", upload);
		assertTrue(upload >= 0 && uploadGuard > upload && uploadTask < 0,
			"selected Vulkan must not drain DH Java upload tasks through the compatibility backend");
	}

	@Test
	void selectedRustDhParityTracingDoesNotRecoverJavaLightmapView() throws Exception {
		String lod = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
		int method = lod.indexOf("private static void traceDhLodTerrainResources(");
		int view = lod.indexOf("lightTexture().getTextureView()", method);
		int selectedGuard = lod.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		assertTrue(method >= 0 && selectedGuard > method && selectedGuard < view,
			"Rust-selected DH parity tracing must not recover a Java lightmap view");
	}

	private static ByteBuffer quadBuffer() {
		ByteBuffer buffer = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4)
			.order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 4; vertex++) {
			buffer.putShort((short)vertex);
			buffer.putShort((short)2);
			buffer.putShort((short)3);
			buffer.putShort((short)0xB7);
			buffer.put((byte)11);
			buffer.put((byte)12);
			buffer.put((byte)13);
			buffer.put((byte)255);
			buffer.put((byte)15);
			// Rust's semantic LOD ABI admits the six canonical face normals 0..5.
			buffer.put((byte)5);
			buffer.putShort((short)0);
		}
		return buffer.flip();
	}
}
