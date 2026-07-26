package net.vulkanic.bridge;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class VulkanicGalBridge implements AutoCloseable {
	public static final int ABI_VERSION = 2;
	public static final int STATUS_OK = 0;

	public static final int BACKEND_VULKAN = 1;
	public static final int BACKEND_OPENGL = 2;

	public static final long FEATURE_GRAPHICS = 1L << 0;
	public static final long FEATURE_DESCRIPTOR_ARRAYS = 1L << 2;
	public static final long FEATURE_OPTIONAL_BINDINGS = 1L << 3;
	public static final long FEATURE_UNIFORM_BUFFERS = 1L << 5;
	public static final long FEATURE_STORAGE_BUFFERS = 1L << 6;
	public static final long FEATURE_TEXTURE_SUBRESOURCE_COPIES = 1L << 13;
	public static final long FEATURE_HOST_BUFFER_ACCESS = 1L << 16;
	public static final long FEATURE_PRESENTATION = 1L << 17;

	public static final long BRIDGE_FEATURES = FEATURE_GRAPHICS
		| FEATURE_DESCRIPTOR_ARRAYS
		| FEATURE_OPTIONAL_BINDINGS
		| FEATURE_UNIFORM_BUFFERS
			| FEATURE_STORAGE_BUFFERS
			| FEATURE_TEXTURE_SUBRESOURCE_COPIES
			| FEATURE_HOST_BUFFER_ACCESS;

	public static final int MEMORY_DEVICE_LOCAL = 1;
	public static final int MEMORY_UPLOAD = 2;
	public static final int MEMORY_READBACK = 3;
	public static final long BUFFER_VERTEX = 1L << 0;
	public static final long BUFFER_INDEX = 1L << 1;
	public static final long BUFFER_UNIFORM = 1L << 2;
	public static final long BUFFER_STORAGE = 1L << 3;
	public static final long BUFFER_TRANSFER_SRC = 1L << 4;
	public static final long BUFFER_TRANSFER_DST = 1L << 5;
	public static final long BUFFER_HOST_READ = 1L << 7;
	public static final long BUFFER_HOST_WRITE = 1L << 8;
	public static final long TEXTURE_SAMPLED = 1L << 0;
	public static final long TEXTURE_COLOR_ATTACHMENT = 1L << 2;
	public static final long TEXTURE_DEPTH_STENCIL_ATTACHMENT = 1L << 3;
	public static final long TEXTURE_TRANSFER_SRC = 1L << 4;
	public static final long TEXTURE_TRANSFER_DST = 1L << 5;
	public static final int TEXTURE_2D = 2;
	public static final int FORMAT_RGBA8 = 1;
	public static final int FORMAT_DEPTH32 = 5;
	public static final int SHADER_VERTEX = 1;
	public static final int SHADER_FRAGMENT = 2;
	public static final int SHADER_GLSL = 3;
	public static final int BINDING_UNIFORM_BUFFER = 1;
	public static final int BINDING_STORAGE_BUFFER = 2;
	public static final int BINDING_SAMPLED_TEXTURE = 3;
	public static final int BINDING_SAMPLER = 5;
	public static final int STAGE_DRAW = 1;
	public static final int ACCESS_READ = 1;
	public static final int ACCESS_TRANSFER = 16;
	public static final int TOPOLOGY_TRIANGLES = 3;
	public static final int CULL_NONE = 1;
	public static final int CULL_BACK = 3;
	public static final int BLEND_ALPHA = 2;
	public static final int BLEND_INVERT = 4;
	public static final int COMPARE_LEQUAL = 3;
	public static final int LOAD_CLEAR = 2;
	public static final int STORE_STORE = 1;
	public static final int STORE_DONT_CARE = 2;
	public static final int USAGE_UNDEFINED = 1;
	public static final int USAGE_SHADER_READ = 2;
	public static final int USAGE_COLOR_ATTACHMENT = 4;
	public static final int USAGE_DEPTH_ATTACHMENT = 5;
	public static final int USAGE_TRANSFER_SRC = 6;
	public static final int USAGE_TRANSFER_DST = 7;
	public static final int QUEUE_GRAPHICS = 1;
	public static final int HANDLE_BUFFER = 1;
	public static final int HANDLE_TEXTURE = 2;
	public static final int HANDLE_TEXTURE_VIEW = 3;
	public static final int HANDLE_SAMPLER = 4;
	public static final int HANDLE_SHADER_MODULE = 5;
	public static final int HANDLE_RESOURCE_LAYOUT = 6;
	public static final int HANDLE_RESOURCE_SET = 7;
	public static final int HANDLE_PIPELINE_LAYOUT = 8;
	public static final int HANDLE_GRAPHICS_PIPELINE = 9;
	public static final int HANDLE_RENDER_PASS = 12;
	public static final int HANDLE_FRAME_TARGET = 13;

	private final Arena arena;
	private final long contextId;
	private final long negotiatedFeatures;
	private boolean closed;

	private VulkanicGalBridge(Arena arena, long contextId, long negotiatedFeatures) {
		this.arena = arena;
		this.contextId = contextId;
		this.negotiatedFeatures = negotiatedFeatures;
	}

	public static VulkanicGalBridge createBorrowedOpenGl(long stableWindowId) {
		if (stableWindowId == 0L) {
			throw new IllegalArgumentException("borrowed OpenGL context requires a non-zero window id");
		}
		Arena arena = Arena.ofConfined();
		try {
			MemorySegment request = Struct.BORROWED_OPENGL_CONTEXT_CREATE.allocate(arena);
			Abi.writeHeader(request, Struct.BORROWED_OPENGL_CONTEXT_CREATE);
			Struct.BORROWED_OPENGL_CONTEXT_CREATE.setLong(request, 1, stableWindowId);
			boolean rustTracy = Boolean.getBoolean("mattmc.dev.tracyCapture") || Boolean.getBoolean("mattmc.dev.rustGalFrame.tracy");
			Struct.BORROWED_OPENGL_CONTEXT_CREATE.setInt(request, 2, rustTracy ? 1 : 0);
			Struct.BORROWED_OPENGL_CONTEXT_CREATE.setInt(request, 3, 0);
			Abi.writeBytes(arena, request, Struct.BORROWED_OPENGL_CONTEXT_CREATE, 4, "java-frame-borrowed-opengl");
			MemorySegment result = Struct.CONTEXT_RESULT.allocate(arena);
			int status = Native.contextCreateBorrowedOpenGl(request, result);
			if (status != STATUS_OK) {
				throw new IllegalStateException("Rust VulkanicGAL borrowed OpenGL context creation failed: status=" + status + ": " + Native.lastError(0));
			}
			long contextId = Struct.CONTEXT_RESULT.getLong(result, 3);
			VulkanicGalBridge bridge = new VulkanicGalBridge(arena, contextId, BRIDGE_FEATURES | FEATURE_PRESENTATION);
			bridge.queryCapabilities();
			return bridge;
		} catch (RuntimeException error) {
			arena.close();
			throw error;
		}
	}

	public static VulkanicGalBridge create(String backendName) {
		int backend = switch (backendName.toLowerCase(Locale.ROOT)) {
			case "rust-vulkan", "vulkan" -> BACKEND_VULKAN;
			case "rust-opengl", "opengl" -> BACKEND_OPENGL;
			default -> throw new IllegalArgumentException("unknown Rust GAL backend: " + backendName);
		};
		Arena arena = Arena.ofConfined();
		try {
			MemorySegment request = Struct.CONTEXT_CREATE.allocate(arena);
			Abi.writeHeader(request, Struct.CONTEXT_CREATE);
			Struct.CONTEXT_CREATE.setInt(request, 1, backend);
			boolean rustTracy = Boolean.getBoolean("mattmc.dev.tracyCapture") || Boolean.getBoolean("mattmc.dev.graphicsSubsystemBenchmark.rustTracy");
			Struct.CONTEXT_CREATE.setInt(request, 2, rustTracy ? 1 : 0);
			Abi.writeBytes(arena, request, Struct.CONTEXT_CREATE, 3, "java-subsystem-" + backendName);
			MemorySegment result = Struct.CONTEXT_RESULT.allocate(arena);
			int status = Native.contextCreate(request, result);
			if (status != STATUS_OK) {
				throw new IllegalStateException("Rust VulkanicGAL context creation failed: status=" + status + ": " + Native.lastError(0));
			}
			long contextId = Struct.CONTEXT_RESULT.getLong(result, 3);
			VulkanicGalBridge bridge = new VulkanicGalBridge(arena, contextId, BRIDGE_FEATURES);
			bridge.queryCapabilities();
			return bridge;
		} catch (RuntimeException error) {
			arena.close();
			throw error;
		}
	}

	public long contextId() {
		return contextId;
	}

	public long negotiatedFeatures() {
		return negotiatedFeatures;
	}

	public Capabilities queryCapabilities() {
		MemorySegment request = Struct.CAPABILITY_QUERY.allocate(arena);
		Abi.writeHeader(request, Struct.CAPABILITY_QUERY);
		Struct.CAPABILITY_QUERY.setLong(request, 1, negotiatedFeatures);
		MemorySegment result = Struct.CAPABILITY_RESULT.allocate(arena);
		int status = Native.capabilities(contextId, request, result);
		checkStatus(status, "capability query");
		long supported = Struct.CAPABILITY_RESULT.getLong(result, 3);
		long negotiated = Struct.CAPABILITY_RESULT.getLong(result, 4);
		return new Capabilities(supported, negotiated);
	}

	public ResourceResults resourceBatch(ResourceBatch batch) {
		Objects.requireNonNull(batch, "batch");
		int resultCount = batch.createCount();
		MemorySegment results = resultCount == 0 ? MemorySegment.NULL : arena.allocate(Struct.CREATE_RESULT.byteSize() * resultCount, Struct.CREATE_RESULT.alignment());
		MemorySegment status = Struct.STATUS.allocate(arena);
		int code = Native.resourceBatch(contextId, batch.segment(), results, resultCount, status);
		checkStatus(code, "resource batch");
			return new ResourceResults(results, resultCount, Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	public Status submit(SubmissionBatch batch) {
		Objects.requireNonNull(batch, "batch");
		MemorySegment status = Struct.STATUS.allocate(arena);
		int code = Native.submitBatch(contextId, batch.segment(), status);
		checkStatus(code, "submission");
		return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	public Completion completion(long submission) {
		MemorySegment request = Struct.COMPLETION_QUERY.allocate(arena);
		Abi.writeHeader(request, Struct.COMPLETION_QUERY);
		Struct.COMPLETION_QUERY.setLong(request, 1, submission);
		MemorySegment result = Struct.COMPLETION_RESULT.allocate(arena);
		int code = Native.completion(contextId, request, result);
		checkStatus(code, "completion query");
		return new Completion(
			Struct.COMPLETION_RESULT.getLong(result, 3),
			Struct.COMPLETION_RESULT.getLong(result, 4),
			Struct.COMPLETION_RESULT.getInt(result, 5) != 0);
	}

	public byte[] readback(long submission, long buffer, long offset, int size) {
		MemorySegment request = Struct.READBACK_REQUEST.allocate(arena);
		Abi.writeHeader(request, Struct.READBACK_REQUEST);
		Struct.READBACK_REQUEST.setLong(request, 1, submission);
		Struct.READBACK_REQUEST.setLong(request, 2, buffer);
		Struct.READBACK_REQUEST.setLong(request, 3, offset);
		Struct.READBACK_REQUEST.setLong(request, 4, size);
		MemorySegment output = arena.allocate(size, 1);
		MemorySegment result = Struct.READBACK_RESULT.allocate(arena);
		int code = Native.readback(contextId, request, output, size, result);
		checkStatus(code, "readback");
		int written = Math.toIntExact(Struct.READBACK_RESULT.getLong(result, 5));
		return output.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE);
	}

	public Status retire(long submission) {
		MemorySegment request = Struct.RETIREMENT_BATCH.allocate(arena);
		Abi.writeHeader(request, Struct.RETIREMENT_BATCH);
		Struct.RETIREMENT_BATCH.setLong(request, 1, submission);
		Abi.writeSlice(request, Struct.RETIREMENT_BATCH, 2, MemorySegment.NULL, 0);
		MemorySegment status = Struct.STATUS.allocate(arena);
		checkStatus(Native.retire(contextId, request, status), "retirement");
		return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	public Status configureFrame(String label, int width, int height, int colorFormat) {
		MemorySegment request = Struct.FRAME_SURFACE_CONFIG.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_SURFACE_CONFIG);
		Abi.writeBytes(arena, request, Struct.FRAME_SURFACE_CONFIG, 1, label);
		long extent = Struct.FRAME_SURFACE_CONFIG.offset(2);
		request.set(ValueLayout.JAVA_INT, extent, width);
		request.set(ValueLayout.JAVA_INT, extent + 4, height);
		request.set(ValueLayout.JAVA_INT, extent + 8, 1);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 3, colorFormat);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 4, 3);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 5, 1);
		MemorySegment status = Struct.STATUS.allocate(arena);
		checkStatus(Native.frameConfigure(contextId, request, status), "frame configure");
		return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	public AcquiredFrame acquireFrame(long correlationId, int width, int height) {
		MemorySegment request = Struct.FRAME_ACQUIRE.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_ACQUIRE);
		Struct.FRAME_ACQUIRE.setLong(request, 1, correlationId);
		long extent = Struct.FRAME_ACQUIRE.offset(2);
		request.set(ValueLayout.JAVA_INT, extent, width);
		request.set(ValueLayout.JAVA_INT, extent + 4, height);
		request.set(ValueLayout.JAVA_INT, extent + 8, 1);
		MemorySegment result = Struct.FRAME_ACQUIRE_RESULT.allocate(arena);
		checkStatus(Native.frameAcquire(contextId, request, result), "frame acquire");
		long resultExtent = Struct.FRAME_ACQUIRE_RESULT.offset(7);
		return new AcquiredFrame(
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 3),
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 4),
			Struct.FRAME_ACQUIRE_RESULT.getInt(result, 5),
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 6),
			result.get(ValueLayout.JAVA_INT, resultExtent),
			result.get(ValueLayout.JAVA_INT, resultExtent + 4),
			Struct.FRAME_ACQUIRE_RESULT.getInt(result, 8));
	}

	public FrameResize resizeFrame(long correlationId, int width, int height) {
		MemorySegment request = Struct.FRAME_RESIZE.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_RESIZE);
		Struct.FRAME_RESIZE.setLong(request, 1, correlationId);
		long extent = Struct.FRAME_RESIZE.offset(2);
		request.set(ValueLayout.JAVA_INT, extent, width);
		request.set(ValueLayout.JAVA_INT, extent + 4, height);
		request.set(ValueLayout.JAVA_INT, extent + 8, 1);
		MemorySegment result = Struct.FRAME_RESIZE_RESULT.allocate(arena);
		checkStatus(Native.frameResize(contextId, request, result), "frame resize");
		long resultExtent = Struct.FRAME_RESIZE_RESULT.offset(4);
		return new FrameResize(
			Struct.FRAME_RESIZE_RESULT.getInt(result, 3),
			result.get(ValueLayout.JAVA_INT, resultExtent),
			result.get(ValueLayout.JAVA_INT, resultExtent + 4));
	}

	public PresentedFrame presentFrame(long frameId, long correlationId, long waitSubmissionId) {
		MemorySegment request = Struct.FRAME_PRESENT.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_PRESENT);
		Struct.FRAME_PRESENT.setLong(request, 1, frameId);
		Struct.FRAME_PRESENT.setLong(request, 2, correlationId);
		Struct.FRAME_PRESENT.setLong(request, 3, waitSubmissionId);
		MemorySegment result = Struct.FRAME_PRESENT_RESULT.allocate(arena);
		checkStatus(Native.framePresent(contextId, request, result), "frame present");
		return new PresentedFrame(
			Struct.FRAME_PRESENT_RESULT.getLong(result, 3),
			Struct.FRAME_PRESENT_RESULT.getLong(result, 4),
			Struct.FRAME_PRESENT_RESULT.getInt(result, 5),
			Struct.FRAME_PRESENT_RESULT.getLong(result, 6));
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation,
		long frameId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		List<GuiSpriteRecord> sprites
	) {
		Objects.requireNonNull(sprites, "sprites");
		if (sprites.isEmpty()) {
			throw new IllegalArgumentException("GUI frame submission requires at least one sprite");
		}
		MemorySegment spriteArray = Struct.GUI_SPRITE_REQUEST.array(arena, sprites.size());
		for (int i = 0; i < sprites.size(); i++) {
			GuiSpriteRecord sprite = sprites.get(i);
			MemorySegment item = Abi.item(spriteArray, Struct.GUI_SPRITE_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.GUI_SPRITE_REQUEST.offset(0), Struct.GUI_SPRITE_REQUEST.byteSize());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 1, sprite.stratum());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 2, sprite.spriteId());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 3, sprite.selectedSlot());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_SPRITE_REQUEST.offset(4), sprite.progressFraction());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 5, sprite.fillDirection());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 6, sprite.colorArgb());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 7, sprite.x());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 8, sprite.y());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 9, sprite.width());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 10, sprite.height());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 11, sprite.guiWidth());
			Struct.GUI_SPRITE_REQUEST.setInt(item, 12, sprite.guiHeight());
		}
		MemorySegment request = Struct.GUI_FRAME_SUBMIT.allocate(arena);
		Abi.writeHeader(request, Struct.GUI_FRAME_SUBMIT);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 1, generation);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 2, frameId);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 3, frameTarget);
		Struct.GUI_FRAME_SUBMIT.setInt(request, 4, guiWidth);
		Struct.GUI_FRAME_SUBMIT.setInt(request, 5, guiHeight);
		Abi.writeSlice(request, Struct.GUI_FRAME_SUBMIT, 6, spriteArray, sprites.size());
		Struct.GUI_FRAME_SUBMIT.setLong(request, 7, negotiatedFeatures);
		MemorySegment result = Struct.GUI_FRAME_SUBMIT_RESULT.allocate(arena);
		checkStatus(Native.guiSubmitFrame(contextId, request, result), "GUI frame submission");
		long metricsOffset = Struct.GUI_FRAME_SUBMIT_RESULT.offset(11);
		BackendMetrics metrics = backendMetricsAt(result, metricsOffset);
		long ffiCalls = result.get(ValueLayout.JAVA_LONG, metricsOffset + 64);
		long ffiInputBytes = result.get(ValueLayout.JAVA_LONG, metricsOffset + 72);
		return new GuiFrameSubmitResult(
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 3),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 4),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 5),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 6),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 7),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 8),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 9),
			Struct.GUI_FRAME_SUBMIT_RESULT.getLong(result, 10),
			ffiCalls,
			ffiInputBytes,
			metrics
		);
	}

	public Status updateGuiAssets(long generation, List<GuiAssetRecord> assets) {
		Objects.requireNonNull(assets, "assets");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment assetArray = Struct.GUI_ASSET_PAYLOAD.array(updateArena, assets.size());
			for (int i = 0; i < assets.size(); i++) {
				GuiAssetRecord asset = assets.get(i);
				MemorySegment item = Abi.item(assetArray, Struct.GUI_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.GUI_ASSET_PAYLOAD.offset(0), Struct.GUI_ASSET_PAYLOAD.byteSize());
				Struct.GUI_ASSET_PAYLOAD.setInt(item, 1, asset.spriteId());
				Abi.writeBytes(updateArena, item, Struct.GUI_ASSET_PAYLOAD, 2, asset.pngBytes());
			}
			MemorySegment request = Struct.GUI_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.GUI_ASSET_UPDATE);
			Struct.GUI_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.GUI_ASSET_UPDATE, 2, assetArray, assets.size());
			Struct.GUI_ASSET_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.guiUpdateAssets(contextId, request, status), "GUI asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public record GuiAssetRecord(int spriteId, byte[] pngBytes) {
		public GuiAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
		}
	}

	public Status shutdownFrame() {
		MemorySegment status = Struct.STATUS.allocate(arena);
		checkStatus(Native.frameShutdown(contextId, status), "frame shutdown");
		return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		MemorySegment status = Struct.STATUS.allocate(arena);
		Native.contextDestroy(contextId, status);
		arena.close();
	}

	private void checkStatus(int status, String operation) {
		if (status == STATUS_OK) {
			return;
		}
		throw new IllegalStateException("Rust VulkanicGAL " + operation + " failed with status " + status + ": " + Native.lastError(contextId));
	}

	private static BackendMetrics backendMetricsAt(MemorySegment segment, long metricsOffset) {
		long commandLists = segment.get(ValueLayout.JAVA_LONG, metricsOffset + 24);
		long commandOps = segment.get(ValueLayout.JAVA_LONG, metricsOffset + 32);
		long backendSubmissions = segment.get(ValueLayout.JAVA_LONG, metricsOffset + 40);
		long backendWaits = segment.get(ValueLayout.JAVA_LONG, metricsOffset + 48);
		return new BackendMetrics(
			commandLists,
			commandOps,
			backendSubmissions,
			backendWaits,
			commandOps,
			0L,
			0L,
			backendSubmissions,
			backendSubmissions,
			backendWaits,
			0L
		);
	}

	public ResourceBatchBuilder resourceBatchBuilder() {
		return new ResourceBatchBuilder(arena, negotiatedFeatures);
	}

	public SubmissionBatchBuilder submissionBatchBuilder(String label) {
		return new SubmissionBatchBuilder(arena, negotiatedFeatures, label);
	}

	public record Capabilities(long supportedFeatureBits, long negotiatedFeatureBits) {
	}

	public record Status(long submissionId, long ffiCalls, long ffiInputBytes, BackendMetrics backendMetrics) {
	}

	public record BackendMetrics(
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
		public static BackendMetrics empty() {
			return new BackendMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
		}
	}

	public record Completion(long requestedSubmissionId, long completedSubmissionId, boolean complete) {
	}

	public record AcquiredFrame(long frameId, long correlationId, int status, long frameTarget, int width, int height, int colorFormat) {
	}

	public record FrameResize(int status, int width, int height) {
	}

	public record PresentedFrame(long frameId, long correlationId, int status, long completedSubmissionId) {
	}

	public record GuiSpriteRecord(
		int stratum,
		int spriteId,
		int selectedSlot,
		float progressFraction,
		int fillDirection,
		int colorArgb,
		int x,
		int y,
		int width,
		int height,
		int guiWidth,
		int guiHeight
	) {
	}

	public record GuiFrameSubmitResult(
		long submissionId,
		long spriteCount,
		long spriteBatchCount,
		long cacheHits,
		long cacheMisses,
		long resourceCreates,
		long commandLists,
		long commandOps,
		long ffiCalls,
		long ffiInputBytes,
		BackendMetrics backendMetrics
	) {
		public Status asStatus() {
			return new Status(submissionId, ffiCalls, ffiInputBytes, backendMetrics);
		}
	}

	public record ResourceResults(MemorySegment segment, int count, long submissionId, long ffiCalls, long ffiInputBytes, BackendMetrics backendMetrics) {
		public long handle(int index) {
			return Struct.CREATE_RESULT.getLong(segment.asSlice((long)index * Struct.CREATE_RESULT.byteSize(), Struct.CREATE_RESULT.byteSize()), 1);
		}
	}

	static final class Native {
		private static final MethodHandle LAYOUT = downcall("mattmc_vulkanic_gal_abi_struct_layout", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		private static final MethodHandle CONTEXT_CREATE = downcall("mattmc_vulkanic_gal_context_create", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle CONTEXT_CREATE_BORROWED_OPENGL = downcall("mattmc_vulkanic_gal_context_create_borrowed_opengl", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle CONTEXT_DESTROY = downcall("mattmc_vulkanic_gal_context_destroy", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		private static final MethodHandle CAPABILITIES = downcall("mattmc_vulkanic_gal_capabilities", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle RESOURCE_BATCH = downcall("mattmc_vulkanic_gal_resource_batch", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		private static final MethodHandle SUBMIT = downcall("mattmc_vulkanic_gal_submit_batch", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle COMPLETION = downcall("mattmc_vulkanic_gal_completion_query", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle RETIRE = downcall("mattmc_vulkanic_gal_retire", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle READBACK = downcall("mattmc_vulkanic_gal_readback", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_CONFIGURE = downcall("mattmc_vulkanic_gal_frame_configure", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_ACQUIRE = downcall("mattmc_vulkanic_gal_frame_acquire", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_RESIZE = downcall("mattmc_vulkanic_gal_frame_resize", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_PRESENT = downcall("mattmc_vulkanic_gal_frame_present", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_SHUTDOWN = downcall("mattmc_vulkanic_gal_frame_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_SUBMIT_FRAME = downcall("mattmc_vulkanic_gal_gui_submit_frame", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_gui_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle LAST_ERROR = downcall("mattmc_vulkanic_gal_last_error", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

		private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
			return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
		}

		static int layout(int structId, MemorySegment out) {
			try {
				return (int) LAYOUT.invokeExact(structId, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to call VulkanicGAL layout ABI", throwable);
			}
		}

		static int contextCreate(MemorySegment request, MemorySegment out) {
			try {
				return (int) CONTEXT_CREATE.invokeExact(request, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to create Rust VulkanicGAL context", throwable);
			}
		}

		static int contextCreateBorrowedOpenGl(MemorySegment request, MemorySegment out) {
			try {
				return (int) CONTEXT_CREATE_BORROWED_OPENGL.invokeExact(request, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to create borrowed Rust VulkanicGAL OpenGL context", throwable);
			}
		}

		static int contextDestroy(long contextId, MemorySegment out) {
			try {
				return (int) CONTEXT_DESTROY.invokeExact(contextId, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to destroy Rust VulkanicGAL context", throwable);
			}
		}

		static int capabilities(long contextId, MemorySegment request, MemorySegment out) {
			try {
				return (int) CAPABILITIES.invokeExact(contextId, request, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to query Rust VulkanicGAL capabilities", throwable);
			}
		}

		static int resourceBatch(long contextId, MemorySegment batch, MemorySegment results, long capacity, MemorySegment status) {
			try {
				return (int) RESOURCE_BATCH.invokeExact(contextId, batch, results, capacity, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to submit Rust VulkanicGAL resource batch", throwable);
			}
		}

		static int submitBatch(long contextId, MemorySegment batch, MemorySegment status) {
			try {
				return (int) SUBMIT.invokeExact(contextId, batch, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to submit Rust VulkanicGAL command batch", throwable);
			}
		}

		static int completion(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) COMPLETION.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to query Rust VulkanicGAL completion", throwable);
			}
		}

		static int retire(long contextId, MemorySegment batch, MemorySegment status) {
			try {
				return (int) RETIRE.invokeExact(contextId, batch, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to retire Rust VulkanicGAL submission", throwable);
			}
		}

		static int readback(long contextId, MemorySegment request, MemorySegment output, long capacity, MemorySegment result) {
			try {
				return (int) READBACK.invokeExact(contextId, request, output, capacity, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to read back Rust VulkanicGAL buffer", throwable);
			}
		}

		static int frameConfigure(long contextId, MemorySegment request, MemorySegment status) {
			try {
				return (int) FRAME_CONFIGURE.invokeExact(contextId, request, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to configure Rust VulkanicGAL frame", throwable);
			}
		}

		static int frameAcquire(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) FRAME_ACQUIRE.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to acquire Rust VulkanicGAL frame", throwable);
			}
		}

		static int frameResize(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) FRAME_RESIZE.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to resize Rust VulkanicGAL frame", throwable);
			}
		}

		static int framePresent(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) FRAME_PRESENT.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to present Rust VulkanicGAL frame", throwable);
			}
		}

		static int frameShutdown(long contextId, MemorySegment status) {
			try {
				return (int) FRAME_SHUTDOWN.invokeExact(contextId, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to shutdown Rust VulkanicGAL frame", throwable);
			}
		}

		static int guiSubmitFrame(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) GUI_SUBMIT_FRAME.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to submit Rust VulkanicGAL GUI frame", throwable);
			}
		}

		static int guiUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) GUI_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL GUI assets", throwable);
			}
		}

		static String lastError(long contextId) {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment bytes = arena.allocate(4096, 1);
				long required = (long) LAST_ERROR.invokeExact(contextId, bytes, 4096L);
				int length = (int)Math.min(required, 4096);
				return length <= 0 ? "" : new String(bytes.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
			} catch (Throwable throwable) {
				return "failed to read Rust VulkanicGAL error: " + throwable.getMessage();
			}
		}
	}

	public enum Struct {
		HEADER(1),
		BYTES(2),
		HANDLE(3),
		CONTEXT_CREATE(4),
		CONTEXT_RESULT(5),
		CAPABILITY_QUERY(6),
		CAPABILITY_RESULT(7),
		STATUS(8),
		CREATE_RESULT(9),
		BUFFER_DESC(10),
		TEXTURE_DESC(11),
		TEXTURE_VIEW_DESC(12),
		SAMPLER_DESC(13),
		SHADER_DESC(14),
		RESOURCE_BINDING_DESC(15),
		RESOURCE_LAYOUT_DESC(16),
		RESOURCE_BINDING(17),
		RESOURCE_SET_DESC(18),
		PIPELINE_LAYOUT_DESC(19),
		GRAPHICS_PIPELINE_DESC(20),
		RENDER_TARGET_DESC(21),
		RENDER_PASS_DESC(22),
		RESOURCE_BATCH(23),
		PASS_ATTACHMENT(24),
		COPY_REGION(25),
		BARRIER(26),
		COMMAND_OP(27),
		COMMAND_LIST(28),
		SUBMISSION_BATCH(29),
		COMPLETION_QUERY(30),
		COMPLETION_RESULT(31),
			RETIREMENT_BATCH(32),
			READBACK_REQUEST(33),
			READBACK_RESULT(34),
			BORROWED_OPENGL_CONTEXT_CREATE(35),
			FRAME_SURFACE_CONFIG(36),
			FRAME_ACQUIRE(37),
			FRAME_ACQUIRE_RESULT(38),
			FRAME_RESIZE(39),
			FRAME_RESIZE_RESULT(40),
			FRAME_PRESENT(41),
			FRAME_PRESENT_RESULT(42),
			DESTROY_DESC(43),
			GUI_SPRITE_REQUEST(44),
			GUI_FRAME_SUBMIT(45),
			GUI_FRAME_SUBMIT_RESULT(46),
			GUI_ASSET_PAYLOAD(47),
			GUI_ASSET_UPDATE(48);

		private final int id;
		private final int byteSize;
		private final int alignment;
		private final int[] offsets;

		Struct(int id) {
			this.id = id;
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment layout = arena.allocate(160, 8);
				int status = Native.layout(id, layout);
				if (status != STATUS_OK) {
					throw new IllegalStateException("Rust ABI layout query failed for struct " + id + " with status " + status);
				}
				this.byteSize = layout.get(ValueLayout.JAVA_INT, 12);
				this.alignment = layout.get(ValueLayout.JAVA_INT, 16);
				int fieldCount = layout.get(ValueLayout.JAVA_INT, 20);
				this.offsets = new int[fieldCount];
				for (int i = 0; i < fieldCount; i++) {
					this.offsets[i] = layout.get(ValueLayout.JAVA_INT, 24L + i * 4L);
				}
			}
		}

		public int id() {
			return id;
		}

		public int byteSize() {
			return byteSize;
		}

		public int alignment() {
			return alignment;
		}

		public MemorySegment allocate(Arena arena) {
			return arena.allocate(byteSize, alignment);
		}

		public MemorySegment array(Arena arena, int count) {
			return count == 0 ? MemorySegment.NULL : arena.allocate((long)byteSize * count, alignment);
		}

		public long offset(int field) {
			return offsets[field];
		}

		public void setInt(MemorySegment segment, int field, int value) {
			segment.set(ValueLayout.JAVA_INT, offset(field), value);
		}

		public int getInt(MemorySegment segment, int field) {
			return segment.get(ValueLayout.JAVA_INT, offset(field));
		}

		public void setLong(MemorySegment segment, int field, long value) {
			segment.set(ValueLayout.JAVA_LONG, offset(field), value);
		}

		public long getLong(MemorySegment segment, int field) {
			return segment.get(ValueLayout.JAVA_LONG, offset(field));
		}

		public static long metricsFfiCalls(MemorySegment status) {
			long metricsOffset = STATUS.offset(7);
			return status.get(ValueLayout.JAVA_LONG, metricsOffset + 64);
		}

		public static long metricsFfiInputBytes(MemorySegment status) {
			long metricsOffset = STATUS.offset(7);
			return status.get(ValueLayout.JAVA_LONG, metricsOffset + 72);
		}

		public static BackendMetrics backendMetrics(MemorySegment status) {
			long metricsOffset = STATUS.offset(7);
			long commandLists = status.get(ValueLayout.JAVA_LONG, metricsOffset + 24);
			long commandOps = status.get(ValueLayout.JAVA_LONG, metricsOffset + 32);
			long backendSubmissions = status.get(ValueLayout.JAVA_LONG, metricsOffset + 40);
			long backendWaits = status.get(ValueLayout.JAVA_LONG, metricsOffset + 48);
			return new BackendMetrics(
				commandLists,
				commandOps,
				backendSubmissions,
				backendWaits,
				commandOps,
				0L,
				0L,
				backendSubmissions,
				backendSubmissions,
				backendWaits,
				0L
			);
		}
	}

	static final class Abi {
		private Abi() {
		}

		static void writeHeader(MemorySegment segment, Struct struct) {
			long header = struct.offset(0);
			segment.set(ValueLayout.JAVA_INT, header, ABI_VERSION);
			segment.set(ValueLayout.JAVA_INT, header + 4, struct.byteSize());
		}

		static void writeBytes(Arena arena, MemorySegment segment, Struct struct, int field, String value) {
			writeBytes(arena, segment, struct, field, value.getBytes(StandardCharsets.UTF_8));
		}

		static void writeBytes(Arena arena, MemorySegment segment, Struct struct, int field, byte[] value) {
			MemorySegment bytes = value.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, value);
			long base = struct.offset(field);
			segment.set(ValueLayout.ADDRESS, base, bytes);
			segment.set(ValueLayout.JAVA_LONG, base + 8, value.length);
		}

		static void writeSlice(MemorySegment segment, Struct struct, int field, MemorySegment ptr, long count) {
			long base = struct.offset(field);
			segment.set(ValueLayout.ADDRESS, base, ptr);
			segment.set(ValueLayout.JAVA_LONG, base + 8, count);
		}

		static MemorySegment item(MemorySegment array, Struct struct, int index) {
			return array.asSlice((long)index * struct.byteSize(), struct.byteSize());
		}

		static byte[] directBytes(ByteBuffer buffer) {
			ByteBuffer duplicate = buffer.duplicate();
			byte[] bytes = new byte[duplicate.remaining()];
			duplicate.get(bytes);
			return bytes;
		}
	}

	public static final class ResourceBatchBuilder {
		private final Arena arena;
		private final long features;
		private final List<MemorySegment> buffers = new ArrayList<>();
		private final List<MemorySegment> textures = new ArrayList<>();
		private final List<MemorySegment> textureViews = new ArrayList<>();
		private final List<MemorySegment> samplers = new ArrayList<>();
		private final List<MemorySegment> shaders = new ArrayList<>();
		private final List<MemorySegment> resourceLayouts = new ArrayList<>();
		private final List<MemorySegment> layoutBindings = new ArrayList<>();
		private final List<MemorySegment> resourceSets = new ArrayList<>();
		private final List<MemorySegment> setBindings = new ArrayList<>();
		private final List<MemorySegment> pipelineLayouts = new ArrayList<>();
		private final List<Long> pipelineLayoutSets = new ArrayList<>();
		private final List<MemorySegment> graphicsPipelines = new ArrayList<>();
		private final List<Integer> passFormats = new ArrayList<>();
			private final List<MemorySegment> renderTargets = new ArrayList<>();
			private final List<Long> targetColorViews = new ArrayList<>();
			private final List<MemorySegment> renderPasses = new ArrayList<>();
			private final List<MemorySegment> destroys = new ArrayList<>();

		ResourceBatchBuilder(Arena arena, long features) {
			this.arena = arena;
			this.features = features;
		}

		public ResourceBatchBuilder buffer(long id, String label, long size, int memory, long usage) {
			MemorySegment item = Struct.BUFFER_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.BUFFER_DESC.offset(0), Struct.BUFFER_DESC.byteSize());
			Struct.BUFFER_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.BUFFER_DESC, 2, label);
			Struct.BUFFER_DESC.setLong(item, 3, size);
			Struct.BUFFER_DESC.setInt(item, 4, memory);
			Struct.BUFFER_DESC.setLong(item, 5, usage);
			buffers.add(item);
			return this;
		}

		public ResourceBatchBuilder texture(long id, String label, int format, int width, int height, long usage) {
			MemorySegment item = Struct.TEXTURE_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.TEXTURE_DESC.offset(0), Struct.TEXTURE_DESC.byteSize());
			Struct.TEXTURE_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.TEXTURE_DESC, 2, label);
			Struct.TEXTURE_DESC.setInt(item, 3, TEXTURE_2D);
			Struct.TEXTURE_DESC.setInt(item, 4, format);
			long extent = Struct.TEXTURE_DESC.offset(5);
			item.set(ValueLayout.JAVA_INT, extent, width);
			item.set(ValueLayout.JAVA_INT, extent + 4, height);
			item.set(ValueLayout.JAVA_INT, extent + 8, 1);
			Struct.TEXTURE_DESC.setInt(item, 6, 1);
			Struct.TEXTURE_DESC.setInt(item, 7, 1);
			Struct.TEXTURE_DESC.setLong(item, 8, usage);
			textures.add(item);
			return this;
		}

		public ResourceBatchBuilder textureView(long id, String label, long texture, int format) {
			MemorySegment item = Struct.TEXTURE_VIEW_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.TEXTURE_VIEW_DESC.offset(0), Struct.TEXTURE_VIEW_DESC.byteSize());
			Struct.TEXTURE_VIEW_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.TEXTURE_VIEW_DESC, 2, label);
			Struct.TEXTURE_VIEW_DESC.setLong(item, 3, texture);
			Struct.TEXTURE_VIEW_DESC.setInt(item, 4, format);
			Struct.TEXTURE_VIEW_DESC.setInt(item, 5, 0);
			Struct.TEXTURE_VIEW_DESC.setInt(item, 6, 1);
			Struct.TEXTURE_VIEW_DESC.setInt(item, 7, 0);
			Struct.TEXTURE_VIEW_DESC.setInt(item, 8, 1);
			textureViews.add(item);
			return this;
		}

		public ResourceBatchBuilder sampler(long id, String label) {
			MemorySegment item = Struct.SAMPLER_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.SAMPLER_DESC.offset(0), Struct.SAMPLER_DESC.byteSize());
			Struct.SAMPLER_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.SAMPLER_DESC, 2, label);
			for (int i = 3; i <= 8; i++) {
				Struct.SAMPLER_DESC.setInt(item, i, 1);
			}
			samplers.add(item);
			return this;
		}

		public ResourceBatchBuilder shader(long id, String label, int stage, String source) {
			MemorySegment item = Struct.SHADER_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.SHADER_DESC.offset(0), Struct.SHADER_DESC.byteSize());
			Struct.SHADER_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.SHADER_DESC, 2, label);
			Struct.SHADER_DESC.setInt(item, 3, stage);
			Struct.SHADER_DESC.setInt(item, 4, SHADER_GLSL);
			Abi.writeBytes(arena, item, Struct.SHADER_DESC, 5, source);
			Abi.writeBytes(arena, item, Struct.SHADER_DESC, 6, "main");
			shaders.add(item);
			return this;
		}

		public ResourceBatchBuilder resourceLayout(long id, String label, BindingDesc... bindings) {
			long start = layoutBindings.size();
			for (BindingDesc binding : bindings) {
				MemorySegment item = Struct.RESOURCE_BINDING_DESC.allocate(arena);
				item.set(ValueLayout.JAVA_INT, Struct.RESOURCE_BINDING_DESC.offset(0), Struct.RESOURCE_BINDING_DESC.byteSize());
				Struct.RESOURCE_BINDING_DESC.setInt(item, 1, binding.binding);
				Struct.RESOURCE_BINDING_DESC.setInt(item, 2, binding.kind);
				Struct.RESOURCE_BINDING_DESC.setInt(item, 3, STAGE_DRAW);
				Struct.RESOURCE_BINDING_DESC.setInt(item, 4, binding.count);
				Struct.RESOURCE_BINDING_DESC.setInt(item, 5, binding.optional ? 1 : 0);
				Struct.RESOURCE_BINDING_DESC.setInt(item, 6, 0);
				layoutBindings.add(item);
			}
			MemorySegment item = Struct.RESOURCE_LAYOUT_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.RESOURCE_LAYOUT_DESC.offset(0), Struct.RESOURCE_LAYOUT_DESC.byteSize());
			Struct.RESOURCE_LAYOUT_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.RESOURCE_LAYOUT_DESC, 2, label);
			writeRange(item, Struct.RESOURCE_LAYOUT_DESC, 3, start, bindings.length);
			resourceLayouts.add(item);
			return this;
		}

		public ResourceBatchBuilder resourceSet(long id, String label, long layout, Binding... bindings) {
			long start = setBindings.size();
			for (Binding binding : bindings) {
				MemorySegment item = Struct.RESOURCE_BINDING.allocate(arena);
				item.set(ValueLayout.JAVA_INT, Struct.RESOURCE_BINDING.offset(0), Struct.RESOURCE_BINDING.byteSize());
				Struct.RESOURCE_BINDING.setInt(item, 1, binding.binding);
				Struct.RESOURCE_BINDING.setInt(item, 2, binding.arrayIndex);
				Struct.RESOURCE_BINDING.setLong(item, 3, binding.resource);
				Struct.RESOURCE_BINDING.setInt(item, 4, binding.kind);
				Struct.RESOURCE_BINDING.setInt(item, 5, ACCESS_READ);
				writeRange(item, Struct.RESOURCE_BINDING, 6, 0, 0);
				setBindings.add(item);
			}
			MemorySegment item = Struct.RESOURCE_SET_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.RESOURCE_SET_DESC.offset(0), Struct.RESOURCE_SET_DESC.byteSize());
			Struct.RESOURCE_SET_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.RESOURCE_SET_DESC, 2, label);
			Struct.RESOURCE_SET_DESC.setLong(item, 3, layout);
			writeRange(item, Struct.RESOURCE_SET_DESC, 4, start, bindings.length);
			resourceSets.add(item);
			return this;
		}

		public ResourceBatchBuilder pipelineLayout(long id, String label, long resourceLayout) {
			long start = pipelineLayoutSets.size();
			pipelineLayoutSets.add(resourceLayout);
			MemorySegment item = Struct.PIPELINE_LAYOUT_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.PIPELINE_LAYOUT_DESC.offset(0), Struct.PIPELINE_LAYOUT_DESC.byteSize());
			Struct.PIPELINE_LAYOUT_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.PIPELINE_LAYOUT_DESC, 2, label);
			writeRange(item, Struct.PIPELINE_LAYOUT_DESC, 3, start, 1);
			pipelineLayouts.add(item);
			return this;
		}

		public ResourceBatchBuilder graphicsPipeline(long id, String label, long layout, long vertex, long fragment) {
			return graphicsPipeline(id, label, layout, vertex, fragment, FORMAT_DEPTH32, COMPARE_LEQUAL);
		}

		public ResourceBatchBuilder graphicsPipelineNoDepth(long id, String label, long layout, long vertex, long fragment) {
			return graphicsPipeline(id, label, layout, vertex, fragment, 0, 0);
		}

		public ResourceBatchBuilder graphicsPipelineInvertBlend(long id, String label, long layout, long vertex, long fragment) {
			return graphicsPipeline(id, label, layout, vertex, fragment, 0, 0, CULL_NONE, BLEND_INVERT);
		}

		public ResourceBatchBuilder graphicsPipelineAlphaBlend(long id, String label, long layout, long vertex, long fragment) {
			return graphicsPipeline(id, label, layout, vertex, fragment, 0, 0, CULL_NONE, BLEND_ALPHA);
		}

		private ResourceBatchBuilder graphicsPipeline(long id, String label, long layout, long vertex, long fragment, int depthFormat, int depthCompare) {
			return graphicsPipeline(id, label, layout, vertex, fragment, depthFormat, depthCompare, CULL_BACK, BLEND_ALPHA);
		}

		private ResourceBatchBuilder graphicsPipeline(
			long id,
			String label,
			long layout,
			long vertex,
			long fragment,
			int depthFormat,
			int depthCompare,
			int cullMode,
			int blendMode
		) {
			long start = passFormats.size();
			passFormats.add(FORMAT_RGBA8);
			MemorySegment item = Struct.GRAPHICS_PIPELINE_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.GRAPHICS_PIPELINE_DESC.offset(0), Struct.GRAPHICS_PIPELINE_DESC.byteSize());
			Struct.GRAPHICS_PIPELINE_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.GRAPHICS_PIPELINE_DESC, 2, label);
			Struct.GRAPHICS_PIPELINE_DESC.setLong(item, 3, layout);
			Struct.GRAPHICS_PIPELINE_DESC.setLong(item, 4, vertex);
			Struct.GRAPHICS_PIPELINE_DESC.setLong(item, 5, fragment);
			Struct.GRAPHICS_PIPELINE_DESC.setInt(item, 6, TOPOLOGY_TRIANGLES);
			Struct.GRAPHICS_PIPELINE_DESC.setInt(item, 7, cullMode);
			Struct.GRAPHICS_PIPELINE_DESC.setInt(item, 8, blendMode);
			Struct.GRAPHICS_PIPELINE_DESC.setInt(item, 9, depthCompare);
			writeRange(item, Struct.GRAPHICS_PIPELINE_DESC, 10, start, 1);
			Struct.GRAPHICS_PIPELINE_DESC.setInt(item, 11, depthFormat);
			graphicsPipelines.add(item);
			return this;
		}

		public ResourceBatchBuilder renderTarget(long id, String label, long colorView, long depthView, int width, int height) {
			long start = targetColorViews.size();
			targetColorViews.add(colorView);
			MemorySegment item = Struct.RENDER_TARGET_DESC.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.RENDER_TARGET_DESC.offset(0), Struct.RENDER_TARGET_DESC.byteSize());
			Struct.RENDER_TARGET_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.RENDER_TARGET_DESC, 2, label);
			writeRange(item, Struct.RENDER_TARGET_DESC, 3, start, 1);
			Struct.RENDER_TARGET_DESC.setLong(item, 4, depthView);
			long extent = Struct.RENDER_TARGET_DESC.offset(5);
			item.set(ValueLayout.JAVA_INT, extent, width);
			item.set(ValueLayout.JAVA_INT, extent + 4, height);
			item.set(ValueLayout.JAVA_INT, extent + 8, 1);
			renderTargets.add(item);
			return this;
		}

			public ResourceBatchBuilder renderPass(long id, String label, long target) {
				return renderPass(id, label, target, FORMAT_DEPTH32);
			}

			public ResourceBatchBuilder frameRenderPass(long id, String label, long frameTarget) {
				return renderPass(id, label, frameTarget, 0);
			}

			private ResourceBatchBuilder renderPass(long id, String label, long target, int depthFormat) {
				long start = passFormats.size();
				passFormats.add(FORMAT_RGBA8);
				MemorySegment item = Struct.RENDER_PASS_DESC.allocate(arena);
				item.set(ValueLayout.JAVA_INT, Struct.RENDER_PASS_DESC.offset(0), Struct.RENDER_PASS_DESC.byteSize());
			Struct.RENDER_PASS_DESC.setLong(item, 1, id);
			Abi.writeBytes(arena, item, Struct.RENDER_PASS_DESC, 2, label);
				Struct.RENDER_PASS_DESC.setLong(item, 3, target);
				writeRange(item, Struct.RENDER_PASS_DESC, 4, start, 1);
				Struct.RENDER_PASS_DESC.setInt(item, 5, depthFormat);
				renderPasses.add(item);
				return this;
			}

			public ResourceBatchBuilder destroy(long handle, int expectedKind) {
				MemorySegment item = Struct.DESTROY_DESC.allocate(arena);
				item.set(ValueLayout.JAVA_INT, Struct.DESTROY_DESC.offset(0), Struct.DESTROY_DESC.byteSize());
				Struct.DESTROY_DESC.setLong(item, 1, handle);
				Struct.DESTROY_DESC.setInt(item, 2, expectedKind);
				destroys.add(item);
				return this;
			}

		public ResourceBatch build() {
			MemorySegment batch = Struct.RESOURCE_BATCH.allocate(arena);
			Abi.writeHeader(batch, Struct.RESOURCE_BATCH);
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 1, copyStructArray(Struct.BUFFER_DESC, buffers), buffers.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 2, copyStructArray(Struct.TEXTURE_DESC, textures), textures.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 3, copyStructArray(Struct.TEXTURE_VIEW_DESC, textureViews), textureViews.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 4, copyStructArray(Struct.SAMPLER_DESC, samplers), samplers.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 5, copyStructArray(Struct.SHADER_DESC, shaders), shaders.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 6, copyStructArray(Struct.RESOURCE_LAYOUT_DESC, resourceLayouts), resourceLayouts.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 7, copyStructArray(Struct.RESOURCE_BINDING_DESC, layoutBindings), layoutBindings.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 8, copyStructArray(Struct.RESOURCE_SET_DESC, resourceSets), resourceSets.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 9, copyStructArray(Struct.RESOURCE_BINDING, setBindings), setBindings.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 10, MemorySegment.NULL, 0);
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 11, copyStructArray(Struct.PIPELINE_LAYOUT_DESC, pipelineLayouts), pipelineLayouts.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 12, copyLongArray(pipelineLayoutSets), pipelineLayoutSets.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 13, copyStructArray(Struct.GRAPHICS_PIPELINE_DESC, graphicsPipelines), graphicsPipelines.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 14, MemorySegment.NULL, 0);
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 15, copyStructArray(Struct.RENDER_TARGET_DESC, renderTargets), renderTargets.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 16, copyLongArray(targetColorViews), targetColorViews.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 17, copyStructArray(Struct.RENDER_PASS_DESC, renderPasses), renderPasses.size());
			Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 18, copyIntArray(passFormats), passFormats.size());
				Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 19, MemorySegment.NULL, 0);
				Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 20, MemorySegment.NULL, 0);
				Abi.writeSlice(batch, Struct.RESOURCE_BATCH, 21, copyStructArray(Struct.DESTROY_DESC, destroys), destroys.size());
				Struct.RESOURCE_BATCH.setLong(batch, 22, features);
				return new ResourceBatch(batch, createCount());
			}

		private int createCount() {
			return buffers.size() + textures.size() + textureViews.size() + samplers.size() + shaders.size()
				+ resourceLayouts.size() + resourceSets.size() + pipelineLayouts.size() + graphicsPipelines.size()
				+ renderTargets.size() + renderPasses.size();
		}

		private MemorySegment copyStructArray(Struct struct, List<MemorySegment> items) {
			MemorySegment array = struct.array(arena, items.size());
			for (int i = 0; i < items.size(); i++) {
				array.asSlice((long)i * struct.byteSize(), struct.byteSize()).copyFrom(items.get(i));
			}
			return array;
		}

		private MemorySegment copyLongArray(List<Long> items) {
			if (items.isEmpty()) {
				return MemorySegment.NULL;
			}
			MemorySegment array = arena.allocate(ValueLayout.JAVA_LONG, items.size());
			for (int i = 0; i < items.size(); i++) {
				array.setAtIndex(ValueLayout.JAVA_LONG, i, items.get(i));
			}
			return array;
		}

		private MemorySegment copyIntArray(List<Integer> items) {
			if (items.isEmpty()) {
				return MemorySegment.NULL;
			}
			MemorySegment array = arena.allocate(ValueLayout.JAVA_INT, items.size());
			for (int i = 0; i < items.size(); i++) {
				array.setAtIndex(ValueLayout.JAVA_INT, i, items.get(i));
			}
			return array;
		}
	}

	public record BindingDesc(int binding, int kind, int count, boolean optional) {
	}

	public record Binding(int binding, int arrayIndex, long resource, int kind) {
	}

	public record ResourceBatch(MemorySegment segment, int createCount) {
	}

	public static final class SubmissionBatchBuilder {
		private final Arena arena;
		private final long features;
		private final String label;
		private final List<MemorySegment> ops = new ArrayList<>();
		private final List<MemorySegment> attachments = new ArrayList<>();
		private final List<MemorySegment> copies = new ArrayList<>();
		private final List<MemorySegment> barriers = new ArrayList<>();

		SubmissionBatchBuilder(Arena arena, long features, String label) {
			this.arena = arena;
			this.features = features;
			this.label = label;
		}

		public SubmissionBatchBuilder hostWrite(long buffer, long offset, byte[] data) {
			MemorySegment op = op(15);
			Struct.COMMAND_OP.setLong(op, 2, buffer);
			Struct.COMMAND_OP.setLong(op, 7, offset);
			Abi.writeBytes(arena, op, Struct.COMMAND_OP, 16, data);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder barrier(long resource, int before, int after, boolean texture) {
			long index = barriers.size();
			MemorySegment barrier = Struct.BARRIER.allocate(arena);
			barrier.set(ValueLayout.JAVA_INT, Struct.BARRIER.offset(0), Struct.BARRIER.byteSize());
			Struct.BARRIER.setLong(barrier, 1, resource);
			Struct.BARRIER.setInt(barrier, 2, texture ? 1 : 0);
			long sub = Struct.BARRIER.offset(3);
			barrier.set(ValueLayout.JAVA_INT, sub, 0);
			barrier.set(ValueLayout.JAVA_INT, sub + 4, 1);
			barrier.set(ValueLayout.JAVA_INT, sub + 8, 0);
			barrier.set(ValueLayout.JAVA_INT, sub + 12, 1);
			Struct.BARRIER.setInt(barrier, 4, before);
			Struct.BARRIER.setInt(barrier, 5, after);
			Struct.BARRIER.setInt(barrier, 6, STAGE_DRAW | 4);
			Struct.BARRIER.setInt(barrier, 7, ACCESS_TRANSFER);
			Struct.BARRIER.setInt(barrier, 8, QUEUE_GRAPHICS);
			Struct.BARRIER.setInt(barrier, 9, QUEUE_GRAPHICS);
			barriers.add(barrier);
			MemorySegment op = op(18);
			writeRange(op, Struct.COMMAND_OP, 15, index, 1);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder largeBarrierBatch(long resource, int count) {
			for (int i = 0; i < count; i++) {
				if ((i & 1) == 0) {
					barrier(resource, USAGE_TRANSFER_SRC, USAGE_TRANSFER_DST, false);
				} else {
					barrier(resource, USAGE_TRANSFER_DST, USAGE_TRANSFER_SRC, false);
				}
			}
			return this;
		}

		public SubmissionBatchBuilder copyBufferToTexture(long buffer, long texture, int width, int height) {
			copyOp(13, buffer, texture, width, height);
			return this;
		}

		public SubmissionBatchBuilder copyTextureToBuffer(long texture, long buffer, int width, int height) {
			copyOp(14, buffer, texture, width, height);
			return this;
		}

		private void copyOp(int kind, long buffer, long texture, int width, int height) {
			long index = copies.size();
			MemorySegment copy = Struct.COPY_REGION.allocate(arena);
			copy.set(ValueLayout.JAVA_INT, Struct.COPY_REGION.offset(0), Struct.COPY_REGION.byteSize());
			Struct.COPY_REGION.setLong(copy, 1, buffer);
			Struct.COPY_REGION.setLong(copy, 2, 0);
			Struct.COPY_REGION.setInt(copy, 3, width * 4);
			Struct.COPY_REGION.setInt(copy, 4, height);
			Struct.COPY_REGION.setLong(copy, 5, texture);
			Struct.COPY_REGION.setInt(copy, 6, 0);
			Struct.COPY_REGION.setInt(copy, 7, 0);
			long origin = Struct.COPY_REGION.offset(8);
			copy.set(ValueLayout.JAVA_INT, origin, 0);
			copy.set(ValueLayout.JAVA_INT, origin + 4, 0);
			copy.set(ValueLayout.JAVA_INT, origin + 8, 0);
			long extent = Struct.COPY_REGION.offset(9);
			copy.set(ValueLayout.JAVA_INT, extent, width);
			copy.set(ValueLayout.JAVA_INT, extent + 4, height);
			copy.set(ValueLayout.JAVA_INT, extent + 8, 1);
			copies.add(copy);
			MemorySegment op = op(kind);
			writeRange(op, Struct.COMMAND_OP, 14, index, 1);
			ops.add(op);
		}

			public SubmissionBatchBuilder beginPass(long pass, long target, long colorView, long depthView) {
				long colorIndex = attachments.size();
				attachments.add(attachment(colorView, LOAD_CLEAR, STORE_STORE, true));
				long depthIndex = attachments.size();
				attachments.add(attachment(depthView, LOAD_CLEAR, STORE_DONT_CARE, false));
			MemorySegment op = op(1);
			Struct.COMMAND_OP.setLong(op, 2, pass);
			Struct.COMMAND_OP.setLong(op, 3, target);
			writeRange(op, Struct.COMMAND_OP, 12, colorIndex, 1);
			writeRange(op, Struct.COMMAND_OP, 13, depthIndex, 1);
				ops.add(op);
				return this;
			}

			public SubmissionBatchBuilder beginFramePass(long pass, long frameTarget) {
				MemorySegment op = op(1);
				Struct.COMMAND_OP.setLong(op, 2, pass);
				Struct.COMMAND_OP.setLong(op, 3, frameTarget);
				writeRange(op, Struct.COMMAND_OP, 12, 0, 0);
				writeRange(op, Struct.COMMAND_OP, 13, 0, 0);
				ops.add(op);
				return this;
			}

		private MemorySegment attachment(long view, int load, int store, boolean clearColor) {
			MemorySegment item = Struct.PASS_ATTACHMENT.allocate(arena);
			item.set(ValueLayout.JAVA_INT, Struct.PASS_ATTACHMENT.offset(0), Struct.PASS_ATTACHMENT.byteSize());
			Struct.PASS_ATTACHMENT.setLong(item, 1, view);
			Struct.PASS_ATTACHMENT.setInt(item, 2, load);
			Struct.PASS_ATTACHMENT.setInt(item, 3, store);
			Struct.PASS_ATTACHMENT.setInt(item, 4, clearColor ? 1 : 0);
			long color = Struct.PASS_ATTACHMENT.offset(5);
			item.set(ValueLayout.JAVA_FLOAT, color, 0.0f);
			item.set(ValueLayout.JAVA_FLOAT, color + 4, 0.0f);
			item.set(ValueLayout.JAVA_FLOAT, color + 8, 0.0f);
			item.set(ValueLayout.JAVA_FLOAT, color + 12, 1.0f);
			return item;
		}

		public SubmissionBatchBuilder bindGraphicsPipeline(long pipeline) {
			MemorySegment op = op(2);
			Struct.COMMAND_OP.setLong(op, 2, pipeline);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder bindResourceSet(long pipelineLayout, long set) {
			MemorySegment op = op(4);
			Struct.COMMAND_OP.setLong(op, 2, pipelineLayout);
			Struct.COMMAND_OP.setLong(op, 3, set);
			Struct.COMMAND_OP.setInt(op, 5, 0);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder setIndexBuffer(long index) {
			MemorySegment op = op(6);
			Struct.COMMAND_OP.setLong(op, 2, index);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder drawIndexed(int indices) {
			return drawIndexed(indices, 1);
		}

		public SubmissionBatchBuilder drawIndexed(int indices, int instances) {
			MemorySegment op = op(8);
			Struct.COMMAND_OP.setInt(op, 9, indices);
			Struct.COMMAND_OP.setInt(op, 10, instances);
			ops.add(op);
			return this;
		}

		public SubmissionBatchBuilder endPass() {
			ops.add(op(19));
			return this;
		}

		public SubmissionBatchBuilder hostRead(long buffer, int size) {
			MemorySegment op = op(16);
			Struct.COMMAND_OP.setLong(op, 2, buffer);
			Struct.COMMAND_OP.setLong(op, 8, size);
			ops.add(op);
			return this;
		}

		private MemorySegment op(int kind) {
			MemorySegment op = Struct.COMMAND_OP.allocate(arena);
			op.set(ValueLayout.JAVA_INT, Struct.COMMAND_OP.offset(0), Struct.COMMAND_OP.byteSize());
			Struct.COMMAND_OP.setInt(op, 1, kind);
			return op;
		}

		public SubmissionBatch build() {
			MemorySegment opArray = Struct.COMMAND_OP.array(arena, ops.size());
			for (int i = 0; i < ops.size(); i++) {
				opArray.asSlice((long)i * Struct.COMMAND_OP.byteSize(), Struct.COMMAND_OP.byteSize()).copyFrom(ops.get(i));
			}
			MemorySegment list = Struct.COMMAND_LIST.allocate(arena);
			list.set(ValueLayout.JAVA_INT, Struct.COMMAND_LIST.offset(0), Struct.COMMAND_LIST.byteSize());
			Abi.writeBytes(arena, list, Struct.COMMAND_LIST, 1, label + ".commands");
			writeRange(list, Struct.COMMAND_LIST, 2, 0, ops.size());
			MemorySegment batch = Struct.SUBMISSION_BATCH.allocate(arena);
			Abi.writeHeader(batch, Struct.SUBMISSION_BATCH);
			Abi.writeBytes(arena, batch, Struct.SUBMISSION_BATCH, 1, label);
			Abi.writeSlice(batch, Struct.SUBMISSION_BATCH, 2, list, 1);
			Abi.writeSlice(batch, Struct.SUBMISSION_BATCH, 3, opArray, ops.size());
			Abi.writeSlice(batch, Struct.SUBMISSION_BATCH, 4, copyStructArray(Struct.PASS_ATTACHMENT, attachments), attachments.size());
			Abi.writeSlice(batch, Struct.SUBMISSION_BATCH, 5, copyStructArray(Struct.COPY_REGION, copies), copies.size());
			Abi.writeSlice(batch, Struct.SUBMISSION_BATCH, 6, copyStructArray(Struct.BARRIER, barriers), barriers.size());
			Struct.SUBMISSION_BATCH.setLong(batch, 7, features);
			return new SubmissionBatch(batch);
		}

		private MemorySegment copyStructArray(Struct struct, List<MemorySegment> items) {
			MemorySegment array = struct.array(arena, items.size());
			for (int i = 0; i < items.size(); i++) {
				array.asSlice((long)i * struct.byteSize(), struct.byteSize()).copyFrom(items.get(i));
			}
			return array;
		}
	}

	public record SubmissionBatch(MemorySegment segment) {
	}

	static void writeRange(MemorySegment segment, Struct struct, int field, long offset, long count) {
		long base = struct.offset(field);
		segment.set(ValueLayout.JAVA_LONG, base, offset);
		segment.set(ValueLayout.JAVA_LONG, base + 8, count);
	}

	public static ByteBuffer directBytes(byte[] data) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
		buffer.put(data);
		buffer.flip();
		return buffer;
	}
}
