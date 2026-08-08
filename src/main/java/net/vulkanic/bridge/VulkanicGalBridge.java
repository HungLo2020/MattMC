package net.vulkanic.bridge;

import net.blaze3d.platform.Window;
import net.minecraft.util.NativeLibraryLoader;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWNativeX11;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class VulkanicGalBridge implements AutoCloseable {
	public static final int ABI_VERSION = 11;
	public static final int STATUS_OK = 0;

	public static final int BACKEND_VULKAN = 1;
	public static final int BACKEND_OPENGL = 2;
	public static final int WINDOW_PLATFORM_X11 = 1;
	public static final int WINDOW_PLATFORM_WAYLAND = 2;

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
	public static final int INDEX_U16 = 1;
	public static final int INDEX_U32 = 2;
	public static final int PRESENT_IMMEDIATE = 1;
	public static final int PRESENT_MAILBOX = 2;
	public static final int PRESENT_FIFO = 3;
	public static final int PRESENT_AUTO_VSYNC = 4;
	public static final int PRESENT_AUTO_NO_VSYNC = 5;
	public static final int PRESENT_FIFO_RELAXED = 6;
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

	public static boolean isBorrowedOpenGlContextCurrent(Window window) {
		long currentContext = GLFW.glfwGetCurrentContext();
		return currentContext != 0L && currentContext == window.handle();
	}

	public static VulkanicGalBridge createBorrowedOpenGl(Window window) {
		return createBorrowedOpenGl(window.handle());
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

	public static VulkanicGalBridge createWindowedVulkan(Window window, int width, int height) {
		return createWindowedVulkan(window, width, height, PRESENT_AUTO_NO_VSYNC);
	}

	public static VulkanicGalBridge createWindowedVulkan(Window window, int width, int height, int presentMode) {
		NativeWindowInfo windowInfo = nativeWindowInfo(window);
		return createWindowedVulkan(
			window.handle(),
			windowInfo.platform,
			windowInfo.nativeDisplay,
			windowInfo.nativeWindow,
			width,
			height,
			presentMode
		);
	}

	public static VulkanicGalBridge createWindowedVulkan(
		long stableWindowId,
		int windowPlatform,
		long nativeDisplay,
		long nativeWindow,
		int width,
		int height,
		int presentMode
	) {
		if (stableWindowId == 0L || nativeDisplay == 0L || nativeWindow == 0L) {
			throw new IllegalArgumentException("windowed Vulkan context requires non-zero window handles");
		}
		Arena arena = Arena.ofConfined();
		try {
			MemorySegment request = Struct.WINDOWED_VULKAN_CONTEXT_CREATE.allocate(arena);
			Abi.writeHeader(request, Struct.WINDOWED_VULKAN_CONTEXT_CREATE);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setInt(request, 1, windowPlatform);
			boolean rustTracy = Boolean.getBoolean("mattmc.dev.tracyCapture") || Boolean.getBoolean("mattmc.dev.rustGalVulkanWholeFrame.tracy");
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setInt(request, 2, rustTracy ? 1 : 0);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setLong(request, 3, stableWindowId);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setLong(request, 4, nativeDisplay);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setLong(request, 5, nativeWindow);
			Abi.writeBytes(arena, request, Struct.WINDOWED_VULKAN_CONTEXT_CREATE, 6, "java-frame-rust-vulkan-windowed");
			Abi.writeBytes(arena, request, Struct.WINDOWED_VULKAN_CONTEXT_CREATE, 7, "minecraft.rust-vulkan.whole-frame");
			long extent = Struct.WINDOWED_VULKAN_CONTEXT_CREATE.offset(8);
			request.set(ValueLayout.JAVA_INT, extent, width);
			request.set(ValueLayout.JAVA_INT, extent + 4, height);
			request.set(ValueLayout.JAVA_INT, extent + 8, 1);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setInt(request, 9, FORMAT_RGBA8);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setInt(request, 10, presentMode);
			Struct.WINDOWED_VULKAN_CONTEXT_CREATE.setInt(request, 11, 2);
			MemorySegment result = Struct.CONTEXT_RESULT.allocate(arena);
			int status = Native.contextCreateWindowedVulkan(request, result);
			if (status != STATUS_OK) {
				throw new IllegalStateException("Rust VulkanicGAL windowed Vulkan context creation failed: status=" + status + ": " + Native.lastError(0));
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

	private static NativeWindowInfo nativeWindowInfo(Window window) {
		int glfwPlatform = GLFW.glfwGetPlatform();
		if (glfwPlatform == GLFW.GLFW_PLATFORM_X11) {
			return new NativeWindowInfo(
				WINDOW_PLATFORM_X11,
				GLFWNativeX11.glfwGetX11Display(),
				GLFWNativeX11.glfwGetX11Window(window.handle())
			);
		}
		if (glfwPlatform == GLFW.GLFW_PLATFORM_WAYLAND) {
			return new NativeWindowInfo(
				WINDOW_PLATFORM_WAYLAND,
				GLFWNativeWayland.glfwGetWaylandDisplay(),
				GLFWNativeWayland.glfwGetWaylandWindow(window.handle())
			);
		}
		throw new IllegalStateException("Rust Vulkan whole-frame shell only supports GLFW X11/Wayland windows; platform=" + glfwPlatform);
	}

	private record NativeWindowInfo(int platform, long nativeDisplay, long nativeWindow) {
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
		return configureFrame(label, width, height, colorFormat, PRESENT_FIFO);
	}

	public Status configureFrame(String label, int width, int height, int colorFormat, int presentMode) {
		MemorySegment request = Struct.FRAME_SURFACE_CONFIG.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_SURFACE_CONFIG);
		Abi.writeBytes(arena, request, Struct.FRAME_SURFACE_CONFIG, 1, label);
		long extent = Struct.FRAME_SURFACE_CONFIG.offset(2);
		request.set(ValueLayout.JAVA_INT, extent, width);
		request.set(ValueLayout.JAVA_INT, extent + 4, height);
		request.set(ValueLayout.JAVA_INT, extent + 8, 1);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 3, colorFormat);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 4, presentMode);
		Struct.FRAME_SURFACE_CONFIG.setInt(request, 5, 2);
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
		long resultExtent = Struct.FRAME_ACQUIRE_RESULT.offset(8);
		return new AcquiredFrame(
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 3),
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 4),
			Struct.FRAME_ACQUIRE_RESULT.getInt(result, 5),
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 6),
			Struct.FRAME_ACQUIRE_RESULT.getLong(result, 7),
			result.get(ValueLayout.JAVA_INT, resultExtent),
			result.get(ValueLayout.JAVA_INT, resultExtent + 4),
			Struct.FRAME_ACQUIRE_RESULT.getInt(result, 9));
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
			Struct.FRAME_PRESENT_RESULT.getLong(result, 6),
			Struct.FRAME_PRESENT_RESULT.getLong(result, 7));
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

	public WholeFrameSubmitResult submitWholeFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<GuiSpriteRecord> guiSprites
	) {
		return submitWholeFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			guiWidth,
			guiHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldBackground,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			List.of(),
			List.of(),
			guiSprites
		);
	}

	public WholeFrameSubmitResult submitWholeFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<GuiSpriteRecord> guiSprites
	) {
		return submitWholeFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			guiWidth,
			guiHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldBackground,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			List.of(),
			guiSprites
		);
	}

	public WholeFrameSubmitResult submitWholeFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances,
		List<GuiSpriteRecord> guiSprites
	) {
		return submitWholeFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			guiWidth,
			guiHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldBackground,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			worldMeshInstances,
			WorldVoxelVolumeFrameRecord.disabled(),
			guiSprites
		);
	}

	public WholeFrameSubmitResult submitWholeFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances,
		WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		List<GuiSpriteRecord> guiSprites
	) {
		return submitWholeFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			guiWidth,
			guiHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldBackground,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			worldMeshInstances,
			voxelVolumeFrame,
			WorldShaderEnvironmentFrameRecord.disabled(),
			guiSprites
		);
	}

	public WholeFrameSubmitResult submitWholeFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances,
		WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame,
		List<GuiSpriteRecord> guiSprites
	) {
		return submitWorldFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			guiWidth,
			guiHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldBackground,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			worldMeshInstances,
			voxelVolumeFrame,
			shaderEnvironmentFrame,
			guiSprites,
			true
		);
	}

	public WholeFrameSubmitResult submitWorldPrimitives(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads
	) {
		return submitWorldPrimitives(
			generation,
			frameId,
			correlationId,
			frameTarget,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			List.of()
		);
	}

	public WholeFrameSubmitResult submitWorldPrimitives(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads
	) {
		return submitWorldFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			viewportWidth,
			viewportHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			WorldBackgroundRecord.diagnosticFallback(),
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			List.of(),
			WorldVoxelVolumeFrameRecord.disabled(),
			WorldShaderEnvironmentFrameRecord.disabled(),
			List.of(),
			false
		);
	}

	public WholeFrameSubmitResult submitWorldPrimitives(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances
	) {
		return submitWorldFrame(
			generation,
			frameId,
			correlationId,
			frameTarget,
			viewportWidth,
			viewportHeight,
			viewportWidth,
			viewportHeight,
			viewMatrix,
			projectionMatrix,
			WorldBackgroundRecord.diagnosticFallback(),
			worldSegments,
			worldCrackQuads,
			worldBorderQuads,
			worldMaterialQuads,
			worldMeshInstances,
			WorldVoxelVolumeFrameRecord.disabled(),
			WorldShaderEnvironmentFrameRecord.disabled(),
			List.of(),
			false
		);
	}

	private WholeFrameSubmitResult submitWorldFrame(
		long generation,
		long frameId,
		long correlationId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments,
		List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads,
		List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances,
		WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame,
		List<GuiSpriteRecord> guiSprites,
		boolean wholeFrame
	) {
		Objects.requireNonNull(viewMatrix, "viewMatrix");
		Objects.requireNonNull(projectionMatrix, "projectionMatrix");
		Objects.requireNonNull(worldBackground, "worldBackground");
		Objects.requireNonNull(worldSegments, "worldSegments");
		Objects.requireNonNull(worldCrackQuads, "worldCrackQuads");
		Objects.requireNonNull(worldBorderQuads, "worldBorderQuads");
		Objects.requireNonNull(worldMaterialQuads, "worldMaterialQuads");
		Objects.requireNonNull(worldMeshInstances, "worldMeshInstances");
		Objects.requireNonNull(voxelVolumeFrame, "voxelVolumeFrame");
		Objects.requireNonNull(shaderEnvironmentFrame, "shaderEnvironmentFrame");
		Objects.requireNonNull(guiSprites, "guiSprites");
		if (viewMatrix.length != 16 || projectionMatrix.length != 16) {
			throw new IllegalArgumentException("whole-frame matrices must contain 16 floats");
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.java-record-packing");
		MemorySegment segmentArray = Struct.WORLD_LINE_SEGMENT_REQUEST.array(arena, worldSegments.size());
		for (int i = 0; i < worldSegments.size(); i++) {
			WorldLineSegmentRecord segment = worldSegments.get(i);
			MemorySegment item = Abi.item(segmentArray, Struct.WORLD_LINE_SEGMENT_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(0), Struct.WORLD_LINE_SEGMENT_REQUEST.byteSize());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 1, segment.stratum());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 2, segment.style());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 3, segment.depthPolicy());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 4, segment.colorArgb());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(5), segment.lineWidth());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(6), segment.startX());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(7), segment.startY());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(8), segment.startZ());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(9), segment.endX());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(10), segment.endY());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_LINE_SEGMENT_REQUEST.offset(11), segment.endZ());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 12, segment.viewportWidth());
			Struct.WORLD_LINE_SEGMENT_REQUEST.setInt(item, 13, segment.viewportHeight());
		}
		MemorySegment crackArray = Struct.WORLD_CRACK_QUAD_REQUEST.array(arena, worldCrackQuads.size());
		for (int i = 0; i < worldCrackQuads.size(); i++) {
			WorldCrackQuadRecord quad = worldCrackQuads.get(i);
			MemorySegment item = Abi.item(crackArray, Struct.WORLD_CRACK_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_CRACK_QUAD_REQUEST.offset(0), Struct.WORLD_CRACK_QUAD_REQUEST.byteSize());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 1, quad.stratum());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 2, quad.stage());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 3, quad.depthPolicy());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 4, quad.blendPolicy());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 5, quad.cullPolicy());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 6, quad.colorArgb());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 7, 0);
			float[] vertices = quad.vertices();
			for (int field = 0; field < 12; field++) {
				item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_CRACK_QUAD_REQUEST.offset(8 + field), vertices[field]);
			}
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 20, quad.viewportWidth());
			Struct.WORLD_CRACK_QUAD_REQUEST.setInt(item, 21, quad.viewportHeight());
		}
		MemorySegment borderArray = Struct.WORLD_BORDER_QUAD_REQUEST.array(arena, worldBorderQuads.size());
		for (int i = 0; i < worldBorderQuads.size(); i++) {
			WorldBorderQuadRecord quad = worldBorderQuads.get(i);
			MemorySegment item = Abi.item(borderArray, Struct.WORLD_BORDER_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(0), Struct.WORLD_BORDER_QUAD_REQUEST.byteSize());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 1, quad.stratum());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 2, quad.textureId());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 3, quad.depthPolicy());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 4, quad.blendPolicy());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 5, quad.cullPolicy());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 6, quad.colorArgb());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 7, 0);
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(8), quad.borderSize());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(9), quad.distanceToBorder());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(10), quad.scrollU());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(11), quad.scrollV());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(12), quad.uvU());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(13), quad.uvV());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(14), quad.uvWidth());
			item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(15), quad.uvHeight());
			float[] vertices = quad.vertices();
			for (int field = 0; field < 12; field++) {
				item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(16 + field), vertices[field]);
			}
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 28, quad.viewportWidth());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 29, quad.viewportHeight());
		}
		MemorySegment materialArray = MemorySegment.NULL;
		LinkedHashMap<WorldMaterialKeyRecord, Integer> materialTable = new LinkedHashMap<>();
		int[] materialIndexes = new int[worldMaterialQuads.size()];
		for (int i = 0; i < worldMaterialQuads.size(); i++) {
			WorldMaterialQuadRecord quad = worldMaterialQuads.get(i);
			WorldMaterialKeyRecord key = WorldMaterialKeyRecord.from(quad);
			Integer index = materialTable.get(key);
			if (index == null) {
				index = materialTable.size();
				materialTable.put(key, index);
			}
			materialIndexes[i] = index;
		}
		MemorySegment materialTableArray = Struct.WORLD_MATERIAL_TABLE_RECORD.array(arena, materialTable.size());
		int materialTableIndex = 0;
		for (Map.Entry<WorldMaterialKeyRecord, Integer> entry : materialTable.entrySet()) {
			WorldMaterialKeyRecord key = entry.getKey();
			MemorySegment item = Abi.item(materialTableArray, Struct.WORLD_MATERIAL_TABLE_RECORD, materialTableIndex++);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_TABLE_RECORD.offset(0), Struct.WORLD_MATERIAL_TABLE_RECORD.byteSize());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 1, key.stratum());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 2, key.materialId());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 3, key.textureId());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 4, key.materialMode());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 5, key.depthPolicy());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 6, key.cullPolicy());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 7, key.topology());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 8, key.winding());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 9, 0);
		}
		MemorySegment compactMaterialArray = Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.array(arena, worldMaterialQuads.size());
		for (int i = 0; i < worldMaterialQuads.size(); i++) {
			WorldMaterialQuadRecord quad = worldMaterialQuads.get(i);
			MemorySegment item = Abi.item(compactMaterialArray, Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.offset(0), Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.byteSize());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 1, materialIndexes[i]);
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 2, quad.colorArgb());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 3, 0);
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 4, quad.p0X());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 5, quad.p0Y());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 6, quad.p0Z());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 7, quad.p1X());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 8, quad.p1Y());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 9, quad.p1Z());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 10, quad.p2X());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 11, quad.p2Y());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 12, quad.p2Z());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 13, quad.p3X());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 14, quad.p3Y());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 15, quad.p3Z());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 16, quad.uv0U());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 17, quad.uv0V());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 18, quad.uv1U());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 19, quad.uv1V());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 20, quad.uv2U());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 21, quad.uv2V());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 22, quad.uv3U());
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setFloat(item, 23, quad.uv3V());
		}
		MemorySegment meshInstanceArray = Struct.WORLD_MESH_INSTANCE_RECORD.array(arena, worldMeshInstances.size());
		for (int i = 0; i < worldMeshInstances.size(); i++) {
			WorldMeshInstanceRecord instance = worldMeshInstances.get(i);
			MemorySegment item = Abi.item(meshInstanceArray, Struct.WORLD_MESH_INSTANCE_RECORD, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_INSTANCE_RECORD.offset(0), Struct.WORLD_MESH_INSTANCE_RECORD.byteSize());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 1, instance.stratum());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 2, instance.meshSectionIndex());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 3, instance.depthPolicy());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 4, instance.cullPolicy());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 5, instance.winding());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 6, instance.colorArgb());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 7, instance.viewportWidth());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 8, instance.viewportHeight());
			Struct.WORLD_MESH_INSTANCE_RECORD.setLong(item, 9, instance.meshKey());
			Struct.WORLD_MESH_INSTANCE_RECORD.setLong(item, 10, instance.meshGeneration());
			long transformOffset = Struct.WORLD_MESH_INSTANCE_RECORD.offset(11);
			float[] transform = instance.transform();
			for (int field = 0; field < 16; field++) {
				item.set(ValueLayout.JAVA_FLOAT, transformOffset + field * 4L, transform[field]);
			}
		}
		MemorySegment spriteArray = Struct.GUI_SPRITE_REQUEST.array(arena, guiSprites.size());
		for (int i = 0; i < guiSprites.size(); i++) {
			GuiSpriteRecord sprite = guiSprites.get(i);
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
		MemorySegment request = Struct.WHOLE_FRAME_SUBMIT.allocate(arena);
		Abi.writeHeader(request, Struct.WHOLE_FRAME_SUBMIT);
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 1, generation);
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 2, frameId);
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 3, correlationId);
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 4, frameTarget);
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 5, guiWidth);
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 6, guiHeight);
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 7, viewportWidth);
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 8, viewportHeight);
		long viewOffset = Struct.WHOLE_FRAME_SUBMIT.offset(9);
		long projectionOffset = Struct.WHOLE_FRAME_SUBMIT.offset(10);
		for (int i = 0; i < 16; i++) {
			request.set(ValueLayout.JAVA_FLOAT, viewOffset + i * 4L, viewMatrix[i]);
			request.set(ValueLayout.JAVA_FLOAT, projectionOffset + i * 4L, projectionMatrix[i]);
		}
		MemorySegment background = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(11),
			Struct.WORLD_BACKGROUND_REQUEST.byteSize()
		);
		background.set(ValueLayout.JAVA_INT, Struct.WORLD_BACKGROUND_REQUEST.offset(0), Struct.WORLD_BACKGROUND_REQUEST.byteSize());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 1, worldBackground.enabled() ? 1 : 0);
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 2, worldBackground.skyType());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 3, worldBackground.loadIntent());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 4, worldBackground.storeIntent());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 5, worldBackground.colorArgb());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 6, worldBackground.viewportWidth());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 7, worldBackground.viewportHeight());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 12, segmentArray, worldSegments.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 13, crackArray, worldCrackQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 14, borderArray, worldBorderQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 15, materialArray, 0);
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 16, materialTableArray, materialTable.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 17, compactMaterialArray, worldMaterialQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 18, meshInstanceArray, worldMeshInstances.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 19, spriteArray, guiSprites.size());
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 20, negotiatedFeatures);
		MemorySegment voxelVolume = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(21),
			Struct.WORLD_VOXEL_VOLUME_FRAME.byteSize()
		);
		voxelVolume.set(ValueLayout.JAVA_INT, Struct.WORLD_VOXEL_VOLUME_FRAME.offset(0), Struct.WORLD_VOXEL_VOLUME_FRAME.byteSize());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setInt(voxelVolume, 1, voxelVolumeFrame.enabled() ? 1 : 0);
		Struct.WORLD_VOXEL_VOLUME_FRAME.setInt(voxelVolume, 2, 0);
		Struct.WORLD_VOXEL_VOLUME_FRAME.setInt(voxelVolume, 3, 0);
		Struct.WORLD_VOXEL_VOLUME_FRAME.setLong(voxelVolume, 4, voxelVolumeFrame.worldGeneration());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setLong(voxelVolume, 5, voxelVolumeFrame.resourceGeneration());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setFloat(voxelVolume, 6, voxelVolumeFrame.cameraX());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setFloat(voxelVolume, 7, voxelVolumeFrame.cameraY());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setFloat(voxelVolume, 8, voxelVolumeFrame.cameraZ());
		Struct.WORLD_VOXEL_VOLUME_FRAME.setInt(voxelVolume, 9, 0);
		MemorySegment shaderEnvironment = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(22),
			Struct.WORLD_SHADER_ENVIRONMENT_FRAME.byteSize()
		);
		shaderEnvironment.set(ValueLayout.JAVA_INT, Struct.WORLD_SHADER_ENVIRONMENT_FRAME.offset(0), Struct.WORLD_SHADER_ENVIRONMENT_FRAME.byteSize());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 1, shaderEnvironmentFrame.enabled() ? 1 : 0);
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 2, shaderEnvironmentFrame.frameCounter());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 3, shaderEnvironmentFrame.worldDay());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setLong(shaderEnvironment, 4, shaderEnvironmentFrame.worldGeneration());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setLong(shaderEnvironment, 5, shaderEnvironmentFrame.worldTime());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 6, shaderEnvironmentFrame.frameTimeSeconds());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 7, shaderEnvironmentFrame.frameTimeCounter());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 8, shaderEnvironmentFrame.timeOfDay());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 9, shaderEnvironmentFrame.rainStrength());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 10, shaderEnvironmentFrame.thunderStrength());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 11, shaderEnvironmentFrame.skyDarken());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 12, shaderEnvironmentFrame.moonPhase());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 13, shaderEnvironmentFrame.eyeSubmersion());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 14, shaderEnvironmentFrame.screenBrightness());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 15, shaderEnvironmentFrame.farPlane());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 16, shaderEnvironmentFrame.relativeEyeX());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 17, shaderEnvironmentFrame.relativeEyeY());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 18, shaderEnvironmentFrame.relativeEyeZ());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 19, shaderEnvironmentFrame.skyColorRed());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 20, shaderEnvironmentFrame.skyColorGreen());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 21, shaderEnvironmentFrame.skyColorBlue());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 22, shaderEnvironmentFrame.darknessLightFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 23, shaderEnvironmentFrame.nightVision());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 24, shaderEnvironmentFrame.fogColorRed());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 25, shaderEnvironmentFrame.fogColorGreen());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 26, shaderEnvironmentFrame.fogColorBlue());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 27, shaderEnvironmentFrame.biomePrecipitation());
		Abi.writeBytes(arena, shaderEnvironment, Struct.WORLD_SHADER_ENVIRONMENT_FRAME, 28, shaderEnvironmentFrame.biomeResourceLocation());
		Abi.writeBytes(arena, shaderEnvironment, Struct.WORLD_SHADER_ENVIRONMENT_FRAME, 29, shaderEnvironmentFrame.mainHandItemModelResourceLocation());
		Abi.writeBytes(arena, shaderEnvironment, Struct.WORLD_SHADER_ENVIRONMENT_FRAME, 30, shaderEnvironmentFrame.offHandItemModelResourceLocation());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 31, shaderEnvironmentFrame.mainHandItemLightEmission());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 32, shaderEnvironmentFrame.offHandItemLightEmission());
		MemorySegment result = Struct.WHOLE_FRAME_SUBMIT_RESULT.allocate(arena);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.java-record-packing");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.native-submit-return");
		int status = wholeFrame
			? Native.wholeFrameSubmit(contextId, request, result)
			: Native.worldPrimitivesSubmit(contextId, request, result);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.native-submit-return");
		checkStatus(status, wholeFrame ? "whole-frame submission" : "world primitive submission");
		long metricsOffset = Struct.WHOLE_FRAME_SUBMIT_RESULT.offset(44);
		long profileOffset = Struct.WHOLE_FRAME_SUBMIT_RESULT.offset(45);
		BackendMetrics metrics = backendMetricsAt(result, metricsOffset);
		WholeFrameProfile profile = wholeFrameProfileAt(result, profileOffset);
		long ffiCalls = result.get(ValueLayout.JAVA_LONG, metricsOffset + 64);
		long ffiInputBytes = result.get(ValueLayout.JAVA_LONG, metricsOffset + 72);
		return new WholeFrameSubmitResult(
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 3),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 4),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 5),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 6),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 7),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 8),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 9),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 10),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 11),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 12),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 13),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 14),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 15),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 16),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 17),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 18),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 19),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 20),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 21),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 22),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 23),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 24),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 25),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 26),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 27),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 28),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 29),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 30),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 31),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 32),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 33),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 34),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 35),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 36),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 37),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 38),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 39),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 40),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 41),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 42),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 43),
			ffiCalls,
			ffiInputBytes,
			metrics,
			profile
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

	public Status updateWorldBorderAsset(long generation, WorldBorderAssetRecord asset) {
		Objects.requireNonNull(asset, "asset");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment request = Struct.WORLD_BORDER_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_BORDER_ASSET_UPDATE);
			Struct.WORLD_BORDER_ASSET_UPDATE.setLong(request, 1, generation);
			Struct.WORLD_BORDER_ASSET_UPDATE.setInt(request, 2, asset.textureId());
			Struct.WORLD_BORDER_ASSET_UPDATE.setInt(request, 3, 0);
			Abi.writeBytes(updateArena, request, Struct.WORLD_BORDER_ASSET_UPDATE, 4, asset.pngBytes());
			Struct.WORLD_BORDER_ASSET_UPDATE.setLong(request, 5, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldBorderUpdateAsset(contextId, request, status), "world-border asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public Status updateWorldCrackAssets(long generation, List<WorldCrackAssetRecord> assets) {
		Objects.requireNonNull(assets, "assets");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment assetArray = Struct.WORLD_CRACK_ASSET_PAYLOAD.array(updateArena, assets.size());
			for (int i = 0; i < assets.size(); i++) {
				WorldCrackAssetRecord asset = assets.get(i);
				MemorySegment item = Abi.item(assetArray, Struct.WORLD_CRACK_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_CRACK_ASSET_PAYLOAD.offset(0), Struct.WORLD_CRACK_ASSET_PAYLOAD.byteSize());
				Struct.WORLD_CRACK_ASSET_PAYLOAD.setInt(item, 1, asset.stage());
				Abi.writeBytes(updateArena, item, Struct.WORLD_CRACK_ASSET_PAYLOAD, 2, asset.pngBytes());
			}
			MemorySegment request = Struct.WORLD_CRACK_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_CRACK_ASSET_UPDATE);
			Struct.WORLD_CRACK_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_CRACK_ASSET_UPDATE, 2, assetArray, assets.size());
			Struct.WORLD_CRACK_ASSET_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldCrackUpdateAssets(contextId, request, status), "world crack asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public Status updateWorldMaterialAssets(long generation, List<WorldMaterialAssetRecord> assets) {
		Objects.requireNonNull(assets, "assets");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment assetArray = Struct.WORLD_MATERIAL_ASSET_PAYLOAD.array(updateArena, assets.size());
			for (int i = 0; i < assets.size(); i++) {
				WorldMaterialAssetRecord asset = assets.get(i);
				MemorySegment item = Abi.item(assetArray, Struct.WORLD_MATERIAL_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_ASSET_PAYLOAD.offset(0), Struct.WORLD_MATERIAL_ASSET_PAYLOAD.byteSize());
				Struct.WORLD_MATERIAL_ASSET_PAYLOAD.setInt(item, 1, asset.textureId());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MATERIAL_ASSET_PAYLOAD, 2, asset.pngBytes());
			}
			MemorySegment request = Struct.WORLD_MATERIAL_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_MATERIAL_ASSET_UPDATE);
			Struct.WORLD_MATERIAL_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_MATERIAL_ASSET_UPDATE, 2, assetArray, assets.size());
			Struct.WORLD_MATERIAL_ASSET_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldMaterialUpdateAssets(contextId, request, status), "world material asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/**
	 * Copies one complete shader-pack source generation into Rust-owned storage.
	 * This is semantic source transport only: it neither selects nor executes a
	 * pack and intentionally carries no Iris/OpenGL/Vulkan runtime objects.
	 */
	public Status updateShaderPackSources(long generation, String packName, List<ShaderPackSourceFileRecord> files) {
		Objects.requireNonNull(packName, "packName");
		Objects.requireNonNull(files, "files");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment fileArray = Struct.SHADER_PACK_SOURCE_FILE.array(updateArena, files.size());
			for (int i = 0; i < files.size(); i++) {
				ShaderPackSourceFileRecord file = Objects.requireNonNull(files.get(i), "files[" + i + "]");
				MemorySegment item = Abi.item(fileArray, Struct.SHADER_PACK_SOURCE_FILE, i);
				item.set(ValueLayout.JAVA_INT, Struct.SHADER_PACK_SOURCE_FILE.offset(0), Struct.SHADER_PACK_SOURCE_FILE.byteSize());
				Struct.SHADER_PACK_SOURCE_FILE.setInt(item, 1, 0);
				Abi.writeBytes(updateArena, item, Struct.SHADER_PACK_SOURCE_FILE, 2, file.path());
				Abi.writeBytes(updateArena, item, Struct.SHADER_PACK_SOURCE_FILE, 3, file.contentsUtf8());
			}
			MemorySegment request = Struct.SHADER_PACK_SOURCE_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.SHADER_PACK_SOURCE_UPDATE);
			Struct.SHADER_PACK_SOURCE_UPDATE.setLong(request, 1, generation);
			Abi.writeBytes(updateArena, request, Struct.SHADER_PACK_SOURCE_UPDATE, 2, packName);
			Abi.writeSlice(request, Struct.SHADER_PACK_SOURCE_UPDATE, 3, fileArray, files.size());
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.shaderPackUpdateSources(contextId, request, status), "shader-pack source update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/**
	 * Copies one complete binary shader-pack asset generation into Rust-owned
	 * storage. This is pack data transport only: it neither decodes assets nor
	 * selects a shader route, and it carries no renderer or backend objects.
	 */
	public Status updateShaderPackAssets(long generation, String packName, List<ShaderPackAssetFileRecord> files) {
		Objects.requireNonNull(packName, "packName");
		Objects.requireNonNull(files, "files");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment fileArray = Struct.SHADER_PACK_ASSET_FILE.array(updateArena, files.size());
			for (int i = 0; i < files.size(); i++) {
				ShaderPackAssetFileRecord file = Objects.requireNonNull(files.get(i), "files[" + i + "]");
				MemorySegment item = Abi.item(fileArray, Struct.SHADER_PACK_ASSET_FILE, i);
				item.set(ValueLayout.JAVA_INT, Struct.SHADER_PACK_ASSET_FILE.offset(0), Struct.SHADER_PACK_ASSET_FILE.byteSize());
				Struct.SHADER_PACK_ASSET_FILE.setInt(item, 1, 0);
				Abi.writeBytes(updateArena, item, Struct.SHADER_PACK_ASSET_FILE, 2, file.path());
				Abi.writeBytes(updateArena, item, Struct.SHADER_PACK_ASSET_FILE, 3, file.contents());
			}
			MemorySegment request = Struct.SHADER_PACK_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.SHADER_PACK_ASSET_UPDATE);
			Struct.SHADER_PACK_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeBytes(updateArena, request, Struct.SHADER_PACK_ASSET_UPDATE, 2, packName);
			Abi.writeSlice(request, Struct.SHADER_PACK_ASSET_UPDATE, 3, fileArray, files.size());
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.shaderPackUpdateAssets(contextId, request, status), "shader-pack asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public Status updateWorldMeshAssets(
		long generation,
		List<WorldMeshAssetRecord> meshes,
		List<WorldMeshTextureAssetRecord> textures,
		List<WorldMeshSortedIndexRecord> sortedIndices
	) {
		Objects.requireNonNull(meshes, "meshes");
		Objects.requireNonNull(textures, "textures");
		Objects.requireNonNull(sortedIndices, "sortedIndices");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment textureArray = Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.array(updateArena, textures.size());
			for (int i = 0; i < textures.size(); i++) {
				WorldMeshTextureAssetRecord texture = textures.get(i);
				MemorySegment item = Abi.item(textureArray, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.offset(0), Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.byteSize());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 1, texture.textureId());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, 2, texture.pngBytes());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 3, texture.frameWidth());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 4, texture.frameHeight());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 5, texture.frameCount());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 6, texture.frameTicks());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 7, texture.animationFlags());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 8, texture.frameRowSize());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 9, texture.interpolationPolicy());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 10, 0);
				MemorySegment animationFrameArray = Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.array(updateArena, texture.animationFrames().size());
				for (int frameIndex = 0; frameIndex < texture.animationFrames().size(); frameIndex++) {
					WorldMeshAnimationFrameRecord frame = texture.animationFrames().get(frameIndex);
					MemorySegment frameItem = Abi.item(animationFrameArray, Struct.WORLD_MESH_ANIMATION_FRAME_RECORD, frameIndex);
					frameItem.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.offset(0), Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.byteSize());
					Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 1, frame.frameIndex());
					Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 2, frame.durationTicks());
					Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 3, 0);
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, 11, animationFrameArray, texture.animationFrames().size());
			}
			MemorySegment meshArray = Struct.WORLD_MESH_ASSET_RECORD.array(updateArena, meshes.size());
			for (int i = 0; i < meshes.size(); i++) {
				WorldMeshAssetRecord mesh = meshes.get(i);
				MemorySegment item = Abi.item(meshArray, Struct.WORLD_MESH_ASSET_RECORD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_ASSET_RECORD.offset(0), Struct.WORLD_MESH_ASSET_RECORD.byteSize());
				Struct.WORLD_MESH_ASSET_RECORD.setInt(item, 1, mesh.vertexLayoutVersion());
				Struct.WORLD_MESH_ASSET_RECORD.setInt(item, 2, mesh.indexType());
				Struct.WORLD_MESH_ASSET_RECORD.setInt(item, 3, 0);
				Struct.WORLD_MESH_ASSET_RECORD.setLong(item, 4, mesh.meshKey());
				Struct.WORLD_MESH_ASSET_RECORD.setLong(item, 5, mesh.meshGeneration());
				MemorySegment vertexArray = Struct.WORLD_MESH_VERTEX.array(updateArena, mesh.vertices().size());
				for (int vertexIndex = 0; vertexIndex < mesh.vertices().size(); vertexIndex++) {
					WorldMeshVertexRecord vertex = mesh.vertices().get(vertexIndex);
					MemorySegment vertexItem = Abi.item(vertexArray, Struct.WORLD_MESH_VERTEX, vertexIndex);
					vertexItem.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_VERTEX.offset(0), Struct.WORLD_MESH_VERTEX.byteSize());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 1, vertex.colorArgb());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 2, vertex.normalPacked());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 3, vertex.light());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 4, vertex.x());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 5, vertex.y());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 6, vertex.z());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 7, vertex.u());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 8, vertex.v());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 9, vertex.atlasU());
					Struct.WORLD_MESH_VERTEX.setFloat(vertexItem, 10, vertex.atlasV());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 11, vertex.shaderBlockId());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 12, vertex.shaderMaterialType());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 13, vertex.midBlockPacked());
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_ASSET_RECORD, 6, vertexArray, mesh.vertices().size());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_ASSET_RECORD, 7, mesh.indexBytes());
				MemorySegment sectionArray = Struct.WORLD_MESH_SECTION_RECORD.array(updateArena, mesh.sections().size());
				for (int sectionIndex = 0; sectionIndex < mesh.sections().size(); sectionIndex++) {
					WorldMeshSectionRecord section = mesh.sections().get(sectionIndex);
					MemorySegment sectionItem = Abi.item(sectionArray, Struct.WORLD_MESH_SECTION_RECORD, sectionIndex);
					sectionItem.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_SECTION_RECORD.offset(0), Struct.WORLD_MESH_SECTION_RECORD.byteSize());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 1, section.materialId());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 2, section.textureId());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 3, section.materialMode());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 4, section.cullPolicy());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 5, section.winding());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 6, section.indexOffset());
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 7, section.indexCount());
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_ASSET_RECORD, 8, sectionArray, mesh.sections().size());
			}
			MemorySegment sortedIndexArray = Struct.WORLD_MESH_SORTED_INDEX_RECORD.array(updateArena, sortedIndices.size());
			for (int i = 0; i < sortedIndices.size(); i++) {
				WorldMeshSortedIndexRecord sortedIndex = sortedIndices.get(i);
				MemorySegment item = Abi.item(sortedIndexArray, Struct.WORLD_MESH_SORTED_INDEX_RECORD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_SORTED_INDEX_RECORD.offset(0), Struct.WORLD_MESH_SORTED_INDEX_RECORD.byteSize());
				Struct.WORLD_MESH_SORTED_INDEX_RECORD.setInt(item, 1, sortedIndex.indexType());
				Struct.WORLD_MESH_SORTED_INDEX_RECORD.setInt(item, 2, 0);
				Struct.WORLD_MESH_SORTED_INDEX_RECORD.setLong(item, 3, sortedIndex.meshKey());
				Struct.WORLD_MESH_SORTED_INDEX_RECORD.setLong(item, 4, sortedIndex.meshGeneration());
				Struct.WORLD_MESH_SORTED_INDEX_RECORD.setLong(item, 5, sortedIndex.indexGeneration());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_SORTED_INDEX_RECORD, 6, sortedIndex.indexBytes());
			}
			MemorySegment request = Struct.WORLD_MESH_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_MESH_ASSET_UPDATE);
			Struct.WORLD_MESH_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 2, meshArray, meshes.size());
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 3, textureArray, textures.size());
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 4, sortedIndexArray, sortedIndices.size());
			Struct.WORLD_MESH_ASSET_UPDATE.setLong(request, 5, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldMeshUpdateAssets(contextId, request, status), "world mesh asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public record GuiAssetRecord(int spriteId, byte[] pngBytes) {
		public GuiAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
		}
	}

	public record WorldBorderAssetRecord(int textureId, byte[] pngBytes) {
		public WorldBorderAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
		}
	}

	public record WorldCrackAssetRecord(int stage, byte[] pngBytes) {
		public WorldCrackAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
		}
	}

	public record WorldMaterialAssetRecord(int textureId, byte[] pngBytes) {
		public WorldMaterialAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
		}
	}

	public record ShaderPackSourceFileRecord(String path, byte[] contentsUtf8) {
		public ShaderPackSourceFileRecord {
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(contentsUtf8, "contentsUtf8");
		}
	}

	public record ShaderPackAssetFileRecord(String path, byte[] contents) {
		public ShaderPackAssetFileRecord {
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(contents, "contents");
		}
	}

	public record WorldMeshTextureAssetRecord(
		int textureId,
		byte[] pngBytes,
		int frameWidth,
		int frameHeight,
		int frameCount,
		int frameTicks,
		int animationFlags,
		int frameRowSize,
		int interpolationPolicy,
		List<WorldMeshAnimationFrameRecord> animationFrames
	) {
		public WorldMeshTextureAssetRecord(int textureId, byte[] pngBytes) {
			this(textureId, pngBytes, 0, 0, 1, 1, 0, 0, 0, List.of());
		}

		public WorldMeshTextureAssetRecord(
			int textureId,
			byte[] pngBytes,
			int frameWidth,
			int frameHeight,
			int frameCount,
			int frameTicks,
			int animationFlags
		) {
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags, 0, 0, List.of());
		}

		public WorldMeshTextureAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
			Objects.requireNonNull(animationFrames, "animationFrames");
			pngBytes = pngBytes.clone();
			animationFrames = List.copyOf(animationFrames);
			if (frameWidth < 0 || frameHeight < 0 || frameCount < 0 || frameTicks < 0 || frameRowSize < 0 || interpolationPolicy < 0) {
				throw new IllegalArgumentException("negative world mesh texture animation metadata");
			}
		}
	}

	public record WorldMeshAnimationFrameRecord(int frameIndex, int durationTicks) {
		public WorldMeshAnimationFrameRecord {
			if (frameIndex < 0 || durationTicks < 0) {
				throw new IllegalArgumentException("negative world mesh animation frame metadata");
			}
		}
	}

	public record WorldMeshSortedIndexRecord(
		long meshKey,
		long meshGeneration,
		long indexGeneration,
		int indexType,
		byte[] indexBytes
	) {
		public WorldMeshSortedIndexRecord {
			Objects.requireNonNull(indexBytes, "indexBytes");
			indexBytes = indexBytes.clone();
		}
	}

	public record WorldMeshVertexRecord(
		float x,
		float y,
		float z,
		float u,
		float v,
		float atlasU,
		float atlasV,
		int shaderBlockId,
		int shaderMaterialType,
		int colorArgb,
		int normalPacked,
		int light,
		int midBlockPacked
	) {
	}

	public record WorldMeshSectionRecord(
		int materialId,
		int textureId,
		int materialMode,
		int cullPolicy,
		int winding,
		int indexOffset,
		int indexCount
	) {
	}

	public record WorldMeshAssetRecord(
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		List<WorldMeshVertexRecord> vertices,
		byte[] indexBytes,
		List<WorldMeshSectionRecord> sections
	) {
		public WorldMeshAssetRecord {
			Objects.requireNonNull(vertices, "vertices");
			Objects.requireNonNull(indexBytes, "indexBytes");
			Objects.requireNonNull(sections, "sections");
			vertices = List.copyOf(vertices);
			indexBytes = indexBytes.clone();
			sections = List.copyOf(sections);
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

	private static WholeFrameProfile wholeFrameProfileAt(MemorySegment segment, long offset) {
		return new WholeFrameProfile(
			profileLong(segment, offset, 0),
			profileLong(segment, offset, 1),
			profileLong(segment, offset, 2),
			profileLong(segment, offset, 3),
			profileLong(segment, offset, 4),
			profileLong(segment, offset, 5),
			profileLong(segment, offset, 6),
			profileLong(segment, offset, 7),
			profileLong(segment, offset, 8),
			profileLong(segment, offset, 9),
			profileLong(segment, offset, 10),
			profileLong(segment, offset, 11),
			profileLong(segment, offset, 12),
			profileLong(segment, offset, 13),
			profileLong(segment, offset, 14),
			profileLong(segment, offset, 15),
			profileLong(segment, offset, 16),
			profileLong(segment, offset, 17),
			profileLong(segment, offset, 18),
			profileLong(segment, offset, 19),
			profileLong(segment, offset, 20),
			profileLong(segment, offset, 21),
			profileLong(segment, offset, 22),
			profileLong(segment, offset, 23),
			profileLong(segment, offset, 24),
			profileLong(segment, offset, 25),
			profileLong(segment, offset, 26),
			profileLong(segment, offset, 27),
			profileLong(segment, offset, 28),
			profileLong(segment, offset, 29),
			profileLong(segment, offset, 30),
			profileLong(segment, offset, 31),
			profileLong(segment, offset, 32),
			profileLong(segment, offset, 33),
			profileLong(segment, offset, 34),
			profileLong(segment, offset, 35),
			profileLong(segment, offset, 36),
			profileLong(segment, offset, 37),
			profileLong(segment, offset, 38),
			profileLong(segment, offset, 39),
			profileLong(segment, offset, 40),
			profileLong(segment, offset, 41),
			profileLong(segment, offset, 42),
			profileLong(segment, offset, 43),
			profileLong(segment, offset, 44),
			profileLong(segment, offset, 45),
			profileLong(segment, offset, 46),
			profileLong(segment, offset, 47),
			profileLong(segment, offset, 48),
			profileLong(segment, offset, 49),
			profileLong(segment, offset, 50),
			profileLong(segment, offset, 51),
			profileLong(segment, offset, 52),
			profileLong(segment, offset, 53),
			profileLong(segment, offset, 54),
			profileLong(segment, offset, 55),
			profileLong(segment, offset, 56),
			profileLong(segment, offset, 57),
			profileLong(segment, offset, 58),
			profileLong(segment, offset, 59),
			profileLong(segment, offset, 60),
			profileLong(segment, offset, 61),
			profileLong(segment, offset, 62),
			profileLong(segment, offset, 63),
			profileLong(segment, offset, 64),
			profileLong(segment, offset, 65),
			profileLong(segment, offset, 66),
			profileLong(segment, offset, 67),
			profileLong(segment, offset, 68),
			profileLong(segment, offset, 69),
			profileLong(segment, offset, 70),
			profileLong(segment, offset, 71),
			profileLong(segment, offset, 72),
			profileLong(segment, offset, 73),
			profileLong(segment, offset, 74),
			profileLong(segment, offset, 75),
			profileLong(segment, offset, 76),
			profileLong(segment, offset, 77),
			profileLong(segment, offset, 78),
			profileLong(segment, offset, 79),
			profileLong(segment, offset, 80),
			profileLong(segment, offset, 81),
			profileLong(segment, offset, 82),
			profileLong(segment, offset, 83),
			profileLong(segment, offset, 84),
			profileLong(segment, offset, 85),
			profileLong(segment, offset, 86),
			profileLong(segment, offset, 87),
			profileLong(segment, offset, 88),
			profileLong(segment, offset, 89),
			profileLong(segment, offset, 90),
			profileLong(segment, offset, 91),
			profileLong(segment, offset, 92),
			profileLong(segment, offset, 93),
			profileLong(segment, offset, 94),
			profileLong(segment, offset, 95),
			profileLong(segment, offset, 96),
			profileLong(segment, offset, 97),
			profileLong(segment, offset, 98),
			profileLong(segment, offset, 99),
			profileLong(segment, offset, 100),
			profileLong(segment, offset, 101),
			profileLong(segment, offset, 102),
			profileLong(segment, offset, 103),
			profileLong(segment, offset, 104),
			profileLong(segment, offset, 105),
			profileLong(segment, offset, 106),
			profileLong(segment, offset, 107),
			profileLong(segment, offset, 108),
			profileLong(segment, offset, 109),
			profileLong(segment, offset, 110),
			profileLong(segment, offset, 111),
			profileLong(segment, offset, 112),
			profileLong(segment, offset, 113),
			profileLong(segment, offset, 114),
			profileLong(segment, offset, 115),
			profileLong(segment, offset, 116),
			profileLong(segment, offset, 117),
			profileLong(segment, offset, 118)
		);
	}

	private static long profileLong(MemorySegment segment, long offset, int index) {
		return segment.get(ValueLayout.JAVA_LONG, offset + (long)index * Long.BYTES);
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

	public record AcquiredFrame(long frameId, long correlationId, int status, long frameTarget, long frameTargetIdentity, int width, int height, int colorFormat) {
	}

	public record FrameResize(int status, int width, int height) {
	}

	public record PresentedFrame(long frameId, long correlationId, int status, long completedSubmissionId, long frameTargetIdentity) {
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

	public record WorldLineSegmentRecord(
		int stratum,
		int style,
		int depthPolicy,
		int colorArgb,
		float lineWidth,
		float startX,
		float startY,
		float startZ,
		float endX,
		float endY,
		float endZ,
		int viewportWidth,
		int viewportHeight
	) {
	}

	public record WorldCrackQuadRecord(
		int stratum,
		int stage,
		int depthPolicy,
		int blendPolicy,
		int cullPolicy,
		int colorArgb,
		float[] vertices,
		int viewportWidth,
		int viewportHeight
	) {
		public WorldCrackQuadRecord {
			Objects.requireNonNull(vertices, "vertices");
			if (vertices.length != 12) {
				throw new IllegalArgumentException("world crack quad requires four xyz vertices");
			}
		}
	}

	public record WorldBorderQuadRecord(
		int stratum,
		int textureId,
		int depthPolicy,
		int blendPolicy,
		int cullPolicy,
		int colorArgb,
		float borderSize,
		float distanceToBorder,
		float scrollU,
		float scrollV,
		float uvU,
		float uvV,
		float uvWidth,
		float uvHeight,
		float[] vertices,
		int viewportWidth,
		int viewportHeight
	) {
		public WorldBorderQuadRecord {
			Objects.requireNonNull(vertices, "vertices");
			if (vertices.length != 12) {
				throw new IllegalArgumentException("world border quad requires four xyz vertices");
			}
		}
	}

	public record WorldMaterialQuadRecord(
		int stratum,
		int materialId,
		int textureId,
		int materialMode,
		int depthPolicy,
		int cullPolicy,
			int topology,
			int winding,
			int colorArgb,
		float p0X,
		float p0Y,
		float p0Z,
		float p1X,
		float p1Y,
		float p1Z,
		float p2X,
		float p2Y,
		float p2Z,
		float p3X,
		float p3Y,
		float p3Z,
		float uv0U,
		float uv0V,
		float uv1U,
		float uv1V,
		float uv2U,
		float uv2V,
		float uv3U,
		float uv3V,
		int viewportWidth,
		int viewportHeight
	) {
	}

	private record WorldMaterialKeyRecord(
		int stratum,
		int materialId,
		int textureId,
		int materialMode,
		int depthPolicy,
		int cullPolicy,
		int topology,
		int winding
	) {
		static WorldMaterialKeyRecord from(WorldMaterialQuadRecord quad) {
			return new WorldMaterialKeyRecord(
				quad.stratum(),
				quad.materialId(),
				quad.textureId(),
				quad.materialMode(),
				quad.depthPolicy(),
				quad.cullPolicy(),
				quad.topology(),
				quad.winding()
			);
		}
	}

	public record WorldMeshInstanceRecord(
		int stratum,
		long meshKey,
		long meshGeneration,
		int meshSectionIndex,
		int depthPolicy,
		int cullPolicy,
		int winding,
		int colorArgb,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		public WorldMeshInstanceRecord {
			Objects.requireNonNull(transform, "transform");
			if (transform.length != 16) {
				throw new IllegalArgumentException("world mesh instance transform must contain 16 floats");
			}
			transform = transform.clone();
		}
	}

	public record WorldBackgroundRecord(
		boolean enabled,
		int skyType,
		int loadIntent,
		int storeIntent,
		int colorArgb,
		int viewportWidth,
		int viewportHeight
	) {
		public static WorldBackgroundRecord diagnosticFallback() {
			return new WorldBackgroundRecord(false, 0, 0, 0, 0, 0, 0);
		}
	}

	/** Coarse world/camera semantic input for a future Rust-owned volume mapping. */
	public record WorldVoxelVolumeFrameRecord(
		boolean enabled,
		long worldGeneration,
		long resourceGeneration,
		float cameraX,
		float cameraY,
		float cameraZ
	) {
		public static WorldVoxelVolumeFrameRecord disabled() {
			return new WorldVoxelVolumeFrameRecord(false, 0L, 0L, 0.0F, 0.0F, 0.0F);
		}
	}

	/** Copied vanilla environment semantics for future Rust-owned shader work. */
	public record WorldShaderEnvironmentFrameRecord(
		boolean enabled,
		long worldGeneration,
		long worldTime,
		int frameCounter,
		float frameTimeSeconds,
		float frameTimeCounter,
		int worldDay,
		int moonPhase,
		float timeOfDay,
		float rainStrength,
		float thunderStrength,
		float skyDarken,
		int eyeSubmersion,
		float screenBrightness,
		float farPlane,
		float relativeEyeX,
		float relativeEyeY,
		float relativeEyeZ,
		float skyColorRed,
		float skyColorGreen,
		float skyColorBlue,
		float darknessLightFactor,
		float nightVision,
		float fogColorRed,
		float fogColorGreen,
		float fogColorBlue,
		int biomePrecipitation,
		String biomeResourceLocation,
		String mainHandItemModelResourceLocation,
		String offHandItemModelResourceLocation,
		int mainHandItemLightEmission,
		int offHandItemLightEmission
	) {
		public static WorldShaderEnvironmentFrameRecord disabled() {
			return new WorldShaderEnvironmentFrameRecord(
				false, 0L, 0L, 0,
				0.0F, 0.0F, 0, 0,
				0.0F, 0.0F, 0.0F, 0.0F,
				0, 0.0F, 0.0F,
				0.0F, 0.0F, 0.0F,
				0.0F, 0.0F, 0.0F,
				0.0F, 0.0F,
				0.0F, 0.0F, 0.0F,
			0, "", "", "", 0, 0
		);
		}
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

	public record WholeFrameSubmitResult(
		long submissionId,
		long worldSegmentCount,
		long worldVertexCount,
		long worldBatchCount,
		long worldDrawCount,
		long worldCrackQuadCount,
		long worldCrackBatchCount,
		long worldCrackDrawCount,
		long worldBorderQuadCount,
		long worldBorderBatchCount,
		long worldBorderDrawCount,
		long worldMaterialQuadCount,
		long worldMaterialBatchCount,
		long worldMaterialDrawCount,
		long worldMeshInstanceCount,
		long worldMeshBatchCount,
		long worldMeshDrawCount,
		long worldBackgroundClearCount,
		long worldBackgroundDiagnosticFallbackCount,
		long worldBackgroundSkyType,
		long worldBackgroundColorArgb,
		long depthAttachmentCreates,
		long depthAttachmentReuses,
		long depthAttachmentRetires,
		long outlineCacheHits,
		long outlineCacheMisses,
		long crackCacheHits,
		long crackCacheMisses,
		long borderCacheHits,
		long borderCacheMisses,
		long materialCacheHits,
		long materialCacheMisses,
		long meshCacheHits,
		long meshCacheMisses,
		long spriteCount,
		long spriteBatchCount,
		long cacheHits,
		long cacheMisses,
		long resourceCreates,
		long commandLists,
		long commandOps,
		long ffiCalls,
		long ffiInputBytes,
		BackendMetrics backendMetrics,
		WholeFrameProfile profile
	) {
		public Status asStatus() {
			return new Status(submissionId, ffiCalls, ffiInputBytes, backendMetrics);
		}
	}

	public record WholeFrameProfile(
		long ffiDecodeNanos,
		long guiFrontendNanos,
		long worldFrontendTotalNanos,
		long worldValidateFrameNanos,
		long worldBatchingNanos,
		long worldResourcePrepareNanos,
		long worldPrepareTargetQueryNanos,
		long worldPrepareRenderResourcesNanos,
		long worldPrepareDepthAttachmentNanos,
		long worldPrepareGBufferResourcesNanos,
		long worldPrepareGBufferCacheCheckNanos,
		long worldPrepareGBufferDestroyNanos,
		long worldPrepareGBufferPlanNanos,
		long worldPrepareGBufferCreateNanos,
		long worldPrepareFramePassNanos,
		long worldMeshSectionExpandGroupNanos,
		long shaderPlanLookupNanos,
		long galCommandGenerationNanos,
		long galSubmitTotalNanos,
		long galValidateOpsNanos,
		long galValidateHandlesNanos,
		long galHazardAnalysisNanos,
		long backendEncodeNanos,
		long backendSubmitNanos,
		long backendRetireNanos,
		long vulkanCommandBufferAllocNanos,
		long vulkanCommandBufferBeginNanos,
		long vulkanCommandRecordingNanos,
		long vulkanCommandBufferEndNanos,
		long vulkanQueueSubmitNanos,
		long vulkanTimelinePollNanos,
		long vulkanTimelineWaitNanos,
		long vulkanDeviceWaitIdleNanos,
		long vulkanCommandBuffersAllocated,
		long vulkanCommandBuffersFreed,
		long vulkanWaitCount,
		long vulkanDeviceWaitIdleCount,
		long resourceCreatesDelta,
		long resourceDestroysDelta,
		long hostWriteOps,
		long hostWriteBytes,
		long barrierOps,
		long passCount,
		long drawOps,
		long drawIndexedOps,
		long pipelineBinds,
		long resourceSetBinds,
		long gpuTimestampStatus,
		long gpuShadowDepthNanos,
		long gpuTerrainOpaqueNanos,
		long gpuTerrainCutoutNanos,
		long gpuDeferredLightingNanos,
		long gpuComposite0Nanos,
		long gpuComposite1Nanos,
		long gpuFinalOutputNanos,
		long gpuFrameTotalNanos,
		long gBufferPersistentCacheHits,
		long gBufferPersistentCacheMisses,
		long gBufferFinalBindingCacheHits,
		long gBufferFinalBindingCacheMisses,
		long gBufferAttachmentCreates,
		long gBufferPipelineCreates,
		long gBufferShaderModuleCreates,
		long gBufferDescriptorCreates,
		long gBufferRenderTargetCreates,
		long gBufferResourcesRetired,
		long worldPrepareGBufferPersistentKeyNanos,
		long worldPrepareGBufferPersistentLookupNanos,
		long worldPrepareGBufferFinalKeyNanos,
		long worldPrepareGBufferFinalLookupNanos,
		long worldPrepareGBufferFinalCreateNanos,
		long worldPrepareFrameTargetAttachmentQueryNanos,
		long worldPrepareMeshMaterialAssetNanos,
		long worldPrepareMetricsAccountingNanos,
		long gBufferFinalPassCreates,
		long vulkanAcquireNanos,
		long vulkanPresentNanos,
		long vulkanPresentWaitNanos,
		long vulkanPresentMode,
		long vulkanRequestedPresentMode,
		long vulkanSupportedPresentModes,
		long vulkanPresentModeFallbackReason,
		long vulkanAcquiredImageIndex,
		long vulkanSwapchainGeneration,
		long vulkanSwapchainImageCount,
		long vulkanSurfaceMinImageCount,
		long vulkanSurfaceMaxImageCount,
		long vulkanConfiguredFramesInFlight,
		long vulkanImagesInFlight,
		long vulkanAvailableFrameSlots,
		long galHazardReadEvents,
		long galHazardWriteEvents,
		long galHazardCandidatesExamined,
		long galHazardConflicts,
		long galHazardBarriersApplied,
		long galHazardActiveReadEntries,
		long galHazardActiveWriteEntries,
		long galCommandOpsBeforeNormalize,
		long galCommandOpsAfterNormalize,
		long galRedundantPipelineBindsRemoved,
		long galRedundantResourceSetBindsRemoved,
		long galRedundantVertexBufferBindsRemoved,
		long galRedundantIndexBufferBindsRemoved,
		long worldPrepareMeshCacheScanNanos,
		long worldPrepareMaterialResourceNanos,
		long worldPrepareMeshStreamCapacityNanos,
		long worldPrepareMeshStreamLookupNanos,
		long worldPrepareMeshStreamGrowNanos,
		long worldPrepareMeshResourceNanos,
		long worldPrepareMaterialSlotCheckNanos,
		long worldPrepareMeshSlotCheckNanos,
		long worldPrepareMeshBatchCount,
		long worldPrepareMeshStreamRequiredBytes,
		long worldPrepareMeshStreamCapacityBytes,
		long worldPrepareMeshStreamGrows,
		long worldMeshStreamPayloadPackNanos,
		long worldMeshDrawRecordNanos,
		long worldMeshStreamPayloadBytes,
		long worldMeshDynamicOffsetCount
	) {
		public static WholeFrameProfile empty() {
			return new WholeFrameProfile(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
			);
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
		private static final MethodHandle CONTEXT_CREATE_WINDOWED_VULKAN = downcall("mattmc_vulkanic_gal_context_create_windowed_vulkan", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
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
		private static final MethodHandle WHOLE_FRAME_SUBMIT = downcall("mattmc_vulkanic_gal_whole_frame_submit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_PRIMITIVES_SUBMIT = downcall("mattmc_vulkanic_gal_world_primitives_submit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_gui_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_BORDER_UPDATE_ASSET = downcall("mattmc_vulkanic_gal_world_border_update_asset", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_CRACK_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_crack_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_MATERIAL_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_material_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_MESH_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_mesh_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle SHADER_PACK_UPDATE_SOURCES = downcall("mattmc_vulkanic_gal_shader_pack_update_sources", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle SHADER_PACK_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_shader_pack_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
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

		static int contextCreateWindowedVulkan(MemorySegment request, MemorySegment out) {
			try {
				return (int) CONTEXT_CREATE_WINDOWED_VULKAN.invokeExact(request, out);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to call VulkanicGAL windowed Vulkan context create ABI", throwable);
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

		static int wholeFrameSubmit(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WHOLE_FRAME_SUBMIT.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to submit Rust VulkanicGAL whole frame", throwable);
			}
		}

		static int worldPrimitivesSubmit(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_PRIMITIVES_SUBMIT.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to submit Rust VulkanicGAL world primitives", throwable);
			}
		}

		static int guiUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) GUI_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL GUI assets", throwable);
			}
		}

		static int worldBorderUpdateAsset(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_BORDER_UPDATE_ASSET.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world-border asset", throwable);
			}
		}

		static int worldCrackUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_CRACK_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world crack assets", throwable);
			}
		}

		static int worldMaterialUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_MATERIAL_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world material assets", throwable);
			}
		}

		static int worldMeshUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_MESH_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world mesh assets", throwable);
			}
		}

		static int shaderPackUpdateSources(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) SHADER_PACK_UPDATE_SOURCES.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL shader-pack sources", throwable);
			}
		}

		static int shaderPackUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) SHADER_PACK_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL shader-pack assets", throwable);
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
			GUI_ASSET_UPDATE(48),
			WINDOWED_VULKAN_CONTEXT_CREATE(49),
			WORLD_LINE_SEGMENT_REQUEST(50),
			WORLD_CRACK_QUAD_REQUEST(51),
			WORLD_BORDER_QUAD_REQUEST(52),
			WHOLE_FRAME_SUBMIT(53),
			WHOLE_FRAME_SUBMIT_RESULT(54),
			WORLD_BORDER_ASSET_UPDATE(55),
			WORLD_BACKGROUND_REQUEST(56),
				WORLD_CRACK_ASSET_PAYLOAD(57),
				WORLD_CRACK_ASSET_UPDATE(58),
				WORLD_MATERIAL_QUAD_REQUEST(59),
				WORLD_MATERIAL_ASSET_PAYLOAD(60),
				WORLD_MATERIAL_ASSET_UPDATE(61),
				WORLD_MATERIAL_TABLE_RECORD(62),
				WORLD_MATERIAL_COMPACT_QUAD_REQUEST(63),
				WORLD_MESH_VERTEX(64),
				WORLD_MESH_SECTION_RECORD(65),
				WORLD_MESH_ASSET_RECORD(66),
				WORLD_MESH_TEXTURE_ASSET_PAYLOAD(67),
				WORLD_MESH_ASSET_UPDATE(68),
					WORLD_MESH_INSTANCE_RECORD(69),
					WORLD_MESH_SORTED_INDEX_RECORD(70),
					WORLD_MESH_ANIMATION_FRAME_RECORD(71),
					WORLD_VOXEL_VOLUME_FRAME(72),
					WORLD_SHADER_ENVIRONMENT_FRAME(73),
					SHADER_PACK_SOURCE_FILE(74),
					SHADER_PACK_SOURCE_UPDATE(75),
					SHADER_PACK_ASSET_FILE(76),
					SHADER_PACK_ASSET_UPDATE(77);

		private final int id;
		private final int byteSize;
		private final int alignment;
		private final int[] offsets;

		Struct(int id) {
			this.id = id;
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment layout = arena.allocate(320, 8);
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

		public void setFloat(MemorySegment segment, int field, float value) {
			segment.set(ValueLayout.JAVA_FLOAT, offset(field), value);
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
				Struct.BARRIER.setInt(barrier, 6, 0);
				Struct.BARRIER.setInt(barrier, 7, 0);
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
