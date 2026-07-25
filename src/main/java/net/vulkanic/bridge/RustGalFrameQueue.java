package net.vulkanic.bridge;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.util.profiling.TracyCompat;
import net.minecraft.world.BossEvent;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class RustGalFrameQueue {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int GUI_UNIFORM_BYTES = 64;
	private static final int GUI_MAX_PACKED_SPRITES = 256;
	private static final int GUI_PACKED_UNIFORM_BYTES = GUI_UNIFORM_BYTES * GUI_MAX_PACKED_SPRITES;
	private static final int GUI_ATLAS_MAX_EXTENT = 4096;
	private static final String CROSSHAIR_PRODUCER = "minecraft.gui.crosshair";
	private static final String HOTBAR_BASE_PRODUCER = "minecraft.gui.hotbar.base";
	private static final String HOTBAR_SELECTION_PRODUCER = "minecraft.gui.hotbar.selection";
	private static final String EXPERIENCE_BACKGROUND_PRODUCER = "minecraft.gui.experience.background";
	private static final String EXPERIENCE_PROGRESS_PRODUCER = "minecraft.gui.experience.progress";
	private static final String ATTACK_CROSSHAIR_BACKGROUND_PRODUCER = "minecraft.gui.attack.crosshair.background";
	private static final String ATTACK_CROSSHAIR_PROGRESS_PRODUCER = "minecraft.gui.attack.crosshair.progress";
	private static final String ATTACK_HOTBAR_BACKGROUND_PRODUCER = "minecraft.gui.attack.hotbar.background";
	private static final String ATTACK_HOTBAR_PROGRESS_PRODUCER = "minecraft.gui.attack.hotbar.progress";
	private static final String BOSS_BAR_BACKGROUND_PRODUCER = "minecraft.gui.boss.background";
	private static final String BOSS_BAR_PROGRESS_PRODUCER = "minecraft.gui.boss.progress";
	private static final String ARMOR_ICON_PRODUCER = "minecraft.gui.armor";
	private static final Object LOCK = new Object();
	private static VulkanicGalBridge bridge;
	private static Thread renderThread;
	private static int configuredWidth;
	private static int configuredHeight;
	private static long cachedFramePass;
	private static long cachedFrameTarget;
	private static long nextCorrelationId = 1L;
	private static long generation = 1L;
	private static long lastSubmitted;
	private static long lastRetiredSubmission;
	private static final DeferredBatchScheduler SCHEDULER = new DeferredBatchScheduler();
	private static final Map<CacheKey, CachedResources> CACHES = new HashMap<>();
	private static final Map<TextureGroup, TextureAtlas> ATLASES = new EnumMap<>(TextureGroup.class);
	private static final Metrics METRICS = new Metrics();

	private RustGalFrameQueue() {
	}

	public static boolean isCrosshairEnabled() {
		return Boolean.parseBoolean(System.getProperty("mattmc.rustGal.guiCrosshair.enabled", "true"));
	}

	public static boolean isMigratedGuiDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.guiCrosshair.disabled") || Boolean.getBoolean("mattmc.dev.rustGalGui.disabled");
	}

	public static boolean isMigratedGuiLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.guiCrosshair.legacyControl") || Boolean.getBoolean("mattmc.dev.rustGalGui.legacyControl");
	}

	public static boolean isArmorDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.armor.disabled");
	}

	public static boolean isArmorLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.armor.legacyControl");
	}

	public static void enqueueCrosshair(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isCrosshairEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.CROSSHAIR, CROSSHAIR_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarBase(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isCrosshairEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_BASE, HOTBAR_BASE_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarSelection(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (selectedSlot < 0 || selectedSlot > 8) {
			throw new IllegalArgumentException("selected hotbar slot must be in 0..8: " + selectedSlot);
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_SELECTION, HOTBAR_SELECTION_PRODUCER, selectedSlot, x, y, width, height);
	}

	public static void enqueueExperienceBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth
	) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("experience progress fraction must be finite: " + progressFraction);
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("experience bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width + 1) {
			throw new IllegalArgumentException("experience bar filled width is outside the vanilla range: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.EXPERIENCE_BAR_BACKGROUND,
			EXPERIENCE_BACKGROUND_PRODUCER,
			-1,
			progressFraction,
			FillDirection.NONE,
			x,
			y,
			width,
			height,
			0,
			0,
			width,
			height
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.EXPERIENCE_BAR_PROGRESS,
				EXPERIENCE_PROGRESS_PRODUCER,
				-1,
				progressFraction,
				FillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				height,
				0,
				0,
				Math.min(filledWidth, width),
				height
			);
		}
	}

	public static void enqueueCrosshairAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledWidth,
		boolean fullIndicator
	) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("crosshair attack indicator progress must be finite: " + cooldownProgress);
		}
		if (fullIndicator) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_FULL,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				Math.max(0.0F, Math.min(1.0F, cooldownProgress)),
				FillDirection.NONE,
				x,
				y,
				16,
				16,
				0,
				0,
				16,
				16
			);
			return;
		}
		if (filledWidth < 0 || filledWidth > 16) {
			throw new IllegalArgumentException("crosshair attack indicator filled width must be in 0..16: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.CROSSHAIR_ATTACK_BACKGROUND,
			ATTACK_CROSSHAIR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			FillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			16,
			4,
			0,
			0,
			16,
			4
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_PROGRESS,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				FillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				4,
				0,
				0,
				filledWidth,
				4
			);
		}
	}

	public static void enqueueHotbarAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledHeight
	) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("hotbar attack indicator progress must be finite: " + cooldownProgress);
		}
		if (filledHeight < 0 || filledHeight > 18) {
			throw new IllegalArgumentException("hotbar attack indicator filled height must be in 0..18: " + filledHeight);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.HOTBAR_ATTACK_BACKGROUND,
			ATTACK_HOTBAR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			FillDirection.VERTICAL_BOTTOM_TO_TOP,
			x,
			y,
			18,
			18,
			0,
			0,
			18,
			18
		);
		if (filledHeight > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.HOTBAR_ATTACK_PROGRESS,
				ATTACK_HOTBAR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				FillDirection.VERTICAL_BOTTOM_TO_TOP,
				x,
				y + 18 - filledHeight,
				18,
				filledHeight,
				0,
				18 - filledHeight,
				18,
				filledHeight
			);
		}
	}

	public static void enqueueArmorIcons(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int armorValue, int x, int y) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (armorValue == 0) {
			return;
		}
		for (int icon = 0; icon < 10; icon++) {
			ArmorIconState state = armorIconState(armorValue, icon);
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				state.sprite(),
				ARMOR_ICON_PRODUCER + "." + state.id() + ".slot" + icon,
				icon,
				armorValue / 20.0F,
				FillDirection.NONE,
				x + icon * 8,
				y,
				9,
				9,
				0,
				0,
				9,
				9
			);
		}
	}

	public static ArmorIconState armorIconStateForTests(int armorValue, int iconIndex) {
		return armorIconState(armorValue, iconIndex);
	}

	private static ArmorIconState armorIconState(int armorValue, int iconIndex) {
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (iconIndex < 0 || iconIndex >= 10) {
			throw new IllegalArgumentException("armor icon index must be in 0..9: " + iconIndex);
		}
		int threshold = iconIndex * 2 + 1;
		if (threshold < armorValue) {
			return ArmorIconState.FULL;
		}
		if (threshold == armorValue) {
			return ArmorIconState.HALF;
		}
		return ArmorIconState.EMPTY;
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		enqueueGuiSprite(minecraft, guiGraphics, sprite, producerId, selectedSlot, -1.0F, FillDirection.NONE, x, y, width, height, 0, 0, width, height);
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		FillDirection fillDirection,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight
	) {
		long started = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal." + sprite.phaseName + ".java-producer");
		if (VulkanicAPI.isVulkanBackendSelected()) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalStateException("Rust VulkanicGAL partial-frame GUI sprite is unsupported for Vulkan; whole-frame Rust presentation is required");
		}
		if (sourceX < 0 || sourceY < 0 || sourceWidth <= 0 || sourceHeight <= 0
			|| sourceX + sourceWidth > sprite.width || sourceY + sourceHeight > sprite.height) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalArgumentException("GUI sprite source region is outside " + sprite.name() + ": "
				+ sourceX + "," + sourceY + " " + sourceWidth + "x" + sourceHeight);
		}
		try {
			synchronized (LOCK) {
				guiGraphics.guiRenderState.submitGuiElement(
					SCHEDULER.enqueue(
						sprite,
							producerId,
							selectedSlot,
							progressFraction,
							fillDirection,
							x,
							y,
						width,
						height,
						sourceX,
						sourceY,
						sourceWidth,
						sourceHeight,
						guiGraphics.guiWidth(),
						guiGraphics.guiHeight(),
						generation
					)
				);
				METRICS.enqueueNanos += elapsedSince(started);
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
		}
	}

	public static void executeFrame(Minecraft minecraft, List<RustGalGuiElementRenderState> elements) {
		if (elements.isEmpty()) {
			return;
		}
		for (RustGalGuiElementRenderState element : elements) {
			if (!element.stratum().supportedForPartialFrame()) {
				throw new IllegalArgumentException("unsupported Rust GAL GUI stratum: " + element.stratum().id());
			}
		}
		ensureRenderThreadAndContext(minecraft);
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		synchronized (LOCK) {
			List<DeferredGuiBatch> batches = SCHEDULER.takeAll(elements);
			executeFrameBatches(window, batches);
		}
	}

	public static void enqueueBossBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay
	) {
		if (!isCrosshairEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("boss bar progress fraction must be finite: " + progressFraction);
		}
		if (color == null || overlay == null) {
			throw new IllegalArgumentException("boss bar color and overlay must be present");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("boss bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width) {
			throw new IllegalArgumentException("boss bar filled width must be in 0.." + width + ": " + filledWidth);
		}
		enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorBackground(color), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
			progressFraction, x, y, width, height, 0, 0, width, height);
		if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayBackground(overlay), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
				progressFraction, x, y, width, height, 0, 0, width, height);
		}
		if (filledWidth > 0) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorProgress(color), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
				progressFraction, x, y, filledWidth, height, 0, 0, filledWidth, height);
			if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
				enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayProgress(overlay), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
					progressFraction, x, y, filledWidth, height, 0, 0, filledWidth, height);
			}
		}
	}

	private static void enqueueBossBarSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerPrefix,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay,
		float progressFraction,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight
	) {
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			sprite,
			producerPrefix + "." + color.getSerializedName() + "." + overlay.getSerializedName() + "." + sprite.semanticSuffix,
			-1,
			progressFraction,
			FillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			width,
			height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight
		);
	}

	public static void resize(int width, int height) {
			synchronized (LOCK) {
					configuredWidth = 0;
					configuredHeight = 0;
					int cancelled = SCHEDULER.cancelAll("resize");
					retireOutstanding(true);
					destroyTransientFrameResources(cachedFramePass, cachedFrameTarget);
					METRICS.cancellations++;
					METRICS.batchesCancelled += cancelled;
			}
		}

	public static void reload() {
		synchronized (LOCK) {
					generation++;
					int cancelled = SCHEDULER.cancelAll("resource-reload");
					METRICS.reloadInvalidations++;
					METRICS.batchesCancelled += cancelled;
					retireOutstanding(true);
					destroyTransientFrameResources(cachedFramePass, cachedFrameTarget);
				destroyCachedResources();
				CACHES.clear();
		}
	}

	public static void cancelPending(String reason) {
		synchronized (LOCK) {
					int cancelled = SCHEDULER.cancelAll(reason);
					METRICS.cancellations++;
					METRICS.batchesCancelled += cancelled;
			}
		}

	public static void shutdown() {
		VulkanicGalBridge existing;
		synchronized (LOCK) {
					int cancelled = SCHEDULER.cancelAll("shutdown");
					existing = bridge;
					retireOutstanding(true);
					destroyTransientFrameResources(cachedFramePass, cachedFrameTarget);
				destroyCachedResources();
				CACHES.clear();
				bridge = null;
				renderThread = null;
					lastSubmitted = 0L;
					lastRetiredSubmission = 0L;
					configuredWidth = 0;
					configuredHeight = 0;
					METRICS.cancellations++;
					METRICS.batchesCancelled += cancelled;
			}
		if (existing != null) {
			try {
				existing.shutdownFrame();
			} finally {
				existing.close();
			}
		}
	}

	public static MetricsSnapshot metricsSnapshot() {
		synchronized (LOCK) {
			return new MetricsSnapshot(
				METRICS.frames,
				METRICS.submissions,
				METRICS.cacheHits,
				METRICS.cacheMisses,
				METRICS.resourceCreates,
				METRICS.resourceDestroys,
				METRICS.ffiCalls,
				METRICS.ffiBytes,
				METRICS.cancellations,
				METRICS.reloadInvalidations,
				METRICS.completionPolls,
					METRICS.completionTimeouts,
					SCHEDULER.pendingCount(),
					METRICS.batchesExecuted,
					METRICS.spriteBatchesExecuted,
					METRICS.packedSpritesExecuted,
					METRICS.batchesCancelled,
				METRICS.contextCreateCalls,
				METRICS.capabilityCalls,
				METRICS.frameConfigureCalls,
				METRICS.frameAcquireCalls,
				METRICS.frameResizeCalls,
				METRICS.framePresentCalls,
				METRICS.resourceBatchCalls,
				METRICS.submitCalls,
				METRICS.completionQueryCalls,
				METRICS.retireCalls,
				METRICS.contextCreateBytes,
				METRICS.capabilityBytes,
				METRICS.frameConfigureBytes,
				METRICS.frameAcquireBytes,
				METRICS.frameResizeBytes,
				METRICS.framePresentBytes,
				METRICS.resourceBatchBytes,
				METRICS.submitBytes,
				METRICS.completionQueryBytes,
				METRICS.retireBytes,
				METRICS.enqueueNanos,
				METRICS.resourceLookupNanos,
				METRICS.resourceCreateNanos,
				METRICS.abiPackingNanos,
				METRICS.frameAcquireNanos,
				METRICS.submitNanos,
				METRICS.framePresentNanos,
				METRICS.retireNanos,
				METRICS.completionQueryNanos,
				METRICS.executeNanos,
				METRICS.commandLists,
				METRICS.commandOps,
				METRICS.backendSubmissions,
				METRICS.backendWaits,
				METRICS.glCalls,
				METRICS.glFlushes,
				METRICS.glFinishes,
				METRICS.glFencesInserted,
				METRICS.glFencesPolled,
				METRICS.glFencesWaited,
				METRICS.glFencesDeleted
			);
		}
	}

	private static void executeFrameBatches(Window window, List<DeferredGuiBatch> batches) {
		if (batches.isEmpty()) {
			return;
		}
		long executeStarted = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.execute");
		long correlationId = nextCorrelationId++;
		long frameId = 0L;
		long submissionId = 0L;
		boolean executeCounted = false;
		try {
				GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.resource-cache");
				long resourceStarted = System.nanoTime();
				List<FrameSpriteBatch> spriteBatches;
				try {
					spriteBatches = packCompatibleSpriteBatches(batches);
				} finally {
					METRICS.resourceLookupNanos += elapsedSince(resourceStarted);
					GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.resource-cache");
			}
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.acquire");
			long acquireStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_ACQUIRE, VulkanicGalBridge.Struct.FRAME_ACQUIRE.byteSize());
			VulkanicGalBridge.AcquiredFrame frame = bridge.acquireFrame(correlationId, window.getWidth(), window.getHeight());
			METRICS.frameAcquireNanos += elapsedSince(acquireStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.acquire");
			frameId = frame.frameId();
			if (frame.status() == 4 || frame.frameTarget() == 0L) {
				int cancelled = SCHEDULER.cancelFrame(frameId, "acquire-skipped");
				METRICS.cancellations++;
				METRICS.batchesCancelled += cancelled;
				return;
			}
				FrameResources frameResources = frameResourcesFor(frame.frameTarget());
				GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.abi-packing");
				long packingStarted = System.nanoTime();
					VulkanicGalBridge.SubmissionBatchBuilder builder = bridge.submissionBatchBuilder("minecraft.gui.frame");
					for (FrameSpriteBatch spriteBatch : spriteBatches) {
						builder.barrier(spriteBatch.resources().uniformBuffer, VulkanicGalBridge.USAGE_SHADER_READ, VulkanicGalBridge.USAGE_TRANSFER_DST, false)
							.hostWrite(spriteBatch.resources().uniformBuffer, 0, packedUniformBytes(spriteBatch.sprites()))
							.barrier(spriteBatch.resources().uniformBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
							.beginFramePass(frameResources.pass(), frameResources.target())
							.bindGraphicsPipeline(spriteBatch.resources().pipeline)
							.bindResourceSet(spriteBatch.resources().pipelineLayout, spriteBatch.resources().resourceSet)
							.setIndexBuffer(spriteBatch.resources().indexBuffer)
							.drawIndexed(6, spriteBatch.sprites().size())
							.endPass();
					}
				VulkanicGalBridge.SubmissionBatch submit = builder.build();
			METRICS.abiPackingNanos += elapsedSince(packingStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.abi-packing");
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.submit");
			long submitStarted = System.nanoTime();
			VulkanicGalBridge.Status status = bridge.submit(submit);
			recordStatus(Operation.SUBMIT, status);
			METRICS.submitNanos += elapsedSince(submitStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.submit");
			submissionId = status.submissionId();
			lastSubmitted = Math.max(lastSubmitted, submissionId);
			TracyCompat.message("gal.frame.deferred producer=gui.frame stratum=gui.frame"
				+ " frame=" + frameId + " submission=" + submissionId + " batches=" + batches.size());
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.present");
			long presentStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_PRESENT, VulkanicGalBridge.Struct.FRAME_PRESENT.byteSize());
			bridge.presentFrame(frameId, correlationId, submissionId);
			METRICS.framePresentNanos += elapsedSince(presentStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.present");
				METRICS.frames++;
				METRICS.submissions++;
				METRICS.batchesExecuted += batches.size();
				METRICS.spriteBatchesExecuted += spriteBatches.size();
				METRICS.packedSpritesExecuted += batches.size();
				retireOutstanding(false);
			METRICS.executeNanos += elapsedSince(executeStarted);
			executeCounted = true;
			if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
					LOGGER.info(
						"Rust VulkanicGAL GUI frame executed: batches={}, spriteBatches={}, frame={}, submission={}, cacheHits={}, cacheMisses={}, ffiCalls={}, ffiBytes={}",
						batches.size(),
						spriteBatches.size(),
						frameId,
						submissionId,
						METRICS.cacheHits,
					METRICS.cacheMisses,
					METRICS.ffiCalls,
					METRICS.ffiBytes
				);
			}
				auditMessage(metricsAuditLine(batches.size(), frameId, submissionId));
		} finally {
			if (!executeCounted) {
				METRICS.executeNanos += elapsedSince(executeStarted);
			}
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.execute");
		}
	}

	private static List<FrameSpriteBatch> packCompatibleSpriteBatches(List<DeferredGuiBatch> batches) {
		List<FrameSpriteBatch> packed = new ArrayList<>();
		FrameSpriteBatchBuilder current = null;
		for (DeferredGuiBatch batch : batches) {
			CachedResources resources = resourcesFor(batch);
			PackedSprite sprite = PackedSprite.from(batch);
			if (current == null || !current.canAppend(batch, resources)) {
				if (current != null) {
					packed.add(current.build());
				}
				current = new FrameSpriteBatchBuilder(batch, resources);
			}
			current.add(sprite);
		}
		if (current != null) {
			packed.add(current.build());
		}
		return packed;
	}

	static List<Integer> debugPackCompatibleRunLengthsForTests(List<RenderStratum> strata, List<String> resourceKeys) {
		if (strata.size() != resourceKeys.size()) {
			throw new IllegalArgumentException("strata and resource key test inputs must have matching sizes");
		}
		List<Integer> runs = new ArrayList<>();
		RenderStratum currentStratum = null;
		String currentKey = null;
		int currentSize = 0;
		for (int i = 0; i < strata.size(); i++) {
			RenderStratum stratum = strata.get(i);
			String resourceKey = resourceKeys.get(i);
			if (currentSize == 0
				|| currentSize >= GUI_MAX_PACKED_SPRITES
				|| stratum != currentStratum
				|| !resourceKey.equals(currentKey)) {
				if (currentSize > 0) {
					runs.add(currentSize);
				}
				currentStratum = stratum;
				currentKey = resourceKey;
				currentSize = 1;
			} else {
				currentSize++;
			}
		}
		if (currentSize > 0) {
			runs.add(currentSize);
		}
		return runs;
	}

	static List<String> debugPackedUniformCommandSequenceForTests(List<RenderStratum> strata, List<String> resourceKeys) {
		List<Integer> runs = debugPackCompatibleRunLengthsForTests(strata, resourceKeys);
		List<String> sequence = new ArrayList<>(runs.size() * 9);
		for (int index = 0; index < runs.size(); index++) {
			sequence.add("batch-" + index + ":barrier-uniform-read-to-transfer");
			sequence.add("batch-" + index + ":host-write-uniforms");
			sequence.add("batch-" + index + ":barrier-uniform-transfer-to-read");
			sequence.add("batch-" + index + ":begin-frame-pass");
			sequence.add("batch-" + index + ":bind-pipeline");
			sequence.add("batch-" + index + ":bind-resource-set");
			sequence.add("batch-" + index + ":set-index-buffer");
			sequence.add("batch-" + index + ":draw-indexed");
			sequence.add("batch-" + index + ":end-pass");
		}
		return sequence;
	}

	static float[] debugArmorOpenGlUvYRangeForTests(ArmorIconState state) {
		PackedSprite sprite = new PackedSprite(
			state.sprite(),
			ARMOR_ICON_PRODUCER + "." + state.id(),
			0,
			1.0F,
			FillDirection.NONE,
			0xFFFFFFFF,
			0,
			0,
			9,
			9,
			0,
			0,
			9,
			9,
			320,
			180
		);
		ByteBuffer uniforms = ByteBuffer.wrap(packedUniformBytes(List.of(sprite))).order(ByteOrder.nativeOrder());
		float originY = uniforms.getFloat(36);
		float height = uniforms.getFloat(44);
		return new float[] {originY + height, originY};
	}

	static int[] debugArmorOpenGlSampledLocalRowsForTests(ArmorIconState state, int guiScale) {
		if (guiScale <= 0) {
			throw new IllegalArgumentException("GUI scale must be positive: " + guiScale);
		}
		GuiSprite guiSprite = state.sprite();
		PackedSprite sprite = new PackedSprite(
			guiSprite,
			ARMOR_ICON_PRODUCER + "." + state.id(),
			0,
			1.0F,
			FillDirection.NONE,
			0xFFFFFFFF,
			0,
			0,
			9,
			9,
			0,
			0,
			9,
			9,
			320,
			180
		);
		TextureAtlas atlas = atlasFor(guiSprite.textureGroup);
		AtlasRegion region = atlas.region(guiSprite);
		ByteBuffer uniforms = ByteBuffer.wrap(packedUniformBytes(List.of(sprite))).order(ByteOrder.nativeOrder());
		int originY = Math.round(uniforms.getFloat(36) * atlas.height());
		int extentY = Math.round(uniforms.getFloat(44) * atlas.height());
		int[] rows = new int[sprite.height() * guiScale];
		for (int y = 0; y < rows.length; y++) {
			float cornerY = (y + 0.5F) / rows.length;
			int sourceY = Math.min(extentY - 1, Math.max(0, (int)Math.floor(cornerY * extentY)));
			int glY = originY + extentY - 1 - sourceY;
			int atlasTopY = atlas.height() - 1 - glY;
			rows[y] = atlasTopY - region.y() - sprite.sourceY();
		}
		return rows;
	}

	static String debugOpenGlPackedSpriteVertexShaderForTests() {
		return VERTEX_SHADER_OPENGL;
	}

	static String debugOpenGlPackedSpriteFragmentShaderForTests() {
		return FRAGMENT_SHADER_OPENGL;
	}

	private static CachedResources resourcesFor(DeferredGuiBatch batch) {
		TextureGroup textureGroup = batch.sprite().textureGroup;
		CacheKey key = new CacheKey(textureGroup.cacheKind, textureGroup.semanticId, generation);
		CachedResources resources = CACHES.get(key);
		if (resources != null) {
			METRICS.cacheHits++;
			return resources;
		}
		METRICS.cacheMisses++;
		CachedResources created = createGuiSpriteResources(textureGroup, key);
		CACHES.put(key, created);
		return created;
	}

	private static CachedResources createGuiSpriteResources(TextureGroup textureGroup, CacheKey key) {
		long createStarted = System.nanoTime();
		List<HandleToDestroy> created = new ArrayList<>();
		try {
			TextureAtlas atlas = atlasFor(textureGroup);
				VulkanicGalBridge.ResourceResults base = bridge.resourceBatch(
					bridge.resourceBatchBuilder()
						.buffer(1, key.label("texture-upload"), atlas.bytes().length, VulkanicGalBridge.MEMORY_UPLOAD,
							VulkanicGalBridge.BUFFER_TRANSFER_SRC | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
						.buffer(2, key.label("index"), 24, VulkanicGalBridge.MEMORY_UPLOAD,
							VulkanicGalBridge.BUFFER_INDEX | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
						.buffer(3, key.label("uniform"), GUI_PACKED_UNIFORM_BYTES, VulkanicGalBridge.MEMORY_UPLOAD,
							VulkanicGalBridge.BUFFER_UNIFORM | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
						.texture(4, key.label("texture"), VulkanicGalBridge.FORMAT_RGBA8, atlas.width(), atlas.height(),
							VulkanicGalBridge.TEXTURE_SAMPLED | VulkanicGalBridge.TEXTURE_TRANSFER_DST)
						.sampler(5, key.label("sampler"))
						.shader(6, key.label("vertex"), VulkanicGalBridge.SHADER_VERTEX, VERTEX_SHADER_OPENGL)
						.shader(7, key.label("fragment"), VulkanicGalBridge.SHADER_FRAGMENT, FRAGMENT_SHADER_OPENGL)
						.build());
				recordResourceBatch(base);
			long uploadBuffer = base.handle(0);
			long indexBuffer = base.handle(1);
			long uniformBuffer = base.handle(2);
			long texture = base.handle(3);
			long sampler = base.handle(4);
			long vertex = base.handle(5);
			long fragment = base.handle(6);
			created.add(new HandleToDestroy(uploadBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(indexBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(uniformBuffer, VulkanicGalBridge.HANDLE_BUFFER));
			created.add(new HandleToDestroy(texture, VulkanicGalBridge.HANDLE_TEXTURE));
			created.add(new HandleToDestroy(sampler, VulkanicGalBridge.HANDLE_SAMPLER));
			created.add(new HandleToDestroy(vertex, VulkanicGalBridge.HANDLE_SHADER_MODULE));
			created.add(new HandleToDestroy(fragment, VulkanicGalBridge.HANDLE_SHADER_MODULE));
				VulkanicGalBridge.ResourceResults dependent = bridge.resourceBatch(
				bridge.resourceBatchBuilder()
					.textureView(10, key.label("texture-view"), texture, VulkanicGalBridge.FORMAT_RGBA8)
						.resourceLayout(20, key.label("resource-layout"),
							new VulkanicGalBridge.BindingDesc(0, VulkanicGalBridge.BINDING_UNIFORM_BUFFER, 1, false),
							new VulkanicGalBridge.BindingDesc(1, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE, 1, false),
							new VulkanicGalBridge.BindingDesc(2, VulkanicGalBridge.BINDING_SAMPLER, 1, false))
						.build());
				recordResourceBatch(dependent);
			long textureView = dependent.handle(0);
			long resourceLayout = dependent.handle(1);
			created.add(new HandleToDestroy(textureView, VulkanicGalBridge.HANDLE_TEXTURE_VIEW));
			created.add(new HandleToDestroy(resourceLayout, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT));
				VulkanicGalBridge.ResourceResults set = bridge.resourceBatch(
				bridge.resourceBatchBuilder()
					.resourceSet(21, key.label("resource-set"), resourceLayout,
						new VulkanicGalBridge.Binding(0, 0, uniformBuffer, VulkanicGalBridge.BINDING_UNIFORM_BUFFER),
						new VulkanicGalBridge.Binding(1, 0, textureView, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE),
						new VulkanicGalBridge.Binding(2, 0, sampler, VulkanicGalBridge.BINDING_SAMPLER))
						.pipelineLayout(30, key.label("pipeline-layout"), resourceLayout)
						.build());
				recordResourceBatch(set);
			long resourceSet = set.handle(0);
			long pipelineLayout = set.handle(1);
			created.add(new HandleToDestroy(resourceSet, VulkanicGalBridge.HANDLE_RESOURCE_SET));
			created.add(new HandleToDestroy(pipelineLayout, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT));
				VulkanicGalBridge.ResourceResults pipeline = bridge.resourceBatch(
					textureGroup.pipeline(bridge.resourceBatchBuilder(), 31, key.label("pipeline"), pipelineLayout, vertex, fragment)
						.build());
				recordResourceBatch(pipeline);
			long graphicsPipeline = pipeline.handle(0);
			created.add(new HandleToDestroy(graphicsPipeline, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE));
			CachedResources resources = new CachedResources(
				key,
				uploadBuffer,
				indexBuffer,
				uniformBuffer,
				texture,
				sampler,
				vertex,
				fragment,
				textureView,
				resourceLayout,
				resourceSet,
				pipelineLayout,
				graphicsPipeline
			);
			uploadPersistentResources(textureGroup, resources);
			METRICS.resourceCreates += resources.handlesInDestroyOrder().size();
			return resources;
		} catch (RuntimeException error) {
			try {
				destroyHandles(created);
			} catch (RuntimeException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			throw error;
		} finally {
			METRICS.resourceCreateNanos += elapsedSince(createStarted);
		}
	}

	private static void uploadPersistentResources(TextureGroup textureGroup, CachedResources resources) {
		TextureAtlas atlas = atlasFor(textureGroup);
		VulkanicGalBridge.Status upload = bridge.submit(
			bridge.submissionBatchBuilder(resources.key().label("upload"))
				.hostWrite(resources.uploadBuffer, 0, atlas.bytes())
				.barrier(resources.uploadBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_TRANSFER_SRC, false)
				.hostWrite(resources.indexBuffer, 0, indexBytes())
				.barrier(resources.indexBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
				.barrier(resources.texture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_TRANSFER_DST, true)
				.copyBufferToTexture(resources.uploadBuffer, resources.texture, atlas.width(), atlas.height())
				.barrier(resources.texture, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, true)
				.build());
		recordStatus(Operation.SUBMIT, upload);
		lastSubmitted = Math.max(lastSubmitted, upload.submissionId());
	}

	private static void destroyCachedResources() {
		if (bridge == null || CACHES.isEmpty()) {
			return;
		}
		List<CachedResources> resources = new ArrayList<>(CACHES.values());
		resources.sort(Comparator.comparing(resource -> resource.key().semanticId()));
		for (CachedResources resource : resources) {
			VulkanicGalBridge.ResourceBatchBuilder destroy = bridge.resourceBatchBuilder();
			for (HandleToDestroy handle : resource.handlesInDestroyOrder()) {
				destroy.destroy(handle.handle(), handle.kind());
				METRICS.resourceDestroys++;
			}
			recordResourceBatch(bridge.resourceBatch(destroy.build()));
		}
	}

	private static FrameResources frameResourcesFor(long acquiredFrameTarget) {
		if (cachedFramePass != 0L && cachedFrameTarget != 0L && cachedFrameTarget == acquiredFrameTarget) {
			return new FrameResources(cachedFrameTarget, cachedFramePass);
		}
		destroyTransientFrameResources(cachedFramePass, cachedFrameTarget);
		VulkanicGalBridge.ResourceResults frameResources = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.frameRenderPass(9000, "minecraft.gui.frame.pass", acquiredFrameTarget)
				.build());
		recordResourceBatch(frameResources);
		cachedFrameTarget = acquiredFrameTarget;
		cachedFramePass = frameResources.handle(0);
		return new FrameResources(cachedFrameTarget, cachedFramePass);
	}

	private static void destroyTransientFrameResources(long pass, long frameTarget) {
		if (bridge == null) {
			return;
		}
		VulkanicGalBridge.ResourceBatchBuilder destroy = bridge.resourceBatchBuilder();
		boolean hasDestroy = false;
		if (pass != 0L) {
			destroy.destroy(pass, VulkanicGalBridge.HANDLE_RENDER_PASS);
			hasDestroy = true;
		}
		if (frameTarget != 0L) {
			destroy.destroy(frameTarget, VulkanicGalBridge.HANDLE_FRAME_TARGET);
			hasDestroy = true;
		}
		if (!hasDestroy) {
			return;
		}
		try {
			recordResourceBatch(bridge.resourceBatch(destroy.build()));
		} catch (RuntimeException cleanupError) {
			LOGGER.error("Rust VulkanicGAL transient frame resource cleanup failed", cleanupError);
		} finally {
			if (pass == cachedFramePass) {
				cachedFramePass = 0L;
			}
			if (frameTarget == cachedFrameTarget) {
				cachedFrameTarget = 0L;
			}
		}
	}

	private static void destroyHandles(List<HandleToDestroy> handles) {
		if (bridge == null || handles.isEmpty()) {
			return;
		}
		VulkanicGalBridge.ResourceBatchBuilder destroy = bridge.resourceBatchBuilder();
		for (int i = handles.size() - 1; i >= 0; i--) {
			HandleToDestroy handle = handles.get(i);
			destroy.destroy(handle.handle(), handle.kind());
			METRICS.resourceDestroys++;
		}
		recordResourceBatch(bridge.resourceBatch(destroy.build()));
	}

	private static void retireOutstanding(boolean force) {
		if (bridge == null || lastSubmitted == 0L || lastSubmitted <= lastRetiredSubmission) {
			return;
		}
		if (!force) {
			return;
		}
		long started = System.nanoTime();
		recordStatus(Operation.RETIRE, bridge.retire(lastSubmitted));
		METRICS.retireNanos += elapsedSince(started);
		lastRetiredSubmission = lastSubmitted;
	}

	private static long elapsedSince(long started) {
		return Math.max(0L, System.nanoTime() - started);
	}

	private static void ensureRenderThreadAndContext(Minecraft minecraft) {
		Thread current = Thread.currentThread();
		if (renderThread == null) {
			renderThread = current;
		} else if (renderThread != current) {
			throw new IllegalStateException("Rust VulkanicGAL deferred frame queue used from the wrong render thread");
		}
		Window window = minecraft.getWindow();
		long currentContext = GLFW.glfwGetCurrentContext();
		if (currentContext == 0L || currentContext != window.handle()) {
			throw new IllegalStateException("Rust VulkanicGAL deferred OpenGL execution requires Minecraft's current GL context");
		}
			if (bridge == null) {
				bridge = VulkanicGalBridge.createBorrowedOpenGl(window.handle());
				recordFixedOperation(Operation.CONTEXT_CREATE, VulkanicGalBridge.Struct.BORROWED_OPENGL_CONTEXT_CREATE.byteSize());
				recordFixedOperation(Operation.CAPABILITY_QUERY, VulkanicGalBridge.Struct.CAPABILITY_QUERY.byteSize());
				configuredWidth = 0;
				configuredHeight = 0;
			}
	}

	private static void ensureConfigured(Window window) {
		int width = Math.max(1, window.getWidth());
		int height = Math.max(1, window.getHeight());
		if (configuredWidth == width && configuredHeight == height) {
			return;
		}
			if (configuredWidth == 0 || configuredHeight == 0) {
				recordStatus(Operation.FRAME_CONFIGURE, bridge.configureFrame("minecraft.borrowed.opengl.default", width, height, VulkanicGalBridge.FORMAT_RGBA8));
			} else {
				recordFixedOperation(Operation.FRAME_RESIZE, VulkanicGalBridge.Struct.FRAME_RESIZE.byteSize());
				bridge.resizeFrame(nextCorrelationId++, width, height);
			}
			configuredWidth = width;
			configuredHeight = height;
		}

	private static void recordStatus(Operation operation, VulkanicGalBridge.Status status) {
		long ffiCalls = status.ffiCalls();
		long ffiBytes = status.ffiInputBytes();
		recordBackendMetrics(status.backendMetrics());
		long deltaCalls = 0L;
			long deltaBytes = 0L;
			if (ffiCalls >= METRICS.lastContextFfiCalls) {
				deltaCalls = ffiCalls - METRICS.lastContextFfiCalls;
				METRICS.ffiCalls += deltaCalls;
			}
			if (ffiBytes >= METRICS.lastContextFfiBytes) {
				deltaBytes = ffiBytes - METRICS.lastContextFfiBytes;
				METRICS.ffiBytes += deltaBytes;
			}
			addOperation(operation, deltaCalls, deltaBytes);
			METRICS.lastContextFfiCalls = ffiCalls;
			METRICS.lastContextFfiBytes = ffiBytes;
		}

	private static void recordResourceBatch(VulkanicGalBridge.ResourceResults results) {
		recordStatus(Operation.RESOURCE_BATCH, new VulkanicGalBridge.Status(results.submissionId(), results.ffiCalls(), results.ffiInputBytes(), results.backendMetrics()));
	}

	private static void recordBackendMetrics(VulkanicGalBridge.BackendMetrics metrics) {
		if (metrics == null) {
			return;
		}
		METRICS.commandLists = Math.max(METRICS.commandLists, metrics.commandLists());
		METRICS.commandOps = Math.max(METRICS.commandOps, metrics.commandOps());
		METRICS.backendSubmissions = Math.max(METRICS.backendSubmissions, metrics.backendSubmissions());
		METRICS.backendWaits = Math.max(METRICS.backendWaits, metrics.backendWaits());
		METRICS.glCalls = Math.max(METRICS.glCalls, metrics.glCalls());
		METRICS.glFlushes = Math.max(METRICS.glFlushes, metrics.glFlushes());
		METRICS.glFinishes = Math.max(METRICS.glFinishes, metrics.glFinishes());
		METRICS.glFencesInserted = Math.max(METRICS.glFencesInserted, metrics.glFencesInserted());
		METRICS.glFencesPolled = Math.max(METRICS.glFencesPolled, metrics.glFencesPolled());
		METRICS.glFencesWaited = Math.max(METRICS.glFencesWaited, metrics.glFencesWaited());
		METRICS.glFencesDeleted = Math.max(METRICS.glFencesDeleted, metrics.glFencesDeleted());
	}

		private static void recordFixedOperation(Operation operation, long inputBytes) {
			METRICS.ffiCalls++;
			METRICS.ffiBytes += inputBytes;
			METRICS.lastContextFfiCalls++;
			METRICS.lastContextFfiBytes += inputBytes;
			addOperation(operation, 1L, inputBytes);
		}

		private static void addOperation(Operation operation, long calls, long bytes) {
			switch (operation) {
				case CONTEXT_CREATE -> {
					METRICS.contextCreateCalls += calls;
					METRICS.contextCreateBytes += bytes;
				}
				case CAPABILITY_QUERY -> {
					METRICS.capabilityCalls += calls;
					METRICS.capabilityBytes += bytes;
				}
				case FRAME_CONFIGURE -> {
					METRICS.frameConfigureCalls += calls;
					METRICS.frameConfigureBytes += bytes;
				}
				case FRAME_ACQUIRE -> {
					METRICS.frameAcquireCalls += calls;
					METRICS.frameAcquireBytes += bytes;
				}
				case FRAME_RESIZE -> {
					METRICS.frameResizeCalls += calls;
					METRICS.frameResizeBytes += bytes;
				}
				case FRAME_PRESENT -> {
					METRICS.framePresentCalls += calls;
					METRICS.framePresentBytes += bytes;
				}
				case RESOURCE_BATCH -> {
					METRICS.resourceBatchCalls += calls;
					METRICS.resourceBatchBytes += bytes;
				}
				case SUBMIT -> {
					METRICS.submitCalls += calls;
					METRICS.submitBytes += bytes;
				}
				case COMPLETION_QUERY -> {
					METRICS.completionQueryCalls += calls;
					METRICS.completionQueryBytes += bytes;
				}
				case RETIRE -> {
					METRICS.retireCalls += calls;
					METRICS.retireBytes += bytes;
				}
			}
		}

	private static void auditMessage(String message) {
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			System.out.println("[MattMC graphics audit] " + message);
		}
	}

	public static String currentAuditMetricsLine() {
		synchronized (LOCK) {
			return metricsAuditLine(0L, METRICS.frames, lastSubmitted);
		}
	}

	private static String metricsAuditLine(long frameBatchCount, long frameId, long submissionId) {
		return "Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame"
			+ " stratum=gui.frame"
			+ " frame_batch_count=" + frameBatchCount
			+ " frame=" + frameId
			+ " submission=" + submissionId
			+ " rust_gal_cache_hits=" + METRICS.cacheHits
			+ " rust_gal_cache_misses=" + METRICS.cacheMisses
			+ " rust_gal_queue_depth=" + SCHEDULER.pendingCount()
			+ " rust_gal_frames_executed=" + METRICS.frames
			+ " rust_gal_batches_executed=" + METRICS.batchesExecuted
			+ " rust_gal_sprite_batches_executed=" + METRICS.spriteBatchesExecuted
			+ " rust_gal_packed_sprites_executed=" + METRICS.packedSpritesExecuted
			+ " rust_gal_batches_cancelled=" + METRICS.batchesCancelled
			+ " rust_gal_completion_polls=" + METRICS.completionPolls
			+ " rust_gal_completion_timeouts=" + METRICS.completionTimeouts
			+ " rust_gal_ffi_context_create_calls=" + METRICS.contextCreateCalls
			+ " rust_gal_ffi_capability_calls=" + METRICS.capabilityCalls
			+ " rust_gal_ffi_frame_configure_calls=" + METRICS.frameConfigureCalls
			+ " rust_gal_ffi_frame_acquire_calls=" + METRICS.frameAcquireCalls
			+ " rust_gal_ffi_frame_resize_calls=" + METRICS.frameResizeCalls
			+ " rust_gal_ffi_frame_present_calls=" + METRICS.framePresentCalls
			+ " rust_gal_ffi_resource_batch_calls=" + METRICS.resourceBatchCalls
			+ " rust_gal_ffi_submit_calls=" + METRICS.submitCalls
			+ " rust_gal_ffi_completion_query_calls=" + METRICS.completionQueryCalls
			+ " rust_gal_ffi_retire_calls=" + METRICS.retireCalls
			+ " rust_gal_ffi_context_create_bytes=" + METRICS.contextCreateBytes
			+ " rust_gal_ffi_capability_bytes=" + METRICS.capabilityBytes
			+ " rust_gal_ffi_frame_configure_bytes=" + METRICS.frameConfigureBytes
			+ " rust_gal_ffi_frame_acquire_bytes=" + METRICS.frameAcquireBytes
			+ " rust_gal_ffi_frame_resize_bytes=" + METRICS.frameResizeBytes
			+ " rust_gal_ffi_frame_present_bytes=" + METRICS.framePresentBytes
			+ " rust_gal_ffi_resource_batch_bytes=" + METRICS.resourceBatchBytes
			+ " rust_gal_ffi_submit_bytes=" + METRICS.submitBytes
			+ " rust_gal_ffi_completion_query_bytes=" + METRICS.completionQueryBytes
			+ " rust_gal_ffi_retire_bytes=" + METRICS.retireBytes
			+ " rust_gal_enqueue_nanos=" + METRICS.enqueueNanos
			+ " rust_gal_resource_lookup_nanos=" + METRICS.resourceLookupNanos
			+ " rust_gal_resource_create_nanos=" + METRICS.resourceCreateNanos
			+ " rust_gal_abi_packing_nanos=" + METRICS.abiPackingNanos
			+ " rust_gal_frame_acquire_nanos=" + METRICS.frameAcquireNanos
			+ " rust_gal_submit_nanos=" + METRICS.submitNanos
			+ " rust_gal_frame_present_nanos=" + METRICS.framePresentNanos
			+ " rust_gal_retire_nanos=" + METRICS.retireNanos
			+ " rust_gal_completion_query_nanos=" + METRICS.completionQueryNanos
			+ " rust_gal_execute_nanos=" + METRICS.executeNanos
			+ " rust_gal_command_lists=" + METRICS.commandLists
			+ " rust_gal_command_ops=" + METRICS.commandOps
			+ " rust_gal_backend_submissions=" + METRICS.backendSubmissions
			+ " rust_gal_backend_waits=" + METRICS.backendWaits
			+ " rust_gal_gl_calls=" + METRICS.glCalls
			+ " rust_gal_gl_flushes=" + METRICS.glFlushes
			+ " rust_gal_gl_finishes=" + METRICS.glFinishes
			+ " rust_gal_gl_fences_inserted=" + METRICS.glFencesInserted
			+ " rust_gal_gl_fences_polled=" + METRICS.glFencesPolled
			+ " rust_gal_gl_fences_waited=" + METRICS.glFencesWaited
			+ " rust_gal_gl_fences_deleted=" + METRICS.glFencesDeleted
			+ " ffi_call_count=" + METRICS.ffiCalls
			+ " ffi_bytes=" + METRICS.ffiBytes;
	}

	private static GuiSprite bossBarColorBackground(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_BACKGROUND;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_BACKGROUND;
			case RED -> GuiSprite.BOSS_BAR_RED_BACKGROUND;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_BACKGROUND;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_BACKGROUND;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_BACKGROUND;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_BACKGROUND;
		};
	}

	private static GuiSprite bossBarColorProgress(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_PROGRESS;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_PROGRESS;
			case RED -> GuiSprite.BOSS_BAR_RED_PROGRESS;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_PROGRESS;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_PROGRESS;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_PROGRESS;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_PROGRESS;
		};
	}

	private static GuiSprite bossBarOverlayBackground(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch background sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_BACKGROUND;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_BACKGROUND;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_BACKGROUND;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_BACKGROUND;
		};
	}

	private static GuiSprite bossBarOverlayProgress(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch progress sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_PROGRESS;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_PROGRESS;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_PROGRESS;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_PROGRESS;
		};
	}

	private static byte[] packedUniformBytes(List<PackedSprite> sprites) {
		if (sprites.isEmpty() || sprites.size() > GUI_MAX_PACKED_SPRITES) {
			throw new IllegalArgumentException("packed GUI sprite count must be in 1.." + GUI_MAX_PACKED_SPRITES + ": " + sprites.size());
		}
		ByteBuffer buffer = ByteBuffer.allocate(GUI_UNIFORM_BYTES * sprites.size()).order(ByteOrder.nativeOrder());
		for (PackedSprite sprite : sprites) {
			TextureAtlas atlas = atlasFor(sprite.sprite().textureGroup);
			AtlasRegion region = atlas.region(sprite.sprite());
			buffer.putFloat(sprite.x());
			buffer.putFloat(sprite.y());
			buffer.putFloat(sprite.width());
			buffer.putFloat(sprite.height());
			buffer.putFloat(sprite.guiWidth());
			buffer.putFloat(sprite.guiHeight());
			buffer.putFloat(sprite.progressFraction());
			buffer.putFloat(sprite.fillDirection().ordinal());
			buffer.putFloat((region.x() + sprite.sourceX()) / (float)atlas.width());
			buffer.putFloat((atlas.height() - (region.y() + sprite.sourceY() + sprite.sourceHeight())) / (float)atlas.height());
			buffer.putFloat(sprite.sourceWidth() / (float)atlas.width());
			buffer.putFloat(sprite.sourceHeight() / (float)atlas.height());
			buffer.putFloat(((sprite.colorArgb() >>> 16) & 0xFF) / 255.0F);
			buffer.putFloat(((sprite.colorArgb() >>> 8) & 0xFF) / 255.0F);
			buffer.putFloat((sprite.colorArgb() & 0xFF) / 255.0F);
			buffer.putFloat(((sprite.colorArgb() >>> 24) & 0xFF) / 255.0F);
		}
		return buffer.array();
	}

	private static TextureAtlas atlasFor(TextureGroup group) {
		TextureAtlas atlas = ATLASES.get(group);
		if (atlas != null) {
			return atlas;
		}
		TextureAtlas created = buildAtlas(group);
		ATLASES.put(group, created);
		return created;
	}

	private static TextureAtlas buildAtlas(TextureGroup group) {
		List<GuiSprite> sprites = new ArrayList<>();
		for (GuiSprite sprite : GuiSprite.values()) {
			if (sprite.textureGroup == group) {
				sprites.add(sprite);
			}
		}
		if (sprites.isEmpty()) {
			throw new IllegalStateException("no GUI sprites are assigned to texture group " + group.semanticId);
		}
		int width = 1;
		int height = 0;
		int rowWidth = 0;
		int rowHeight = 0;
		for (GuiSprite sprite : sprites) {
			if (sprite.width > GUI_ATLAS_MAX_EXTENT || sprite.height > GUI_ATLAS_MAX_EXTENT) {
				throw new IllegalStateException("GUI sprite " + sprite.name() + " exceeds atlas extent " + GUI_ATLAS_MAX_EXTENT);
			}
			if (rowWidth > 0 && rowWidth + sprite.width > GUI_ATLAS_MAX_EXTENT) {
				width = Math.max(width, rowWidth);
				height += rowHeight;
				rowWidth = 0;
				rowHeight = 0;
			}
			rowWidth += sprite.width;
			rowHeight = Math.max(rowHeight, sprite.height);
		}
		width = Math.max(width, rowWidth);
		height += rowHeight;
		if (height > GUI_ATLAS_MAX_EXTENT) {
			throw new IllegalStateException("GUI sprite atlas " + group.semanticId + " exceeds atlas extent " + GUI_ATLAS_MAX_EXTENT);
		}
		byte[] bytes = new byte[width * height * 4];
		Map<GuiSprite, AtlasRegion> regions = new EnumMap<>(GuiSprite.class);
		int xOffset = 0;
		int yOffset = 0;
		rowHeight = 0;
		for (GuiSprite sprite : sprites) {
			if (xOffset > 0 && xOffset + sprite.width > GUI_ATLAS_MAX_EXTENT) {
				xOffset = 0;
				yOffset += rowHeight;
				rowHeight = 0;
			}
			byte[] spriteBytes = spriteTextureBytes(sprite);
			for (int y = 0; y < sprite.height; y++) {
				int src = y * sprite.width * 4;
				int dst = ((yOffset + y) * width + xOffset) * 4;
				System.arraycopy(spriteBytes, src, bytes, dst, sprite.width * 4);
			}
			regions.put(sprite, new AtlasRegion(xOffset, yOffset, sprite.width, sprite.height));
			xOffset += sprite.width;
			rowHeight = Math.max(rowHeight, sprite.height);
		}
		return new TextureAtlas(group, width, height, bytes, regions);
	}

	private static byte[] spriteTextureBytes(GuiSprite sprite) {
		try (InputStream input = RustGalFrameQueue.class.getResourceAsStream(sprite.textureResource)) {
			if (input == null) {
				throw new IllegalStateException("missing GUI texture resource: " + sprite.textureResource);
			}
			BufferedImage image = ImageIO.read(input);
			if (image == null || image.getWidth() != sprite.width || image.getHeight() != sprite.height) {
				throw new IllegalStateException("unexpected GUI texture dimensions for " + sprite.textureResource);
			}
			byte[] bytes = new byte[sprite.textureBytes()];
			int offset = 0;
			for (int y = 0; y < sprite.height; y++) {
				for (int x = 0; x < sprite.width; x++) {
					int argb = image.getRGB(x, y);
					bytes[offset++] = (byte)((argb >>> 16) & 0xFF);
					bytes[offset++] = (byte)((argb >>> 8) & 0xFF);
					bytes[offset++] = (byte)(argb & 0xFF);
					bytes[offset++] = (byte)((argb >>> 24) & 0xFF);
				}
			}
			return bytes;
		} catch (IOException error) {
			throw new IllegalStateException("failed to load GUI texture " + sprite.textureResource, error);
		}
	}

	private static byte[] indexBytes() {
		byte[] bytes = new byte[24];
		int[] values = {0, 1, 2, 3, 4, 5};
		for (int i = 0; i < values.length; i++) {
			int value = values[i];
			int offset = i * 4;
			bytes[offset] = (byte)value;
			bytes[offset + 1] = (byte)(value >>> 8);
			bytes[offset + 2] = (byte)(value >>> 16);
			bytes[offset + 3] = (byte)(value >>> 24);
		}
		return bytes;
	}

	public enum RenderStratum {
		GUI_CROSSHAIR("gui.crosshair", 200),
		GUI_HOTBAR_BASE("gui.hotbar.base", 300),
		GUI_HOTBAR_SELECTION("gui.hotbar.selection", 310),
		GUI_ARMOR("gui.armor", 350),
		GUI_EXPERIENCE_BAR_BACKGROUND("gui.experience.background", 400),
		GUI_EXPERIENCE_BAR_PROGRESS("gui.experience.progress", 410),
		GUI_ATTACK_CROSSHAIR_BACKGROUND("gui.attack.crosshair.background", 500),
		GUI_ATTACK_CROSSHAIR_PROGRESS("gui.attack.crosshair.progress", 510),
		GUI_ATTACK_HOTBAR_BACKGROUND("gui.attack.hotbar.background", 520),
		GUI_ATTACK_HOTBAR_PROGRESS("gui.attack.hotbar.progress", 530),
		GUI_BOSS_BAR_BACKGROUND("gui.boss.background", 600),
		GUI_BOSS_BAR_PROGRESS("gui.boss.progress", 610);

		private final String id;
		private final int order;

		RenderStratum(String id, int order) {
			this.id = id;
			this.order = order;
		}

		public String id() {
			return id;
		}

		public int order() {
			return order;
		}

		boolean supportedForPartialFrame() {
			return this == GUI_CROSSHAIR
				|| this == GUI_HOTBAR_BASE
				|| this == GUI_HOTBAR_SELECTION
				|| this == GUI_ARMOR
				|| this == GUI_EXPERIENCE_BAR_BACKGROUND
				|| this == GUI_EXPERIENCE_BAR_PROGRESS
				|| this == GUI_ATTACK_CROSSHAIR_BACKGROUND
				|| this == GUI_ATTACK_CROSSHAIR_PROGRESS
				|| this == GUI_ATTACK_HOTBAR_BACKGROUND
				|| this == GUI_ATTACK_HOTBAR_PROGRESS
				|| this == GUI_BOSS_BAR_BACKGROUND
				|| this == GUI_BOSS_BAR_PROGRESS;
			}
		}

	public enum FillDirection {
		NONE("none"),
		HORIZONTAL_LEFT_TO_RIGHT("horizontal-left-to-right"),
		VERTICAL_BOTTOM_TO_TOP("vertical-bottom-to-top");

		private final String id;

		FillDirection(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	public enum ArmorIconState {
		EMPTY("empty", GuiSprite.ARMOR_EMPTY),
		HALF("half", GuiSprite.ARMOR_HALF),
		FULL("full", GuiSprite.ARMOR_FULL);

		private final String id;
		private final GuiSprite sprite;

		ArmorIconState(String id, GuiSprite sprite) {
			this.id = id;
			this.sprite = sprite;
		}

		private String id() {
			return this.id;
		}

		private GuiSprite sprite() {
			return this.sprite;
		}
	}

	private static final class DeferredBatchScheduler {
		private final NavigableMap<Long, DeferredGuiBatch> pending = new TreeMap<>();
		private long nextBatchId = 1L;
		private long nextSequence = 1L;
		private int lastExecutedOrder = Integer.MIN_VALUE;

		RustGalGuiElementRenderState enqueue(
			GuiSprite sprite,
			String producerId,
				int selectedSlot,
				float progressFraction,
				FillDirection fillDirection,
				int x,
				int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int guiWidth,
			int guiHeight,
			long generation
		) {
			long batchId = this.nextBatchId++;
			long sequence = this.nextSequence++;
			DeferredGuiBatch batch = new DeferredGuiBatch(
					batchId,
					sequence,
					generation,
					sprite.stratum,
					sprite,
					producerId,
						selectedSlot,
						progressFraction,
						fillDirection,
						x,
					y,
					width,
					height,
					sourceX,
					sourceY,
					sourceWidth,
					sourceHeight,
					guiWidth,
					guiHeight
				);
			this.pending.put(batchId, batch);
			return new RustGalGuiElementRenderState(
				batchId,
				sequence,
				generation,
				batch.stratum(),
				producerId,
					selectedSlot,
					progressFraction,
					fillDirection,
					sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				x,
				y,
				width,
				height,
				guiWidth,
				guiHeight
			);
		}

		List<DeferredGuiBatch> takeAll(List<RustGalGuiElementRenderState> elements) {
			List<RustGalGuiElementRenderState> ordered = new ArrayList<>(elements);
			ordered.sort(Comparator.comparingInt((RustGalGuiElementRenderState element) -> element.stratum().order()).thenComparingLong(RustGalGuiElementRenderState::sequence));
			List<DeferredGuiBatch> batches = new ArrayList<>(ordered.size());
			int lastOrder = Integer.MIN_VALUE;
			for (RustGalGuiElementRenderState element : ordered) {
				DeferredGuiBatch batch = this.take(element);
				if (batch.stratum().order() < lastOrder) {
					throw new IllegalStateException("Rust VulkanicGAL deferred batch executed out of stratum order: stratum=" + batch.stratum().id());
				}
				lastOrder = batch.stratum().order();
				batches.add(batch);
			}
			this.lastExecutedOrder = Integer.MIN_VALUE;
			return batches;
		}

		private DeferredGuiBatch take(RustGalGuiElementRenderState element) {
			DeferredGuiBatch batch = this.pending.remove(element.batchId());
			if (batch == null) {
				throw new IllegalStateException("Rust VulkanicGAL deferred batch is no longer pending: batch=" + element.batchId());
			}
			if (batch.generation() != element.generation() || batch.generation() != generation) {
				throw new IllegalStateException("Rust VulkanicGAL deferred batch generation is stale: batch=" + element.batchId());
			}
			if (batch.sequence() != element.sequence() || batch.stratum() != element.stratum()) {
				throw new IllegalStateException("Rust VulkanicGAL deferred batch token does not match scheduled work: batch=" + element.batchId());
			}
			if (batch.stratum().order() < this.lastExecutedOrder) {
				throw new IllegalStateException("Rust VulkanicGAL deferred batch executed out of stratum order: stratum=" + batch.stratum().id());
			}
			this.lastExecutedOrder = batch.stratum().order();
			return batch;
		}

			int cancelFrame(long frameId, String reason) {
				if (frameId == 0L || this.pending.isEmpty()) {
					return 0;
				}
				return cancelAll(reason + ":frame=" + frameId);
			}

			int cancelAll(String reason) {
				int cancelled = this.pending.size();
				if (!this.pending.isEmpty()) {
					LOGGER.info("Cancelling {} Rust VulkanicGAL deferred GUI batch(es): {}", this.pending.size(), reason);
					this.pending.clear();
				}
				this.lastExecutedOrder = Integer.MIN_VALUE;
				return cancelled;
			}

		int pendingCount() {
			return this.pending.size();
		}
	}

	private record DeferredGuiBatch(
		long batchId,
		long sequence,
		long generation,
		RenderStratum stratum,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		FillDirection fillDirection,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight,
		int guiWidth,
		int guiHeight
	) {
	}

	private static final class FrameSpriteBatchBuilder {
		private final RenderStratum stratum;
		private final CachedResources resources;
		private final List<PackedSprite> sprites = new ArrayList<>();

		FrameSpriteBatchBuilder(DeferredGuiBatch first, CachedResources resources) {
			this.stratum = first.stratum();
			this.resources = resources;
		}

		boolean canAppend(DeferredGuiBatch batch, CachedResources resources) {
			return this.sprites.size() < GUI_MAX_PACKED_SPRITES
				&& batch.stratum() == this.stratum
				&& resources.sameBindingsAs(this.resources);
		}

		void add(PackedSprite sprite) {
			this.sprites.add(sprite);
		}

		FrameSpriteBatch build() {
			return new FrameSpriteBatch(this.stratum, this.resources, List.copyOf(this.sprites));
		}
	}

	private record FrameSpriteBatch(RenderStratum stratum, CachedResources resources, List<PackedSprite> sprites) {
	}

	private record PackedSprite(
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		FillDirection fillDirection,
		int colorArgb,
		int x,
		int y,
		int width,
		int height,
		int sourceX,
		int sourceY,
		int sourceWidth,
		int sourceHeight,
		int guiWidth,
		int guiHeight
	) {
		static PackedSprite from(DeferredGuiBatch batch) {
			return new PackedSprite(
				batch.sprite(),
				batch.producerId(),
				batch.selectedSlot(),
				batch.progressFraction(),
				batch.fillDirection(),
				0xFFFFFFFF,
				batch.x(),
				batch.y(),
				batch.width(),
				batch.height(),
				batch.sourceX(),
				batch.sourceY(),
				batch.sourceWidth(),
				batch.sourceHeight(),
				batch.guiWidth(),
				batch.guiHeight()
			);
		}
	}

	private record CacheKey(String kind, String semanticId, long generation) {
		String label(String suffix) {
			return kind + "." + semanticId + ".gen" + generation + "." + suffix;
		}
	}

	private record CachedResources(
		CacheKey key,
		long uploadBuffer,
		long indexBuffer,
		long uniformBuffer,
		long texture,
		long sampler,
		long vertexShader,
		long fragmentShader,
		long textureView,
		long resourceLayout,
		long resourceSet,
		long pipelineLayout,
			long pipeline
	) {
		boolean sameBindingsAs(CachedResources other) {
			return this.pipeline == other.pipeline
				&& this.pipelineLayout == other.pipelineLayout
				&& this.resourceSet == other.resourceSet
				&& this.indexBuffer == other.indexBuffer
				&& this.uniformBuffer == other.uniformBuffer;
		}

		List<HandleToDestroy> handlesInDestroyOrder() {
			return List.of(
				new HandleToDestroy(pipeline, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE),
				new HandleToDestroy(pipelineLayout, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT),
				new HandleToDestroy(resourceSet, VulkanicGalBridge.HANDLE_RESOURCE_SET),
				new HandleToDestroy(resourceLayout, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT),
				new HandleToDestroy(textureView, VulkanicGalBridge.HANDLE_TEXTURE_VIEW),
				new HandleToDestroy(fragmentShader, VulkanicGalBridge.HANDLE_SHADER_MODULE),
				new HandleToDestroy(vertexShader, VulkanicGalBridge.HANDLE_SHADER_MODULE),
				new HandleToDestroy(sampler, VulkanicGalBridge.HANDLE_SAMPLER),
				new HandleToDestroy(texture, VulkanicGalBridge.HANDLE_TEXTURE),
				new HandleToDestroy(uniformBuffer, VulkanicGalBridge.HANDLE_BUFFER),
				new HandleToDestroy(indexBuffer, VulkanicGalBridge.HANDLE_BUFFER),
				new HandleToDestroy(uploadBuffer, VulkanicGalBridge.HANDLE_BUFFER)
			);
		}
	}

	private record HandleToDestroy(long handle, int kind) {
	}

	private record FrameResources(long target, long pass) {
	}

	private record AtlasRegion(int x, int y, int width, int height) {
	}

	private record TextureAtlas(TextureGroup group, int width, int height, byte[] bytes, Map<GuiSprite, AtlasRegion> regions) {
		AtlasRegion region(GuiSprite sprite) {
			AtlasRegion region = this.regions.get(sprite);
			if (region == null) {
				throw new IllegalArgumentException("sprite " + sprite.name() + " is not in atlas " + this.group.semanticId);
			}
			return region;
		}
	}

	private enum TextureGroup {
		GUI_ALPHA("gui-textured-alpha-atlas", "gui-alpha", false),
		GUI_INVERT("gui-textured-invert-atlas", "gui-invert", true);

		private final String cacheKind;
		private final String semanticId;
		private final boolean invertBlend;

		TextureGroup(String cacheKind, String semanticId, boolean invertBlend) {
			this.cacheKind = cacheKind;
			this.semanticId = semanticId;
			this.invertBlend = invertBlend;
		}

		private VulkanicGalBridge.ResourceBatchBuilder pipeline(
			VulkanicGalBridge.ResourceBatchBuilder builder,
			long id,
			String label,
			long pipelineLayout,
			long vertex,
			long fragment
		) {
			if (this.invertBlend) {
				return builder.guiInvertPipeline(id, label, pipelineLayout, vertex, fragment);
			}
			return builder.guiAlphaPipeline(id, label, pipelineLayout, vertex, fragment);
		}
	}

	private enum GuiSprite {
		CROSSHAIR(
			RenderStratum.GUI_CROSSHAIR,
			"crosshair",
			"gui-textured-invert-crosshair",
			"/assets/minecraft/textures/gui/sprites/hud/crosshair.png",
			15,
			15,
			true
		),
		HOTBAR_BASE(
			RenderStratum.GUI_HOTBAR_BASE,
			"hotbar-base",
			"gui-textured-alpha-hotbar-base",
			"/assets/minecraft/textures/gui/sprites/hud/hotbar.png",
			182,
			22,
			false
		),
			HOTBAR_SELECTION(
				RenderStratum.GUI_HOTBAR_SELECTION,
				"hotbar-selection",
				"gui-textured-alpha-hotbar-selection",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
				24,
				23,
				false
			),
			ARMOR_EMPTY(
				RenderStratum.GUI_ARMOR,
				"armor-empty",
				"gui-textured-alpha-armor-empty",
				"/assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
				9,
				9,
				false
			),
			ARMOR_HALF(
				RenderStratum.GUI_ARMOR,
				"armor-half",
				"gui-textured-alpha-armor-half",
				"/assets/minecraft/textures/gui/sprites/hud/armor_half.png",
				9,
				9,
				false
			),
			ARMOR_FULL(
				RenderStratum.GUI_ARMOR,
				"armor-full",
				"gui-textured-alpha-armor-full",
				"/assets/minecraft/textures/gui/sprites/hud/armor_full.png",
				9,
				9,
				false
			),
			EXPERIENCE_BAR_BACKGROUND(
			RenderStratum.GUI_EXPERIENCE_BAR_BACKGROUND,
			"experience-background",
			"gui-textured-alpha-experience-background",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png",
			182,
			5,
			false
		),
			EXPERIENCE_BAR_PROGRESS(
				RenderStratum.GUI_EXPERIENCE_BAR_PROGRESS,
				"experience-progress",
			"gui-textured-alpha-experience-progress",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png",
				182,
				5,
				false
			),
			CROSSHAIR_ATTACK_FULL(
				RenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-full",
				"gui-textured-alpha-attack-crosshair-full",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
				16,
				16,
				false
			),
			CROSSHAIR_ATTACK_BACKGROUND(
				RenderStratum.GUI_ATTACK_CROSSHAIR_BACKGROUND,
				"attack-crosshair-background",
				"gui-textured-alpha-attack-crosshair-background",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
				16,
				4,
				false
			),
			CROSSHAIR_ATTACK_PROGRESS(
				RenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-progress",
				"gui-textured-alpha-attack-crosshair-progress",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
				16,
				4,
				false
			),
			HOTBAR_ATTACK_BACKGROUND(
				RenderStratum.GUI_ATTACK_HOTBAR_BACKGROUND,
				"attack-hotbar-background",
				"gui-textured-alpha-attack-hotbar-background",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
				18,
				18,
				false
			),
			HOTBAR_ATTACK_PROGRESS(
				RenderStratum.GUI_ATTACK_HOTBAR_PROGRESS,
				"attack-hotbar-progress",
				"gui-textured-alpha-attack-hotbar-progress",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
				18,
				18,
				false
			),
			BOSS_BAR_PINK_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-pink-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-blue-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-red-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-green-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-yellow-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-purple-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-white-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PINK_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-pink-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-blue-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-red-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-green-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-yellow-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-purple-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-white-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-6-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-10-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-12-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_BACKGROUND(
				RenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-20-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-6-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-10-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-12-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_PROGRESS(
				RenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-20-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png",
				182,
				5,
				false
			);

		private final RenderStratum stratum;
		private final String phaseName;
		private final String cacheKind;
		private final String semanticSuffix;
		private final String textureResource;
		private final int width;
		private final int height;
		private final TextureGroup textureGroup;

		GuiSprite(RenderStratum stratum, String phaseName, String cacheKind, String textureResource, int width, int height, boolean invertBlend) {
			this.stratum = stratum;
			this.phaseName = phaseName;
			this.cacheKind = cacheKind;
			this.semanticSuffix = semanticSuffix(cacheKind);
			this.textureResource = textureResource;
			this.width = width;
			this.height = height;
			this.textureGroup = invertBlend ? TextureGroup.GUI_INVERT : TextureGroup.GUI_ALPHA;
		}

		private int textureBytes() {
			return this.width * this.height * 4;
		}

		private static String semanticSuffix(String cacheKind) {
			if (cacheKind.startsWith("gui-textured-alpha-")) {
				return cacheKind.substring("gui-textured-alpha-".length()).replace('_', '-');
			}
			if (cacheKind.startsWith("gui-textured-invert-")) {
				return cacheKind.substring("gui-textured-invert-".length()).replace('_', '-');
			}
			return cacheKind.replace('_', '-');
		}
	}

	private enum Operation {
		CONTEXT_CREATE,
		CAPABILITY_QUERY,
		FRAME_CONFIGURE,
		FRAME_ACQUIRE,
		FRAME_RESIZE,
		FRAME_PRESENT,
		RESOURCE_BATCH,
		SUBMIT,
		COMPLETION_QUERY,
		RETIRE
	}

	private static final class Metrics {
		long frames;
		long submissions;
		long cacheHits;
		long cacheMisses;
		long resourceCreates;
		long resourceDestroys;
		long ffiCalls;
		long ffiBytes;
		long lastContextFfiCalls;
		long lastContextFfiBytes;
		long cancellations;
		long reloadInvalidations;
			long completionPolls;
			long completionTimeouts;
			long batchesExecuted;
			long spriteBatchesExecuted;
			long packedSpritesExecuted;
			long batchesCancelled;
		long contextCreateCalls;
		long capabilityCalls;
		long frameConfigureCalls;
		long frameAcquireCalls;
		long frameResizeCalls;
		long framePresentCalls;
		long resourceBatchCalls;
		long submitCalls;
		long completionQueryCalls;
		long retireCalls;
		long contextCreateBytes;
		long capabilityBytes;
		long frameConfigureBytes;
		long frameAcquireBytes;
		long frameResizeBytes;
		long framePresentBytes;
		long resourceBatchBytes;
		long submitBytes;
		long completionQueryBytes;
		long retireBytes;
		long enqueueNanos;
		long resourceLookupNanos;
		long resourceCreateNanos;
		long abiPackingNanos;
		long frameAcquireNanos;
		long submitNanos;
		long framePresentNanos;
		long retireNanos;
		long completionQueryNanos;
		long executeNanos;
		long commandLists;
		long commandOps;
		long backendSubmissions;
		long backendWaits;
		long glCalls;
		long glFlushes;
		long glFinishes;
		long glFencesInserted;
		long glFencesPolled;
		long glFencesWaited;
		long glFencesDeleted;
	}

	public record MetricsSnapshot(
		long frames,
		long submissions,
		long cacheHits,
		long cacheMisses,
		long resourceCreates,
		long resourceDestroys,
		long ffiCalls,
		long ffiBytes,
		long cancellations,
		long reloadInvalidations,
		long completionPolls,
		long completionTimeouts,
			long pendingBatches,
			long batchesExecuted,
			long spriteBatchesExecuted,
			long packedSpritesExecuted,
			long batchesCancelled,
		long contextCreateCalls,
		long capabilityCalls,
		long frameConfigureCalls,
		long frameAcquireCalls,
		long frameResizeCalls,
		long framePresentCalls,
		long resourceBatchCalls,
		long submitCalls,
		long completionQueryCalls,
		long retireCalls,
		long contextCreateBytes,
		long capabilityBytes,
		long frameConfigureBytes,
		long frameAcquireBytes,
		long frameResizeBytes,
		long framePresentBytes,
		long resourceBatchBytes,
		long submitBytes,
		long completionQueryBytes,
		long retireBytes,
		long enqueueNanos,
		long resourceLookupNanos,
		long resourceCreateNanos,
		long abiPackingNanos,
		long frameAcquireNanos,
		long submitNanos,
		long framePresentNanos,
		long retireNanos,
		long completionQueryNanos,
		long executeNanos,
		long commandLists,
		long commandOps,
		long backendSubmissions,
		long backendWaits,
		long glCalls,
		long glFlushes,
		long glFinishes,
		long glFencesInserted,
		long glFencesPolled,
		long glFencesWaited,
		long glFencesDeleted
	) {
	}

		private static final String VERTEX_SHADER_OPENGL = """
			#version 330 core
		struct PackedGuiSprite {
		    vec4 rect;
		    vec4 viewport;
		    vec4 uv_region;
		    vec4 color;
		};
		layout(std140) uniform GuiSpriteBatch {
		    PackedGuiSprite sprites[256];
		};
			out vec2 v_uv;
			out vec2 v_sprite_corner;
			out vec4 v_color;
			flat out vec4 v_uv_region;
		const vec2 corner[6] = vec2[6](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0),
		    vec2(0.0, 0.0)
		);
		void main() {
		    int vertex = gl_VertexID;
		    PackedGuiSprite sprite = sprites[gl_InstanceID];
		    vec2 pixel = sprite.rect.xy + corner[vertex] * sprite.rect.zw;
		    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, 1.0 - (pixel.y / sprite.viewport.y) * 2.0);
			    gl_Position = vec4(ndc, 0.0, 1.0);
			    v_uv_region = sprite.uv_region;
			    v_sprite_corner = corner[vertex];
			    v_uv = vec2(
			        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
			        sprite.uv_region.y + (1.0 - corner[vertex].y) * sprite.uv_region.w
		    );
		    v_color = sprite.color;
		}
		""";

	private static final String FRAGMENT_SHADER_OPENGL = """
			#version 330 core
			uniform sampler2D Sampler0;
			in vec2 v_uv;
			in vec2 v_sprite_corner;
			in vec4 v_color;
			flat in vec4 v_uv_region;
			out vec4 out_color;
			void main() {
			    ivec2 texture_size = textureSize(Sampler0, 0);
			    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
			    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
			    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
			    ivec2 texel = ivec2(origin.x + local.x, origin.y + extent.y - 1 - local.y);
			    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
			    vec4 color = texelFetch(Sampler0, texel, 0) * v_color;
			    if (color.a <= 0.0) {
			        discard;
		    }
		    out_color = color;
		}
		""";
}
