package net.vulkanic.bridge;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.util.profiling.TracyCompat;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class RustGalFrameQueue {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int GUI_UNIFORM_BYTES = 32;
	private static final String CROSSHAIR_PRODUCER = "minecraft.gui.crosshair";
	private static final String HOTBAR_BASE_PRODUCER = "minecraft.gui.hotbar.base";
	private static final String HOTBAR_SELECTION_PRODUCER = "minecraft.gui.hotbar.selection";
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
		long started = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal." + sprite.phaseName + ".java-producer");
		if (VulkanicAPI.isVulkanBackendSelected()) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalStateException("Rust VulkanicGAL partial-frame GUI sprite is unsupported for Vulkan; whole-frame Rust presentation is required");
		}
		try {
			synchronized (LOCK) {
				guiGraphics.guiRenderState.submitGuiElement(
					SCHEDULER.enqueue(
						sprite,
						producerId,
						selectedSlot,
						x,
						y,
						width,
						height,
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
			List<FrameDraw> frameDraws = new ArrayList<>(batches.size());
			try {
				for (DeferredGuiBatch batch : batches) {
					frameDraws.add(new FrameDraw(batch, resourcesFor(batch)));
				}
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
			FrameResources frameResources = frameResourcesFor(batches.get(0), frame.frameTarget());
			GraphicsFrameBenchmark.beginPhase("rust-gal.gui-frame.abi-packing");
			long packingStarted = System.nanoTime();
			VulkanicGalBridge.SubmissionBatchBuilder builder = bridge.submissionBatchBuilder("minecraft.gui.frame");
			for (FrameDraw draw : frameDraws) {
				builder.hostWrite(draw.resources().uniformBuffer, 0, uniformBytes(draw.batch()))
					.barrier(draw.resources().uniformBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false);
			}
			builder.beginFramePass(frameResources.pass(), frameResources.target());
			for (FrameDraw draw : frameDraws) {
				builder.bindGraphicsPipeline(draw.resources().pipeline)
					.bindResourceSet(draw.resources().pipelineLayout, draw.resources().resourceSet)
					.setIndexBuffer(draw.resources().indexBuffer)
					.drawIndexed(6);
			}
			VulkanicGalBridge.SubmissionBatch submit = builder.endPass().build();
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
			retireOutstanding(false);
			METRICS.executeNanos += elapsedSince(executeStarted);
			executeCounted = true;
			if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
				LOGGER.info(
					"Rust VulkanicGAL GUI frame executed: batches={}, frame={}, submission={}, cacheHits={}, cacheMisses={}, ffiCalls={}, ffiBytes={}",
					batches.size(),
					frameId,
					submissionId,
					METRICS.cacheHits,
					METRICS.cacheMisses,
					METRICS.ffiCalls,
					METRICS.ffiBytes
				);
			}
			auditMessage(
				"Rust OpenGL VulkanicGAL GUI frame executed producer=gui.frame"
					+ " stratum=gui.frame"
					+ " frame_batch_count=" + batches.size()
					+ " frame=" + frameId
					+ " submission=" + submissionId
					+ " rust_gal_cache_hits=" + METRICS.cacheHits
					+ " rust_gal_cache_misses=" + METRICS.cacheMisses
					+ " rust_gal_queue_depth=" + SCHEDULER.pendingCount()
					+ " rust_gal_frames_executed=" + METRICS.frames
					+ " rust_gal_batches_executed=" + METRICS.batchesExecuted
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
					+ " ffi_bytes=" + METRICS.ffiBytes
			);
		} finally {
			if (!executeCounted) {
				METRICS.executeNanos += elapsedSince(executeStarted);
			}
			GraphicsFrameBenchmark.endPhase("rust-gal.gui-frame.execute");
		}
	}

	private static CachedResources resourcesFor(DeferredGuiBatch batch) {
		CacheKey key = new CacheKey(batch.sprite().cacheKind, batch.producerId(), generation);
		CachedResources resources = CACHES.get(key);
		if (resources != null) {
			METRICS.cacheHits++;
			return resources;
		}
		METRICS.cacheMisses++;
		CachedResources created = createGuiSpriteResources(batch.sprite(), key);
		CACHES.put(key, created);
		return created;
	}

	private static CachedResources createGuiSpriteResources(GuiSprite sprite, CacheKey key) {
		long createStarted = System.nanoTime();
		List<HandleToDestroy> created = new ArrayList<>();
		try {
				VulkanicGalBridge.ResourceResults base = bridge.resourceBatch(
					bridge.resourceBatchBuilder()
						.buffer(1, key.label("texture-upload"), sprite.textureBytes(), VulkanicGalBridge.MEMORY_UPLOAD,
							VulkanicGalBridge.BUFFER_TRANSFER_SRC | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.buffer(2, key.label("index"), 24, VulkanicGalBridge.MEMORY_UPLOAD,
						VulkanicGalBridge.BUFFER_INDEX | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.buffer(3, key.label("uniform"), GUI_UNIFORM_BYTES, VulkanicGalBridge.MEMORY_UPLOAD,
						VulkanicGalBridge.BUFFER_UNIFORM | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
					.texture(4, key.label("texture"), VulkanicGalBridge.FORMAT_RGBA8, sprite.width, sprite.height,
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
				sprite.pipeline(bridge.resourceBatchBuilder(), 31, key.label("pipeline"), pipelineLayout, vertex, fragment)
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
			uploadPersistentResources(sprite, resources);
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

	private static void uploadPersistentResources(GuiSprite sprite, CachedResources resources) {
		VulkanicGalBridge.Status upload = bridge.submit(
			bridge.submissionBatchBuilder(resources.key().label("upload"))
				.hostWrite(resources.uploadBuffer, 0, spriteTextureBytes(sprite))
				.barrier(resources.uploadBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_TRANSFER_SRC, false)
				.hostWrite(resources.indexBuffer, 0, indexBytes())
				.barrier(resources.indexBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
				.barrier(resources.texture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_TRANSFER_DST, true)
				.copyBufferToTexture(resources.uploadBuffer, resources.texture, sprite.width, sprite.height)
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

	private static FrameResources frameResourcesFor(DeferredGuiBatch batch, long acquiredFrameTarget) {
		if (cachedFramePass != 0L && cachedFrameTarget != 0L && cachedFrameTarget == acquiredFrameTarget) {
			return new FrameResources(cachedFrameTarget, cachedFramePass);
		}
		destroyTransientFrameResources(cachedFramePass, cachedFrameTarget);
		VulkanicGalBridge.ResourceResults frameResources = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.frameRenderPass(9000, batch.producerId() + ".pass", acquiredFrameTarget)
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

	private static byte[] uniformBytes(DeferredGuiBatch batch) {
		ByteBuffer buffer = ByteBuffer.allocate(GUI_UNIFORM_BYTES).order(ByteOrder.nativeOrder());
		buffer.putFloat(batch.x());
		buffer.putFloat(batch.y());
		buffer.putFloat(batch.width());
		buffer.putFloat(batch.height());
		buffer.putFloat(batch.guiWidth());
		buffer.putFloat(batch.guiHeight());
		buffer.putFloat(0.0F);
		buffer.putFloat(0.0F);
		return buffer.array();
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
		GUI_HOTBAR_SELECTION("gui.hotbar.selection", 310);

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
			return this == GUI_CROSSHAIR || this == GUI_HOTBAR_BASE || this == GUI_HOTBAR_SELECTION;
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
			int x,
			int y,
			int width,
			int height,
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
				x,
				y,
				width,
				height,
				guiWidth,
				guiHeight
			);
			this.pending.put(batchId, batch);
			return new RustGalGuiElementRenderState(batchId, sequence, generation, batch.stratum(), producerId, selectedSlot, x, y, width, height, guiWidth, guiHeight);
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
		int x,
		int y,
		int width,
		int height,
		int guiWidth,
		int guiHeight
	) {
	}

	private record FrameDraw(DeferredGuiBatch batch, CachedResources resources) {
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
		);

		private final RenderStratum stratum;
		private final String phaseName;
		private final String cacheKind;
		private final String textureResource;
		private final int width;
		private final int height;
		private final boolean invertBlend;

		GuiSprite(RenderStratum stratum, String phaseName, String cacheKind, String textureResource, int width, int height, boolean invertBlend) {
			this.stratum = stratum;
			this.phaseName = phaseName;
			this.cacheKind = cacheKind;
			this.textureResource = textureResource;
			this.width = width;
			this.height = height;
			this.invertBlend = invertBlend;
		}

		private int textureBytes() {
			return this.width * this.height * 4;
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
		layout(std140) uniform GuiRect {
		    vec4 rect;
		    vec4 viewport;
		};
		out vec2 v_uv;
		const vec2 corner[6] = vec2[6](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0),
		    vec2(0.0, 0.0)
		);
		const vec2 uv[6] = vec2[6](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0),
		    vec2(0.0, 0.0)
		);
		void main() {
		    int vertex = gl_VertexID;
		    vec2 pixel = rect.xy + corner[vertex] * rect.zw;
		    vec2 ndc = vec2((pixel.x / viewport.x) * 2.0 - 1.0, 1.0 - (pixel.y / viewport.y) * 2.0);
		    gl_Position = vec4(ndc, 0.0, 1.0);
		    v_uv = uv[vertex];
		}
		""";

	private static final String FRAGMENT_SHADER_OPENGL = """
		#version 330 core
		uniform sampler2D Sampler0;
		in vec2 v_uv;
		out vec4 out_color;
		void main() {
		    ivec2 texture_size = textureSize(Sampler0, 0);
		    ivec2 texel = clamp(ivec2(floor(v_uv * vec2(texture_size))), ivec2(0), texture_size - ivec2(1));
		    vec4 color = texelFetch(Sampler0, texel, 0);
		    if (color.a <= 0.0) {
		        discard;
		    }
		    out_color = color;
		}
		""";
}
