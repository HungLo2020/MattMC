package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanicGalBridgeAbiTest {
	@Test
	void javaLayoutsAreQueriedFromRustAbi() {
		for (VulkanicGalBridge.Struct struct : VulkanicGalBridge.Struct.values()) {
			assertTrue(struct.byteSize() > 0, "byte size should be reported for " + struct);
			assertTrue(struct.alignment() > 0, "alignment should be reported for " + struct);
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = struct.allocate(arena);
				assertEquals(struct.byteSize(), segment.byteSize(), "allocation should use Rust byte size for " + struct);
			}
		}
	}

	@Test
	void malformedBackendKindIsRejectedDeterministically() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment request = VulkanicGalBridge.Struct.CONTEXT_CREATE.allocate(arena);
			VulkanicGalBridge.Abi.writeHeader(request, VulkanicGalBridge.Struct.CONTEXT_CREATE);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 1, 9999);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 2, 0);
			VulkanicGalBridge.Abi.writeBytes(arena, request, VulkanicGalBridge.Struct.CONTEXT_CREATE, 3, "bad-backend");
			MemorySegment result = VulkanicGalBridge.Struct.CONTEXT_RESULT.allocate(arena);

			int status = VulkanicGalBridge.Native.contextCreate(request, result);

			assertEquals(-5, status);
			assertEquals(-5, VulkanicGalBridge.Struct.CONTEXT_RESULT.getInt(result, 1));
		}
	}

	@Test
	void rustBridgeIsOnlyRoutedFromSubsystemBenchmarkControls() throws Exception {
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));
		String bridge = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(subsystem.contains("rust-vulkan") && subsystem.contains("rust-opengl"));
		assertTrue(bridge.contains("Rust VulkanicGAL bridge"));
	}

	@Test
	void bridgePollsCompletionBeforeReadbackAndReportsFinalFfiMetrics() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String benchmark = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(bridge.contains("mattmc_vulkanic_gal_completion_query"));
		assertTrue(benchmark.indexOf("pollCompletion(bridge, submission)") < benchmark.indexOf("bridge.readback(submission"));
		assertTrue(benchmark.contains("VulkanicGalBridge.Status retireStatus = bridge.retire(submission)"));
		assertTrue(benchmark.contains("\\\"completionPolls\\\""));
	}

	@Test
	void rustOpenGlContextFallbackIsExplicitAndDoesNotUseProductionCallsites() throws Exception {
		String context = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/context.rs"));
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));

		assertTrue(context.contains("EGL:") && context.contains("GLX:"));
		assertTrue(context.contains("glXCreatePbuffer"));
		assertTrue(subsystem.contains("RustGraphicsSubsystemBenchmark.run"));
		assertTrue(subsystem.contains("minecraft.stop()"));
	}

	@Test
	void frameAbiV2AddsBorrowedOpenGlAndProductionGuiCrosshairContract() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalFrameQueue.java"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));

		assertEquals(2, VulkanicGalBridge.ABI_VERSION);
		assertTrue(bridge.contains("mattmc_vulkanic_gal_context_create_borrowed_opengl"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_frame_acquire"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_frame_present"));
		assertEquals(1, VulkanicGalBridge.HANDLE_BUFFER);
		assertEquals(2, VulkanicGalBridge.HANDLE_TEXTURE);
		assertEquals(3, VulkanicGalBridge.HANDLE_TEXTURE_VIEW);
		assertEquals(4, VulkanicGalBridge.HANDLE_SAMPLER);
		assertEquals(5, VulkanicGalBridge.HANDLE_SHADER_MODULE);
		assertEquals(6, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT);
		assertEquals(7, VulkanicGalBridge.HANDLE_RESOURCE_SET);
		assertEquals(8, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT);
		assertEquals(9, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE);
		assertEquals(13, VulkanicGalBridge.HANDLE_FRAME_TARGET);
		assertTrue(queue.contains("GUI_CROSSHAIR"));
		assertTrue(queue.contains("GUI_HOTBAR_BASE"));
		assertTrue(queue.contains("HOTBAR_BASE"));
		assertTrue(queue.contains("enqueueHotbarBase"));
		assertTrue(bridge.contains("guiAlphaPipeline"));
		assertTrue(queue.contains("builder.guiAlphaPipeline"));
		assertTrue(queue.contains("DeferredBatchScheduler"));
		assertTrue(queue.contains("CacheKey"));
		assertTrue(queue.contains("cacheHits"));
		assertTrue(queue.contains("completionTimeouts"));
		assertFalse(queue.contains("RETIRE_INTERVAL_FRAMES"));
		assertTrue(queue.contains("if (!force)"));
		assertTrue(queue.contains("rust_gal_frames_executed"));
		assertTrue(queue.contains("destroyHandles(created)"));
		assertTrue(queue.contains("GLFW.glfwGetCurrentContext()"));
		assertTrue(queue.contains("beginFramePass(frameResources.pass(), frameResources.target())"));
		assertTrue(queue.contains("Rust VulkanicGAL partial-frame GUI sprite is unsupported for Vulkan"));
		assertTrue(gameRenderer.contains("RustGalFrameQueue.resize"));
		assertTrue(gameRenderer.contains("RustGalFrameQueue.shutdown"));
		assertTrue(gui.contains("RustGalFrameQueue.enqueueCrosshair"));
		assertTrue(gui.contains("RustGalFrameQueue.enqueueHotbarBase"));
		assertTrue(guiRenderer.contains("RustGalGuiElementRenderState"));
		assertTrue(guiRenderer.contains("RustGalFrameQueue.executeFrame"));
		assertTrue(guiRenderer.contains("try (RenderPass ignored = VulkanicAPI.createRenderPass("));
		assertTrue(
			guiRenderer.indexOf("try (RenderPass ignored = VulkanicAPI.createRenderPass(") < guiRenderer.indexOf("RustGalFrameQueue.executeFrame"),
			"Rust OpenGL must execute while the Java GUI render target is bound so frame_acquire captures the visible framebuffer"
		);
		assertTrue(
			guiRenderer.indexOf("RustGalFrameQueue.executeFrame") < guiRenderer.indexOf("rustGalFrameExecuted.setTrue()"),
			"the combined Rust GUI frame should be marked executed only after the scoped render-pass submission"
		);
	}

	@Test
	void productionCrosshairSliceHasLifecycleInvalidationAndNoJavaFallback() throws Exception {
		String minecraft = Files.readString(Path.of("src/main/java/net/minecraft/client/Minecraft.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalFrameQueue.java"));
		String context = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/context.rs"));
		String openGlResources = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/resources.rs"));

		assertTrue(minecraft.contains("ResourceManagerReloadListener") && minecraft.contains("RustGalFrameQueue.reload()"));
		assertTrue(minecraft.contains("RustGalFrameQueue.cancelPending(\"world-disconnect\")"));
		assertTrue(minecraft.contains("RustGalFrameQueue.cancelPending(\"world-unload\")"));
		assertTrue(queue.contains("SCHEDULER.cancelAll(\"resource-reload\")"));
		assertTrue(queue.contains("SCHEDULER.cancelAll(\"resize\")"));
		assertTrue(queue.contains("SCHEDULER.cancelAll(\"shutdown\")"));
		assertTrue(queue.contains("mattmc.rustGal.guiCrosshair.enabled"));
		assertTrue(queue.contains("rust_gal_ffi_resource_batch_calls"));
		assertTrue(queue.contains("rust_gal_ffi_completion_query_calls"));
		assertTrue(queue.contains("rust_gal_queue_depth"));
		assertTrue(queue.contains("rust_gal_batches_executed"));
		assertTrue(queue.contains("mattmc.dev.guiCrosshair.disabled"));
		assertTrue(queue.contains("mattmc.dev.guiCrosshair.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.legacyControl"));
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()") < gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE"));
		assertTrue(gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE") < gui.indexOf("RustGalFrameQueue.enqueueCrosshair"));
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", gui.indexOf("renderItemHotbar")) < gui.indexOf("blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE"));
		int hotbarMethod = gui.indexOf("renderItemHotbar");
		assertTrue(gui.indexOf("RustGalFrameQueue.enqueueHotbarBase", hotbarMethod) < gui.indexOf("HOTBAR_SELECTION_SPRITE", hotbarMethod));
		assertTrue(context.contains("MAX_COMBINED_TEXTURE_IMAGE_UNITS"));
		assertTrue(openGlResources.contains("current_frame_target_framebuffer"),
			"persistent borrowed frame-target handles must refresh the native OpenGL framebuffer after screen transitions");
		assertTrue(openGlResources.contains("borrowed_frame_targets_follow_latest_acquired_framebuffer"));
	}
}
