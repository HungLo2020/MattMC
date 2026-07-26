package net.vulkanic.gui;

import net.vulkanic.bridge.RustGalFrameScheduler;
import net.vulkanic.bridge.VulkanicGalBridge;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.util.profiling.TracyCompat;
import net.minecraft.world.BossEvent;
import net.vulkanic.VulkanicAPI;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RustGalGuiRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
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
	private static final String PLAYER_HEART_PRODUCER = "minecraft.gui.player-heart";
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
	private static final RustGalFrameScheduler<GuiBatchBuilder.GuiSpriteRequest> SCHEDULER =
		new RustGalFrameScheduler<>("Rust VulkanicGAL deferred GUI");
	private static final GuiResourceCache RESOURCE_CACHE = new GuiResourceCache();
	private static final GuiResourceCache.Recorder RESOURCE_RECORDER = new GuiResourceCache.Recorder() {
		@Override
		public void recordResourceBatch(VulkanicGalBridge.ResourceResults results) {
			RustGalGuiRenderer.recordResourceBatch(results);
		}

		@Override
		public void recordUploadStatus(VulkanicGalBridge.Status status) {
			RustGalGuiRenderer.recordStatus(Operation.SUBMIT, status);
			lastSubmitted = Math.max(lastSubmitted, status.submissionId());
		}

		@Override
		public void resourcesCreated(int count) {
			METRICS.resourceCreates += count;
		}

		@Override
		public void resourceDestroyed() {
			METRICS.resourceDestroys++;
		}
	};
	private static final Metrics METRICS = new Metrics();

	private RustGalGuiRenderer() {
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

	public static boolean isPlayerHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.disabled");
	}

	public static boolean isPlayerHealthLegacyControl() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.legacyControl");
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
			GuiFillDirection.NONE,
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
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
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
				GuiFillDirection.NONE,
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
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
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
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
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
			GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
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
				GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
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
				GuiFillDirection.NONE,
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

	public static void enqueuePlayerHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<PlayerHeartRequest> hearts
	) {
		if (!isCrosshairEnabled() || hearts.isEmpty()) {
			return;
		}
		for (PlayerHeartRequest heart : hearts) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				heart.sprite(),
				PLAYER_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9,
				0,
				0,
				9,
				9
			);
		}
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
		enqueueGuiSprite(minecraft, guiGraphics, sprite, producerId, selectedSlot, -1.0F, GuiFillDirection.NONE, x, y, width, height, 0, 0, width, height);
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		GuiFillDirection fillDirection,
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
				GuiBatchBuilder.GuiSpriteRequest request = new GuiBatchBuilder.GuiSpriteRequest(
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
					guiGraphics.guiWidth(),
					guiGraphics.guiHeight()
				);
				RustGalFrameScheduler.Token token = SCHEDULER.enqueue(generation, sprite.stratum.id(), sprite.stratum.order(), request);
				guiGraphics.guiRenderState.submitGuiElement(
					new RustGalGuiElementRenderState(
						token,
						sprite.stratum,
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
						guiGraphics.guiWidth(),
						guiGraphics.guiHeight()
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
			List<RustGalFrameScheduler.Token> tokens = elements.stream().map(RustGalGuiElementRenderState::token).toList();
			List<GuiBatchBuilder.GuiSpriteRequest> requests = SCHEDULER.takeAll(tokens, generation);
			executeFrameBatches(window, requests);
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
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
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
				RESOURCE_CACHE.clearAtlasesAndCaches();
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
				RESOURCE_CACHE.clearCachesOnly();
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

	private static void executeFrameBatches(Window window, List<GuiBatchBuilder.GuiSpriteRequest> requests) {
		if (requests.isEmpty()) {
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
				List<GuiBatchBuilder.FrameSpriteBatch> spriteBatches;
				try {
					spriteBatches = GuiBatchBuilder.packCompatibleSpriteBatches(requests, RustGalGuiRenderer::resourcesFor);
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
				VulkanicGalBridge.SubmissionBatch submit = GuiBatchBuilder.buildSubmission(bridge, frameResources.pass(), frameResources.target(), spriteBatches);
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
				+ " frame=" + frameId + " submission=" + submissionId + " batches=" + requests.size());
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.present");
			long presentStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_PRESENT, VulkanicGalBridge.Struct.FRAME_PRESENT.byteSize());
			bridge.presentFrame(frameId, correlationId, submissionId);
			METRICS.framePresentNanos += elapsedSince(presentStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.present");
				METRICS.frames++;
				METRICS.submissions++;
				METRICS.batchesExecuted += requests.size();
				METRICS.spriteBatchesExecuted += spriteBatches.size();
				METRICS.packedSpritesExecuted += requests.size();
				retireOutstanding(false);
			METRICS.executeNanos += elapsedSince(executeStarted);
			executeCounted = true;
			if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
					LOGGER.info(
						"Rust VulkanicGAL GUI frame executed: batches={}, spriteBatches={}, frame={}, submission={}, cacheHits={}, cacheMisses={}, ffiCalls={}, ffiBytes={}",
						requests.size(),
						spriteBatches.size(),
						frameId,
						submissionId,
						METRICS.cacheHits,
					METRICS.cacheMisses,
					METRICS.ffiCalls,
					METRICS.ffiBytes
				);
			}
				auditMessage(metricsAuditLine(requests.size(), frameId, submissionId));
		} finally {
			if (!executeCounted) {
				METRICS.executeNanos += elapsedSince(executeStarted);
			}
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.execute");
		}
	}

	public static List<Integer> debugPackCompatibleRunLengthsForTests(List<GuiRenderStratum> strata, List<String> resourceKeys) {
		return GuiBatchBuilder.debugPackCompatibleRunLengthsForTests(strata, resourceKeys);
	}

	public static List<String> debugPackedUniformCommandSequenceForTests(List<GuiRenderStratum> strata, List<String> resourceKeys) {
		return GuiBatchBuilder.debugPackedUniformCommandSequenceForTests(strata, resourceKeys);
	}

	public static float[] debugArmorOpenGlUvYRangeForTests(ArmorIconState state) {
		return GuiBatchBuilder.debugArmorOpenGlUvYRangeForTests(state);
	}

	public static int[] debugArmorOpenGlSampledLocalRowsForTests(ArmorIconState state, int guiScale) {
		return GuiBatchBuilder.debugArmorOpenGlSampledLocalRowsForTests(state, guiScale);
	}

	public static String debugOpenGlPackedSpriteVertexShaderForTests() {
		return GuiPipelineLibrary.VERTEX_SHADER_OPENGL;
	}

	public static String debugOpenGlPackedSpriteFragmentShaderForTests() {
		return GuiPipelineLibrary.FRAGMENT_SHADER_OPENGL;
	}

	private static GuiResourceCache.CachedResources resourcesFor(GuiBatchBuilder.GuiSpriteRequest request) {
		GuiResourceCache.Lookup lookup = RESOURCE_CACHE.resourcesFor(bridge, generation, request.sprite().textureGroup, RESOURCE_RECORDER);
		if (lookup.cacheHit()) {
			METRICS.cacheHits++;
		} else {
			METRICS.cacheMisses++;
			METRICS.resourceCreateNanos += lookup.createNanos();
		}
		return lookup.resources();
	}

	private static void destroyCachedResources() {
		RESOURCE_CACHE.destroyCachedResources(bridge, RESOURCE_RECORDER);
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

		String id() {
			return this.id;
		}

		GuiSprite sprite() {
			return this.sprite;
		}
	}

	public record PlayerHeartRequest(
		PlayerHeartVariant variant,
		PlayerHeartState state,
		boolean hardcore,
		boolean flashing,
		int order,
		int x,
		int y
	) {
		public PlayerHeartRequest {
			if (variant == null) {
				throw new IllegalArgumentException("player heart variant must be provided");
			}
			if (state == null) {
				throw new IllegalArgumentException("player heart state must be provided");
			}
			if (order < 0) {
				throw new IllegalArgumentException("player heart order must be non-negative: " + order);
			}
			if (state == PlayerHeartState.CONTAINER && variant != PlayerHeartVariant.CONTAINER) {
				throw new IllegalArgumentException("container player hearts must use the container variant");
			}
			if (state != PlayerHeartState.CONTAINER && variant == PlayerHeartVariant.CONTAINER) {
				throw new IllegalArgumentException("filled player hearts must use a non-container variant");
			}
		}

		GuiSprite sprite() {
			return variant.sprite(state, hardcore, flashing);
		}
	}

	public enum PlayerHeartState {
		CONTAINER("container", 0.0F),
		HALF("half", 0.5F),
		FULL("full", 1.0F);

		private final String id;
		private final float progressValue;

		PlayerHeartState(String id, float progressValue) {
			this.id = id;
			this.progressValue = progressValue;
		}

		String id() {
			return this.id;
		}

		float progressValue() {
			return this.progressValue;
		}
	}

	public enum PlayerHeartVariant {
		CONTAINER("container"),
		NORMAL("normal"),
		POISONED("poisoned"),
		WITHERED("withered"),
		FROZEN("frozen");

		private final String id;

		PlayerHeartVariant(String id) {
			this.id = id;
		}

		String id() {
			return this.id;
		}

		GuiSprite sprite(PlayerHeartState state, boolean hardcore, boolean flashing) {
			return switch (this) {
				case CONTAINER -> {
					if (state != PlayerHeartState.CONTAINER) {
						throw new IllegalArgumentException("container heart variant cannot render " + state);
					}
					if (hardcore) {
						yield flashing ? GuiSprite.HEART_CONTAINER_HARDCORE_FLASHING : GuiSprite.HEART_CONTAINER_HARDCORE;
					}
					yield flashing ? GuiSprite.HEART_CONTAINER_FLASHING : GuiSprite.HEART_CONTAINER;
				}
				case NORMAL -> filledSprite(state, hardcore, flashing,
					GuiSprite.HEART_NORMAL_FULL,
					GuiSprite.HEART_NORMAL_FULL_FLASHING,
					GuiSprite.HEART_NORMAL_HALF,
					GuiSprite.HEART_NORMAL_HALF_FLASHING,
					GuiSprite.HEART_NORMAL_HARDCORE_FULL,
					GuiSprite.HEART_NORMAL_HARDCORE_FULL_FLASHING,
					GuiSprite.HEART_NORMAL_HARDCORE_HALF,
					GuiSprite.HEART_NORMAL_HARDCORE_HALF_FLASHING);
				case POISONED -> filledSprite(state, hardcore, flashing,
					GuiSprite.HEART_POISONED_FULL,
					GuiSprite.HEART_POISONED_FULL_FLASHING,
					GuiSprite.HEART_POISONED_HALF,
					GuiSprite.HEART_POISONED_HALF_FLASHING,
					GuiSprite.HEART_POISONED_HARDCORE_FULL,
					GuiSprite.HEART_POISONED_HARDCORE_FULL_FLASHING,
					GuiSprite.HEART_POISONED_HARDCORE_HALF,
					GuiSprite.HEART_POISONED_HARDCORE_HALF_FLASHING);
				case WITHERED -> filledSprite(state, hardcore, flashing,
					GuiSprite.HEART_WITHERED_FULL,
					GuiSprite.HEART_WITHERED_FULL_FLASHING,
					GuiSprite.HEART_WITHERED_HALF,
					GuiSprite.HEART_WITHERED_HALF_FLASHING,
					GuiSprite.HEART_WITHERED_HARDCORE_FULL,
					GuiSprite.HEART_WITHERED_HARDCORE_FULL_FLASHING,
					GuiSprite.HEART_WITHERED_HARDCORE_HALF,
					GuiSprite.HEART_WITHERED_HARDCORE_HALF_FLASHING);
				case FROZEN -> filledSprite(state, hardcore, flashing,
					GuiSprite.HEART_FROZEN_FULL,
					GuiSprite.HEART_FROZEN_FULL_FLASHING,
					GuiSprite.HEART_FROZEN_HALF,
					GuiSprite.HEART_FROZEN_HALF_FLASHING,
					GuiSprite.HEART_FROZEN_HARDCORE_FULL,
					GuiSprite.HEART_FROZEN_HARDCORE_FULL_FLASHING,
					GuiSprite.HEART_FROZEN_HARDCORE_HALF,
					GuiSprite.HEART_FROZEN_HARDCORE_HALF_FLASHING);
			};
		}

		private static GuiSprite filledSprite(
			PlayerHeartState state,
			boolean hardcore,
			boolean flashing,
			GuiSprite full,
			GuiSprite fullFlashing,
			GuiSprite half,
			GuiSprite halfFlashing,
			GuiSprite hardcoreFull,
			GuiSprite hardcoreFullFlashing,
			GuiSprite hardcoreHalf,
			GuiSprite hardcoreHalfFlashing
		) {
			return switch (state) {
				case CONTAINER -> throw new IllegalArgumentException("filled player heart variant cannot render a container");
				case FULL -> hardcore ? (flashing ? hardcoreFullFlashing : hardcoreFull) : (flashing ? fullFlashing : full);
				case HALF -> hardcore ? (flashing ? hardcoreHalfFlashing : hardcoreHalf) : (flashing ? halfFlashing : half);
			};
		}
	}

	private record FrameResources(long target, long pass) {
	}

	enum TextureGroup {
		GUI_ALPHA("gui-textured-alpha-atlas", "gui-alpha", false),
		GUI_INVERT("gui-textured-invert-atlas", "gui-invert", true);

		final String cacheKind;
		final String semanticId;
		final boolean invertBlend;

		TextureGroup(String cacheKind, String semanticId, boolean invertBlend) {
			this.cacheKind = cacheKind;
			this.semanticId = semanticId;
			this.invertBlend = invertBlend;
		}
	}

	enum GuiSprite {
		CROSSHAIR(
			GuiRenderStratum.GUI_CROSSHAIR,
			"crosshair",
			"gui-textured-invert-crosshair",
			"/assets/minecraft/textures/gui/sprites/hud/crosshair.png",
			15,
			15,
			true
		),
		HOTBAR_BASE(
			GuiRenderStratum.GUI_HOTBAR_BASE,
			"hotbar-base",
			"gui-textured-alpha-hotbar-base",
			"/assets/minecraft/textures/gui/sprites/hud/hotbar.png",
			182,
			22,
			false
		),
			HOTBAR_SELECTION(
				GuiRenderStratum.GUI_HOTBAR_SELECTION,
				"hotbar-selection",
				"gui-textured-alpha-hotbar-selection",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
				24,
				23,
				false
			),
			ARMOR_EMPTY(
				GuiRenderStratum.GUI_ARMOR,
				"armor-empty",
				"gui-textured-alpha-armor-empty",
				"/assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
				9,
				9,
				false
			),
			ARMOR_HALF(
				GuiRenderStratum.GUI_ARMOR,
				"armor-half",
				"gui-textured-alpha-armor-half",
				"/assets/minecraft/textures/gui/sprites/hud/armor_half.png",
				9,
				9,
				false
			),
				ARMOR_FULL(
					GuiRenderStratum.GUI_ARMOR,
					"armor-full",
					"gui-textured-alpha-armor-full",
					"/assets/minecraft/textures/gui/sprites/hud/armor_full.png",
					9,
					9,
					false
				),
				HEART_CONTAINER(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				EXPERIENCE_BAR_BACKGROUND(
			GuiRenderStratum.GUI_EXPERIENCE_BAR_BACKGROUND,
			"experience-background",
			"gui-textured-alpha-experience-background",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png",
			182,
			5,
			false
		),
			EXPERIENCE_BAR_PROGRESS(
				GuiRenderStratum.GUI_EXPERIENCE_BAR_PROGRESS,
				"experience-progress",
			"gui-textured-alpha-experience-progress",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png",
				182,
				5,
				false
			),
			CROSSHAIR_ATTACK_FULL(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-full",
				"gui-textured-alpha-attack-crosshair-full",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
				16,
				16,
				false
			),
			CROSSHAIR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_BACKGROUND,
				"attack-crosshair-background",
				"gui-textured-alpha-attack-crosshair-background",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
				16,
				4,
				false
			),
			CROSSHAIR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-progress",
				"gui-textured-alpha-attack-crosshair-progress",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
				16,
				4,
				false
			),
			HOTBAR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_BACKGROUND,
				"attack-hotbar-background",
				"gui-textured-alpha-attack-hotbar-background",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
				18,
				18,
				false
			),
			HOTBAR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_PROGRESS,
				"attack-hotbar-progress",
				"gui-textured-alpha-attack-hotbar-progress",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
				18,
				18,
				false
			),
			BOSS_BAR_PINK_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-pink-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-blue-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-red-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-green-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-yellow-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-purple-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-white-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PINK_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-pink-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-blue-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-red-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-green-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-yellow-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-purple-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-white-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-6-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-10-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-12-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-20-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-6-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-10-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-12-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-20-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png",
				182,
				5,
				false
			);

		final GuiRenderStratum stratum;
		final String phaseName;
		final String cacheKind;
		final String semanticSuffix;
		final String textureResource;
		final int width;
		final int height;
		final TextureGroup textureGroup;

		GuiSprite(GuiRenderStratum stratum, String phaseName, String cacheKind, String textureResource, int width, int height, boolean invertBlend) {
			this.stratum = stratum;
			this.phaseName = phaseName;
			this.cacheKind = cacheKind;
			this.semanticSuffix = semanticSuffix(cacheKind);
			this.textureResource = textureResource;
			this.width = width;
			this.height = height;
			this.textureGroup = invertBlend ? TextureGroup.GUI_INVERT : TextureGroup.GUI_ALPHA;
		}

		int textureBytes() {
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

}
