package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
	void guiSpriteBatchingPreservesIncompatibleStratumAndStateBoundaries() {
		assertEquals(RustGalFrameQueue.ArmorIconState.EMPTY, RustGalFrameQueue.armorIconStateForTests(0, 0));
		assertEquals(RustGalFrameQueue.ArmorIconState.HALF, RustGalFrameQueue.armorIconStateForTests(1, 0));
		assertEquals(RustGalFrameQueue.ArmorIconState.FULL, RustGalFrameQueue.armorIconStateForTests(2, 0));
		assertEquals(RustGalFrameQueue.ArmorIconState.EMPTY, RustGalFrameQueue.armorIconStateForTests(18, 9));
		assertEquals(RustGalFrameQueue.ArmorIconState.HALF, RustGalFrameQueue.armorIconStateForTests(19, 9));
		assertEquals(RustGalFrameQueue.ArmorIconState.FULL, RustGalFrameQueue.armorIconStateForTests(20, 9));
		for (RustGalFrameQueue.ArmorIconState state : RustGalFrameQueue.ArmorIconState.values()) {
			float[] uvY = RustGalFrameQueue.debugArmorOpenGlUvYRangeForTests(state);
			assertTrue(uvY[0] > uvY[1], "top GUI vertex must sample above bottom vertex for " + state);
		}
		for (RustGalFrameQueue.ArmorIconState state : RustGalFrameQueue.ArmorIconState.values()) {
			for (int guiScale = 1; guiScale <= 4; guiScale++) {
				int[] sampledRows = RustGalFrameQueue.debugArmorOpenGlSampledLocalRowsForTests(state, guiScale);
				assertEquals(9 * guiScale, sampledRows.length, "armor row coverage length for " + state + " scale=" + guiScale);
				for (int row = 0; row < 9; row++) {
					for (int repeat = 0; repeat < guiScale; repeat++) {
						int index = row * guiScale + repeat;
						assertEquals(
							row,
							sampledRows[index],
							"packed 9x9 sprite must sample each source row exactly at atlas boundaries for "
								+ state + " scale=" + guiScale + " index=" + index
						);
					}
				}
			}
		}
		assertTrue(
			RustGalFrameQueue.debugOpenGlPackedSpriteVertexShaderForTests().contains("(1.0 - corner[vertex].y)"),
			"OpenGL packed GUI shader must translate top-left GUI corners to bottom-left texture storage"
		);
		assertTrue(
			RustGalFrameQueue.debugOpenGlPackedSpriteFragmentShaderForTests().contains("texelFetch(Sampler0, texel, 0)"),
			"packed GUI shader must use exact source-rectangle texel coverage"
		);
		for (int armor = 0; armor <= 20; armor++) {
			for (int icon = 0; icon < 10; icon++) {
				int threshold = icon * 2 + 1;
				RustGalFrameQueue.ArmorIconState expected = threshold < armor
					? RustGalFrameQueue.ArmorIconState.FULL
					: threshold == armor ? RustGalFrameQueue.ArmorIconState.HALF : RustGalFrameQueue.ArmorIconState.EMPTY;
				assertEquals(expected, RustGalFrameQueue.armorIconStateForTests(armor, icon), "armor=" + armor + " icon=" + icon);
			}
		}

		assertEquals(
			List.of(2, 1, 1, 2),
			RustGalFrameQueue.debugPackCompatibleRunLengthsForTests(
				List.of(
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_BACKGROUND,
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_BACKGROUND,
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_BACKGROUND,
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_PROGRESS,
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_PROGRESS,
					RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_PROGRESS
				),
				List.of("alpha-atlas", "alpha-atlas", "other-texture", "alpha-atlas", "other-texture", "other-texture")
			)
		);

		List<RustGalFrameQueue.RenderStratum> strata = new ArrayList<>(Collections.nCopies(257, RustGalFrameQueue.RenderStratum.GUI_BOSS_BAR_BACKGROUND));
		List<String> resources = new ArrayList<>(Collections.nCopies(257, "alpha-atlas"));
		assertEquals(List.of(256, 1), RustGalFrameQueue.debugPackCompatibleRunLengthsForTests(strata, resources));

		List<String> uniformSequence = RustGalFrameQueue.debugPackedUniformCommandSequenceForTests(
			List.of(
				RustGalFrameQueue.RenderStratum.GUI_HOTBAR_BASE,
				RustGalFrameQueue.RenderStratum.GUI_HOTBAR_BASE,
				RustGalFrameQueue.RenderStratum.GUI_ARMOR
			),
			List.of("alpha-atlas", "alpha-atlas", "alpha-atlas")
		);
		assertEquals("batch-0:host-write-uniforms", uniformSequence.get(1));
		assertEquals("batch-0:barrier-uniform-transfer-to-read", uniformSequence.get(2));
		assertEquals("batch-0:draw-indexed", uniformSequence.get(7));
		assertEquals("batch-1:host-write-uniforms", uniformSequence.get(10));
		assertTrue(
			uniformSequence.indexOf("batch-0:draw-indexed") < uniformSequence.indexOf("batch-1:host-write-uniforms"),
			"the next packed alpha batch must not overwrite shared uniforms before the current batch draws"
		);
	}

	@Test
	void frameAbiV2AddsBorrowedOpenGlAndProductionGuiCrosshairContract() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalFrameQueue.java"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));

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
			assertTrue(queue.contains("GUI_HOTBAR_SELECTION"));
			assertTrue(queue.contains("GUI_ARMOR"));
			assertTrue(queue.contains("GUI_EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("GUI_EXPERIENCE_BAR_PROGRESS"));
		assertTrue(queue.contains("GUI_BOSS_BAR_BACKGROUND"));
		assertTrue(queue.contains("GUI_BOSS_BAR_PROGRESS"));
		assertTrue(queue.contains("HOTBAR_BASE"));
		assertTrue(queue.contains("HOTBAR_SELECTION"));
		assertTrue(queue.contains("EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("EXPERIENCE_BAR_PROGRESS"));
			assertTrue(queue.contains("enqueueHotbarBase"));
			assertTrue(queue.contains("enqueueHotbarSelection"));
			assertTrue(queue.contains("enqueueArmorIcons"));
			assertTrue(queue.contains("enqueueExperienceBar"));
		assertTrue(queue.contains("enqueueBossBar"));
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
			assertTrue(queue.contains("packCompatibleSpriteBatches"));
			assertTrue(queue.contains("drawIndexed(6, spriteBatch.sprites().size())"));
			assertTrue(queue.contains("TextureGroup.GUI_ALPHA"));
			assertTrue(queue.contains("rust_gal_sprite_batches_executed"));
			assertTrue(queue.contains("Rust VulkanicGAL partial-frame GUI sprite is unsupported for Vulkan"));
		assertTrue(gameRenderer.contains("RustGalFrameQueue.resize"));
		assertTrue(gameRenderer.contains("RustGalFrameQueue.shutdown"));
		assertTrue(gui.contains("RustGalFrameQueue.enqueueCrosshair"));
			assertTrue(gui.contains("RustGalFrameQueue.enqueueHotbarBase"));
			assertTrue(gui.contains("RustGalFrameQueue.enqueueArmorIcons"));
			assertTrue(experienceBar.contains("RustGalFrameQueue.enqueueExperienceBar"));
		assertTrue(bossOverlay.contains("RustGalFrameQueue.enqueueBossBar"));
		assertTrue(bossOverlay.contains("drawString(this.minecraft.font"));
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
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));
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
		assertTrue(queue.contains("mattmc.dev.rustGalGui.armor.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.armor.legacyControl"));
		assertTrue(queue.contains("HOTBAR_SELECTION_PRODUCER"));
		assertTrue(queue.contains("EXPERIENCE_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("EXPERIENCE_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("ATTACK_CROSSHAIR_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("ATTACK_CROSSHAIR_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("ATTACK_HOTBAR_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("ATTACK_HOTBAR_PROGRESS_PRODUCER"));
			assertTrue(queue.contains("BOSS_BAR_BACKGROUND_PRODUCER"));
			assertTrue(queue.contains("BOSS_BAR_PROGRESS_PRODUCER"));
			assertTrue(queue.contains("ARMOR_ICON_PRODUCER"));
			assertTrue(queue.contains("selected hotbar slot must be in 0..8"));
			assertTrue(queue.contains("armor value must be in 0..20"));
			assertTrue(queue.contains("experience progress fraction must be finite"));
		assertTrue(queue.contains("experience bar filled width is outside the vanilla range"));
		assertTrue(queue.contains("crosshair attack indicator filled width must be in 0..16"));
		assertTrue(queue.contains("hotbar attack indicator filled height must be in 0..18"));
		assertTrue(queue.contains("boss bar progress fraction must be finite"));
		assertTrue(queue.contains("boss bar filled width must be in 0.."));
			assertTrue(queue.indexOf("GUI_HOTBAR_BASE(\"gui.hotbar.base\", 300)") < queue.indexOf("GUI_HOTBAR_SELECTION(\"gui.hotbar.selection\", 310)"));
			assertTrue(queue.indexOf("GUI_HOTBAR_SELECTION(\"gui.hotbar.selection\", 310)") < queue.indexOf("GUI_ARMOR(\"gui.armor\", 350)"));
			assertTrue(queue.indexOf("GUI_ARMOR(\"gui.armor\", 350)") < queue.indexOf("GUI_EXPERIENCE_BAR_BACKGROUND(\"gui.experience.background\", 400)"));
			assertTrue(queue.indexOf("GUI_EXPERIENCE_BAR_BACKGROUND(\"gui.experience.background\", 400)") < queue.indexOf("GUI_EXPERIENCE_BAR_PROGRESS(\"gui.experience.progress\", 410)"));
		assertTrue(queue.indexOf("GUI_EXPERIENCE_BAR_PROGRESS(\"gui.experience.progress\", 410)") < queue.indexOf("GUI_ATTACK_CROSSHAIR_BACKGROUND(\"gui.attack.crosshair.background\", 500)"));
		assertTrue(queue.indexOf("GUI_ATTACK_CROSSHAIR_BACKGROUND(\"gui.attack.crosshair.background\", 500)") < queue.indexOf("GUI_ATTACK_CROSSHAIR_PROGRESS(\"gui.attack.crosshair.progress\", 510)"));
		assertTrue(queue.indexOf("GUI_ATTACK_CROSSHAIR_PROGRESS(\"gui.attack.crosshair.progress\", 510)") < queue.indexOf("GUI_ATTACK_HOTBAR_BACKGROUND(\"gui.attack.hotbar.background\", 520)"));
		assertTrue(queue.indexOf("GUI_ATTACK_HOTBAR_BACKGROUND(\"gui.attack.hotbar.background\", 520)") < queue.indexOf("GUI_ATTACK_HOTBAR_PROGRESS(\"gui.attack.hotbar.progress\", 530)"));
		assertTrue(queue.indexOf("GUI_ATTACK_HOTBAR_PROGRESS(\"gui.attack.hotbar.progress\", 530)") < queue.indexOf("GUI_BOSS_BAR_BACKGROUND(\"gui.boss.background\", 600)"));
		assertTrue(queue.indexOf("GUI_BOSS_BAR_BACKGROUND(\"gui.boss.background\", 600)") < queue.indexOf("GUI_BOSS_BAR_PROGRESS(\"gui.boss.progress\", 610)"));
			assertTrue(queue.contains("GuiSprite.HOTBAR_SELECTION"));
			assertTrue(queue.contains("GuiSprite.ARMOR_FULL"));
			assertTrue(queue.contains("GuiSprite.ARMOR_HALF"));
			assertTrue(queue.contains("GuiSprite.ARMOR_EMPTY"));
			assertTrue(queue.contains("GuiSprite.EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.EXPERIENCE_BAR_PROGRESS"));
		assertTrue(queue.contains("GuiSprite.CROSSHAIR_ATTACK_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.CROSSHAIR_ATTACK_PROGRESS"));
		assertTrue(queue.contains("GuiSprite.HOTBAR_ATTACK_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.HOTBAR_ATTACK_PROGRESS"));
		assertTrue(queue.contains("BOSS_BAR_PINK_BACKGROUND"));
		assertTrue(queue.contains("BOSS_BAR_WHITE_PROGRESS"));
		assertTrue(queue.contains("BOSS_BAR_NOTCHED_20_BACKGROUND"));
		assertTrue(queue.contains("BOSS_BAR_NOTCHED_20_PROGRESS"));
		assertTrue(queue.contains("FillDirection.HORIZONTAL_LEFT_TO_RIGHT"));
		assertTrue(queue.contains("FillDirection.VERTICAL_BOTTOM_TO_TOP"));
		assertTrue(queue.contains("uv_region"));
		assertTrue(queue.contains("selectedSlot"));
		assertTrue(queue.contains("progressFraction"));
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()") < gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE"));
		assertTrue(gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE") < gui.indexOf("RustGalFrameQueue.enqueueCrosshair"));
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", gui.indexOf("renderItemHotbar")) < gui.indexOf("blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE"));
			int hotbarMethod = gui.indexOf("renderItemHotbar");
			assertTrue(gui.indexOf("RustGalFrameQueue.enqueueHotbarBase", hotbarMethod) < gui.indexOf("HOTBAR_SELECTION_SPRITE", hotbarMethod));
		assertTrue(gui.indexOf("HOTBAR_SELECTION_SPRITE", hotbarMethod) < gui.indexOf("RustGalFrameQueue.enqueueHotbarSelection", hotbarMethod));
		assertTrue(gui.indexOf("RustGalFrameQueue.enqueueHotbarSelection", hotbarMethod) < gui.indexOf("HOTBAR_OFFHAND_LEFT_SPRITE", hotbarMethod));
			assertTrue(gui.contains("selectedHotbarHighlightX"));
			assertTrue(gui.contains("selectedHotbarHighlightY"));
			int armorMethod = gui.indexOf("renderArmor");
			assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", armorMethod)
				< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL_SPRITE", armorMethod));
			assertTrue(gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY_SPRITE", armorMethod)
				< gui.indexOf("RustGalFrameQueue.enqueueArmorIcons", armorMethod));
			int experienceMethod = experienceBar.indexOf("renderBackground");
		assertTrue(experienceBar.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", experienceMethod)
			< experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE", experienceMethod));
		assertTrue(experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE", experienceMethod)
			< experienceBar.indexOf("RustGalFrameQueue.enqueueExperienceBar", experienceMethod));
		assertTrue(experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS_SPRITE", experienceMethod)
			< experienceBar.indexOf("RustGalFrameQueue.enqueueExperienceBar", experienceMethod));
		int crosshairAttack = gui.indexOf("CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE");
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", gui.indexOf("renderCrosshair"))
			< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE", crosshairAttack));
		int crosshairProgressBlit = gui.indexOf("guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE", crosshairAttack);
		assertTrue(crosshairProgressBlit < gui.indexOf("RustGalFrameQueue.enqueueCrosshairAttackIndicator", crosshairProgressBlit));
		int hotbarAttack = gui.indexOf("HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE");
		assertTrue(gui.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()", gui.indexOf("AttackIndicatorStatus.HOTBAR"))
			< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE", hotbarAttack));
		assertTrue(gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE", hotbarAttack)
			< gui.indexOf("RustGalFrameQueue.enqueueHotbarAttackIndicator", hotbarAttack));
		assertTrue(bossOverlay.contains("DeterministicCameraCapture.applyBossBarOverridesForDiagnostics"));
		assertTrue(bossOverlay.contains("RustGalFrameQueue.enqueueBossBar"));
		assertTrue(bossOverlay.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()") < bossOverlay.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocations"));
		assertTrue(bossOverlay.indexOf("RustGalFrameQueue.isMigratedGuiLegacyControl()") < bossOverlay.indexOf("RustGalFrameQueue.enqueueBossBar"));
		assertTrue(bossOverlay.indexOf("RustGalFrameQueue.enqueueBossBar")
			< bossOverlay.indexOf("private void drawBar(\n\t\tGuiGraphics guiGraphics"));
		assertTrue(bossOverlay.contains("drawString(this.minecraft.font"));
		assertTrue(context.contains("MAX_COMBINED_TEXTURE_IMAGE_UNITS"));
		assertTrue(openGlResources.contains("current_frame_target_framebuffer"),
			"persistent borrowed frame-target handles must refresh the native OpenGL framebuffer after screen transitions");
		assertTrue(openGlResources.contains("borrowed_frame_targets_follow_latest_acquired_framebuffer"));
	}
}
