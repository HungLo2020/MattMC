package net.vulkanic.gui;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.dev.RenderDocCaptureHook;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.TracyCompat;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalFrameScheduler;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RustGalFrameCoordinator {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Object LOCK = new Object();
	private static VulkanicGalBridge bridge;
	private static Thread renderThread;
	private static int configuredWidth;
	private static int configuredHeight;
	private static long nextCorrelationId = 1L;
	private static long generation = 1L;
	private static long assetGeneration = 1L;
	private static long uploadedAssetGeneration;
	private static long attemptedAssetGeneration;
	private static long lastAssetPayloadCount;
	private static long lastAssetPayloadBytes;
	private static long assetUpdateFailures;
	private static long lastSubmitted;
	private static long lastRetiredSubmission;
	private static boolean wholeFrameAttachmentCorrelationWritten;
	private static List<VulkanicGalBridge.GuiAssetRecord> pendingAssets = List.of();
	private static final RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord> SCHEDULER =
		new RustGalFrameScheduler<>("Rust VulkanicGAL deferred GUI");
	private static final Metrics METRICS = new Metrics();

	private RustGalFrameCoordinator() {
	}

	static RustGalFrameScheduler.Token enqueueGuiRequest(
		VulkanicGalBridge.GuiSpriteRecord request,
		GuiRenderStratum stratum,
		long startedNanos
	) {
		synchronized (LOCK) {
			RustGalFrameScheduler.Token token = SCHEDULER.enqueue(generation, stratum.id(), stratum.order(), request);
			METRICS.enqueueNanos += elapsedSince(startedNanos);
			return token;
		}
	}

	public static void executeGuiFrame(Minecraft minecraft, List<RustGalGuiElementRenderState> elements) {
		if (elements.isEmpty()) {
			return;
		}
		RustGalGuiRenderer.GuiExecutionRoute route = RustGalGuiRenderer.currentExecutionRoute();
		if (route != RustGalGuiRenderer.GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT) {
			throw new IllegalStateException("Rust OpenGL borrowed-context GUI execution requires route "
				+ RustGalGuiRenderer.GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT + "; current route is " + route);
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
			List<VulkanicGalBridge.GuiSpriteRecord> requests = SCHEDULER.takeAll(tokens, generation);
			executeFrameBatches(window, requests, false, window.getGuiScaledWidth(), window.getGuiScaledHeight());
		}
	}

	public static boolean executeWorldPrimitiveFrame(
		Minecraft minecraft,
		RustGalWorldPrimitiveRenderer.PrimitiveFrame primitiveFrame,
		String producerLabel
	) {
		if (primitiveFrame == null
			|| (primitiveFrame.segments().isEmpty()
				&& primitiveFrame.crackQuads().isEmpty()
				&& primitiveFrame.borderQuads().isEmpty()
				&& primitiveFrame.materialQuads().isEmpty()
				&& primitiveFrame.meshInstances().isEmpty())) {
			return false;
		}
		RustGalGuiRenderer.GuiExecutionRoute route = RustGalGuiRenderer.currentExecutionRoute();
		if (route != RustGalGuiRenderer.GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT) {
			throw new IllegalStateException("Rust OpenGL borrowed-context world primitive execution requires route "
				+ RustGalGuiRenderer.GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT + "; current route is " + route);
		}
		ensureRenderThreadAndContext(minecraft);
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		synchronized (LOCK) {
			flushPendingWorldAssetsLocked();
			long executeStarted = System.nanoTime();
			GraphicsFrameBenchmark.beginPhase("rust-gal.world-primitives.execute");
			long correlationId = nextCorrelationId++;
			long frameId = 0L;
			long submissionId = 0L;
			boolean executeCounted = false;
			try {
				GraphicsFrameBenchmark.beginPhase("rust-gal.world-primitives.ffi.acquire");
				long acquireStarted = System.nanoTime();
				recordFixedOperation(Operation.FRAME_ACQUIRE, VulkanicGalBridge.Struct.FRAME_ACQUIRE.byteSize());
				VulkanicGalBridge.AcquiredFrame frame = bridge.acquireFrame(correlationId, window.getWidth(), window.getHeight());
				METRICS.frameAcquireNanos += elapsedSince(acquireStarted);
				GraphicsFrameBenchmark.endPhase("rust-gal.world-primitives.ffi.acquire");
				frameId = frame.frameId();
				if (frame.status() == 4 || frame.frameTarget() == 0L) {
					METRICS.cancellations++;
					return false;
				}
				GraphicsFrameBenchmark.beginPhase("rust-gal.world-primitives.submit-call");
				long packingStarted = System.nanoTime();
				VulkanicGalBridge.WholeFrameSubmitResult result = bridge.submitWorldPrimitives(
					generation,
					frameId,
					correlationId,
					frame.frameTarget(),
					primitiveFrame.viewportWidth() <= 0 ? window.getWidth() : primitiveFrame.viewportWidth(),
					primitiveFrame.viewportHeight() <= 0 ? window.getHeight() : primitiveFrame.viewportHeight(),
					primitiveFrame.viewMatrix(),
					primitiveFrame.projectionMatrix(),
					primitiveFrame.segments(),
					primitiveFrame.crackQuads(),
					primitiveFrame.borderQuads(),
					primitiveFrame.materialQuads(),
					primitiveFrame.meshInstances()
				);
				METRICS.abiPackingNanos += elapsedSince(packingStarted);
				GraphicsFrameBenchmark.endPhase("rust-gal.world-primitives.submit-call");
				recordStatus(Operation.SUBMIT, result.asStatus());
				submissionId = result.submissionId();
				lastSubmitted = Math.max(lastSubmitted, submissionId);
				GraphicsFrameBenchmark.beginPhase("rust-gal.world-primitives.ffi.present");
				long presentStarted = System.nanoTime();
				recordFixedOperation(Operation.FRAME_PRESENT, VulkanicGalBridge.Struct.FRAME_PRESENT.byteSize());
				bridge.presentFrame(frameId, correlationId, submissionId);
				METRICS.framePresentNanos += elapsedSince(presentStarted);
				GraphicsFrameBenchmark.endPhase("rust-gal.world-primitives.ffi.present");
				METRICS.frames++;
				METRICS.submissions++;
				recordWorldMetrics(result);
				TracyCompat.message("gal.frame.deferred producer=" + producerLabel
					+ " stratum=world.primitives frame=" + frameId + " submission=" + submissionId
					+ " segments=" + result.worldSegmentCount()
					+ " crackQuads=" + result.worldCrackQuadCount()
					+ " borderQuads=" + result.worldBorderQuadCount()
					+ " materialQuads=" + result.worldMaterialQuadCount()
					+ " meshInstances=" + result.worldMeshInstanceCount());
				retireOutstanding(false);
				auditMessage(metricsAuditLine(0, frameId, submissionId, false));
				METRICS.executeNanos += elapsedSince(executeStarted);
				executeCounted = true;
				return true;
			} finally {
				if (!executeCounted) {
					METRICS.executeNanos += elapsedSince(executeStarted);
				}
				GraphicsFrameBenchmark.endPhase("rust-gal.world-primitives.execute");
			}
		}
	}

	public static void executeWholeFrameVulkan(Minecraft minecraft, GuiRenderState renderState) {
		if (!RustGalGuiRenderer.isWholeFrameVulkanActive()) {
			throw new IllegalStateException("Rust Vulkan whole-frame shell requires "
				+ RustGalVulkanWholeFrameMode.propertyName() + "=true and Vulkan backend selection");
		}
		if (!VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Rust Vulkan whole-frame shell requires the Java Vulkan backend selection at startup");
		}
		ensureRenderThreadAndWindowedVulkanContext(minecraft);
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		synchronized (LOCK) {
			List<RustGalFrameScheduler.Token> tokens = new ArrayList<>();
			renderState.forEachElement(element -> {
				if (element instanceof RustGalGuiElementRenderState rustGalElement) {
					tokens.add(rustGalElement.token());
				}
			}, GuiRenderState.TraverseRange.ALL);
			List<VulkanicGalBridge.GuiSpriteRecord> requests = SCHEDULER.takeAll(tokens, generation);
			executeFrameBatches(window, requests, true, window.getGuiScaledWidth(), window.getGuiScaledHeight());
		}
	}

	public static void resize(int width, int height) {
		synchronized (LOCK) {
			configuredWidth = 0;
			configuredHeight = 0;
			int cancelled = SCHEDULER.cancelAll("resize");
			retireOutstanding(true);
			METRICS.cancellations++;
			METRICS.batchesCancelled += cancelled;
		}
	}

	public static void reload(ResourceManager resourceManager) {
		if (RustGalGuiRenderer.assetUpdatesDisabled()) {
			auditMessage("Rust VulkanicGAL GUI asset update skipped reason=diagnostic-disabled");
			return;
		}
		List<VulkanicGalBridge.GuiAssetRecord> assets = RustGalGuiRenderer.collectResolvedAssets(resourceManager);
		RustGalWorldPrimitiveRenderer.reloadWorldAssets(resourceManager);
		synchronized (LOCK) {
			generation++;
			assetGeneration++;
			pendingAssets = assets;
			attemptedAssetGeneration = Math.min(attemptedAssetGeneration, uploadedAssetGeneration);
			int cancelled = SCHEDULER.cancelAll("resource-reload");
			METRICS.reloadInvalidations++;
			METRICS.batchesCancelled += cancelled;
			retireOutstanding(true);
			flushPendingGuiAssetsLocked();
			flushPendingWorldAssetsLocked();
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
			auditMessage(metricsAuditLine(0L, METRICS.frames, lastSubmitted, RustGalGuiRenderer.isWholeFrameVulkanEnabled()));
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

	public static String currentAuditMetricsLine() {
		synchronized (LOCK) {
			return metricsAuditLine(0L, METRICS.frames, lastSubmitted, RustGalGuiRenderer.isWholeFrameVulkanEnabled());
		}
	}

	static void auditMessage(String message) {
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			System.out.println("[MattMC graphics audit] " + message);
		}
	}

	private static void executeFrameBatches(Window window, List<VulkanicGalBridge.GuiSpriteRecord> requests, boolean allowEmpty, int guiWidth, int guiHeight) {
		if (requests.isEmpty() && !allowEmpty) {
			return;
		}
		long executeStarted = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.execute");
		long correlationId = nextCorrelationId++;
		long frameId = 0L;
		long submissionId = 0L;
		boolean executeCounted = false;
		RustGalWorldPrimitiveRenderer.PrimitiveFrame primitiveFrame = null;
		boolean wholeFrameVulkan = allowEmpty && RustGalGuiRenderer.isWholeFrameVulkanActive();
		boolean renderdocFrameCaptureStarted = false;
		try {
			if (wholeFrameVulkan) {
				primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();
				flushPendingWorldAssetsLocked();
				if (!primitiveFrame.segments().isEmpty()
					|| !primitiveFrame.crackQuads().isEmpty()
					|| !primitiveFrame.borderQuads().isEmpty()
					|| !primitiveFrame.materialQuads().isEmpty()
					|| !primitiveFrame.meshInstances().isEmpty()) {
					renderdocFrameCaptureStarted = RenderDocCaptureHook.beginFrameCaptureOnce(
						window,
						"rust-vulkan-whole-frame-world#" + correlationId
							+ "-segments=" + primitiveFrame.segments().size()
							+ "-crackQuads=" + primitiveFrame.crackQuads().size()
							+ "-borderQuads=" + primitiveFrame.borderQuads().size()
							+ "-materialQuads=" + primitiveFrame.materialQuads().size()
							+ "-meshInstances=" + primitiveFrame.meshInstances().size()
					);
				}
			}
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.acquire");
			long acquireStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_ACQUIRE, VulkanicGalBridge.Struct.FRAME_ACQUIRE.byteSize());
			VulkanicGalBridge.AcquiredFrame frame = bridge.acquireFrame(correlationId, window.getWidth(), window.getHeight());
			METRICS.frameAcquireNanos += elapsedSince(acquireStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.acquire");
			frameId = frame.frameId();
			if (wholeFrameVulkan) {
				recordWholeFrameAcquire(frame, correlationId);
			}
			if (frame.status() == 4 || frame.frameTarget() == 0L) {
				int cancelled = SCHEDULER.cancelFrame(frameId, "acquire-skipped");
				METRICS.cancellations++;
				METRICS.batchesCancelled += cancelled;
				return;
			}
			if (wholeFrameVulkan && primitiveFrame != null) {
				primitiveFrame = RustGalWorldPrimitiveRenderer.withViewport(
					primitiveFrame,
					Math.max(1, frame.width()),
					Math.max(1, frame.height())
				);
			}

			GraphicsFrameBenchmark.beginPhase("rust-gal.frame.submit-call");
			long packingStarted = System.nanoTime();
			int frameGuiWidth = requests.isEmpty() ? guiWidth : requests.get(0).guiWidth();
			int frameGuiHeight = requests.isEmpty() ? guiHeight : requests.get(0).guiHeight();
			VulkanicGalBridge.WholeFrameSubmitResult wholeFrameResult = null;
			VulkanicGalBridge.GuiFrameSubmitResult guiResult = null;
			if (wholeFrameVulkan) {
				if (primitiveFrame == null) {
					primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();
				}
				primitiveFrame = RustGalWorldPrimitiveRenderer.withViewport(
					primitiveFrame,
					Math.max(1, frame.width()),
					Math.max(1, frame.height())
				);
				wholeFrameResult = bridge.submitWholeFrame(
					generation,
					frameId,
					correlationId,
					frame.frameTarget(),
					frameGuiWidth,
					frameGuiHeight,
					primitiveFrame.viewportWidth() <= 0 ? window.getWidth() : primitiveFrame.viewportWidth(),
					primitiveFrame.viewportHeight() <= 0 ? window.getHeight() : primitiveFrame.viewportHeight(),
					primitiveFrame.viewMatrix(),
					primitiveFrame.projectionMatrix(),
					primitiveFrame.background(),
					primitiveFrame.segments(),
					primitiveFrame.crackQuads(),
					primitiveFrame.borderQuads(),
					primitiveFrame.materialQuads(),
					primitiveFrame.meshInstances(),
					requests
				);
			} else {
				guiResult = bridge.submitGuiFrame(
					generation,
					frameId,
					frame.frameTarget(),
					frameGuiWidth,
					frameGuiHeight,
					requests
				);
			}
			METRICS.abiPackingNanos += elapsedSince(packingStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.frame.submit-call");
			recordStatus(Operation.SUBMIT, wholeFrameResult != null ? wholeFrameResult.asStatus() : guiResult.asStatus());
			submissionId = wholeFrameResult != null ? wholeFrameResult.submissionId() : guiResult.submissionId();
			if (wholeFrameVulkan) {
				auditWholeFrameTarget(frame, primitiveFrame);
			}
			lastSubmitted = Math.max(lastSubmitted, submissionId);
			TracyCompat.message("gal.frame.deferred producer=gui.frame stratum=gui.frame"
				+ " frame=" + frameId + " submission=" + submissionId + " batches=" + requests.size());

			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.ffi.present");
			long presentStarted = System.nanoTime();
			recordFixedOperation(Operation.FRAME_PRESENT, VulkanicGalBridge.Struct.FRAME_PRESENT.byteSize());
			VulkanicGalBridge.PresentedFrame presented = bridge.presentFrame(frameId, correlationId, submissionId);
				if (wholeFrameVulkan) {
					auditMessage("gal.frame.present backend=vulkan correlation=" + correlationId
						+ " frame=" + presented.frameId()
						+ " image=" + presented.frameTargetIdentity()
						+ " submission=" + submissionId
						+ " status=" + presented.status());
					writeWholeFrameAttachmentCorrelation(frame, presented, wholeFrameResult, primitiveFrame);
				}
			METRICS.framePresentNanos += elapsedSince(presentStarted);
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.ffi.present");
			if (renderdocFrameCaptureStarted) {
				RenderDocCaptureHook.endFrameCaptureOnce(window, "rust-vulkan-whole-frame-world#" + frameId + "-submission=" + submissionId);
				renderdocFrameCaptureStarted = false;
			}

			METRICS.frames++;
			METRICS.submissions++;
			METRICS.batchesExecuted += requests.size();
			if (wholeFrameResult != null) {
				recordWholeFrameMetrics(wholeFrameResult);
			} else {
				recordGuiMetrics(guiResult);
			}
			retireOutstanding(false);
			auditMessage(metricsAuditLine(requests.size(), frameId, submissionId, wholeFrameResult != null));
			METRICS.executeNanos += elapsedSince(executeStarted);
			executeCounted = true;
			if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
				TracyCompat.message("Rust VulkanicGAL GUI frame executed"
					+ " batches=" + requests.size()
					+ " spriteBatches=" + (wholeFrameResult != null ? wholeFrameResult.spriteBatchCount() : guiResult.spriteBatchCount())
					+ " frame=" + frameId
					+ " submission=" + submissionId);
			}
		} finally {
			if (renderdocFrameCaptureStarted) {
				RenderDocCaptureHook.endFrameCaptureOnce(window, "rust-vulkan-whole-frame-world#aborted");
			}
			if (!executeCounted) {
				METRICS.executeNanos += elapsedSince(executeStarted);
			}
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.execute");
		}
	}

	private static void recordWholeFrameAcquire(VulkanicGalBridge.AcquiredFrame frame, long correlationId) {
		if (frame.frameTarget() != 0L) {
			METRICS.frameTargetGenerations++;
			if (METRICS.lastFrameTargetIdentity != 0L && METRICS.lastFrameTargetIdentity != frame.frameTargetIdentity()) {
				METRICS.frameTargetIdentityChanges++;
			}
			METRICS.lastFrameTargetGeneration = frame.frameId();
			METRICS.lastFrameTargetIdentity = frame.frameTargetIdentity();
		}
		auditMessage("gal.frame.acquire backend=vulkan correlation=" + correlationId
			+ " frame=" + frame.frameId()
			+ " image=" + frame.frameTargetIdentity()
			+ " target=0x" + Long.toUnsignedString(frame.frameTarget(), 16)
			+ " extent=" + frame.width() + "x" + frame.height());
	}

	private static void auditWholeFrameTarget(VulkanicGalBridge.AcquiredFrame frame, RustGalWorldPrimitiveRenderer.PrimitiveFrame primitiveFrame) {
		VulkanicGalBridge.WorldBackgroundRecord background = primitiveFrame == null
			? VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback()
			: primitiveFrame.background();
		String clearExpectation = background.enabled()
			? "clear=" + clearColorString(background.colorArgb())
				+ " expected=semantic-world-background sky_type=" + background.skyType()
				+ " color_argb=0x" + Integer.toUnsignedString(background.colorArgb(), 16)
			: "clear=0.063,0.157,0.855,1.000 expected=blue-diagnostic-shell";
		auditMessage("gal.frame.target.begin backend=vulkan frame=" + frame.frameId()
			+ " image=" + frame.frameTargetIdentity()
			+ " extent=" + frame.width() + "x" + frame.height()
			+ " " + (primitiveFrame == null
				? "material_marker_barrier_quads=0 material_marker_light_quads=0 material_marker_light_level_mask=0 material_marker_last_light_level=-1 material_marker_last_texture_id=0"
				: RustGalWorldPrimitiveRenderer.materialMarkerSummary(primitiveFrame.materialQuads()))
			+ " " + clearExpectation);
		auditMessage("gal.frame.target.present-ready backend=vulkan frame=" + frame.frameId()
			+ " image=" + frame.frameTargetIdentity());
	}

	private static void writeWholeFrameAttachmentCorrelation(
		VulkanicGalBridge.AcquiredFrame acquired,
		VulkanicGalBridge.PresentedFrame presented,
		VulkanicGalBridge.WholeFrameSubmitResult result,
		RustGalWorldPrimitiveRenderer.PrimitiveFrame primitiveFrame
	) {
		String dir = System.getenv("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR");
		if (dir == null || dir.isBlank() || wholeFrameAttachmentCorrelationWritten || result == null || primitiveFrame == null) {
			return;
		}
		if (result.worldMeshInstanceCount() <= 0L) {
			return;
		}
		long minFrame = parseLongEnv("MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_FRAME", 0L);
		if (acquired.frameId() < minFrame) {
			return;
		}
		wholeFrameAttachmentCorrelationWritten = true;
		Path root = Path.of(dir);
		String json = "{\n"
			+ "  \"artifact_class\":\"rust_vulkan_whole_frame_gameplay_correlation\",\n"
			+ "  \"source\":\"java-frame-coordinator-after-present\",\n"
			+ "  \"gameplay_frame_id\":" + acquired.frameId() + ",\n"
			+ "  \"correlation_id\":" + acquired.correlationId() + ",\n"
			+ "  \"gal_submission_id\":" + result.submissionId() + ",\n"
			+ "  \"vulkan_submission_timeline_value\":" + result.submissionId() + ",\n"
			+ "  \"acquired_swapchain_image\":" + acquired.frameTargetIdentity() + ",\n"
			+ "  \"presented_swapchain_image\":" + presented.frameTargetIdentity() + ",\n"
			+ "  \"present_completed_submission_id\":" + presented.completedSubmissionId() + ",\n"
			+ "  \"extent\":{\"width\":" + acquired.width() + ",\"height\":" + acquired.height() + "},\n"
			+ "  \"same_acquired_presented_image\":" + (acquired.frameTargetIdentity() == presented.frameTargetIdentity()) + ",\n"
			+ "  \"producer_workload_fingerprint\":\"" + escapeJson(primitiveFrameFingerprint(primitiveFrame)) + "\",\n"
			+ "  \"gui_sprites\":" + result.spriteCount() + ",\n"
			+ "  \"world_mesh_instances\":" + result.worldMeshInstanceCount() + ",\n"
			+ "  \"world_mesh_batches\":" + result.worldMeshBatchCount() + ",\n"
			+ "  \"world_mesh_draws\":" + result.worldMeshDrawCount() + ",\n"
			+ "  \"world_material_quads\":" + result.worldMaterialQuadCount() + ",\n"
			+ "  \"world_crack_quads\":" + result.worldCrackQuadCount() + ",\n"
			+ "  \"world_border_quads\":" + result.worldBorderQuadCount() + ",\n"
			+ "  \"java_vulkan_frame_execution\":false,\n"
			+ "  \"rust_whole_frame_presenter\":true\n"
			+ "}\n";
		try {
			Files.createDirectories(root);
			Files.writeString(root.resolve("gameplay-correlation-frame-" + acquired.frameId() + ".json"), json, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			LOGGER.warn("Unable to write Rust whole-frame gameplay attachment correlation sidecar", exception);
		}
	}

	private static long parseLongEnv(String name, long fallback) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String primitiveFrameFingerprint(RustGalWorldPrimitiveRenderer.PrimitiveFrame frame) {
		return "segments=" + frame.segments().size()
			+ " crack_quads=" + frame.crackQuads().size()
			+ " border_quads=" + frame.borderQuads().size()
			+ " material_quads=" + frame.materialQuads().size()
			+ " mesh_instances=" + frame.meshInstances().size()
			+ " background_enabled=" + frame.background().enabled();
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r");
	}

	private static void recordGuiMetrics(VulkanicGalBridge.GuiFrameSubmitResult result) {
		METRICS.spriteBatchesExecuted += result.spriteBatchCount();
		METRICS.packedSpritesExecuted += result.spriteCount();
		METRICS.cacheHits += result.cacheHits();
		METRICS.cacheMisses += result.cacheMisses();
		METRICS.resourceCreates += result.resourceCreates();
	}

	private static void recordWholeFrameMetrics(VulkanicGalBridge.WholeFrameSubmitResult result) {
		METRICS.spriteBatchesExecuted += result.spriteBatchCount();
		METRICS.packedSpritesExecuted += result.spriteCount();
		recordWorldMetrics(result);
		recordWholeFrameProfile(result.profile());
		METRICS.worldBackgroundClearsExecuted += result.worldBackgroundClearCount();
		METRICS.worldBackgroundDiagnosticFallbacks += result.worldBackgroundDiagnosticFallbackCount();
		if (result.worldBackgroundSkyType() != 0L) {
			METRICS.lastWorldBackgroundSkyType = result.worldBackgroundSkyType();
			METRICS.lastWorldBackgroundColorArgb = result.worldBackgroundColorArgb();
		}
	}

	private static void recordWholeFrameProfile(VulkanicGalBridge.WholeFrameProfile profile) {
		recordWholeFrameProfilePhaseSamples(profile);
		METRICS.profileFfiDecodeNanos += profile.ffiDecodeNanos();
		METRICS.profileGuiFrontendNanos += profile.guiFrontendNanos();
		METRICS.profileWorldFrontendNanos += profile.worldFrontendTotalNanos();
		METRICS.profileWorldValidateFrameNanos += profile.worldValidateFrameNanos();
		METRICS.profileWorldBatchingNanos += profile.worldBatchingNanos();
		METRICS.profileWorldResourcePrepareNanos += profile.worldResourcePrepareNanos();
		METRICS.profileWorldMeshSectionExpandGroupNanos += profile.worldMeshSectionExpandGroupNanos();
		METRICS.profileShaderPlanLookupNanos += profile.shaderPlanLookupNanos();
		METRICS.profileGalCommandGenerationNanos += profile.galCommandGenerationNanos();
		METRICS.profileGalSubmitTotalNanos += profile.galSubmitTotalNanos();
		METRICS.profileGalValidateOpsNanos += profile.galValidateOpsNanos();
		METRICS.profileGalValidateHandlesNanos += profile.galValidateHandlesNanos();
		METRICS.profileGalHazardAnalysisNanos += profile.galHazardAnalysisNanos();
		METRICS.profileBackendEncodeNanos += profile.backendEncodeNanos();
		METRICS.profileBackendSubmitNanos += profile.backendSubmitNanos();
		METRICS.profileBackendRetireNanos += profile.backendRetireNanos();
		METRICS.profileVulkanCommandBufferAllocNanos += profile.vulkanCommandBufferAllocNanos();
		METRICS.profileVulkanCommandBufferBeginNanos += profile.vulkanCommandBufferBeginNanos();
		METRICS.profileVulkanCommandRecordingNanos += profile.vulkanCommandRecordingNanos();
		METRICS.profileVulkanCommandBufferEndNanos += profile.vulkanCommandBufferEndNanos();
		METRICS.profileVulkanQueueSubmitNanos += profile.vulkanQueueSubmitNanos();
		METRICS.profileVulkanTimelinePollNanos += profile.vulkanTimelinePollNanos();
		METRICS.profileVulkanTimelineWaitNanos += profile.vulkanTimelineWaitNanos();
		METRICS.profileVulkanDeviceWaitIdleNanos += profile.vulkanDeviceWaitIdleNanos();
		METRICS.profileVulkanCommandBuffersAllocated += profile.vulkanCommandBuffersAllocated();
		METRICS.profileVulkanCommandBuffersFreed += profile.vulkanCommandBuffersFreed();
		METRICS.profileVulkanWaitCount += profile.vulkanWaitCount();
		METRICS.profileVulkanDeviceWaitIdleCount += profile.vulkanDeviceWaitIdleCount();
		METRICS.profileResourceCreatesDelta += profile.resourceCreatesDelta();
		METRICS.profileResourceDestroysDelta += profile.resourceDestroysDelta();
		METRICS.profileHostWriteOps += profile.hostWriteOps();
		METRICS.profileHostWriteBytes += profile.hostWriteBytes();
		METRICS.profileBarrierOps += profile.barrierOps();
		METRICS.profilePassCount += profile.passCount();
		METRICS.profileDrawOps += profile.drawOps();
		METRICS.profileDrawIndexedOps += profile.drawIndexedOps();
		METRICS.profilePipelineBinds += profile.pipelineBinds();
		METRICS.profileResourceSetBinds += profile.resourceSetBinds();
		METRICS.profileGpuTimestampUnavailableFrames += profile.gpuTimestampStatus() == 0L ? 1L : 0L;
		METRICS.profileGBufferPersistentCacheHits += profile.gBufferPersistentCacheHits();
		METRICS.profileGBufferPersistentCacheMisses += profile.gBufferPersistentCacheMisses();
		METRICS.profileGBufferFinalBindingCacheHits += profile.gBufferFinalBindingCacheHits();
		METRICS.profileGBufferFinalBindingCacheMisses += profile.gBufferFinalBindingCacheMisses();
		METRICS.profileGBufferAttachmentCreates += profile.gBufferAttachmentCreates();
		METRICS.profileGBufferPipelineCreates += profile.gBufferPipelineCreates();
		METRICS.profileGBufferShaderModuleCreates += profile.gBufferShaderModuleCreates();
		METRICS.profileGBufferDescriptorCreates += profile.gBufferDescriptorCreates();
		METRICS.profileGBufferRenderTargetCreates += profile.gBufferRenderTargetCreates();
		METRICS.profileGBufferResourcesRetired += profile.gBufferResourcesRetired();
	}

	private static void recordWholeFrameProfilePhaseSamples(VulkanicGalBridge.WholeFrameProfile profile) {
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.ffi-decode", profile.ffiDecodeNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gui-frontend", profile.guiFrontendNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-frontend", profile.worldFrontendTotalNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-validate-frame", profile.worldValidateFrameNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-batching", profile.worldBatchingNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-resource-prepare", profile.worldResourcePrepareNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-target-query", profile.worldPrepareTargetQueryNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-render-resources", profile.worldPrepareRenderResourcesNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-depth-attachment", profile.worldPrepareDepthAttachmentNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-g-buffer-resources", profile.worldPrepareGBufferResourcesNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-g-buffer-cache-check", profile.worldPrepareGBufferCacheCheckNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-g-buffer-destroy", profile.worldPrepareGBufferDestroyNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-g-buffer-plan", profile.worldPrepareGBufferPlanNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-g-buffer-create", profile.worldPrepareGBufferCreateNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-persistent-cache-hits", profile.gBufferPersistentCacheHits());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-persistent-cache-misses", profile.gBufferPersistentCacheMisses());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-final-binding-cache-hits", profile.gBufferFinalBindingCacheHits());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-final-binding-cache-misses", profile.gBufferFinalBindingCacheMisses());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-attachment-creates", profile.gBufferAttachmentCreates());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-pipeline-creates", profile.gBufferPipelineCreates());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-shader-module-creates", profile.gBufferShaderModuleCreates());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-descriptor-creates", profile.gBufferDescriptorCreates());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-render-target-creates", profile.gBufferRenderTargetCreates());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.g-buffer-resources-retired", profile.gBufferResourcesRetired());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-prepare-frame-pass", profile.worldPrepareFramePassNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.world-mesh-expand-group", profile.worldMeshSectionExpandGroupNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.shader-plan-lookup", profile.shaderPlanLookupNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gal-command-generation", profile.galCommandGenerationNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gal-submit-total", profile.galSubmitTotalNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gal-validate-ops", profile.galValidateOpsNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gal-validate-handles", profile.galValidateHandlesNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.gal-hazard-analysis", profile.galHazardAnalysisNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.backend-encode", profile.backendEncodeNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.backend-submit", profile.backendSubmitNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.backend-retire", profile.backendRetireNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-command-buffer-alloc", profile.vulkanCommandBufferAllocNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-command-buffer-begin", profile.vulkanCommandBufferBeginNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-command-recording", profile.vulkanCommandRecordingNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-command-buffer-end", profile.vulkanCommandBufferEndNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-queue-submit", profile.vulkanQueueSubmitNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-timeline-poll", profile.vulkanTimelinePollNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-timeline-wait", profile.vulkanTimelineWaitNanos());
		GraphicsFrameBenchmark.recordPhaseSample("rust-gal.native-profile.vulkan-device-wait-idle", profile.vulkanDeviceWaitIdleNanos());
	}

	private static void recordWorldMetrics(VulkanicGalBridge.WholeFrameSubmitResult result) {
		METRICS.worldPrimitiveBatchesExecuted += result.worldBatchCount();
		METRICS.worldLineSegmentsExecuted += result.worldSegmentCount();
		METRICS.worldLineVerticesExecuted += result.worldVertexCount();
		METRICS.worldPrimitiveDrawsExecuted += result.worldDrawCount();
		METRICS.worldCrackQuadsExecuted += result.worldCrackQuadCount();
		METRICS.worldCrackBatchesExecuted += result.worldCrackBatchCount();
		METRICS.worldCrackDrawsExecuted += result.worldCrackDrawCount();
		METRICS.worldBorderQuadsExecuted += result.worldBorderQuadCount();
		METRICS.worldBorderBatchesExecuted += result.worldBorderBatchCount();
		METRICS.worldBorderDrawsExecuted += result.worldBorderDrawCount();
		METRICS.worldMaterialQuadsExecuted += result.worldMaterialQuadCount();
		METRICS.worldMaterialBatchesExecuted += result.worldMaterialBatchCount();
		METRICS.worldMaterialDrawsExecuted += result.worldMaterialDrawCount();
		METRICS.worldMeshInstancesExecuted += result.worldMeshInstanceCount();
		METRICS.worldMeshBatchesExecuted += result.worldMeshBatchCount();
		METRICS.worldMeshDrawsExecuted += result.worldMeshDrawCount();
		METRICS.worldDepthAttachmentCreates += result.depthAttachmentCreates();
		METRICS.worldDepthAttachmentReuses += result.depthAttachmentReuses();
		METRICS.worldDepthAttachmentRetires += result.depthAttachmentRetires();
		METRICS.worldOutlineCacheHits += result.outlineCacheHits();
		METRICS.worldOutlineCacheMisses += result.outlineCacheMisses();
		METRICS.worldCrackCacheHits += result.crackCacheHits();
		METRICS.worldCrackCacheMisses += result.crackCacheMisses();
		METRICS.worldBorderCacheHits += result.borderCacheHits();
		METRICS.worldBorderCacheMisses += result.borderCacheMisses();
		METRICS.worldMaterialCacheHits += result.materialCacheHits();
		METRICS.worldMaterialCacheMisses += result.materialCacheMisses();
		METRICS.worldMeshCacheHits += result.meshCacheHits();
		METRICS.worldMeshCacheMisses += result.meshCacheMisses();
		METRICS.cacheHits += result.cacheHits();
		METRICS.cacheMisses += result.cacheMisses();
		METRICS.resourceCreates += result.resourceCreates();
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

	private static void ensureRenderThreadAndContext(Minecraft minecraft) {
		Thread current = Thread.currentThread();
		if (renderThread == null) {
			renderThread = current;
		} else if (renderThread != current) {
			throw new IllegalStateException("Rust VulkanicGAL deferred frame queue used from the wrong render thread");
		}
		Window window = minecraft.getWindow();
		if (!VulkanicGalBridge.isBorrowedOpenGlContextCurrent(window)) {
			throw new IllegalStateException("Rust VulkanicGAL deferred OpenGL execution requires Minecraft's current GL context");
		}
		if (bridge == null) {
			bridge = VulkanicGalBridge.createBorrowedOpenGl(window);
			recordFixedOperation(Operation.CONTEXT_CREATE, VulkanicGalBridge.Struct.BORROWED_OPENGL_CONTEXT_CREATE.byteSize());
			recordFixedOperation(Operation.CAPABILITY_QUERY, VulkanicGalBridge.Struct.CAPABILITY_QUERY.byteSize());
			flushPendingGuiAssetsLocked();
			flushPendingWorldAssetsLocked();
			configuredWidth = 0;
			configuredHeight = 0;
		}
	}

	private static void ensureRenderThreadAndWindowedVulkanContext(Minecraft minecraft) {
		Thread current = Thread.currentThread();
		if (renderThread == null) {
			renderThread = current;
		} else if (renderThread != current) {
			throw new IllegalStateException("Rust VulkanicGAL whole-frame queue used from the wrong render thread");
		}
		Window window = minecraft.getWindow();
		if (bridge == null) {
			bridge = VulkanicGalBridge.createWindowedVulkan(
				window,
				Math.max(1, window.getWidth()),
				Math.max(1, window.getHeight())
			);
			recordFixedOperation(Operation.CONTEXT_CREATE, VulkanicGalBridge.Struct.WINDOWED_VULKAN_CONTEXT_CREATE.byteSize());
			recordFixedOperation(Operation.CAPABILITY_QUERY, VulkanicGalBridge.Struct.CAPABILITY_QUERY.byteSize());
			flushPendingGuiAssetsLocked();
			flushPendingWorldAssetsLocked();
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
			String label = RustGalGuiRenderer.isWholeFrameVulkanEnabled() && VulkanicAPI.isVulkanBackendSelected()
				? "minecraft.rust-vulkan.swapchain"
				: "minecraft.borrowed.opengl.default";
			recordStatus(Operation.FRAME_CONFIGURE, bridge.configureFrame(label, width, height, VulkanicGalBridge.FORMAT_RGBA8));
		} else {
			recordFixedOperation(Operation.FRAME_RESIZE, VulkanicGalBridge.Struct.FRAME_RESIZE.byteSize());
			bridge.resizeFrame(nextCorrelationId++, width, height);
		}
		configuredWidth = width;
		configuredHeight = height;
	}

	private static void flushPendingGuiAssetsLocked() {
		if (bridge == null || uploadedAssetGeneration >= assetGeneration || attemptedAssetGeneration >= assetGeneration) {
			return;
		}
		attemptedAssetGeneration = assetGeneration;
		try {
			recordStatus(Operation.GUI_ASSET_UPDATE, bridge.updateGuiAssets(assetGeneration, pendingAssets));
			lastAssetPayloadCount = pendingAssets.size();
			lastAssetPayloadBytes = pendingAssets.stream().mapToLong(asset -> asset.pngBytes().length).sum();
			uploadedAssetGeneration = assetGeneration;
			auditMessage(
				"Rust VulkanicGAL GUI asset update accepted"
					+ " generation=" + assetGeneration
					+ " payloads=" + lastAssetPayloadCount
					+ " payload_bytes=" + lastAssetPayloadBytes
					+ " uploaded_generation=" + uploadedAssetGeneration
			);
		} catch (RuntimeException error) {
			assetUpdateFailures++;
			LOGGER.error(
				"Rust VulkanicGAL GUI asset update failed for generation {}; preserving last valid atlas",
				assetGeneration,
				error
			);
			auditMessage(
				"Rust VulkanicGAL GUI asset update failed"
					+ " generation=" + assetGeneration
					+ " uploaded_generation=" + uploadedAssetGeneration
					+ " failures=" + assetUpdateFailures
					+ " preserve_last_valid=true"
			);
		}
	}

	private static void flushPendingWorldAssetsLocked() {
		VulkanicGalBridge.Status status = RustGalWorldPrimitiveRenderer.flushPendingWorldBorderAssets(bridge);
		if (status != null) {
			recordStatus(Operation.WORLD_BORDER_ASSET_UPDATE, status);
		}
		status = RustGalWorldPrimitiveRenderer.flushPendingWorldCrackAssets(bridge);
		if (status != null) {
			recordStatus(Operation.WORLD_CRACK_ASSET_UPDATE, status);
		}
		status = RustGalWorldPrimitiveRenderer.flushPendingWorldMaterialAssets(bridge);
		if (status != null) {
			recordStatus(Operation.WORLD_MATERIAL_ASSET_UPDATE, status);
		}
		status = RustGalWorldPrimitiveRenderer.flushPendingWorldMeshAssets(bridge);
		if (status != null) {
			recordStatus(Operation.WORLD_MESH_ASSET_UPDATE, status);
		}
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
			case GUI_ASSET_UPDATE -> {
				METRICS.guiAssetUpdateCalls += calls;
				METRICS.guiAssetUpdateBytes += bytes;
			}
			case WORLD_BORDER_ASSET_UPDATE -> {
				METRICS.worldBorderAssetUpdateCalls += calls;
				METRICS.worldBorderAssetUpdateBytes += bytes;
			}
			case WORLD_CRACK_ASSET_UPDATE -> {
				METRICS.worldCrackAssetUpdateCalls += calls;
				METRICS.worldCrackAssetUpdateBytes += bytes;
			}
			case WORLD_MATERIAL_ASSET_UPDATE -> {
				METRICS.worldMaterialAssetUpdateCalls += calls;
				METRICS.worldMaterialAssetUpdateBytes += bytes;
			}
			case WORLD_MESH_ASSET_UPDATE -> {
				METRICS.worldMeshAssetUpdateCalls += calls;
				METRICS.worldMeshAssetUpdateBytes += bytes;
			}
		}
	}

	private static String metricsAuditLine(long frameBatchCount, long frameId, long submissionId, boolean wholeFrameVulkan) {
		RustGalWorldPrimitiveRenderer.WorldBorderAssetMetrics worldBorderAssetMetrics =
			RustGalWorldPrimitiveRenderer.worldBorderAssetMetrics();
		RustGalWorldPrimitiveRenderer.WorldCrackAssetMetrics worldCrackAssetMetrics =
			RustGalWorldPrimitiveRenderer.worldCrackAssetMetrics();
		RustGalWorldPrimitiveRenderer.WorldMaterialAssetMetrics worldMaterialAssetMetrics =
			RustGalWorldPrimitiveRenderer.worldMaterialAssetMetrics();
		return auditBackendPrefix(wholeFrameVulkan) + " GUI frame executed producer=gui.frame"
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
			+ " rust_gal_world_primitive_batches_executed=" + METRICS.worldPrimitiveBatchesExecuted
			+ " rust_gal_world_line_segments_executed=" + METRICS.worldLineSegmentsExecuted
			+ " rust_gal_world_line_vertices_executed=" + METRICS.worldLineVerticesExecuted
			+ " rust_gal_world_primitive_draws_executed=" + METRICS.worldPrimitiveDrawsExecuted
			+ " rust_gal_world_crack_quads_executed=" + METRICS.worldCrackQuadsExecuted
			+ " rust_gal_world_crack_batches_executed=" + METRICS.worldCrackBatchesExecuted
			+ " rust_gal_world_crack_draws_executed=" + METRICS.worldCrackDrawsExecuted
			+ " rust_gal_world_border_quads_executed=" + METRICS.worldBorderQuadsExecuted
			+ " rust_gal_world_border_batches_executed=" + METRICS.worldBorderBatchesExecuted
			+ " rust_gal_world_border_draws_executed=" + METRICS.worldBorderDrawsExecuted
			+ " rust_gal_world_material_quads_executed=" + METRICS.worldMaterialQuadsExecuted
			+ " rust_gal_world_material_batches_executed=" + METRICS.worldMaterialBatchesExecuted
			+ " rust_gal_world_material_draws_executed=" + METRICS.worldMaterialDrawsExecuted
			+ " rust_gal_world_mesh_instances_executed=" + METRICS.worldMeshInstancesExecuted
			+ " rust_gal_world_mesh_batches_executed=" + METRICS.worldMeshBatchesExecuted
			+ " rust_gal_world_mesh_draws_executed=" + METRICS.worldMeshDrawsExecuted
			+ " rust_gal_world_background_clears_executed=" + METRICS.worldBackgroundClearsExecuted
			+ " rust_gal_world_background_diagnostic_fallbacks=" + METRICS.worldBackgroundDiagnosticFallbacks
			+ " rust_gal_world_background_sky_type=" + METRICS.lastWorldBackgroundSkyType
			+ " rust_gal_world_background_color_argb=" + Long.toUnsignedString(METRICS.lastWorldBackgroundColorArgb, 16)
			+ " rust_gal_world_depth_attachment_creates=" + METRICS.worldDepthAttachmentCreates
			+ " rust_gal_world_depth_attachment_reuses=" + METRICS.worldDepthAttachmentReuses
			+ " rust_gal_world_depth_attachment_retires=" + METRICS.worldDepthAttachmentRetires
			+ " rust_gal_world_outline_cache_hits=" + METRICS.worldOutlineCacheHits
			+ " rust_gal_world_outline_cache_misses=" + METRICS.worldOutlineCacheMisses
			+ " rust_gal_world_crack_cache_hits=" + METRICS.worldCrackCacheHits
			+ " rust_gal_world_crack_cache_misses=" + METRICS.worldCrackCacheMisses
			+ " rust_gal_world_crack_asset_generation=" + worldCrackAssetMetrics.generation()
			+ " rust_gal_world_crack_uploaded_asset_generation=" + worldCrackAssetMetrics.uploadedGeneration()
			+ " rust_gal_world_crack_asset_payload_count=" + worldCrackAssetMetrics.payloadCount()
			+ " rust_gal_world_crack_asset_payload_bytes=" + worldCrackAssetMetrics.payloadBytes()
			+ " rust_gal_world_crack_asset_update_failures=" + worldCrackAssetMetrics.failures()
			+ " rust_gal_world_crack_asset_source_pack=" + metricValue(worldCrackAssetMetrics.sourcePack())
			+ " rust_gal_world_crack_asset_sha256=" + metricValue(worldCrackAssetMetrics.sha256())
			+ " rust_gal_world_crack_asset_fallback=" + worldCrackAssetMetrics.fallback()
			+ " rust_gal_world_border_cache_hits=" + METRICS.worldBorderCacheHits
			+ " rust_gal_world_border_cache_misses=" + METRICS.worldBorderCacheMisses
			+ " rust_gal_world_material_cache_hits=" + METRICS.worldMaterialCacheHits
			+ " rust_gal_world_material_cache_misses=" + METRICS.worldMaterialCacheMisses
			+ " rust_gal_world_mesh_cache_hits=" + METRICS.worldMeshCacheHits
			+ " rust_gal_world_mesh_cache_misses=" + METRICS.worldMeshCacheMisses
			+ " rust_gal_world_border_asset_generation=" + worldBorderAssetMetrics.generation()
			+ " rust_gal_world_border_uploaded_asset_generation=" + worldBorderAssetMetrics.uploadedGeneration()
			+ " rust_gal_world_border_asset_payload_count=" + worldBorderAssetMetrics.payloadCount()
			+ " rust_gal_world_border_asset_payload_bytes=" + worldBorderAssetMetrics.payloadBytes()
			+ " rust_gal_world_border_asset_update_failures=" + worldBorderAssetMetrics.failures()
			+ " rust_gal_world_border_asset_source_pack=" + metricValue(worldBorderAssetMetrics.sourcePack())
			+ " rust_gal_world_border_asset_sha256=" + metricValue(worldBorderAssetMetrics.sha256())
			+ " rust_gal_world_border_asset_fallback=" + worldBorderAssetMetrics.fallback()
			+ " rust_gal_world_material_asset_generation=" + worldMaterialAssetMetrics.generation()
			+ " rust_gal_world_material_uploaded_asset_generation=" + worldMaterialAssetMetrics.uploadedGeneration()
			+ " rust_gal_world_material_asset_payload_count=" + worldMaterialAssetMetrics.payloadCount()
			+ " rust_gal_world_material_asset_payload_bytes=" + worldMaterialAssetMetrics.payloadBytes()
			+ " rust_gal_world_material_asset_update_failures=" + worldMaterialAssetMetrics.failures()
			+ " rust_gal_world_material_asset_source_pack=" + metricValue(worldMaterialAssetMetrics.sourcePack())
			+ " rust_gal_world_material_asset_sha256=" + metricValue(worldMaterialAssetMetrics.sha256())
			+ " rust_gal_world_material_asset_fallback=" + worldMaterialAssetMetrics.fallback()
			+ " rust_gal_frame_target_generations=" + METRICS.frameTargetGenerations
			+ " rust_gal_frame_target_identity_changes=" + METRICS.frameTargetIdentityChanges
			+ " rust_gal_last_frame_target_generation=" + METRICS.lastFrameTargetGeneration
			+ " rust_gal_last_frame_target_identity=" + METRICS.lastFrameTargetIdentity
			+ " rust_gal_batches_cancelled=" + METRICS.batchesCancelled
			+ " rust_gal_completion_polls=" + METRICS.completionPolls
			+ " rust_gal_completion_timeouts=" + METRICS.completionTimeouts
			+ " rust_gal_asset_generation=" + assetGeneration
			+ " rust_gal_uploaded_asset_generation=" + uploadedAssetGeneration
			+ " rust_gal_asset_payload_count=" + lastAssetPayloadCount
			+ " rust_gal_asset_payload_bytes=" + lastAssetPayloadBytes
			+ " rust_gal_asset_update_failures=" + assetUpdateFailures
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
			+ " rust_gal_ffi_asset_update_calls=" + METRICS.guiAssetUpdateCalls
			+ " rust_gal_ffi_world_border_asset_update_calls=" + METRICS.worldBorderAssetUpdateCalls
			+ " rust_gal_ffi_world_crack_asset_update_calls=" + METRICS.worldCrackAssetUpdateCalls
			+ " rust_gal_ffi_world_material_asset_update_calls=" + METRICS.worldMaterialAssetUpdateCalls
			+ " rust_gal_ffi_world_mesh_asset_update_calls=" + METRICS.worldMeshAssetUpdateCalls
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
			+ " rust_gal_ffi_asset_update_bytes=" + METRICS.guiAssetUpdateBytes
			+ " rust_gal_ffi_world_border_asset_update_bytes=" + METRICS.worldBorderAssetUpdateBytes
			+ " rust_gal_ffi_world_crack_asset_update_bytes=" + METRICS.worldCrackAssetUpdateBytes
			+ " rust_gal_ffi_world_material_asset_update_bytes=" + METRICS.worldMaterialAssetUpdateBytes
			+ " rust_gal_ffi_world_mesh_asset_update_bytes=" + METRICS.worldMeshAssetUpdateBytes
			+ " rust_gal_enqueue_nanos=" + METRICS.enqueueNanos
			+ " rust_gal_resource_lookup_nanos=" + METRICS.resourceLookupNanos
			+ " rust_gal_resource_create_nanos=" + METRICS.resourceCreateNanos
			+ " rust_gal_abi_packing_nanos=" + METRICS.abiPackingNanos
			+ " rust_gal_profile_ffi_decode_nanos=" + METRICS.profileFfiDecodeNanos
			+ " rust_gal_profile_gui_frontend_nanos=" + METRICS.profileGuiFrontendNanos
			+ " rust_gal_profile_world_frontend_nanos=" + METRICS.profileWorldFrontendNanos
			+ " rust_gal_profile_world_validate_frame_nanos=" + METRICS.profileWorldValidateFrameNanos
			+ " rust_gal_profile_world_batching_nanos=" + METRICS.profileWorldBatchingNanos
			+ " rust_gal_profile_world_resource_prepare_nanos=" + METRICS.profileWorldResourcePrepareNanos
			+ " rust_gal_profile_world_mesh_expand_group_nanos=" + METRICS.profileWorldMeshSectionExpandGroupNanos
			+ " rust_gal_profile_shader_plan_lookup_nanos=" + METRICS.profileShaderPlanLookupNanos
			+ " rust_gal_profile_gal_command_generation_nanos=" + METRICS.profileGalCommandGenerationNanos
			+ " rust_gal_profile_gal_submit_total_nanos=" + METRICS.profileGalSubmitTotalNanos
			+ " rust_gal_profile_gal_validate_ops_nanos=" + METRICS.profileGalValidateOpsNanos
			+ " rust_gal_profile_gal_validate_handles_nanos=" + METRICS.profileGalValidateHandlesNanos
			+ " rust_gal_profile_gal_hazard_analysis_nanos=" + METRICS.profileGalHazardAnalysisNanos
			+ " rust_gal_profile_backend_encode_nanos=" + METRICS.profileBackendEncodeNanos
			+ " rust_gal_profile_backend_submit_nanos=" + METRICS.profileBackendSubmitNanos
			+ " rust_gal_profile_backend_retire_nanos=" + METRICS.profileBackendRetireNanos
			+ " rust_gal_profile_vulkan_command_buffer_alloc_nanos=" + METRICS.profileVulkanCommandBufferAllocNanos
			+ " rust_gal_profile_vulkan_command_buffer_begin_nanos=" + METRICS.profileVulkanCommandBufferBeginNanos
			+ " rust_gal_profile_vulkan_command_recording_nanos=" + METRICS.profileVulkanCommandRecordingNanos
			+ " rust_gal_profile_vulkan_command_buffer_end_nanos=" + METRICS.profileVulkanCommandBufferEndNanos
			+ " rust_gal_profile_vulkan_queue_submit_nanos=" + METRICS.profileVulkanQueueSubmitNanos
			+ " rust_gal_profile_vulkan_timeline_poll_nanos=" + METRICS.profileVulkanTimelinePollNanos
			+ " rust_gal_profile_vulkan_timeline_wait_nanos=" + METRICS.profileVulkanTimelineWaitNanos
			+ " rust_gal_profile_vulkan_device_wait_idle_nanos=" + METRICS.profileVulkanDeviceWaitIdleNanos
			+ " rust_gal_profile_vulkan_command_buffers_allocated=" + METRICS.profileVulkanCommandBuffersAllocated
			+ " rust_gal_profile_vulkan_command_buffers_freed=" + METRICS.profileVulkanCommandBuffersFreed
			+ " rust_gal_profile_vulkan_wait_count=" + METRICS.profileVulkanWaitCount
			+ " rust_gal_profile_vulkan_device_wait_idle_count=" + METRICS.profileVulkanDeviceWaitIdleCount
			+ " rust_gal_profile_resource_creates_delta=" + METRICS.profileResourceCreatesDelta
			+ " rust_gal_profile_resource_destroys_delta=" + METRICS.profileResourceDestroysDelta
			+ " rust_gal_profile_host_write_ops=" + METRICS.profileHostWriteOps
			+ " rust_gal_profile_host_write_bytes=" + METRICS.profileHostWriteBytes
			+ " rust_gal_profile_barrier_ops=" + METRICS.profileBarrierOps
			+ " rust_gal_profile_pass_count=" + METRICS.profilePassCount
			+ " rust_gal_profile_draw_ops=" + METRICS.profileDrawOps
			+ " rust_gal_profile_draw_indexed_ops=" + METRICS.profileDrawIndexedOps
			+ " rust_gal_profile_pipeline_binds=" + METRICS.profilePipelineBinds
				+ " rust_gal_profile_resource_set_binds=" + METRICS.profileResourceSetBinds
				+ " rust_gal_profile_gpu_timestamp_unavailable_frames=" + METRICS.profileGpuTimestampUnavailableFrames
				+ " rust_gal_profile_g_buffer_persistent_cache_hits=" + METRICS.profileGBufferPersistentCacheHits
				+ " rust_gal_profile_g_buffer_persistent_cache_misses=" + METRICS.profileGBufferPersistentCacheMisses
				+ " rust_gal_profile_g_buffer_final_binding_cache_hits=" + METRICS.profileGBufferFinalBindingCacheHits
				+ " rust_gal_profile_g_buffer_final_binding_cache_misses=" + METRICS.profileGBufferFinalBindingCacheMisses
				+ " rust_gal_profile_g_buffer_attachment_creates=" + METRICS.profileGBufferAttachmentCreates
				+ " rust_gal_profile_g_buffer_pipeline_creates=" + METRICS.profileGBufferPipelineCreates
				+ " rust_gal_profile_g_buffer_shader_module_creates=" + METRICS.profileGBufferShaderModuleCreates
				+ " rust_gal_profile_g_buffer_descriptor_creates=" + METRICS.profileGBufferDescriptorCreates
				+ " rust_gal_profile_g_buffer_render_target_creates=" + METRICS.profileGBufferRenderTargetCreates
				+ " rust_gal_profile_g_buffer_resources_retired=" + METRICS.profileGBufferResourcesRetired
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

	private static String auditBackendPrefix(boolean wholeFrameVulkan) {
		return wholeFrameVulkan
			? "Rust VulkanicGAL"
			: "Rust OpenGL VulkanicGAL";
	}

	private static String metricValue(String value) {
		return value == null || value.isBlank() ? "unset" : value.replaceAll("\\s+", "_");
	}

	private static String clearColorString(int colorArgb) {
		float alpha = ((colorArgb >>> 24) & 0xFF) / 255.0F;
		float red = ((colorArgb >>> 16) & 0xFF) / 255.0F;
		float green = ((colorArgb >>> 8) & 0xFF) / 255.0F;
		float blue = (colorArgb & 0xFF) / 255.0F;
		return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f,%.3f", red, green, blue, alpha);
	}

	private static long elapsedSince(long started) {
		return Math.max(0L, System.nanoTime() - started);
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
		RETIRE,
		GUI_ASSET_UPDATE,
		WORLD_BORDER_ASSET_UPDATE,
		WORLD_CRACK_ASSET_UPDATE,
		WORLD_MATERIAL_ASSET_UPDATE,
		WORLD_MESH_ASSET_UPDATE
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
		long worldPrimitiveBatchesExecuted;
		long worldLineSegmentsExecuted;
		long worldLineVerticesExecuted;
		long worldPrimitiveDrawsExecuted;
		long worldCrackQuadsExecuted;
		long worldCrackBatchesExecuted;
		long worldCrackDrawsExecuted;
		long worldBorderQuadsExecuted;
		long worldBorderBatchesExecuted;
		long worldBorderDrawsExecuted;
		long worldMaterialQuadsExecuted;
		long worldMaterialBatchesExecuted;
		long worldMaterialDrawsExecuted;
		long worldMeshInstancesExecuted;
		long worldMeshBatchesExecuted;
		long worldMeshDrawsExecuted;
		long worldBackgroundClearsExecuted;
		long worldBackgroundDiagnosticFallbacks;
		long lastWorldBackgroundSkyType;
		long lastWorldBackgroundColorArgb;
		long worldDepthAttachmentCreates;
		long worldDepthAttachmentReuses;
		long worldDepthAttachmentRetires;
		long worldOutlineCacheHits;
		long worldOutlineCacheMisses;
		long worldCrackCacheHits;
		long worldCrackCacheMisses;
		long worldBorderCacheHits;
		long worldBorderCacheMisses;
		long worldMaterialCacheHits;
		long worldMaterialCacheMisses;
		long worldMeshCacheHits;
		long worldMeshCacheMisses;
		long frameTargetGenerations;
		long frameTargetIdentityChanges;
		long lastFrameTargetGeneration;
		long lastFrameTargetIdentity;
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
		long guiAssetUpdateCalls;
		long worldBorderAssetUpdateCalls;
		long worldCrackAssetUpdateCalls;
		long worldMaterialAssetUpdateCalls;
		long worldMeshAssetUpdateCalls;
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
		long guiAssetUpdateBytes;
		long worldBorderAssetUpdateBytes;
		long worldCrackAssetUpdateBytes;
		long worldMaterialAssetUpdateBytes;
		long worldMeshAssetUpdateBytes;
		long enqueueNanos;
		long resourceLookupNanos;
		long resourceCreateNanos;
		long abiPackingNanos;
		long profileFfiDecodeNanos;
		long profileGuiFrontendNanos;
		long profileWorldFrontendNanos;
		long profileWorldValidateFrameNanos;
		long profileWorldBatchingNanos;
		long profileWorldResourcePrepareNanos;
		long profileWorldMeshSectionExpandGroupNanos;
		long profileShaderPlanLookupNanos;
		long profileGalCommandGenerationNanos;
		long profileGalSubmitTotalNanos;
		long profileGalValidateOpsNanos;
		long profileGalValidateHandlesNanos;
		long profileGalHazardAnalysisNanos;
		long profileBackendEncodeNanos;
		long profileBackendSubmitNanos;
		long profileBackendRetireNanos;
		long profileVulkanCommandBufferAllocNanos;
		long profileVulkanCommandBufferBeginNanos;
		long profileVulkanCommandRecordingNanos;
		long profileVulkanCommandBufferEndNanos;
		long profileVulkanQueueSubmitNanos;
		long profileVulkanTimelinePollNanos;
		long profileVulkanTimelineWaitNanos;
		long profileVulkanDeviceWaitIdleNanos;
		long profileVulkanCommandBuffersAllocated;
		long profileVulkanCommandBuffersFreed;
		long profileVulkanWaitCount;
		long profileVulkanDeviceWaitIdleCount;
		long profileResourceCreatesDelta;
		long profileResourceDestroysDelta;
		long profileHostWriteOps;
		long profileHostWriteBytes;
		long profileBarrierOps;
		long profilePassCount;
		long profileDrawOps;
		long profileDrawIndexedOps;
		long profilePipelineBinds;
		long profileResourceSetBinds;
		long profileGpuTimestampUnavailableFrames;
		long profileGBufferPersistentCacheHits;
		long profileGBufferPersistentCacheMisses;
		long profileGBufferFinalBindingCacheHits;
		long profileGBufferFinalBindingCacheMisses;
		long profileGBufferAttachmentCreates;
		long profileGBufferPipelineCreates;
		long profileGBufferShaderModuleCreates;
		long profileGBufferDescriptorCreates;
		long profileGBufferRenderTargetCreates;
		long profileGBufferResourcesRetired;
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
