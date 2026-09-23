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
import java.util.AbstractList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;

public final class VulkanicGalBridge implements AutoCloseable {
	private static final ThreadLocal<ArrayDeque<Integer>> ACTIVE_BLOCK_ENTITY_IDS =
		ThreadLocal.withInitial(ArrayDeque::new);

	public static void beginSemanticBlockEntity(int blockEntityId) {
		ACTIVE_BLOCK_ENTITY_IDS.get().addLast(blockEntityId);
	}

	public static void endSemanticBlockEntity() {
		ArrayDeque<Integer> ids = ACTIVE_BLOCK_ENTITY_IDS.get();
		if (ids.isEmpty()) {
			ACTIVE_BLOCK_ENTITY_IDS.remove();
			throw new IllegalStateException("semantic block-entity scope ended without a matching begin");
		}
		ids.removeLast();
		if (ids.isEmpty()) {
			ACTIVE_BLOCK_ENTITY_IDS.remove();
		}
	}

	public static int activeSemanticBlockEntityId() {
		ArrayDeque<Integer> ids = ACTIVE_BLOCK_ENTITY_IDS.get();
		return ids.isEmpty() ? -1 : ids.peekLast();
	}
    // Copied authored metadata during synchronous model extraction. This scope
    // never sorts work or owns renderer state; each emitted record keeps a value.
    private static final ThreadLocal<ArrayDeque<Integer>> ACTIVE_MODEL_ORDERS =
        ThreadLocal.withInitial(ArrayDeque::new);
    public static void beginSemanticModelOrder(int order) { ACTIVE_MODEL_ORDERS.get().addLast(order); }
    public static void endSemanticModelOrder() {
        var orders=ACTIVE_MODEL_ORDERS.get();
        if (orders.isEmpty()) throw new IllegalStateException("model order scope ended without begin");
        orders.removeLast();
        if (orders.isEmpty()) ACTIVE_MODEL_ORDERS.remove();
    }
    private static Integer activeSemanticModelOrder() {
        var orders=ACTIVE_MODEL_ORDERS.get();
        return orders.isEmpty() ? null : orders.peekLast();
    }
	private static final float GUI_UV_OVERLAP_LIMIT = 1.0F / 16.0F;
	/** Texture bytes already use VulkanicGAL's sampler-row convention. */
	public static final int WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC = 0;
	/** PNG rows use Minecraft model UVs, whose V origin is the image top edge. */
	public static final int WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_MINECRAFT_TOP_LEFT = 1;
	/** Stable semantic stratum used by Rust for indexed entity/model meshes. */
	public static final int WORLD_MESH_ENTITY_STRATUM = 67;
	/** Stable semantic stratum for ordinary block-display mesh producers. */
	public static final int WORLD_MESH_ORDINARY_BLOCK_STRATUM = 71;
	private static final boolean TRACE_WORLD_MATERIAL_FRAME =
		Boolean.getBoolean("mattmc.dev.graphicsAuditMaterialFrameTrace");
	private static final int TRACE_WORLD_MATERIAL_FRAME_MAX_LOGS =
		Integer.getInteger("mattmc.dev.graphicsAuditMaterialFrameTrace.maxLogs", 4);
	private static final AtomicInteger WORLD_MATERIAL_FRAME_TRACE_LOGS = new AtomicInteger();
	/** Reuse immutable UTF-8 identities while serializing large DH provenance batches. */
	private static final Map<String, byte[]> UTF8_IDENTITY_CACHE = new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
			return size() > 4096;
		}
	};

	private static byte[] utf8Identity(String value) {
		synchronized (UTF8_IDENTITY_CACHE) {
			return UTF8_IDENTITY_CACHE.computeIfAbsent(value, key -> key.getBytes(StandardCharsets.UTF_8));
		}
	}

	/** Pack Java's 0xRRGGBB tint into the explicit semantic RGB lanes of the DH ABI. */
	static int packWorldLodTint(int tintArgb) {
		int tintRgb = tintArgb & 0x00ffffff;
		return ((tintRgb >> 16) & 0xff) << 3
			| ((tintRgb >> 8) & 0xff) << 11
			| (tintRgb & 0xff) << 19;
	}

	public static final int ABI_VERSION = 65;
	public static final int WORLD_MESH_VIEW_LAYER_PERSPECTIVE = 4;
	public static final int WORLD_MESH_VIEW_LAYER_ORTHOGRAPHIC = 8;

	/** Immutable engine inputs; Rust owns Globals normalization, packing and GPU storage. */
	public record EngineGlobalsRecord(int screenWidth, int screenHeight, long gameTicks,
		float partialTick, float glintAlpha, int menuBlurRadius) {}

	static void writeEngineGlobals(MemorySegment request, EngineGlobalsRecord engineGlobals) {
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 37, engineGlobals == null ? 0 : 1);
		if (engineGlobals != null) {
			Struct.WHOLE_FRAME_SUBMIT.setInt(request, 38, engineGlobals.screenWidth());
			Struct.WHOLE_FRAME_SUBMIT.setInt(request, 39, engineGlobals.screenHeight());
			Struct.WHOLE_FRAME_SUBMIT.setLong(request, 40, engineGlobals.gameTicks());
			Struct.WHOLE_FRAME_SUBMIT.setFloat(request, 41, engineGlobals.partialTick());
			Struct.WHOLE_FRAME_SUBMIT.setFloat(request, 42, engineGlobals.glintAlpha());
			Struct.WHOLE_FRAME_SUBMIT.setInt(request, 43, engineGlobals.menuBlurRadius());
		}
	}
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
	/** GUI mesh ABI mode for a Frozen-parity panorama: unlit, no culling, no depth test. */
	public static final int GUI_MESH_MATERIAL_PANORAMA = 5;
	public static final int GUI_MESH_MATERIAL_MODEL_OVERLAY = 6;
	public static final int GUI_MESH_MATERIAL_ENTITY_CUTOUT_NO_CULL = 7;
	public static final int GUI_MESH_MATERIAL_ENTITY_TRANSLUCENT_NO_CULL = 8;
	/** Vanilla armor decal cutout: two-sided alpha test with Equal depth and depth writes. */
	public static final int GUI_MESH_MATERIAL_ENTITY_DECAL_CUTOUT_NO_CULL = 9;
	/** Ordinary inventory model lighting in the Y-down GUI normal space. */
	public static final int GUI_MESH_LIGHTING_INVENTORY_BLOCK = 3;
	public static final int GUI_MESH_LIGHTING_FRONT_MODEL = 4;
	public static final int GUI_MESH_LIGHTING_ENTITY_PREVIEW = 5;

	// Long-lived context requests use the context arena; large frame payloads
	// are serialized in a per-submit confined arena and released immediately
	// after the synchronous native call returns.
	private Arena arena;
	private final Arena contextArena;
	private final long contextId;
	private final long negotiatedFeatures;
	private static final int MAX_PERSISTENT_GUI_MESH_TOPOLOGIES = 4096;
	/** The Rust FFI rejects larger visible DH lists; keep the reusable staging
	 * allocation bounded to that same semantic contract. */
	private static final int MAX_PERSISTENT_WORLD_LOD_INSTANCES = 16_384;
	/** Must match Rust's bounded whole-frame world-mesh instance slice. */
	private static final int MAX_PERSISTENT_WORLD_MESH_INSTANCES = 4_096;
	/** Must match Rust's bounded whole-frame material-quad slice. */
	private static final int MAX_PERSISTENT_COMPACT_MATERIAL_QUADS = 65_536;
	private final IdentityHashMap<List<GuiMeshVertexRecord>, IdentityHashMap<List<Integer>, PackedGuiMeshTopology>>
		persistentGuiMeshTopologies = new IdentityHashMap<>();
	private int persistentGuiMeshTopologyCount;

	/**
	 * Reusable copied LOD instance staging. The native call copies this slice
	 * synchronously, so it may live in the context arena and be referenced by a
	 * frame request without exposing Java objects or native renderer state.
	 */
	private MemorySegment persistentWorldLodInstanceArray = MemorySegment.NULL;
	private int persistentWorldLodInstanceCapacity;
	private int persistentWorldLodInstanceCount = -1;
	private int[] persistentWorldLodLayers = new int[0];
	private int[] persistentWorldLodSegments = new int[0];
	private int[] persistentWorldLodOrders = new int[0];
	private long[] persistentWorldLodKeys = new long[0];
	private long[] persistentWorldLodGenerations = new long[0];
	private MemorySegment persistentWorldMeshInstanceArray = MemorySegment.NULL;
	private int persistentWorldMeshInstanceCapacity;
	private int persistentWorldMeshInstanceCount;
	private WorldMeshInstanceRecord[] persistentWorldMeshInstanceIdentities = new WorldMeshInstanceRecord[0];
	private MemorySegment persistentCompactMaterialArray = MemorySegment.NULL;
	private int persistentCompactMaterialCapacity;
	private int persistentCompactMaterialCount;
	private WorldMaterialQuadRecord[] persistentCompactMaterialRecords = new WorldMaterialQuadRecord[0];
	private int[] persistentCompactMaterialIndexes = new int[0];
	/**
	 * Reusable frame-local material partitioning scratch. These lists contain
	 * only the caller's immutable semantic records; the native request remains
	 * backed by the confined frame arena below. Keeping the containers on the
	 * render-thread bridge removes steady-state list/map/array churn without
	 * retaining renderer objects or crossing the Java/Rust ownership boundary.
	 */
	private final ArrayList<WorldMaterialQuadRecord> vertexModulatedMaterialScratch = new ArrayList<>();
	private final ArrayList<WorldMaterialQuadRecord> compactMaterialScratch = new ArrayList<>();
	private final ArrayList<WorldMaterialKeyRecord> materialTableScratch = new ArrayList<>();
	private int[] materialKeyBucketsScratch = new int[0];
	private int[] materialIndexesScratch = new int[0];
	private boolean closed;

	private VulkanicGalBridge(Arena arena, long contextId, long negotiatedFeatures) {
		this.arena = arena;
		this.contextArena = arena;
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

	/** Releases an acquired native presentation image after an aborted frame transaction. */
	public Status cancelFrame(long frameId, long correlationId) {
		MemorySegment request = Struct.FRAME_CANCEL.allocate(arena);
		Abi.writeHeader(request, Struct.FRAME_CANCEL);
		Struct.FRAME_CANCEL.setLong(request, 1, frameId);
		Struct.FRAME_CANCEL.setLong(request, 2, correlationId);
		MemorySegment status = Struct.STATUS.allocate(arena);
		checkStatus(Native.frameCancel(contextId, request, status), "frame cancel");
		return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation,
		long frameId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		List<GuiSpriteRecord> sprites
	) {
		return submitGuiFrame(generation, frameId, frameTarget, guiWidth, guiHeight, sprites, List.of(), List.of());
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation,
		long frameId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		List<GuiSpriteRecord> sprites,
		List<GuiAffineQuadRecord> affineQuads
	) {
		return submitGuiFrame(generation, frameId, frameTarget, guiWidth, guiHeight, sprites, affineQuads, List.of());
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation,
		long frameId,
		long frameTarget,
		int guiWidth,
		int guiHeight,
		List<GuiSpriteRecord> sprites,
		List<GuiAffineQuadRecord> affineQuads,
		List<GuiMeshBatchRecord> meshBatches
	) {
		return submitGuiFrame(generation, frameId, frameTarget, guiWidth, guiHeight,
			sprites, affineQuads, meshBatches, new GuiProjectionRecord(guiWidth, guiHeight));
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation, long frameId, long frameTarget, int guiWidth, int guiHeight,
		List<GuiSpriteRecord> sprites, List<GuiAffineQuadRecord> affineQuads,
		List<GuiMeshBatchRecord> meshBatches, GuiProjectionRecord guiProjection
	) {
		return submitGuiFrame(generation, frameId, frameTarget, guiWidth, guiHeight,
			sprites, affineQuads, meshBatches, guiProjection, List.of());
	}

	public GuiFrameSubmitResult submitGuiFrame(
		long generation, long frameId, long frameTarget, int guiWidth, int guiHeight,
		List<GuiSpriteRecord> sprites, List<GuiAffineQuadRecord> affineQuads,
		List<GuiMeshBatchRecord> meshBatches, GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> tiledQuads
	) {
		Arena previousArena = arena;
		Arena frameArena = Arena.ofConfined();
		arena = frameArena;
		try {
		Objects.requireNonNull(sprites, "sprites");
		Objects.requireNonNull(affineQuads, "affineQuads");
		Objects.requireNonNull(meshBatches, "meshBatches");
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
			Struct.GUI_SPRITE_REQUEST.setLong(item, 13, sprite.sequence());
		}
		MemorySegment affineQuadArray = encodeGuiAffineQuads(affineQuads);
		MemorySegment meshBatchArray = encodeGuiMeshBatches(meshBatches);
		MemorySegment request = Struct.GUI_FRAME_SUBMIT.allocate(arena);
		Abi.writeHeader(request, Struct.GUI_FRAME_SUBMIT);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 1, generation);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 2, frameId);
		Struct.GUI_FRAME_SUBMIT.setLong(request, 3, frameTarget);
		Struct.GUI_FRAME_SUBMIT.setInt(request, 4, guiWidth);
		guiProjection.validateLayout(guiWidth, guiHeight);
		Struct.GUI_FRAME_SUBMIT.setFloat(request, 10, guiProjection.width());
		Struct.GUI_FRAME_SUBMIT.setFloat(request, 11, guiProjection.height());
		Abi.writeSlice(request, Struct.GUI_FRAME_SUBMIT, 12,
			encodeGuiTiledQuads(tiledQuads, guiWidth, guiHeight), tiledQuads.size());
		Struct.GUI_FRAME_SUBMIT.setInt(request, 5, guiHeight);
		Abi.writeSlice(request, Struct.GUI_FRAME_SUBMIT, 6, spriteArray, sprites.size());
		Abi.writeSlice(request, Struct.GUI_FRAME_SUBMIT, 7, affineQuadArray, affineQuads.size());
		Struct.GUI_FRAME_SUBMIT.setLong(request, 8, negotiatedFeatures);
		Abi.writeSlice(request, Struct.GUI_FRAME_SUBMIT, 9, meshBatchArray, meshBatches.size());
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
		} finally {
			arena = previousArena;
			frameArena.close();
		}
	}

	private MemorySegment encodeGuiMeshBatches(List<GuiMeshBatchRecord> batches) {
		MemorySegment batchArray = Struct.GUI_MESH_BATCH_REQUEST.array(arena, batches.size());
		for (int i = 0; i < batches.size(); i++) {
			GuiMeshBatchRecord batch = batches.get(i);
			// These arrays and lists were defensively copied by their record constructors.
			// The bridge is their enclosing nestmate and reads them only during this
			// synchronous confined-arena encode, so using the immutable backing values
			// avoids manufacturing a clone for every component written to the FFI stream.
			List<GuiMeshVertexRecord> vertexRecords = batch.vertices;
			List<Integer> indexRecords = batch.indices;
			PackedGuiMeshTopology persistent = persistentGuiMeshTopology(batch, vertexRecords, indexRecords);
			MemorySegment vertices = persistent == null
				? encodeGuiMeshVertices(arena, vertexRecords) : persistent.vertices();
			MemorySegment indices = persistent == null
				? encodeGuiMeshIndices(arena, indexRecords) : persistent.indices();
			MemorySegment item = Abi.item(batchArray, Struct.GUI_MESH_BATCH_REQUEST, i);
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 0, Struct.GUI_MESH_BATCH_REQUEST.byteSize());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 1, batch.stratum());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 2, batch.layerIndex());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 3, batch.materialMode());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 4, batch.lightingMode());
			Struct.GUI_MESH_BATCH_REQUEST.setLong(item, 5, batch.assetId());
			Struct.GUI_MESH_BATCH_REQUEST.setLong(item, 6, batch.sequence());
			Struct.GUI_MESH_BATCH_REQUEST.setFloat(item, 7, batch.alphaCutoff());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 8, 0);
			for (int component = 0; component < 16; component++) item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_MESH_BATCH_REQUEST.offset(9) + component * 4L, batch.modelTransform[component]);
			for (int component = 0; component < 6; component++) item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_MESH_BATCH_REQUEST.offset(10) + component * 4L, batch.guiPose[component]);
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 11, batch.left());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 12, batch.top());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 13, batch.right());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 14, batch.bottom());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 15, batch.guiWidth());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 16, batch.guiHeight());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 17, batch.renderWidth());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 18, batch.renderHeight());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 19, batch.guardPixels());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 20, batch.clipMode());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 21, batch.clipLeft());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 22, batch.clipTop());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 23, batch.clipWidth());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 24, batch.clipHeight());
			Abi.writeSlice(item, Struct.GUI_MESH_BATCH_REQUEST, 25, vertices, vertexRecords.size());
			Abi.writeSlice(item, Struct.GUI_MESH_BATCH_REQUEST, 26, indices, indexRecords.size());
			StandardItemFoilRecord foil = batch.itemFoil();
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 27, foil == null ? 0 : foil.kind().wireValue());
			Struct.GUI_MESH_BATCH_REQUEST.setLong(item, 28, foil == null ? 0 : foil.clockMillis());
			item.set(ValueLayout.JAVA_DOUBLE, Struct.GUI_MESH_BATCH_REQUEST.offset(29), foil == null ? 0.0 : foil.speed());
			Struct.GUI_MESH_BATCH_REQUEST.setFloat(item, 30, foil == null ? 0.0F : foil.strength());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 31, batch.itemRasterScale());
			GuiDecalFoilRecord decal = batch.decalFoil();
			float[] decalModelPose = decal == null ? null : decal.modelPose;
			float[] decalNormalPose = decal == null ? null : decal.normalPose;
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 32, decal == null ? 0 : decal.nativeItemLayout() ? 2 : 1);
			for (int component = 0; component < 16; component++) item.set(ValueLayout.JAVA_FLOAT,
				Struct.GUI_MESH_BATCH_REQUEST.offset(33) + component * 4L, decalModelPose == null ? 0.0F : decalModelPose[component]);
			for (int component = 0; component < 9; component++) item.set(ValueLayout.JAVA_FLOAT,
				Struct.GUI_MESH_BATCH_REQUEST.offset(34) + component * 4L, decalNormalPose == null ? 0.0F : decalNormalPose[component]);
			GuiBlockItemRasterRecord block = batch.blockItemRaster();
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 35, block == null ? 0 : block.guiScale());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 37, block == null ? 0 : block.oversizedGui() ? 2 : 1);
			GuiItemCacheRecord cache = batch.itemCache();
			Struct.GUI_MESH_BATCH_REQUEST.setLong(item, 38, cache == null ? 0 : cache.identity());
			Struct.GUI_MESH_BATCH_REQUEST.setInt(item, 39, cache == null ? 0 : cache.animated() ? 2 : 1);
			double[] bounds = block == null ? null : block.modelBounds;
			for (int component = 0; component < 6; component++) item.set(ValueLayout.JAVA_DOUBLE,
				Struct.GUI_MESH_BATCH_REQUEST.offset(36) + component * 8L, bounds == null ? 0.0 : bounds[component]);
		}
		return batchArray;
	}

	private PackedGuiMeshTopology persistentGuiMeshTopology(
		GuiMeshBatchRecord batch, List<GuiMeshVertexRecord> vertices, List<Integer> indices
	) {
		GuiItemCacheRecord cache = batch.itemCache;
		if (cache == null || cache.animated() || batch.itemFoil != null) return null;
		IdentityHashMap<List<Integer>, PackedGuiMeshTopology> byIndices = persistentGuiMeshTopologies.get(vertices);
		if (byIndices != null) {
			PackedGuiMeshTopology existing = byIndices.get(indices);
			if (existing != null) return existing;
		}
		if (persistentGuiMeshTopologyCount >= MAX_PERSISTENT_GUI_MESH_TOPOLOGIES) return null;
		if (byIndices == null) {
			byIndices = new IdentityHashMap<>();
			persistentGuiMeshTopologies.put(vertices, byIndices);
		}
		PackedGuiMeshTopology packed = new PackedGuiMeshTopology(
			encodeGuiMeshVertices(contextArena, vertices), encodeGuiMeshIndices(contextArena, indices));
		byIndices.put(indices, packed);
		persistentGuiMeshTopologyCount++;
		return packed;
	}

	private static MemorySegment encodeGuiMeshVertices(Arena targetArena, List<GuiMeshVertexRecord> records) {
		if (records instanceof PackedGuiMeshVertices packed) return packed.encode(targetArena);
		MemorySegment vertices = Struct.GUI_MESH_VERTEX.array(targetArena, records.size());
		for (int vertexIndex = 0; vertexIndex < records.size(); vertexIndex++) {
			GuiMeshVertexRecord vertex = records.get(vertexIndex);
			MemorySegment item = Abi.item(vertices, Struct.GUI_MESH_VERTEX, vertexIndex);
			for (int component = 0; component < 3; component++) item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_MESH_VERTEX.offset(0) + component * 4L, vertex.position[component]);
			for (int component = 0; component < 2; component++) item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_MESH_VERTEX.offset(1) + component * 4L, vertex.atlasUv[component]);
			for (int component = 0; component < 2; component++) item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_MESH_VERTEX.offset(2) + component * 4L, vertex.localUv[component]);
			Struct.GUI_MESH_VERTEX.setInt(item, 3, vertex.colorArgb());
			Struct.GUI_MESH_VERTEX.setInt(item, 4, vertex.normalPacked());
			Struct.GUI_MESH_VERTEX.setInt(item, 5, vertex.sourceFace());
			Struct.GUI_MESH_VERTEX.setInt(item, 6, vertex.sourceFoilType());
		}
		return vertices;
	}

	private static MemorySegment encodeGuiMeshIndices(Arena targetArena, List<Integer> records) {
		if (records instanceof PackedGuiMeshIndices packed) return packed.encode(targetArena);
		MemorySegment indices = targetArena.allocate((long)records.size() * Integer.BYTES, Integer.BYTES);
		for (int index = 0; index < records.size(); index++) indices.setAtIndex(ValueLayout.JAVA_INT, index, records.get(index));
		return indices;
	}

	private record PackedGuiMeshTopology(MemorySegment vertices, MemorySegment indices) {}

	static MemorySegment encodeParticleQuads(Arena arena, List<WorldParticleQuadRecord> particles) {
		if (particles.size() > 65_536) throw new IllegalArgumentException("too many particle semantics");
		var layout = Struct.WORLD_PARTICLE_QUAD_REQUEST;
		MemorySegment array = layout.array(arena, particles.size());
		int previousIndex = 0;
		for (int i = 0; i < particles.size(); i++) {
			var p = particles.get(i);
			if (p.materialIndex() < previousIndex) throw new IllegalArgumentException("particle ordering must be nondecreasing");
			previousIndex = p.materialIndex();
			var item = Abi.item(array, layout, i);
			layout.setInt(item, 0, layout.byteSize());
			layout.setInt(item, 1, p.textureId());
			layout.setInt(item, 2, p.surface().wireValue());
			layout.setInt(item, 3, p.materialIndex());
			item.asSlice(layout.offset(4), 12).copyFrom(MemorySegment.ofArray(p.center));
			item.asSlice(layout.offset(5), 16).copyFrom(MemorySegment.ofArray(p.rotation));
			layout.setFloat(item, 6, p.size());
			item.asSlice(layout.offset(7), 16).copyFrom(MemorySegment.ofArray(p.uvBounds));
			layout.setInt(item, 8, p.colorArgb());
			layout.setInt(item, 9, p.packedLight());
		}
		return array;
	}

	/** Map collection indices to the bridge's per-vertex-then-compact stream. */
	static List<WorldParticleQuadRecord> mapParticleMaterialOrder(
		List<WorldMaterialQuadRecord> materials, List<WorldParticleQuadRecord> particles) {
		if ((long)materials.size() + particles.size() > 65_536)
			throw new IllegalArgumentException("combined particle/material frame bound exceeded");
		if (particles.isEmpty()) return List.of();
		int[] compactPrefix = new int[materials.size() + 1];
		for (int i = 0; i < materials.size(); i++)
			compactPrefix[i + 1] = compactPrefix[i] + (materials.get(i).hasVertexModulation() ? 0 : 1);
		int modulated = materials.size() - compactPrefix[materials.size()];
		var result = new ArrayList<WorldParticleQuadRecord>(particles.size());
		int previous = 0;
		for (var p : particles) {
			if (p.materialIndex() < previous || p.materialIndex() > materials.size())
				throw new IllegalArgumentException("invalid particle collection order");
			previous = p.materialIndex();
			result.add(new WorldParticleQuadRecord(p.textureId(), p.surface(),
				modulated + compactPrefix[p.materialIndex()], p.center(), p.rotation(),
				p.size(), p.uvBounds(), p.colorArgb(), p.packedLight()));
		}
		return List.copyOf(result);
	}

	private MemorySegment encodeGuiTiledQuads(List<GuiTiledQuadRecord> tiles, int guiWidth, int guiHeight) {
		Objects.requireNonNull(tiles, "tiles");
		if (tiles.size() > 65_536) throw new IllegalArgumentException("too many typed GUI commands");
		MemorySegment array = Struct.GUI_TILED_QUAD_REQUEST.array(arena, tiles.size());
		for (int i = 0; i < tiles.size(); i++) {
			GuiTiledQuadRecord tile = tiles.get(i);
			if (tile.guiWidth() != guiWidth || tile.guiHeight() != guiHeight) {
				throw new IllegalArgumentException("tiled GUI extent must match its frame");
			}
			MemorySegment item = Abi.item(array, Struct.GUI_TILED_QUAD_REQUEST, i);
			Struct.GUI_TILED_QUAD_REQUEST.setInt(item, 0, Struct.GUI_TILED_QUAD_REQUEST.byteSize());
			Struct.GUI_TILED_QUAD_REQUEST.setInt(item, 1, tile.stratum());
			Struct.GUI_TILED_QUAD_REQUEST.setLong(item, 2, tile.assetId());
			item.asSlice(Struct.GUI_TILED_QUAD_REQUEST.offset(3), 16).copyFrom(MemorySegment.ofArray(tile.bounds));
			item.set(ValueLayout.JAVA_INT, Struct.GUI_TILED_QUAD_REQUEST.offset(4), tile.tileWidth());
			item.set(ValueLayout.JAVA_INT, Struct.GUI_TILED_QUAD_REQUEST.offset(4) + Integer.BYTES, tile.tileHeight());
			item.asSlice(Struct.GUI_TILED_QUAD_REQUEST.offset(5), 16).copyFrom(MemorySegment.ofArray(tile.uv));
			item.asSlice(Struct.GUI_TILED_QUAD_REQUEST.offset(6), 24).copyFrom(MemorySegment.ofArray(tile.pose));
			Struct.GUI_TILED_QUAD_REQUEST.setFloat(item, 7, tile.z());
			Struct.GUI_TILED_QUAD_REQUEST.setInt(item, 8, tile.colorArgb());
			Struct.GUI_TILED_QUAD_REQUEST.setLong(item, 9, tile.sequence());
			Struct.GUI_TILED_QUAD_REQUEST.setInt(item, 10, tile.clipMode());
			item.asSlice(Struct.GUI_TILED_QUAD_REQUEST.offset(11), 16).copyFrom(MemorySegment.ofArray(tile.clip));
		}
		return array;
	}

	private MemorySegment encodeGuiAffineQuads(List<GuiAffineQuadRecord> affineQuads) {
		if (affineQuads.stream().mapToLong(quad -> quad.itemRasterLayers().size()).sum() > 4096) {
			throw new IllegalArgumentException("GUI item layer stream exceeds frame bound");
		}
		MemorySegment affineQuadArray = Struct.GUI_AFFINE_QUAD_REQUEST.array(arena, affineQuads.size());
		for (int i = 0; i < affineQuads.size(); i++) {
			GuiAffineQuadRecord quad = affineQuads.get(i);
			MemorySegment item = Abi.item(affineQuadArray, Struct.GUI_AFFINE_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(0), Struct.GUI_AFFINE_QUAD_REQUEST.byteSize());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 1, quad.stratum());
			Struct.GUI_AFFINE_QUAD_REQUEST.setLong(item, 2, quad.assetId());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 3, quad.x0());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 4, quad.y0());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 5, quad.x1());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 6, quad.y1());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 7, quad.x3());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 8, quad.y3());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 9, quad.z());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 10, quad.u0());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 11, quad.v0());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 12, quad.u1());
			Struct.GUI_AFFINE_QUAD_REQUEST.setFloat(item, 13, quad.v1());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 14, quad.colorArgb());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 15, quad.guiWidth());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 16, quad.guiHeight());
			Struct.GUI_AFFINE_QUAD_REQUEST.setLong(item, 17, quad.sequence());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 18, quad.clipMode());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 19, quad.clipLeft());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 20, quad.clipTop());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 21, quad.clipWidth());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 22, quad.clipHeight());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 23, quad.materialMode());
			Struct.GUI_AFFINE_QUAD_REQUEST.setInt(item, 24, quad.itemRasterScale());
			GuiItemRasterGeometryRecord geometry = quad.itemRasterGeometry();
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25), geometry.x0());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25) + 4L, geometry.y0());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25) + 8L, geometry.x1());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25) + 12L, geometry.y1());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25) + 16L, geometry.x3());
			item.set(ValueLayout.JAVA_FLOAT, Struct.GUI_AFFINE_QUAD_REQUEST.offset(25) + 20L, geometry.y3());
			MemorySegment layers = Struct.GUI_ITEM_RASTER_LAYER.array(arena, quad.itemRasterLayers().size());
			for (int j = 0; j < quad.itemRasterLayers().size(); j++) {
				GuiItemRasterLayerRecord layer = quad.itemRasterLayers().get(j);
				MemorySegment child = Abi.item(layers, Struct.GUI_ITEM_RASTER_LAYER, j);
				Struct.GUI_ITEM_RASTER_LAYER.setInt(child, 0, Struct.GUI_ITEM_RASTER_LAYER.byteSize());
				Struct.GUI_ITEM_RASTER_LAYER.setInt(child, 1, layer.materialMode());
				Struct.GUI_ITEM_RASTER_LAYER.setLong(child, 2, layer.assetId());
				Struct.GUI_ITEM_RASTER_LAYER.setInt(child, 3, layer.colorArgb());
				GuiItemRasterGeometryRecord layerGeometry = layer.geometry();
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4), layerGeometry.x0());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4) + 4L, layerGeometry.y0());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4) + 8L, layerGeometry.x1());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4) + 12L, layerGeometry.y1());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4) + 16L, layerGeometry.x3());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(4) + 20L, layerGeometry.y3());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(5), layer.u0());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(5) + 4L, layer.v0());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(5) + 8L, layer.u1());
				child.set(ValueLayout.JAVA_FLOAT, Struct.GUI_ITEM_RASTER_LAYER.offset(5) + 12L, layer.v1());
				child.asSlice(Struct.GUI_ITEM_RASTER_LAYER.offset(6),64).copyFrom(MemorySegment.ofArray(layer.modelTransform));
			}
			Abi.writeSlice(item, Struct.GUI_AFFINE_QUAD_REQUEST, 26, layers, quad.itemRasterLayers().size());
		}
		return affineQuadArray;
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
		return submitWholeFrame(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, List.of(),
			WorldLodRenderFrameRecord.disabled(), WorldFeatureCoverageRecord.empty(), guiSprites
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
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
			worldLodInstances,
			worldLodRenderFrame,
			worldFeatureCoverage,
			guiSprites,
			List.of(),
			List.of(),
			List.of(),
			WorldFirstPersonFrameRecord.disabled(),
			List.of(),
			-1,
			-1,
			null,
			true,
			new GuiProjectionRecord(guiWidth, guiHeight)
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
			WorldLodRenderFrameRecord.disabled(),
			WorldFeatureCoverageRecord.empty(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			WorldFirstPersonFrameRecord.disabled(),
			List.of(),
			-1,
			-1,
			null,
			false,
			new GuiProjectionRecord(viewportWidth, viewportHeight)
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
			WorldLodRenderFrameRecord.disabled(),
			WorldFeatureCoverageRecord.empty(),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			WorldFirstPersonFrameRecord.disabled(),
			List.of(),
			-1,
			-1,
			null,
			false,
			new GuiProjectionRecord(viewportWidth, viewportHeight)
		);
	}

	public WholeFrameSubmitResult submitWholeFrameWithAffineGui(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
			WorldFeatureCoverageRecord worldFeatureCoverage,
			List<GuiSpriteRecord> guiSprites,
			List<GuiAffineQuadRecord> guiAffineQuads
	) {
		return submitWholeFrameWithAffineGuiAndWorldText(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, List.of(), List.of()
		);
	}

	/**
	 * Submits shared semantic world text alongside the combined frame. This is
	 * intentionally separate from route selection: callers may only send it
	 * once the Rust-owned world-text resource and pass contracts are admitted.
	 */
	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldText(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads
	) {
		return submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			WorldFirstPersonFrameRecord.disabled(), List.of()
		);
	}

	/**
	 * Combined-frame transport for the Rust-owned first-person pass.
	 * The record is semantic only: no Java renderer, Iris object, or native
	 * resource can enter this method. Existing callers delegate with explicit
	 * zero work until extraction and Rust execution are selected together.
	 */
	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances
	) {
		return submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			firstPersonFrame, firstPersonMeshInstances, -1, -1, null,
			new GuiProjectionRecord(guiWidth, guiHeight)
		);
	}

	/**
	 * Combined-frame transport with the semantic GUI blur boundary appended to
	 * the request. The optional radius is copied semantic settings data for the
	 * Rust-owned blur graph; it never reopens Java post-processing.
	 */
	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum
	) {
		return submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, -1, null,
			new GuiProjectionRecord(guiWidth, guiHeight)
		);
	}

	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		GuiProjectionRecord guiProjection
	) {
		return submitWorldFrame(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, true, guiProjection
		);
	}

	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads,
		EngineGlobalsRecord engineGlobals,
		List<WorldParticleQuadRecord> worldParticles
	) {
		return submitWorldFrame(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, true, guiProjection, guiTiledQuads, engineGlobals, worldParticles, List.of(), List.of()
		);
	}

	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
		long generation, long frameId, long correlationId, long frameTarget,
		int guiWidth, int guiHeight, int viewportWidth, int viewportHeight,
		float[] viewMatrix, float[] projectionMatrix, WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments, List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads, List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances, WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame, List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame, WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites, List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches, List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame, List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum, int guiBlurRadius, String postEffectId, GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads, EngineGlobalsRecord engineGlobals,
		List<WorldParticleQuadRecord> worldParticles, List<WorldExperienceOrbInstanceRecord> worldOrbs,
		List<WorldDistantHorizonsGenericBoxRecord> worldDistantHorizonsGenericBoxes
	) {
		return submitWorldFrame(generation, frameId, correlationId, frameTarget, guiWidth, guiHeight,
			viewportWidth, viewportHeight, viewMatrix, projectionMatrix, worldBackground, worldSegments,
			worldCrackQuads, worldBorderQuads, worldMaterialQuads, worldMeshInstances, voxelVolumeFrame,
			shaderEnvironmentFrame, worldLodInstances, worldLodRenderFrame, worldFeatureCoverage,
			guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads, firstPersonFrame,
			firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, true,
			guiProjection, guiTiledQuads, engineGlobals, worldParticles, worldOrbs,
			worldDistantHorizonsGenericBoxes);
	}

	public WholeFrameSubmitResult submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(
		long generation, long frameId, long correlationId, long frameTarget,
		int guiWidth, int guiHeight, int viewportWidth, int viewportHeight,
		float[] viewMatrix, float[] projectionMatrix, WorldBackgroundRecord worldBackground,
		List<WorldLineSegmentRecord> worldSegments, List<WorldCrackQuadRecord> worldCrackQuads,
		List<WorldBorderQuadRecord> worldBorderQuads, List<WorldMaterialQuadRecord> worldMaterialQuads,
		List<WorldMeshInstanceRecord> worldMeshInstances, WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame, List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame, WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites, List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches, List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame, List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum, int guiBlurRadius, String postEffectId, GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads, EngineGlobalsRecord engineGlobals,
		List<WorldParticleQuadRecord> worldParticles, List<WorldExperienceOrbInstanceRecord> worldOrbs,
		List<WorldDistantHorizonsGenericBoxRecord> worldDistantHorizonsGenericBoxes,
		TerrainFrameCamera terrainFrameCamera
	) {
		return submitWorldFrame(generation, frameId, correlationId, frameTarget, guiWidth, guiHeight,
			viewportWidth, viewportHeight, viewMatrix, projectionMatrix, worldBackground, worldSegments,
			worldCrackQuads, worldBorderQuads, worldMaterialQuads, worldMeshInstances, voxelVolumeFrame,
			shaderEnvironmentFrame, worldLodInstances, worldLodRenderFrame, worldFeatureCoverage,
			guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads, firstPersonFrame,
			firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, true,
			guiProjection, guiTiledQuads, engineGlobals, worldParticles, worldOrbs,
			worldDistantHorizonsGenericBoxes, terrainFrameCamera);
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		boolean wholeFrame,
		GuiProjectionRecord guiProjection
	) {
		return submitWorldFrame(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight, viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads, worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances, worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads, firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, wholeFrame, guiProjection, List.of(), null);
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		boolean wholeFrame,
		GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads,
		EngineGlobalsRecord engineGlobals
	) {
		return submitWorldFrame(generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight, viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads, worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances, worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads, firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId, wholeFrame, guiProjection, guiTiledQuads, engineGlobals, List.of(), List.of(), List.of());
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		boolean wholeFrame,
		GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads,
		EngineGlobalsRecord engineGlobals,
		List<WorldParticleQuadRecord> worldParticles,
		List<WorldExperienceOrbInstanceRecord> worldOrbs,
		List<WorldDistantHorizonsGenericBoxRecord> worldDistantHorizonsGenericBoxes
	) {
		return submitWorldFrame(
			generation, frameId, correlationId, frameTarget, guiWidth, guiHeight, viewportWidth, viewportHeight,
			viewMatrix, projectionMatrix, worldBackground, worldSegments, worldCrackQuads, worldBorderQuads,
			worldMaterialQuads, worldMeshInstances, voxelVolumeFrame, shaderEnvironmentFrame, worldLodInstances,
			worldLodRenderFrame, worldFeatureCoverage, guiSprites, guiAffineQuads, guiMeshBatches, worldTextQuads,
			firstPersonFrame, firstPersonMeshInstances, guiBlurBeforeStratum, guiBlurRadius, postEffectId,
			wholeFrame, guiProjection, guiTiledQuads, engineGlobals, worldParticles, worldOrbs,
			worldDistantHorizonsGenericBoxes, null
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
		List<WorldLodColumnInstanceRecord> worldLodInstances,
		WorldLodRenderFrameRecord worldLodRenderFrame,
		WorldFeatureCoverageRecord worldFeatureCoverage,
		List<GuiSpriteRecord> guiSprites,
		List<GuiAffineQuadRecord> guiAffineQuads,
		List<GuiMeshBatchRecord> guiMeshBatches,
		List<WorldTextQuadRecord> worldTextQuads,
		WorldFirstPersonFrameRecord firstPersonFrame,
		List<WorldMeshInstanceRecord> firstPersonMeshInstances,
		int guiBlurBeforeStratum,
		int guiBlurRadius,
		String postEffectId,
		boolean wholeFrame,
		GuiProjectionRecord guiProjection,
		List<GuiTiledQuadRecord> guiTiledQuads,
		EngineGlobalsRecord engineGlobals,
		List<WorldParticleQuadRecord> worldParticles,
		List<WorldExperienceOrbInstanceRecord> worldOrbs,
		List<WorldDistantHorizonsGenericBoxRecord> worldDistantHorizonsGenericBoxes,
		TerrainFrameCamera terrainFrameCamera
	) {
		Arena previousArena = arena;
		Arena frameArena = Arena.ofConfined();
		arena = frameArena;
		try {
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
		Objects.requireNonNull(worldLodInstances, "worldLodInstances");
		Objects.requireNonNull(worldLodRenderFrame, "worldLodRenderFrame");
		Objects.requireNonNull(worldFeatureCoverage, "worldFeatureCoverage");
		Objects.requireNonNull(guiSprites, "guiSprites");
		Objects.requireNonNull(guiAffineQuads, "guiAffineQuads");
		Objects.requireNonNull(guiMeshBatches, "guiMeshBatches");
		Objects.requireNonNull(worldTextQuads, "worldTextQuads");
		Objects.requireNonNull(firstPersonFrame, "firstPersonFrame");
		Objects.requireNonNull(firstPersonMeshInstances, "firstPersonMeshInstances");
		Objects.requireNonNull(worldDistantHorizonsGenericBoxes, "worldDistantHorizonsGenericBoxes");
		if (viewMatrix.length != 16 || projectionMatrix.length != 16) {
			throw new IllegalArgumentException("whole-frame matrices must contain 16 floats");
		}
		for (float value : viewMatrix) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException("whole-frame view matrix must contain finite values");
			}
		}
		for (float value : projectionMatrix) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException("whole-frame projection matrix must contain finite values");
			}
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.java-record-packing");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-world-primitives");
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
			// The record constructor owns a validated immutable copy. This class is
			// its enclosing nestmate, so read that copy directly during the
			// synchronous FFI encode instead of allocating another defensive array.
			float[] vertices = quad.vertices;
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
			float[] vertices = quad.vertices;
			for (int field = 0; field < 12; field++) {
				item.set(ValueLayout.JAVA_FLOAT, Struct.WORLD_BORDER_QUAD_REQUEST.offset(16 + field), vertices[field]);
			}
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 28, quad.viewportWidth());
			Struct.WORLD_BORDER_QUAD_REQUEST.setInt(item, 29, quad.viewportHeight());
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-world-primitives");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-world-materials");
		vertexModulatedMaterialScratch.clear();
		compactMaterialScratch.clear();
		List<WorldMaterialQuadRecord> vertexModulatedMaterialQuads = vertexModulatedMaterialScratch;
		List<WorldMaterialQuadRecord> compactMaterialQuads = compactMaterialScratch;
		for (WorldMaterialQuadRecord quad : worldMaterialQuads) {
			if (quad.hasVertexModulation()) {
				vertexModulatedMaterialQuads.add(quad);
			} else {
				compactMaterialQuads.add(quad);
			}
		}
		MemorySegment materialArray = Struct.WORLD_MATERIAL_QUAD_REQUEST.array(arena, vertexModulatedMaterialQuads.size());
		for (int i = 0; i < vertexModulatedMaterialQuads.size(); i++) {
			WorldMaterialQuadRecord quad = vertexModulatedMaterialQuads.get(i);
			MemorySegment item = Abi.item(materialArray, Struct.WORLD_MATERIAL_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_QUAD_REQUEST.offset(0), Struct.WORLD_MATERIAL_QUAD_REQUEST.byteSize());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 1, quad.stratum());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 2, quad.materialId());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 3, quad.textureId());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 4, quad.materialMode());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 5, quad.depthPolicy());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 6, quad.cullPolicy());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 7, quad.topology());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 8, quad.colorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 9, quad.winding());
			// Write the copied semantic record directly. Avoid allocating temporary
			// position/UV arrays for every visible quad during whole-frame packing.
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 10, quad.p0X());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 11, quad.p0Y());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 12, quad.p0Z());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 13, quad.p1X());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 14, quad.p1Y());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 15, quad.p1Z());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 16, quad.p2X());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 17, quad.p2Y());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 18, quad.p2Z());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 19, quad.p3X());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 20, quad.p3Y());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 21, quad.p3Z());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 22, quad.uv0U());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 23, quad.uv0V());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 24, quad.uv1U());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 25, quad.uv1V());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 26, quad.uv2U());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 27, quad.uv2V());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 28, quad.uv3U());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setFloat(item, 29, quad.uv3V());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 30, quad.viewportWidth());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 31, quad.viewportHeight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 32, quad.sourceProgram());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 33, quad.sourceColorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 34, quad.packedLight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 35, quad.sourceUvSpace());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 36, quad.vertex0ColorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 37, quad.vertex1ColorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 38, quad.vertex2ColorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 39, quad.vertex3ColorArgb());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 40, quad.vertex0PackedLight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 41, quad.vertex1PackedLight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 42, quad.vertex2PackedLight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 43, quad.vertex3PackedLight());
			Struct.WORLD_MATERIAL_QUAD_REQUEST.setInt(item, 44, quad.blockEntityId());
		}
		materialTableScratch.clear();
		ArrayList<WorldMaterialKeyRecord> materialTable = materialTableScratch;
		ensureMaterialKeyBucketCapacity(compactMaterialQuads.size());
		Arrays.fill(materialKeyBucketsScratch, 0);
		int materialKeyBucketMask = materialKeyBucketsScratch.length - 1;
		if (materialIndexesScratch.length < compactMaterialQuads.size()) {
			materialIndexesScratch = new int[Math.max(compactMaterialQuads.size(),
				Math.max(64, materialIndexesScratch.length * 2))];
		}
		int[] materialIndexes = materialIndexesScratch;
		for (int i = 0; i < compactMaterialQuads.size(); i++) {
			WorldMaterialQuadRecord quad = compactMaterialQuads.get(i);
			int bucket = WorldMaterialKeyRecord.hash(quad) & materialKeyBucketMask;
			int index;
			while (true) {
				int storedIndex = materialKeyBucketsScratch[bucket] - 1;
				if (storedIndex < 0) {
					index = materialTable.size();
					break;
				}
				if (materialTable.get(storedIndex).matches(quad)) {
					index = storedIndex;
					break;
				}
				bucket = (bucket + 1) & materialKeyBucketMask;
			}
			if (index == materialTable.size()) {
				WorldMaterialKeyRecord key = WorldMaterialKeyRecord.from(quad);
				materialTable.add(key);
				materialKeyBucketsScratch[bucket] = index + 1;
			}
			materialIndexes[i] = index;
		}
		MemorySegment materialTableArray = Struct.WORLD_MATERIAL_TABLE_RECORD.array(arena, materialTable.size());
		for (int materialTableIndex = 0; materialTableIndex < materialTable.size(); materialTableIndex++) {
			WorldMaterialKeyRecord key = materialTable.get(materialTableIndex);
			MemorySegment item = Abi.item(materialTableArray, Struct.WORLD_MATERIAL_TABLE_RECORD, materialTableIndex);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_TABLE_RECORD.offset(0), Struct.WORLD_MATERIAL_TABLE_RECORD.byteSize());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 1, key.stratum());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 2, key.materialId());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 3, key.textureId());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 4, key.materialMode());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 5, key.depthPolicy());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 6, key.cullPolicy());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 7, key.topology());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 8, key.winding());
			Struct.WORLD_MATERIAL_TABLE_RECORD.setInt(item, 9, key.sourceProgram());
		}
		traceWorldMaterialFrame(frameId, worldMaterialQuads.size(), materialTable);
		MemorySegment compactMaterialArray = encodeCompactMaterialQuads(
			compactMaterialQuads, materialIndexes);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-world-materials");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-world-meshes");
		MemorySegment meshInstanceArray = encodeWorldMeshInstances(worldMeshInstances, terrainFrameCamera);
		MemorySegment firstPersonMeshInstanceArray = Struct.WORLD_MESH_INSTANCE_RECORD.array(arena, firstPersonMeshInstances.size());
		for (int i = 0; i < firstPersonMeshInstances.size(); i++) {
			WorldMeshInstanceRecord instance = firstPersonMeshInstances.get(i);
			if (instance.terrainPlacement() != null) throw new IllegalArgumentException("first-person meshes cannot carry terrain placement");
			MemorySegment item = Abi.item(firstPersonMeshInstanceArray, Struct.WORLD_MESH_INSTANCE_RECORD, i);
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
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 11, instance.entityId());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 12, instance.entityColorArgb());
			long transformOffset = Struct.WORLD_MESH_INSTANCE_RECORD.offset(13);
			float[] transform = instance.transform;
			MemorySegment.copy(transform, 0, item, ValueLayout.JAVA_FLOAT, transformOffset, 16);
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 14, instance.outlineColorArgb());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 15, instance.flags());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 16, instance.blockEntityId());
			Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 30, instance.packedLight());
			encodeWorldItemFoil(item, instance.itemFoil());
			encodeWorldDecalFoil(item, instance.decalFoil());
	            encodeModelSubmissionOrder(item, instance.modelSubmissionOrder());
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-world-meshes");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-world-text-and-lod");
		MemorySegment worldTextQuadArray = Struct.WORLD_TEXT_QUAD_REQUEST.array(arena, worldTextQuads.size());
		for (int i = 0; i < worldTextQuads.size(); i++) {
			WorldTextQuadRecord quad = worldTextQuads.get(i);
			MemorySegment item = Abi.item(worldTextQuadArray, Struct.WORLD_TEXT_QUAD_REQUEST, i);
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_TEXT_QUAD_REQUEST.offset(0), Struct.WORLD_TEXT_QUAD_REQUEST.byteSize());
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 1, quad.colored() ? 1 : 0);
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 2, quad.depthPolicy());
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 3, quad.packedLight());
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 4, quad.colorArgb());
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 5, 0);
			Struct.WORLD_TEXT_QUAD_REQUEST.setLong(item, 6, quad.assetId());
			Struct.WORLD_TEXT_QUAD_REQUEST.setLong(item, 7, quad.atlasGeneration());
			Struct.WORLD_TEXT_QUAD_REQUEST.setLong(item, 8, quad.atlasRevision());
			item.set(ValueLayout.JAVA_DOUBLE, Struct.WORLD_TEXT_QUAD_REQUEST.offset(9), quad.distanceToCameraSq());
			float[] modelView = quad.modelViewMatrix;
			float[] positions = quad.positions;
			float[] uvs = quad.uvs;
			MemorySegment.copy(modelView, 0, item, ValueLayout.JAVA_FLOAT, Struct.WORLD_TEXT_QUAD_REQUEST.offset(10), 16);
			MemorySegment.copy(positions, 0, item, ValueLayout.JAVA_FLOAT, Struct.WORLD_TEXT_QUAD_REQUEST.offset(11), 12);
			MemorySegment.copy(uvs, 0, item, ValueLayout.JAVA_FLOAT, Struct.WORLD_TEXT_QUAD_REQUEST.offset(12), 8);
			Struct.WORLD_TEXT_QUAD_REQUEST.setInt(item, 13, quad.blockEntityId());
		}
		MemorySegment lodInstanceArray = encodeWorldLodInstances(worldLodInstances);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-world-text-and-lod");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-gui-streams");
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
			Struct.GUI_SPRITE_REQUEST.setLong(item, 13, sprite.sequence());
		}
		MemorySegment affineQuadArray = encodeGuiAffineQuads(guiAffineQuads);
		MemorySegment guiMeshBatchArray = encodeGuiMeshBatches(guiMeshBatches);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-gui-streams");
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
		MemorySegment.copy(viewMatrix, 0, request, ValueLayout.JAVA_FLOAT, viewOffset, 16);
		MemorySegment.copy(projectionMatrix, 0, request, ValueLayout.JAVA_FLOAT, projectionOffset, 16);
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
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 8, worldBackground.skyVisible() ? 1 : 0);
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 9, worldBackground.skySunriseOrSunset() ? 1 : 0);
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 10, worldBackground.skyDarkDisc() ? 1 : 0);
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 11, 0);
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 12, worldBackground.skySunAngle());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 13, worldBackground.skyTimeOfDay());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 14, worldBackground.skyRainBrightness());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 15, worldBackground.skyStarBrightness());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 16, worldBackground.skySunriseAndSunsetColorArgb());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 17, worldBackground.skyMoonPhase());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 18, worldBackground.skyEndFlashIntensity());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 19, worldBackground.skyEndFlashXAngle());
		Struct.WORLD_BACKGROUND_REQUEST.setFloat(background, 20, worldBackground.skyEndFlashYAngle());
		Struct.WORLD_BACKGROUND_REQUEST.setInt(background, 21, worldBackground.skyColorArgb());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 12, segmentArray, worldSegments.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 13, crackArray, worldCrackQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 14, borderArray, worldBorderQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 15, materialArray, vertexModulatedMaterialQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 16, materialTableArray, materialTable.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 17, compactMaterialArray, compactMaterialQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 18, meshInstanceArray, worldMeshInstances.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 19, spriteArray, guiSprites.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 20, affineQuadArray, guiAffineQuads.size());
		Struct.WHOLE_FRAME_SUBMIT.setLong(request, 21, negotiatedFeatures);
		MemorySegment voxelVolume = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(22),
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
			Struct.WHOLE_FRAME_SUBMIT.offset(23),
			Struct.WORLD_SHADER_ENVIRONMENT_FRAME.byteSize()
		);
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 24, lodInstanceArray, worldLodInstances.size());
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-lod-frame");
		MemorySegment lodRenderFrame = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(25),
			Struct.WORLD_LOD_RENDER_FRAME.byteSize()
		);
		lodRenderFrame.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_RENDER_FRAME.offset(0), Struct.WORLD_LOD_RENDER_FRAME.byteSize());
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 1, worldLodRenderFrame.enabled() ? 1 : 0);
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 2, worldLodRenderFrame.flags());
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 3, worldLodRenderFrame.worldYOffset());
		long lodMatrixOffset = Struct.WORLD_LOD_RENDER_FRAME.offset(4);
		float[] lodCombinedMatrix = worldLodRenderFrame.combinedMatrix;
		MemorySegment.copy(lodCombinedMatrix, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT, lodMatrixOffset, 16);
		float[] lodDhFogParameters = worldLodRenderFrame.dhFogParameters;
		MemorySegment.copy(lodDhFogParameters, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT,
			Struct.WORLD_LOD_RENDER_FRAME.offset(18), lodDhFogParameters.length);
		MemorySegment featureCoverage = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(26),
			Struct.WORLD_FEATURE_COVERAGE.byteSize()
		);
		featureCoverage.set(ValueLayout.JAVA_INT, Struct.WORLD_FEATURE_COVERAGE.offset(0), Struct.WORLD_FEATURE_COVERAGE.byteSize());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 1, worldFeatureCoverage.modelSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 2, worldFeatureCoverage.modelPartSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 3, worldFeatureCoverage.blockModelSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 4, worldFeatureCoverage.ordinaryBlockSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 5, worldFeatureCoverage.itemSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 6, worldFeatureCoverage.customGeometrySubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 7, worldFeatureCoverage.shadowSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 8, worldFeatureCoverage.flameSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 9, worldFeatureCoverage.nameTagSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 10, worldFeatureCoverage.textSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 11, worldFeatureCoverage.hitboxSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 12, worldFeatureCoverage.leashSubmits());
		Struct.WORLD_FEATURE_COVERAGE.setInt(featureCoverage, 13, worldFeatureCoverage.particleGroupSubmits());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 27, worldTextQuadArray, worldTextQuads.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 28, guiMeshBatchArray, guiMeshBatches.size());
		guiProjection.validateLayout(guiWidth, guiHeight);
		Struct.WHOLE_FRAME_SUBMIT.setFloat(request, 34, guiProjection.width());
		Struct.WHOLE_FRAME_SUBMIT.setFloat(request, 35, guiProjection.height());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 36,
			encodeGuiTiledQuads(guiTiledQuads, guiWidth, guiHeight), guiTiledQuads.size());
		writeEngineGlobals(request, engineGlobals);
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.pack-particles-and-orbs");
		var orderedParticles = mapParticleMaterialOrder(worldMaterialQuads, worldParticles);
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 44,
			encodeParticleQuads(arena, orderedParticles), orderedParticles.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 45,
			encodeExperienceOrbInstances(arena, worldOrbs, worldMeshInstances.size()), worldOrbs.size());
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 46,
			encodeDistantHorizonsGenericBoxes(arena, worldDistantHorizonsGenericBoxes),
			worldDistantHorizonsGenericBoxes.size());
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-particles-and-orbs");
		MemorySegment firstPerson = request.asSlice(
			Struct.WHOLE_FRAME_SUBMIT.offset(29),
			Struct.WORLD_FIRST_PERSON_FRAME.byteSize()
		);
		firstPerson.set(ValueLayout.JAVA_INT, Struct.WORLD_FIRST_PERSON_FRAME.offset(0), Struct.WORLD_FIRST_PERSON_FRAME.byteSize());
		Struct.WORLD_FIRST_PERSON_FRAME.setInt(firstPerson, 1, firstPersonFrame.enabled() ? 1 : 0);
		Struct.WORLD_FIRST_PERSON_FRAME.setInt(firstPerson, 2, firstPersonFrame.clearDepthBefore() ? 1 : 0);
		Struct.WORLD_FIRST_PERSON_FRAME.setInt(firstPerson, 3, firstPersonFrame.mainHandInstanceCount());
		long firstPersonProjectionOffset = Struct.WORLD_FIRST_PERSON_FRAME.offset(4);
		float[] firstPersonProjection = firstPersonFrame.projectionMatrix();
		MemorySegment.copy(firstPersonProjection, 0, firstPerson, ValueLayout.JAVA_FLOAT, firstPersonProjectionOffset, 16);
		long firstPersonModelViewOffset = Struct.WORLD_FIRST_PERSON_FRAME.offset(5);
		float[] firstPersonModelView = firstPersonFrame.modelViewMatrix();
		MemorySegment.copy(firstPersonModelView, 0, firstPerson, ValueLayout.JAVA_FLOAT, firstPersonModelViewOffset, 16);
		Abi.writeSlice(request, Struct.WHOLE_FRAME_SUBMIT, 30, firstPersonMeshInstanceArray, firstPersonMeshInstances.size());
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 31, guiBlurBeforeStratum);
		Struct.WHOLE_FRAME_SUBMIT.setInt(request, 32, guiBlurRadius);
		// ABI v25 semantic slot. Generic post-effect identity transport is kept
		// empty until the coordinator admits a Rust-resolved graph; Java
		// PostChain objects never enter this request.
		Abi.writeBytes(arena, request, Struct.WHOLE_FRAME_SUBMIT, 33,
			postEffectId == null ? new byte[0] : postEffectId.getBytes(StandardCharsets.UTF_8));
		long lodModelViewOffset = Struct.WORLD_LOD_RENDER_FRAME.offset(5);
		long lodProjectionOffset = Struct.WORLD_LOD_RENDER_FRAME.offset(6);
		long lodProjectionInverseOffset = Struct.WORLD_LOD_RENDER_FRAME.offset(7);
		float[] lodModelViewMatrix = worldLodRenderFrame.modelViewMatrix;
		float[] lodProjectionMatrix = worldLodRenderFrame.projectionMatrix;
		float[] lodProjectionInverseMatrix = worldLodRenderFrame.projectionInverseMatrix;
		MemorySegment.copy(lodModelViewMatrix, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT, lodModelViewOffset, 16);
		MemorySegment.copy(lodProjectionMatrix, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT, lodProjectionOffset, 16);
		MemorySegment.copy(lodProjectionInverseMatrix, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT, lodProjectionInverseOffset, 16);
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 8, worldLodRenderFrame.clipDistance());
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 9, worldLodRenderFrame.microOffset());
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 10, worldLodRenderFrame.noiseIntensity());
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 11, worldLodRenderFrame.earthRadius());
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 12, worldLodRenderFrame.noiseSteps());
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 13, worldLodRenderFrame.noiseDropoff());
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 14, 0);
		float[] lodCameraWorldPosition = worldLodRenderFrame.cameraWorldPosition;
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 15, lodCameraWorldPosition[0]);
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 16, lodCameraWorldPosition[1]);
		Struct.WORLD_LOD_RENDER_FRAME.setFloat(lodRenderFrame, 17, lodCameraWorldPosition[2]);
		Struct.WORLD_LOD_RENDER_FRAME.setInt(lodRenderFrame, 19, worldLodRenderFrame.maxLevelHeight());
		float[] lodSsaoParameters = worldLodRenderFrame.ssaoParameters();
		MemorySegment.copy(lodSsaoParameters, 0, lodRenderFrame, ValueLayout.JAVA_FLOAT,
			Struct.WORLD_LOD_RENDER_FRAME.offset(20), lodSsaoParameters.length);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.pack-lod-frame");
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
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 33, shaderEnvironmentFrame.lightmapEnabled() ? 1 : 0);
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 34, 0);
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setLong(shaderEnvironment, 35, shaderEnvironmentFrame.lightmapGeneration());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 36, shaderEnvironmentFrame.lightmapAmbientLightFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 37, shaderEnvironmentFrame.lightmapSkyFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 38, shaderEnvironmentFrame.lightmapBlockFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 39, shaderEnvironmentFrame.lightmapNightVisionFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 40, shaderEnvironmentFrame.lightmapDarknessScale());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 41, shaderEnvironmentFrame.lightmapDarkenWorldFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 42, shaderEnvironmentFrame.lightmapBrightnessFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 43, shaderEnvironmentFrame.lightmapSkyLightRed());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 44, shaderEnvironmentFrame.lightmapSkyLightGreen());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 45, shaderEnvironmentFrame.lightmapSkyLightBlue());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 46, shaderEnvironmentFrame.lightmapAmbientRed());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 47, shaderEnvironmentFrame.lightmapAmbientGreen());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 48, shaderEnvironmentFrame.lightmapAmbientBlue());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 49, shaderEnvironmentFrame.blindness());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 50, shaderEnvironmentFrame.darknessFactor());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 51, shaderEnvironmentFrame.eyeBrightnessBlock());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 52, shaderEnvironmentFrame.eyeBrightnessSky());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 53, shaderEnvironmentFrame.fogParameterColorRed());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 54, shaderEnvironmentFrame.fogParameterColorGreen());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 55, shaderEnvironmentFrame.fogParameterColorBlue());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 56, shaderEnvironmentFrame.fogParameterColorAlpha());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 57, shaderEnvironmentFrame.fogEnvironmentalStart());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 58, shaderEnvironmentFrame.fogEnvironmentalEnd());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 59, shaderEnvironmentFrame.fogRenderDistanceStart());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 60, shaderEnvironmentFrame.fogRenderDistanceEnd());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setInt(shaderEnvironment, 61, shaderEnvironmentFrame.distantHorizonsRenderDistance());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 62, shaderEnvironmentFrame.fogSkyEnd());
		Struct.WORLD_SHADER_ENVIRONMENT_FRAME.setFloat(shaderEnvironment, 63, shaderEnvironmentFrame.fogCloudsEnd());
		MemorySegment result = Struct.WHOLE_FRAME_SUBMIT_RESULT.allocate(arena);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.java-record-packing");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("rust-gal.whole-frame.native-submit-return");
		int status = wholeFrame
			? Native.wholeFrameSubmit(contextId, request, result)
			: Native.worldPrimitivesSubmit(contextId, request, result);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("rust-gal.whole-frame.native-submit-return");
		checkStatus(status, wholeFrame ? "whole-frame submission" : "world primitive submission");
		long metricsOffset = Struct.WHOLE_FRAME_SUBMIT_RESULT.offset(53);
		long profileOffset = Struct.WHOLE_FRAME_SUBMIT_RESULT.offset(54);
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
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 44),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 45),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 46),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 47),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 48),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 49),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 50),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 51),
			Struct.WHOLE_FRAME_SUBMIT_RESULT.getLong(result, 52),
			ffiCalls,
			ffiInputBytes,
			metrics,
			profile
		);
		} finally {
			arena = previousArena;
			frameArena.close();
		}
	}


	private MemorySegment encodeCompactMaterialQuads(
		List<WorldMaterialQuadRecord> quads, int[] materialIndexes
	) {
		int count = quads.size();
		if (count == 0) {
			Arrays.fill(persistentCompactMaterialRecords, 0,
				Math.min(persistentCompactMaterialCount, persistentCompactMaterialRecords.length), null);
			persistentCompactMaterialCount = 0;
			return MemorySegment.NULL;
		}
		if (count > MAX_PERSISTENT_COMPACT_MATERIAL_QUADS) {
			throw new IllegalArgumentException(
				"compact world material quad count exceeds reusable staging bound: " + count);
		}
		ensureCompactMaterialStagingCapacity(count);
		for (int index = 0; index < count; index++) {
			WorldMaterialQuadRecord quad = Objects.requireNonNull(
				quads.get(index), "compactMaterialQuads[" + index + "]");
			int materialIndex = materialIndexes[index];
			if (quad.equals(persistentCompactMaterialRecords[index])
				&& materialIndex == persistentCompactMaterialIndexes[index]) {
				continue;
			}
			MemorySegment item = Abi.item(
				persistentCompactMaterialArray, Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST, index);
			encodeCompactMaterialQuad(item, quad, materialIndex);
			persistentCompactMaterialRecords[index] = quad;
			persistentCompactMaterialIndexes[index] = materialIndex;
		}
		if (count < persistentCompactMaterialCount) {
			Arrays.fill(persistentCompactMaterialRecords, count, persistentCompactMaterialCount, null);
		}
		persistentCompactMaterialCount = count;
		return persistentCompactMaterialArray.asSlice(
			0L, (long) Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.byteSize() * count);
	}

	private void ensureCompactMaterialStagingCapacity(int requiredCount) {
		if (requiredCount <= persistentCompactMaterialCapacity) {
			return;
		}
		int capacity = Math.max(64, persistentCompactMaterialCapacity);
		while (capacity < requiredCount) {
			capacity = Math.min(MAX_PERSISTENT_COMPACT_MATERIAL_QUADS, capacity * 2);
		}
		persistentCompactMaterialArray =
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.array(contextArena, capacity);
		persistentCompactMaterialCapacity = capacity;
		persistentCompactMaterialRecords = new WorldMaterialQuadRecord[capacity];
		persistentCompactMaterialIndexes = new int[capacity];
	}

	private static void encodeCompactMaterialQuad(
		MemorySegment item, WorldMaterialQuadRecord quad, int materialIndex
	) {
		item.set(ValueLayout.JAVA_INT, Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.offset(0),
			Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.byteSize());
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 1, materialIndex);
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 2, quad.colorArgb());
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 3, quad.sourceUvSpace());
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
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 24, quad.sourceColorArgb());
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 25, quad.packedLight());
		Struct.WORLD_MATERIAL_COMPACT_QUAD_REQUEST.setInt(item, 26, quad.blockEntityId());
	}

	/**
	 * Packs immutable world-mesh instances into context-owned staging. Exact
	 * record identity is sufficient for reuse because every record constructor
	 * defensively owns its mutable arrays. Rust copies the returned slice during
	 * the synchronous submission, so no Java object or native renderer state is
	 * retained across the VulkanicGAL boundary.
	 */
	private MemorySegment encodeWorldMeshInstances(List<WorldMeshInstanceRecord> instances) {
		return encodeWorldMeshInstances(instances, null);
	}

	private MemorySegment encodeWorldMeshInstances(
		List<WorldMeshInstanceRecord> instances,
		TerrainFrameCamera terrainFrameCamera
	) {
		int count = instances.size();
		if (count == 0) {
			Arrays.fill(persistentWorldMeshInstanceIdentities, 0,
				Math.min(persistentWorldMeshInstanceCount, persistentWorldMeshInstanceIdentities.length), null);
			persistentWorldMeshInstanceCount = 0;
			return MemorySegment.NULL;
		}
		if (count > MAX_PERSISTENT_WORLD_MESH_INSTANCES) {
			throw new IllegalArgumentException(
				"world mesh instance count exceeds reusable staging bound: " + count);
		}
		ensureWorldMeshInstanceStagingCapacity(count);
		for (int index = 0; index < count; index++) {
			WorldMeshInstanceRecord instance = Objects.requireNonNull(
				instances.get(index), "worldMeshInstances[" + index + "]");
			MemorySegment item = Abi.item(
				persistentWorldMeshInstanceArray, Struct.WORLD_MESH_INSTANCE_RECORD, index);
			if (persistentWorldMeshInstanceIdentities[index] == instance) {
				encodeTerrainPlacement(item, instance.terrainPlacement(), terrainFrameCamera);
				continue;
			}
			encodeWorldMeshInstance(item, instance, terrainFrameCamera);
			persistentWorldMeshInstanceIdentities[index] = instance;
		}
		if (count < persistentWorldMeshInstanceCount) {
			Arrays.fill(persistentWorldMeshInstanceIdentities, count,
				persistentWorldMeshInstanceCount, null);
		}
		persistentWorldMeshInstanceCount = count;
		return persistentWorldMeshInstanceArray.asSlice(
			0L, (long) Struct.WORLD_MESH_INSTANCE_RECORD.byteSize() * count);
	}

	private void ensureWorldMeshInstanceStagingCapacity(int requiredCount) {
		if (requiredCount <= persistentWorldMeshInstanceCapacity) {
			return;
		}
		int capacity = Math.max(64, persistentWorldMeshInstanceCapacity);
		while (capacity < requiredCount) {
			capacity = Math.min(MAX_PERSISTENT_WORLD_MESH_INSTANCES, capacity * 2);
		}
		persistentWorldMeshInstanceArray = Struct.WORLD_MESH_INSTANCE_RECORD.array(contextArena, capacity);
		persistentWorldMeshInstanceCapacity = capacity;
		persistentWorldMeshInstanceIdentities = new WorldMeshInstanceRecord[capacity];
	}

	private static void encodeWorldMeshInstance(
		MemorySegment item, WorldMeshInstanceRecord instance
	) {
		encodeWorldMeshInstance(item, instance, null);
	}

	private static void encodeWorldMeshInstance(
		MemorySegment item, WorldMeshInstanceRecord instance, TerrainFrameCamera terrainFrameCamera
	) {
		item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_INSTANCE_RECORD.offset(0),
			Struct.WORLD_MESH_INSTANCE_RECORD.byteSize());
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
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 11, instance.entityId());
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 12, instance.entityColorArgb());
		MemorySegment.copy(instance.transform, 0, item, ValueLayout.JAVA_FLOAT,
			Struct.WORLD_MESH_INSTANCE_RECORD.offset(13), 16);
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 14, instance.outlineColorArgb());
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 15, instance.flags());
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 16, instance.blockEntityId());
		encodeTerrainPlacement(item, instance.terrainPlacement(), terrainFrameCamera);
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 30, instance.packedLight());
		encodeWorldItemFoil(item, instance.itemFoil());
		encodeWorldDecalFoil(item, instance.decalFoil());
		encodeModelSubmissionOrder(item, instance.modelSubmissionOrder());
	}

	private static void encodeTerrainPlacement(
		MemorySegment item, TerrainSectionPlacement placement, TerrainFrameCamera terrainFrameCamera
	) {
		if (placement != null && terrainFrameCamera == null) {
			throw new IllegalArgumentException("terrain section origin requires frame camera semantics");
		}
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 17, placement == null ? 0 : 1);
		for (int axis = 0; axis < 3; axis++) {
			int origin = placement == null ? 0 : switch (axis) {
				case 0 -> placement.x();
				case 1 -> placement.y();
				default -> placement.z();
			};
			double camera = placement == null ? 0.0 : switch (axis) {
				case 0 -> terrainFrameCamera.x();
				case 1 -> terrainFrameCamera.y();
				default -> terrainFrameCamera.z();
			};
			item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_INSTANCE_RECORD.offset(18) + axis * 4L, origin);
			item.set(ValueLayout.JAVA_DOUBLE, Struct.WORLD_MESH_INSTANCE_RECORD.offset(19) + axis * 8L, camera);
		}
	}

	/**
	 * Packs the visible DH instance list into a bounded context-arena slice.
	 *
	 * <p>DH normally keeps the same visible column/segment ordering for many
	 * settled frames. Reusing the copied ABI bytes avoids allocating and
	 * rewriting a native array on each frame, while the primitive shadow arrays
	 * make the skip decision exact rather than hash-based. The request remains
	 * synchronous: Rust copies the semantic slice before this method returns,
	 * so the persistent staging memory never becomes borrowed renderer state.</p>
	 */
	private MemorySegment encodeWorldLodInstances(List<WorldLodColumnInstanceRecord> instances) {
		Objects.requireNonNull(instances, "worldLodInstances");
		int count = instances.size();
		if (count == 0) {
			persistentWorldLodInstanceCount = 0;
			return MemorySegment.NULL;
		}
		if (count > MAX_PERSISTENT_WORLD_LOD_INSTANCES) {
			throw new IllegalArgumentException(
				"world LOD instance count exceeds reusable staging bound: " + count);
		}
		boolean unchanged = count == persistentWorldLodInstanceCount;
		if (unchanged) {
			for (int index = 0; index < count; index++) {
				WorldLodColumnInstanceRecord instance = Objects.requireNonNull(
					instances.get(index), "worldLodInstances[" + index + "]");
				if (persistentWorldLodLayers[index] != instance.layer()
					|| persistentWorldLodSegments[index] != instance.segmentIndex()
					|| persistentWorldLodOrders[index] != instance.order()
					|| persistentWorldLodKeys[index] != instance.columnKey()
					|| persistentWorldLodGenerations[index] != instance.columnGeneration()) {
					unchanged = false;
					break;
				}
			}
		}
		if (!unchanged) {
			ensureWorldLodInstanceStagingCapacity(count);
			for (int index = 0; index < count; index++) {
				WorldLodColumnInstanceRecord instance = Objects.requireNonNull(
					instances.get(index), "worldLodInstances[" + index + "]");
				MemorySegment item = Abi.item(
					persistentWorldLodInstanceArray,
					Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD,
					index);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.offset(0),
					Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.byteSize());
				Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.setInt(item, 1, instance.layer());
				Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.setInt(item, 2, instance.segmentIndex());
				Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.setInt(item, 3, instance.order());
				Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.setLong(item, 4, instance.columnKey());
				Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.setLong(item, 5, instance.columnGeneration());
				persistentWorldLodLayers[index] = instance.layer();
				persistentWorldLodSegments[index] = instance.segmentIndex();
				persistentWorldLodOrders[index] = instance.order();
				persistentWorldLodKeys[index] = instance.columnKey();
				persistentWorldLodGenerations[index] = instance.columnGeneration();
			}
			persistentWorldLodInstanceCount = count;
		}
		return persistentWorldLodInstanceArray.asSlice(
			0L,
			(long) Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.byteSize() * count);
	}

	private void ensureWorldLodInstanceStagingCapacity(int requiredCount) {
		if (requiredCount <= persistentWorldLodInstanceCapacity) {
			return;
		}
		int capacity = Math.max(64, persistentWorldLodInstanceCapacity);
		while (capacity < requiredCount) {
			capacity = Math.min(MAX_PERSISTENT_WORLD_LOD_INSTANCES, capacity * 2);
		}
		persistentWorldLodInstanceArray = Struct.WORLD_LOD_COLUMN_INSTANCE_RECORD.array(contextArena, capacity);
		persistentWorldLodInstanceCapacity = capacity;
		persistentWorldLodLayers = new int[capacity];
		persistentWorldLodSegments = new int[capacity];
		persistentWorldLodOrders = new int[capacity];
		persistentWorldLodKeys = new long[capacity];
		persistentWorldLodGenerations = new long[capacity];
	}

	private void ensureMaterialKeyBucketCapacity(int quadCount) {
		if (quadCount > (1 << 29)) {
			throw new IllegalArgumentException("world material quad count exceeds key-table capacity");
		}
		int required = Math.max(64, quadCount * 2);
		if (materialKeyBucketsScratch.length >= required) {
			return;
		}
		int capacity = Math.max(64, materialKeyBucketsScratch.length);
		while (capacity < required) {
			capacity *= 2;
		}
		materialKeyBucketsScratch = new int[capacity];
	}

	private static void traceWorldMaterialFrame(
		long frameId,
		int quadCount,
		List<WorldMaterialKeyRecord> materialTable
	) {
		if (!TRACE_WORLD_MATERIAL_FRAME || materialTable.isEmpty()
			|| WORLD_MATERIAL_FRAME_TRACE_LOGS.getAndIncrement() >= TRACE_WORLD_MATERIAL_FRAME_MAX_LOGS) {
			return;
		}
		StringBuilder message = new StringBuilder("[MattMC graphics audit] world-material-frame")
			.append(" frame=").append(frameId)
			.append(" quads=").append(quadCount)
			.append(" table=").append(materialTable.size());
		for (int index = 0; index < materialTable.size(); index++) {
			if (index == 8) {
				message.append(" ...");
				break;
			}
			WorldMaterialKeyRecord key = materialTable.get(index);
			message.append(" entry[").append(index).append("]={")
				.append("stratum=").append(key.stratum())
				.append(",material=").append(key.materialId())
				.append(",texture=").append(key.textureId())
				.append(",mode=").append(key.materialMode())
				.append(",depth=").append(key.depthPolicy())
				.append(",cull=").append(key.cullPolicy())
				.append(",topology=").append(key.topology())
				.append(",winding=").append(key.winding())
				.append('}');
		}
		System.out.println(message);
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
				// The record defensively copied this payload at construction and its
				// public accessor keeps returning a copy. As the enclosing bridge we
				// can copy the private immutable bytes straight into confined FFI
				// memory instead of cloning the complete atlas once more first.
				Abi.writeBytes(updateArena, item, Struct.GUI_ASSET_PAYLOAD, 2, asset.pngBytes);
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

	/**
	 * Uploads copied semantic GUI image assets. Intended for font atlases and
	 * other source-owned images; callers cannot pass atlas objects or textures.
	 */
	public Status updateGuiRawImages(long generation, List<GuiRawImageAssetRecord> assets) {
		Objects.requireNonNull(assets, "assets");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment assetArray = Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.array(updateArena, assets.size());
			for (int i = 0; i < assets.size(); i++) {
				GuiRawImageAssetRecord asset = assets.get(i);
				MemorySegment item = Abi.item(assetArray, Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.offset(0), Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.byteSize());
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setInt(item, 1, asset.format());
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setLong(item, 2, asset.assetId());
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setInt(item, 3, asset.width());
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setInt(item, 4, asset.height());
				// `pixels` is an immutable defensive copy owned by the record. The
				// native request receives its own confined-memory copy below, so the
				// public cloning accessor is unnecessary on this trusted bridge path.
				Abi.writeBytes(updateArena, item, Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD, 5, asset.pixels);
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setInt(item, 6, asset.samplingFilter());
				Struct.GUI_RAW_IMAGE_ASSET_PAYLOAD.setInt(item, 7, asset.samplingAddress());
			}
			MemorySegment request = Struct.GUI_RAW_IMAGE_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.GUI_RAW_IMAGE_UPDATE);
			Struct.GUI_RAW_IMAGE_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.GUI_RAW_IMAGE_UPDATE, 2, assetArray, assets.size());
			Struct.GUI_RAW_IMAGE_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.guiUpdateRawImages(contextId, request, status), "raw GUI image update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/** Publishes semantic declarations only; it does not admit GUI atlas rendering. */
	public Status updateGuiAtlasReferences(long revision, List<GuiAtlasReferenceRecord> references) {
		Objects.requireNonNull(references, "references");
		if (revision == 0L || references.size() > 4096) {
			throw new IllegalArgumentException("invalid GUI atlas reference revision or count");
		}
		List<GuiAtlasReferenceRecord> copied = List.copyOf(references);
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment array = Struct.GUI_ATLAS_REFERENCE.array(updateArena, copied.size());
			for (int i = 0; i < copied.size(); i++) {
				GuiAtlasReferenceRecord reference = copied.get(i);
				MemorySegment item = Abi.item(array, Struct.GUI_ATLAS_REFERENCE, i);
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 0, Struct.GUI_ATLAS_REFERENCE.byteSize());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 1, reference.textureId());
				Struct.GUI_ATLAS_REFERENCE.setLong(item, 2, reference.assetId());
				Struct.GUI_ATLAS_REFERENCE.setLong(item, 3, reference.atlasGeneration());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 4, reference.atlasWidth());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 5, reference.atlasHeight());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 6, reference.x());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 7, reference.y());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 8, reference.width());
				Struct.GUI_ATLAS_REFERENCE.setInt(item, 9, reference.height());
			}
			MemorySegment request = Struct.GUI_ATLAS_REFERENCE_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.GUI_ATLAS_REFERENCE_UPDATE);
			Struct.GUI_ATLAS_REFERENCE_UPDATE.setLong(request, 1, revision);
			Abi.writeSlice(request, Struct.GUI_ATLAS_REFERENCE_UPDATE, 2, array, copied.size());
			Struct.GUI_ATLAS_REFERENCE_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.guiUpdateAtlasReferences(contextId, request, status), "GUI atlas reference update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status),
				Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/**
	 * Uploads copied world-text atlas pixels. The payload is intentionally
	 * distinct from GUI images because world-text lifetime and depth ordering
	 * belong to the world frontend.
	 */
	public Status updateWorldTextImages(long generation, List<WorldTextImageAssetRecord> assets) {
		Objects.requireNonNull(assets, "assets");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment assetArray = Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.array(updateArena, assets.size());
			for (int i = 0; i < assets.size(); i++) {
				WorldTextImageAssetRecord asset = assets.get(i);
				MemorySegment item = Abi.item(assetArray, Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.offset(0), Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.byteSize());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setInt(item, 1, asset.format());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setInt(item, 2, asset.width());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setInt(item, 3, asset.height());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setLong(item, 4, asset.assetId());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setLong(item, 5, asset.atlasGeneration());
				Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD.setLong(item, 6, asset.atlasRevision());
				Abi.writeBytes(updateArena, item, Struct.WORLD_TEXT_IMAGE_ASSET_PAYLOAD, 7, asset.pixels());
			}
			MemorySegment request = Struct.WORLD_TEXT_IMAGE_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_TEXT_IMAGE_UPDATE);
			Struct.WORLD_TEXT_IMAGE_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_TEXT_IMAGE_UPDATE, 2, assetArray, assets.size());
			Struct.WORLD_TEXT_IMAGE_UPDATE.setLong(request, 3, negotiatedFeatures);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldTextUpdateImages(contextId, request, status), "world text image update");
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
		return updateWorldMeshAssets(generation, meshes, textures, sortedIndices, List.of());
	}

	/**
	 * Publishes immutable mesh additions/replacements together with explicit
	 * generation-guarded retirements. This is a resource-lifetime transaction;
	 * it never relies on a renderer reset or an implicit cache flush.
	 */
	public Status updateWorldMeshAssets(
		long generation,
		List<WorldMeshAssetRecord> meshes,
		List<WorldMeshTextureAssetRecord> textures,
		List<WorldMeshSortedIndexRecord> sortedIndices,
		List<WorldMeshAssetRetirementRecord> retirements
	) {
		return updateWorldMeshAssets(generation, meshes, textures, sortedIndices, retirements, List.of());
	}

	/** Typed immutable orb appearances use the same explicit mesh lifetime transaction. */
	public Status updateWorldMeshAssets(
		long generation,
		List<WorldMeshAssetRecord> meshes,
		List<WorldMeshTextureAssetRecord> textures,
		List<WorldMeshSortedIndexRecord> sortedIndices,
		List<WorldMeshAssetRetirementRecord> retirements,
		List<WorldExperienceOrbAssetRecord> experienceOrbs
	) {
		Objects.requireNonNull(meshes, "meshes");
		Objects.requireNonNull(textures, "textures");
		Objects.requireNonNull(sortedIndices, "sortedIndices");
		Objects.requireNonNull(retirements, "retirements");
		Objects.requireNonNull(experienceOrbs, "experienceOrbs");
		if ((long) meshes.size() + experienceOrbs.size() > 16_384)
			throw new IllegalArgumentException("combined world mesh/orb asset count exceeds residency");
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment textureArray = Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.array(updateArena, textures.size());
			for (int i = 0; i < textures.size(); i++) {
				WorldMeshTextureAssetRecord texture = textures.get(i);
				MemorySegment item = Abi.item(textureArray, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.offset(0), Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.byteSize());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 1, texture.textureId());
				// The record already owns an immutable defensive copy. The confined
				// FFI arena copies it synchronously, so cloning it once more here only
				// doubles transient allocation at the publication boundary.
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, 2, texture.pngBytes);
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 3, texture.frameWidth());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 4, texture.frameHeight());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 5, texture.frameCount());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 6, texture.frameTicks());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 7, texture.animationFlags());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 8, texture.frameRowSize());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 9, texture.interpolationPolicy());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 10, texture.coordinateOrigin());
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
				List<byte[]> mipPngBytes = texture.mipPngBytes;
				MemorySegment mipPngArray = Struct.BYTES.array(updateArena, mipPngBytes.size());
				for (int mipIndex = 0; mipIndex < mipPngBytes.size(); mipIndex++) {
					Abi.writeBytes(updateArena, Abi.item(mipPngArray, Struct.BYTES, mipIndex), Struct.BYTES, 0, mipPngBytes.get(mipIndex));
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD, 12, mipPngArray, mipPngBytes.size());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 13, texture.samplingFilter());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 14, texture.samplingAddress());
				Struct.WORLD_MESH_TEXTURE_ASSET_PAYLOAD.setInt(item, 15, texture.requestedMipLevels());
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
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 13, vertex.terrainMaterialBits());
					Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 14, vertex.midBlockPacked());
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_ASSET_RECORD, 6, vertexArray, mesh.vertices().size());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_ASSET_RECORD, 7, mesh.indexBytes);
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
					Struct.WORLD_MESH_SECTION_RECORD.setInt(sectionItem, 8, section.sourceFacing());
				}
				Abi.writeSlice(item, Struct.WORLD_MESH_ASSET_RECORD, 8, sectionArray, mesh.sections().size());
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_ASSET_RECORD, 9, mesh.entityIdentity());
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
				Abi.writeBytes(updateArena, item, Struct.WORLD_MESH_SORTED_INDEX_RECORD, 6, sortedIndex.indexBytes);
			}
			MemorySegment retirementArray = Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.array(updateArena, retirements.size());
			for (int i = 0; i < retirements.size(); i++) {
				WorldMeshAssetRetirementRecord retirement = Objects.requireNonNull(retirements.get(i), "retirements[" + i + "]");
				MemorySegment item = Abi.item(retirementArray, Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD, i);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.offset(0), Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.byteSize());
				Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.setInt(item, 1, 0);
				Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.setLong(item, 2, retirement.meshKey());
				Struct.WORLD_MESH_ASSET_RETIREMENT_RECORD.setLong(item, 3, retirement.meshGeneration());
			}
			MemorySegment request = Struct.WORLD_MESH_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_MESH_ASSET_UPDATE);
			Struct.WORLD_MESH_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 2, meshArray, meshes.size());
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 3, textureArray, textures.size());
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 4, sortedIndexArray, sortedIndices.size());
			Struct.WORLD_MESH_ASSET_UPDATE.setLong(request, 5, negotiatedFeatures);
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 6, retirementArray, retirements.size());
			Abi.writeSlice(request, Struct.WORLD_MESH_ASSET_UPDATE, 7,
				encodeExperienceOrbAssets(updateArena, experienceOrbs), experienceOrbs.size());
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldMeshUpdateAssets(contextId, request, status), "world mesh asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/**
	 * Copies Distant Horizons CPU LOD column semantics into the private Rust
	 * registry. This does not select a route or issue a draw; callers remain
	 * responsible for admitting the copied assets before the Rust LOD material
	 * and pass path consumes them.
	 */
	public Status updateWorldLodAssets(
		long generation,
		List<WorldLodColumnAssetRecord> assets,
		List<WorldLodColumnRetirementRecord> retirements
	) {
		return updateWorldLodAssets(generation, assets, retirements, List.of());
	}

	public Status updateWorldLodAssets(
		long generation,
		List<WorldLodColumnAssetRecord> assets,
		List<WorldLodColumnRetirementRecord> retirements,
		List<WorldLodColumnMaterialProvenanceRecord> materialProvenance
	) {
		Objects.requireNonNull(assets, "assets");
		Objects.requireNonNull(retirements, "retirements");
		Objects.requireNonNull(materialProvenance, "materialProvenance");
		try (Arena updateArena = Arena.ofConfined()) {
			Map<String, MemorySegment> updateIdentitySegments = new LinkedHashMap<>();
			MemorySegment assetArray = Struct.WORLD_LOD_COLUMN_ASSET_RECORD.array(updateArena, assets.size());
			for (int assetIndex = 0; assetIndex < assets.size(); assetIndex++) {
				WorldLodColumnAssetRecord asset = Objects.requireNonNull(assets.get(assetIndex), "assets[" + assetIndex + "]");
				MemorySegment item = Abi.item(assetArray, Struct.WORLD_LOD_COLUMN_ASSET_RECORD, assetIndex);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_COLUMN_ASSET_RECORD.offset(0), Struct.WORLD_LOD_COLUMN_ASSET_RECORD.byteSize());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setInt(item, 1, asset.vertexLayoutVersion());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setInt(item, 2, asset.originX());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setInt(item, 3, asset.originY());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setInt(item, 4, asset.originZ());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setInt(item, 5, 0);
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setLong(item, 6, asset.columnKey());
				Struct.WORLD_LOD_COLUMN_ASSET_RECORD.setLong(item, 7, asset.columnGeneration());
				MemorySegment segmentArray = Struct.WORLD_LOD_SEGMENT_RECORD.array(updateArena, asset.segments().size());
				for (int segmentIndex = 0; segmentIndex < asset.segments().size(); segmentIndex++) {
				WorldLodSegmentRecord segment = asset.segments().get(segmentIndex);
				MemorySegment segmentItem = Abi.item(segmentArray, Struct.WORLD_LOD_SEGMENT_RECORD, segmentIndex);
				segmentItem.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_SEGMENT_RECORD.offset(0), Struct.WORLD_LOD_SEGMENT_RECORD.byteSize());
				Struct.WORLD_LOD_SEGMENT_RECORD.setInt(segmentItem, 1, segment.layer());
				if (segment.hasPackedVertices()) {
					byte[] packedVertexBytes = segment.packedVertexBytes();
					MemorySegment packedVertices = updateArena.allocate(packedVertexBytes.length);
					packedVertices.copyFrom(MemorySegment.ofArray(packedVertexBytes));
					Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_RECORD, 2, MemorySegment.NULL, 0);
					Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_RECORD, 3,
						packedVertices, packedVertexBytes.length);
					} else {
						MemorySegment vertexArray = Struct.WORLD_LOD_VERTEX.array(updateArena, segment.vertices().size());
						for (int vertexIndex = 0; vertexIndex < segment.vertices().size(); vertexIndex++) {
							WorldLodVertexRecord vertex = segment.vertices().get(vertexIndex);
							MemorySegment vertexItem = Abi.item(vertexArray, Struct.WORLD_LOD_VERTEX, vertexIndex);
							vertexItem.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_VERTEX.offset(0), Struct.WORLD_LOD_VERTEX.byteSize());
							vertexItem.set(ValueLayout.JAVA_SHORT, Struct.WORLD_LOD_VERTEX.offset(1), (short)vertex.localX());
							vertexItem.set(ValueLayout.JAVA_SHORT, Struct.WORLD_LOD_VERTEX.offset(2), (short)vertex.localY());
							vertexItem.set(ValueLayout.JAVA_SHORT, Struct.WORLD_LOD_VERTEX.offset(3), (short)vertex.localZ());
							vertexItem.set(ValueLayout.JAVA_SHORT, Struct.WORLD_LOD_VERTEX.offset(4), (short)vertex.packedLightAndMicroOffset());
							Struct.WORLD_LOD_VERTEX.setInt(vertexItem, 5, vertex.colorRgba());
							Struct.WORLD_LOD_VERTEX.setInt(vertexItem, 6, vertex.materialId());
							Struct.WORLD_LOD_VERTEX.setInt(vertexItem, 7, vertex.normalIndex());
						}
						Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_RECORD, 2, vertexArray, segment.vertices().size());
						Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_RECORD, 3, MemorySegment.NULL, 0);
					}
				}
				Abi.writeSlice(item, Struct.WORLD_LOD_COLUMN_ASSET_RECORD, 8, segmentArray, asset.segments().size());
			}
			MemorySegment retirementArray = Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.array(updateArena, retirements.size());
			for (int index = 0; index < retirements.size(); index++) {
				WorldLodColumnRetirementRecord retirement = Objects.requireNonNull(retirements.get(index), "retirements[" + index + "]");
				MemorySegment item = Abi.item(retirementArray, Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD, index);
				item.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.offset(0), Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.byteSize());
				Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.setInt(item, 1, 0);
				Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.setLong(item, 2, retirement.columnKey());
				Struct.WORLD_LOD_COLUMN_RETIREMENT_RECORD.setLong(item, 3, retirement.columnGeneration());
			}
			MemorySegment provenanceArray = Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.array(updateArena, materialProvenance.size());
			for (int provenanceIndex = 0; provenanceIndex < materialProvenance.size(); provenanceIndex++) {
				WorldLodColumnMaterialProvenanceRecord provenance = Objects.requireNonNull(
					materialProvenance.get(provenanceIndex), "materialProvenance[" + provenanceIndex + "]"
				);
				MemorySegment provenanceItem = Abi.item(
					provenanceArray, Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD, provenanceIndex
				);
				provenanceItem.set(ValueLayout.JAVA_INT,
					Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.offset(0),
					Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.byteSize());
				Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.setInt(provenanceItem, 1, 0);
				Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.setLong(provenanceItem, 2, provenance.columnKey());
				Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD.setLong(provenanceItem, 3, provenance.columnGeneration());
				MemorySegment identityArray = Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD.array(updateArena, provenance.identities().size());
				for (int identityIndex = 0; identityIndex < provenance.identities().size(); identityIndex++) {
					WorldLodMaterialIdentityRecord identity = provenance.identities().get(identityIndex);
					MemorySegment identityItem = Abi.item(identityArray, Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD, identityIndex);
					identityItem.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD.offset(0),
						Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD.byteSize());
					Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD.setInt(identityItem, 1, 0);
					Abi.writeCachedBytes(updateArena, identityItem, Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD, 2,
						updateIdentitySegments, identity.blockStateIdentity());
					Abi.writeCachedBytes(updateArena, identityItem, Struct.WORLD_LOD_MATERIAL_IDENTITY_RECORD, 3,
						updateIdentitySegments, identity.biomeIdentity());
				}
				MemorySegment segmentArray = Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.array(updateArena, provenance.segments().size());
				for (int segmentIndex = 0; segmentIndex < provenance.segments().size(); segmentIndex++) {
					WorldLodSegmentMaterialProvenanceRecord segment = provenance.segments().get(segmentIndex);
					// These record accessors return defensive copies. Keep one copy per
					// segment while packing so a large DH update does not clone the same
					// provenance arrays once for every length check and element write.
					int[] quadMaterialIds = segment.quadMaterialIds();
					byte[] quadVariantStates = segment.quadVariantStates();
					long[] quadVariantPositions = segment.quadVariantPositions();
					MemorySegment segmentItem = Abi.item(segmentArray, Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD, segmentIndex);
					segmentItem.set(ValueLayout.JAVA_INT,
						Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.offset(0),
						Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.byteSize());
					Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.setInt(segmentItem, 1, segment.layer());
					Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.setInt(segmentItem, 2, segment.segmentIndex());
					Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD.setInt(segmentItem, 3, 0);
					MemorySegment materialIds = updateArena.allocate(ValueLayout.JAVA_INT, quadMaterialIds.length);
					// The Java arrays are immutable record-owned copies. Bulk-copy their
					// native-order bytes into the confined arena instead of issuing one
					// FFM scalar write per quad; the arena remains the sole FFI lifetime
					// owner and Rust still receives a copied slice.
					materialIds.copyFrom(MemorySegment.ofArray(quadMaterialIds));
					Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD, 4,
						materialIds, quadMaterialIds.length);
					MemorySegment variantStates = updateArena.allocate(ValueLayout.JAVA_BYTE, quadVariantStates.length);
					MemorySegment variantPositions = updateArena.allocate(ValueLayout.JAVA_LONG, quadVariantPositions.length);
					variantStates.copyFrom(MemorySegment.ofArray(quadVariantStates));
					variantPositions.copyFrom(MemorySegment.ofArray(quadVariantPositions));
					Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD, 5,
						variantStates, quadVariantStates.length);
					Abi.writeSlice(segmentItem, Struct.WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD, 6,
						variantPositions, quadVariantPositions.length);
				}
				MemorySegment faceMaterialArray = Struct.WORLD_LOD_FACE_MATERIAL_RECORD.array(updateArena, provenance.faceMaterials().size());
				for (int faceIndex = 0; faceIndex < provenance.faceMaterials().size(); faceIndex++) {
					WorldLodFaceMaterialRecord face = provenance.faceMaterials().get(faceIndex);
					MemorySegment faceItem = Abi.item(faceMaterialArray, Struct.WORLD_LOD_FACE_MATERIAL_RECORD, faceIndex);
					faceItem.set(ValueLayout.JAVA_INT, Struct.WORLD_LOD_FACE_MATERIAL_RECORD.offset(0), Struct.WORLD_LOD_FACE_MATERIAL_RECORD.byteSize());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setInt(faceItem, 1, face.materialId());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setInt(faceItem, 2, face.face());
					// The Rust ABI stores semantic tint channels in explicit
					// red/green/blue bit lanes. A packed Java RGB integer is
					// numerically BGR when read from its least-significant byte,
					// so do not shift the 0xRRGGBB value as one word.
					int packedTint = packWorldLodTint(face.tintArgb());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setInt(faceItem, 3,
						face.faceLayer() | (face.tinted() ? 0x4 : 0) | packedTint);
					Abi.writeCachedBytes(updateArena, faceItem, Struct.WORLD_LOD_FACE_MATERIAL_RECORD, 4,
						updateIdentitySegments, face.atlasIdentity());
					Abi.writeCachedBytes(updateArena, faceItem, Struct.WORLD_LOD_FACE_MATERIAL_RECORD, 5,
						updateIdentitySegments, face.spriteIdentity());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setFloat(faceItem, 6, face.u0());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setFloat(faceItem, 7, face.v0());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setFloat(faceItem, 8, face.u1());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setFloat(faceItem, 9, face.v1());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setInt(faceItem, 10, face.uvCornerOrder());
					Struct.WORLD_LOD_FACE_MATERIAL_RECORD.setLong(faceItem, 11, face.variantPosition());
				}
				Abi.writeSlice(provenanceItem, Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD, 4,
					identityArray, provenance.identities().size());
				Abi.writeSlice(provenanceItem, Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD, 5,
					segmentArray, provenance.segments().size());
				Abi.writeSlice(provenanceItem, Struct.WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD, 6,
					faceMaterialArray, provenance.faceMaterials().size());
			}
			MemorySegment request = Struct.WORLD_LOD_ASSET_UPDATE.allocate(updateArena);
			Abi.writeHeader(request, Struct.WORLD_LOD_ASSET_UPDATE);
			Struct.WORLD_LOD_ASSET_UPDATE.setLong(request, 1, generation);
			Abi.writeSlice(request, Struct.WORLD_LOD_ASSET_UPDATE, 2, assetArray, assets.size());
			Abi.writeSlice(request, Struct.WORLD_LOD_ASSET_UPDATE, 3, retirementArray, retirements.size());
			Struct.WORLD_LOD_ASSET_UPDATE.setLong(request, 4, negotiatedFeatures);
			Abi.writeSlice(request, Struct.WORLD_LOD_ASSET_UPDATE, 5, provenanceArray, materialProvenance.size());
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.worldLodUpdateAssets(contextId, request, status), "world LOD asset update");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status), Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	public record GuiAssetRecord(int spriteId, byte[] pngBytes) {
		public GuiAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
			pngBytes = pngBytes.clone();
		}

		@Override
		public byte[] pngBytes() {
			return this.pngBytes.clone();
		}

		/** Allocation-free payload size for staging metrics. */
		public int pngByteLength() {
			return this.pngBytes.length;
		}
	}

	/** Immutable source identity and region; contains no selected pixels or GPU state. */
	public record GuiAtlasReferenceRecord(long assetId, int textureId, long atlasGeneration,
		int atlasWidth, int atlasHeight, int x, int y, int width, int height) {
		public GuiAtlasReferenceRecord {
			if (assetId == 0L || textureId == 0 || atlasGeneration == 0L
				|| atlasWidth <= 0 || atlasHeight <= 0 || (long) atlasWidth * atlasHeight > 16L * 1024 * 1024
				|| x < 0 || y < 0 || width <= 0 || height <= 0
				|| (long) x + width > atlasWidth || (long) y + height > atlasHeight) {
				throw new IllegalArgumentException("invalid semantic GUI atlas reference");
			}
		}
	}

	/** Format values deliberately match the semantic Rust image contract. */
	public record GuiRawImageAssetRecord(long assetId, int format, int width, int height, byte[] pixels, int samplingFilter, int samplingAddress) {
		public GuiRawImageAssetRecord(long assetId, int format, int width, int height, byte[] pixels) {
			this(assetId, format, width, height, pixels, 0, 0);
		}
		private static final int MAX_RAW_IMAGE_BYTES = 64 * 1024 * 1024;
		private static final int MAX_RAW_IMAGE_DIMENSION = 8192;
		private static final int MAX_RAW_IMAGE_PIXELS = 16 * 1024 * 1024;

		public GuiRawImageAssetRecord {
			if (!((samplingFilter == 0 && samplingAddress == 0)
				|| (samplingFilter >= 1 && samplingFilter <= 2 && samplingAddress >= 1 && samplingAddress <= 2))) {
				throw new IllegalArgumentException("invalid explicit raw GUI image sampling");
			}
			if (assetId == 0L || format < 1 || format > 2 || width <= 0 || height <= 0
				|| width > MAX_RAW_IMAGE_DIMENSION || height > MAX_RAW_IMAGE_DIMENSION) {
				throw new IllegalArgumentException("invalid semantic GUI raw image asset");
			}
			Objects.requireNonNull(pixels, "pixels");
			long pixelCount = (long) width * height;
			int bytesPerPixel = format == 1 ? 1 : 4;
			long expectedBytes = pixelCount * bytesPerPixel;
			if (pixelCount > MAX_RAW_IMAGE_PIXELS || expectedBytes > MAX_RAW_IMAGE_BYTES
				|| pixels.length != expectedBytes) {
				throw new IllegalArgumentException("semantic GUI raw image pixels must exactly match its bounded format and dimensions");
			}
			if (pixels.length > MAX_RAW_IMAGE_BYTES) {
				throw new IllegalArgumentException("semantic GUI raw image exceeds the 64 MiB ABI bound");
			}
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}

		/**
		 * Allocation-free metadata for bounded staging. The backing pixels remain
		 * private and immutable after the constructor's defensive copy.
		 */
		public int pixelByteLength() {
			return this.pixels.length;
		}

		/** Compares immutable payloads without exposing or cloning either array. */
		public boolean hasSamePixels(GuiRawImageAssetRecord other) {
			return other != null && Arrays.equals(this.pixels, other.pixels);
		}
	}

	/** Immutable copied world-text image with semantic atlas generation data. */
	public record WorldTextImageAssetRecord(
		long assetId,
		long atlasGeneration,
		long atlasRevision,
		int format,
		int width,
		int height,
		byte[] pixels
	) {
		private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
		public WorldTextImageAssetRecord {
			if (assetId == 0L || atlasGeneration <= 0L || atlasRevision <= 0L
				|| format < 1 || format > 2 || width <= 0 || height <= 0) {
				throw new IllegalArgumentException("invalid semantic world text image asset");
			}
			Objects.requireNonNull(pixels, "pixels");
			long expectedBytes = (long) width * height * (format == 1 ? 1L : 4L);
			if (expectedBytes <= 0L || expectedBytes > MAX_IMAGE_BYTES || pixels.length != expectedBytes) {
				throw new IllegalArgumentException("semantic world text image pixels must exactly match its bounded format and dimensions");
			}
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}
	}

	public record WorldBorderAssetRecord(int textureId, byte[] pngBytes) {
		private static final int MAX_PNG_BYTES = 2 * 1024 * 1024;
		public WorldBorderAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
			if (pngBytes.length > MAX_PNG_BYTES) {
				throw new IllegalArgumentException("world-border PNG exceeds the 2 MiB ABI bound");
			}
			pngBytes = pngBytes.clone();
		}

		@Override
		public byte[] pngBytes() {
			return this.pngBytes.clone();
		}
	}

	public record WorldCrackAssetRecord(int stage, byte[] pngBytes) {
		private static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
		public WorldCrackAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
			if (stage < 0 || stage >= 10 || pngBytes.length > MAX_PNG_BYTES) {
				throw new IllegalArgumentException("invalid or oversized world-crack asset");
			}
			pngBytes = pngBytes.clone();
		}

		@Override
		public byte[] pngBytes() {
			return this.pngBytes.clone();
		}
	}

	public record WorldMaterialAssetRecord(int textureId, byte[] pngBytes) {
		private static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
		public WorldMaterialAssetRecord {
			Objects.requireNonNull(pngBytes, "pngBytes");
			if (pngBytes.length > MAX_PNG_BYTES) {
				throw new IllegalArgumentException("world-material PNG exceeds the 4 MiB ABI bound");
			}
			pngBytes = pngBytes.clone();
		}

		@Override
		public byte[] pngBytes() {
			return this.pngBytes.clone();
		}
	}

	public record ShaderPackSourceFileRecord(String path, byte[] contentsUtf8) {
		public ShaderPackSourceFileRecord {
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(contentsUtf8, "contentsUtf8");
			contentsUtf8 = contentsUtf8.clone();
		}

		@Override
		public byte[] contentsUtf8() {
			return this.contentsUtf8.clone();
		}
	}

	public record ShaderPackAssetFileRecord(String path, byte[] contents) {
		public ShaderPackAssetFileRecord {
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(contents, "contents");
			contents = contents.clone();
		}

		@Override
		public byte[] contents() {
			return this.contents.clone();
		}
	}

	public record SpriteAnimationMipRecord(int width, int height, byte[] rgba) {
		public SpriteAnimationMipRecord {
			if (width <= 0 || height <= 0 || (long)width * height > 96L * 1024L * 1024L / 4L
				|| (long)width * height * 4L != rgba.length) {
				throw new IllegalArgumentException("Invalid animation mip dimensions");
			}
			rgba = rgba.clone();
		}
		@Override public byte[] rgba() { return rgba.clone(); }
	}

	public record AtlasAnimationSourceRecord(int spriteId, int atlasX, int atlasY,
		int frameWidth, int frameHeight, boolean interpolate,
		List<WorldMeshAnimationFrameRecord> frames, List<SpriteAnimationMipRecord> mips) {
		public AtlasAnimationSourceRecord {
			Objects.requireNonNull(frames, "frames");
			Objects.requireNonNull(mips, "mips");
			if (spriteId <= 0 || atlasX < 0 || atlasY < 0 || frameWidth <= 0 || frameHeight <= 0
				|| frames.isEmpty() || frames.size() > 16384 || mips.isEmpty() || mips.size() > 32
				|| frames.stream().anyMatch(frame -> frame.durationTicks() <= 0)) {
				throw new IllegalArgumentException("Invalid animation source metadata");
			}
			frames = List.copyOf(frames);
			mips = List.copyOf(mips);
		}
	}

	/** Typed staging only. No native submission or capability admission occurs here. */
	public Status stageAtlasAnimationAssets(int textureId, long acceptedTextureGeneration, long initialTick,
		net.minecraft.client.renderer.texture.SemanticAtlasAnimationSource snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (textureId == 0 || acceptedTextureGeneration <= 0 || initialTick < 0) {
			throw new IllegalArgumentException("Invalid atlas animation staging identity");
		}
		// The resource snapshot generation and accepted mesh-update generation
		// are different identity domains. Only the latter binds native storage.
		return stageAtlasAnimationAssets(textureId, acceptedTextureGeneration, initialTick,
			atlasAnimationRecords(snapshot));
	}

	public record AtlasAnimationTickResult(boolean accepted, Status status) {}

	/** Semantic tick transport. Calling this does not admit a live animation capability. */
	public AtlasAnimationTickResult tickAtlasAnimation(int textureId, long generation, long tick,
		int[] visibleSpriteIds, boolean animateOnlyVisible) {
		Objects.requireNonNull(visibleSpriteIds, "visibleSpriteIds");
		if (textureId == 0 || generation <= 0 || tick < 0 || visibleSpriteIds.length > 16384) {
			throw new IllegalArgumentException("Invalid atlas animation tick");
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment ids = visibleSpriteIds.length == 0 ? MemorySegment.NULL
				: arena.allocateFrom(ValueLayout.JAVA_INT, visibleSpriteIds);
			MemorySegment accepted = arena.allocate(ValueLayout.JAVA_INT);
			MemorySegment status = Struct.STATUS.allocate(arena);
			checkStatus(Native.atlasAnimationTick(contextId, textureId, generation, tick, ids,
				(long)visibleSpriteIds.length, animateOnlyVisible ? 1 : 0, accepted, status), "atlas animation tick");
			return new AtlasAnimationTickResult(accepted.get(ValueLayout.JAVA_INT, 0) != 0,
				new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status),
					Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status)));
		}
	}

	static List<AtlasAnimationSourceRecord> atlasAnimationRecords(
		net.minecraft.client.renderer.texture.SemanticAtlasAnimationSource snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (snapshot.generation() <= 0 || snapshot.width() <= 0 || snapshot.height() <= 0
			|| (long)snapshot.width() * snapshot.height() > 16L * 1024L * 1024L
			|| snapshot.mipCount() <= 0 || snapshot.mipCount() > 32 || snapshot.sprites().size() > 16384) {
			throw new IllegalArgumentException("Invalid semantic atlas animation snapshot");
		}
		// Validate aggregate size before defensive record copies. Public semantic
		// records may also be supplied by tests/tools, not only the atlas producer.
		long bytes = 0, declarations = 0;
		java.util.HashSet<Integer> ids = new java.util.HashSet<>();
		for (var sprite : snapshot.sprites()) {
			var source = sprite.source();
			if (!ids.add(sprite.id()) || sprite.id() <= 0 || sprite.x() < 0 || sprite.y() < 0
				|| source.frameWidth() <= 0 || source.frameHeight() <= 0
				|| source.frames().isEmpty() || source.frames().size() > 16384
				|| (long)sprite.x() + source.frameWidth() > snapshot.width()
				|| (long)sprite.y() + source.frameHeight() > snapshot.height()
				|| source.mips().size() != snapshot.mipCount()) {
				throw new IllegalArgumentException("Invalid semantic atlas animation placement");
			}
			if (source.frameRowSize() != source.mips().getFirst().width() / source.frameWidth()) {
				throw new IllegalArgumentException("Inconsistent animation sheet row declaration");
			}
			declarations += source.frames().size() + source.mips().size();
			if (declarations > 65536) throw new IllegalArgumentException("Animation declarations exceeded");
			for (var mip : source.mips()) {
				bytes = Math.addExact(bytes, (long)mip.width() * mip.height() * 4L);
				if (bytes > 96L * 1024L * 1024L) throw new IllegalArgumentException("Animation bytes exceeded");
			}
		}
		return snapshot.sprites().stream().map(sprite -> {
			var source = sprite.source();
			return new AtlasAnimationSourceRecord(sprite.id(), sprite.x(), sprite.y(),
				source.frameWidth(), source.frameHeight(), source.interpolate(),
				source.frames().stream().map(frame -> new WorldMeshAnimationFrameRecord(
					frame.index(), frame.durationTicks())).toList(),
				source.mips().stream().map(mip -> new SpriteAnimationMipRecord(
					mip.width(), mip.height(), mip.rgba())).toList());
		}).toList();
	}

	/** Typed staging only. No native submission or capability admission occurs here. */
	Status stageAtlasAnimationAssets(int textureId, long generation, long initialTick,
		List<AtlasAnimationSourceRecord> sprites) {
		try (Arena updateArena = Arena.ofConfined()) {
			MemorySegment request = encodeAtlasAnimationUpdate(updateArena, textureId, generation, initialTick, sprites);
			MemorySegment status = Struct.STATUS.allocate(updateArena);
			checkStatus(Native.atlasAnimationStageAssets(contextId, request, status), "atlas animation staging");
			return new Status(Struct.STATUS.getLong(status, 5), Struct.STATUS.metricsFfiCalls(status),
				Struct.STATUS.metricsFfiInputBytes(status), Struct.STATUS.backendMetrics(status));
		}
	}

	/** Encoding only; native staging copies the payload into Rust-owned storage. */
	static MemorySegment encodeAtlasAnimationUpdate(Arena arena, int textureId, long generation,
		long initialTick, List<AtlasAnimationSourceRecord> sprites) {
		Objects.requireNonNull(arena, "arena");
		Objects.requireNonNull(sprites, "sprites");
		if (textureId == 0 || generation <= 0 || initialTick < 0 || sprites.size() > 16384) {
			throw new IllegalArgumentException("Invalid atlas animation update");
		}
		sprites = List.copyOf(sprites);
		long bytes = 0L;
		long frames = 0L;
		long mips = 0L;
		var ids = new java.util.HashSet<Integer>();
		for (AtlasAnimationSourceRecord sprite : sprites) {
			if (!ids.add(sprite.spriteId())) throw new IllegalArgumentException("Duplicate animation sprite ID");
			frames += sprite.frames().size();
			mips += sprite.mips().size();
			for (SpriteAnimationMipRecord mip : sprite.mips()) bytes = Math.addExact(bytes, mip.rgba.length);
			if (bytes > 96L * 1024L * 1024L || frames > 65536L || mips > 65536L) {
				throw new IllegalArgumentException("Atlas animation update exceeds owned resource bounds");
			}
		}
		// All nested aggregate bounds are checked before the first arena allocation.
		MemorySegment sources = Struct.SPRITE_ANIMATION_SOURCE.array(arena, sprites.size());
		for (int i = 0; i < sprites.size(); i++) {
			AtlasAnimationSourceRecord sprite = sprites.get(i);
			MemorySegment item = Abi.item(sources, Struct.SPRITE_ANIMATION_SOURCE, i);
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 0, Struct.SPRITE_ANIMATION_SOURCE.byteSize());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 1, sprite.spriteId());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 2, sprite.atlasX());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 3, sprite.atlasY());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 4, sprite.frameWidth());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 5, sprite.frameHeight());
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 6, sprite.interpolate() ? 1 : 0);
			Struct.SPRITE_ANIMATION_SOURCE.setInt(item, 7, 0);
			MemorySegment frameArray = Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.array(arena, sprite.frames().size());
			for (int f = 0; f < sprite.frames().size(); f++) {
				var frame = sprite.frames().get(f);
				MemorySegment frameItem = Abi.item(frameArray, Struct.WORLD_MESH_ANIMATION_FRAME_RECORD, f);
				Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 0, Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.byteSize());
				Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 1, frame.frameIndex());
				Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 2, frame.durationTicks());
				Struct.WORLD_MESH_ANIMATION_FRAME_RECORD.setInt(frameItem, 3, 0);
			}
			MemorySegment mipArray = Struct.SPRITE_ANIMATION_MIP.array(arena, sprite.mips().size());
			for (int m = 0; m < sprite.mips().size(); m++) {
				var mip = sprite.mips().get(m);
				MemorySegment mipItem = Abi.item(mipArray, Struct.SPRITE_ANIMATION_MIP, m);
				Struct.SPRITE_ANIMATION_MIP.setInt(mipItem, 0, Struct.SPRITE_ANIMATION_MIP.byteSize());
				Struct.SPRITE_ANIMATION_MIP.setInt(mipItem, 1, mip.width());
				Struct.SPRITE_ANIMATION_MIP.setInt(mipItem, 2, mip.height());
				Struct.SPRITE_ANIMATION_MIP.setInt(mipItem, 3, 0);
				Abi.writeBytes(arena, mipItem, Struct.SPRITE_ANIMATION_MIP, 4, mip.rgba);
			}
			Abi.writeSlice(item, Struct.SPRITE_ANIMATION_SOURCE, 8, frameArray, sprite.frames().size());
			Abi.writeSlice(item, Struct.SPRITE_ANIMATION_SOURCE, 9, mipArray, sprite.mips().size());
		}
		MemorySegment request = Struct.ATLAS_ANIMATION_ASSET_UPDATE.allocate(arena);
		Abi.writeHeader(request, Struct.ATLAS_ANIMATION_ASSET_UPDATE);
		Struct.ATLAS_ANIMATION_ASSET_UPDATE.setInt(request, 1, textureId);
		Struct.ATLAS_ANIMATION_ASSET_UPDATE.setInt(request, 2, 0);
		Struct.ATLAS_ANIMATION_ASSET_UPDATE.setLong(request, 3, generation);
		Struct.ATLAS_ANIMATION_ASSET_UPDATE.setLong(request, 4, initialTick);
		Abi.writeSlice(request, Struct.ATLAS_ANIMATION_ASSET_UPDATE, 5, sources, sprites.size());
		return request;
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
		List<WorldMeshAnimationFrameRecord> animationFrames,
		int coordinateOrigin,
		List<byte[]> mipPngBytes,
		int samplingFilter,
		int samplingAddress,
		int requestedMipLevels
	) {
		public WorldMeshTextureAssetRecord(int textureId, byte[] pngBytes, int frameWidth,
			int frameHeight, int frameCount, int frameTicks, int animationFlags, int frameRowSize,
			int interpolationPolicy, List<WorldMeshAnimationFrameRecord> animationFrames,
			int coordinateOrigin, List<byte[]> mipPngBytes, int samplingFilter, int samplingAddress) {
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags,
				frameRowSize, interpolationPolicy, animationFrames, coordinateOrigin, mipPngBytes, samplingFilter, samplingAddress, 0);
		}

		/** Exact resource storage levels, including mip zero; no backend state is consulted. */
		public WorldMeshTextureAssetRecord withMipLevels(int levels) {
			if (levels < 1) throw new IllegalArgumentException("explicit texture mip count must be positive");
			return new WorldMeshTextureAssetRecord(textureId, pngBytes, frameWidth, frameHeight,
				frameCount, frameTicks, animationFlags, frameRowSize, interpolationPolicy,
				animationFrames, coordinateOrigin, mipPngBytes, samplingFilter, samplingAddress, levels);
		}
		/** Zero means the rendering family's declared sampling default; 1/2 are nearest/linear and repeat/clamp. */
		public WorldMeshTextureAssetRecord(int textureId, byte[] pngBytes, int frameWidth,
			int frameHeight, int frameCount, int frameTicks, int animationFlags, int frameRowSize,
			int interpolationPolicy, List<WorldMeshAnimationFrameRecord> animationFrames,
			int coordinateOrigin, List<byte[]> mipPngBytes) {
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags,
				frameRowSize, interpolationPolicy, animationFrames, coordinateOrigin, mipPngBytes, 0, 0);
		}

		public WorldMeshTextureAssetRecord withTextureMetadata(boolean blur, boolean clamp) {
			return new WorldMeshTextureAssetRecord(textureId, pngBytes, frameWidth, frameHeight,
				frameCount, frameTicks, animationFlags, frameRowSize, interpolationPolicy,
				animationFrames, coordinateOrigin, mipPngBytes, blur ? 2 : 1, clamp ? 2 : 1, requestedMipLevels);
		}
		public WorldMeshTextureAssetRecord(int textureId, byte[] pngBytes) {
			this(textureId, pngBytes, 0, 0, 1, 1, 0, 0, 0, List.of(), WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC, List.of());
		}

		/** Complete copied mip chain for a semantic atlas, excluding mip zero. */
		public WorldMeshTextureAssetRecord(int textureId, byte[] pngBytes, List<byte[]> mipPngBytes) {
			this(textureId, pngBytes, 0, 0, 1, 1, 0, 0, 0, List.of(), WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC,
				mipPngBytes, 0, 0, mipPngBytes.size() + 1);
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
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags, 0, 0, List.of(), WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC, List.of());
		}

		public WorldMeshTextureAssetRecord(
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
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags, frameRowSize, interpolationPolicy, animationFrames, WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC, List.of());
		}

		public WorldMeshTextureAssetRecord(
			int textureId,
			byte[] pngBytes,
			int frameWidth,
			int frameHeight,
			int frameCount,
			int frameTicks,
			int animationFlags,
			int frameRowSize,
			int interpolationPolicy,
			List<WorldMeshAnimationFrameRecord> animationFrames,
			int coordinateOrigin
		) {
			this(textureId, pngBytes, frameWidth, frameHeight, frameCount, frameTicks, animationFlags, frameRowSize, interpolationPolicy, animationFrames, coordinateOrigin, List.of());
		}

		public WorldMeshTextureAssetRecord {
			if (requestedMipLevels < 0 || requestedMipLevels > 32) throw new IllegalArgumentException("invalid texture mip count");
			if (!(samplingFilter == 0 && samplingAddress == 0)
				&& !(samplingFilter >= 1 && samplingFilter <= 2 && samplingAddress >= 1 && samplingAddress <= 2)) {
				throw new IllegalArgumentException("invalid explicit texture sampling descriptor");
			}
			Objects.requireNonNull(pngBytes, "pngBytes");
			Objects.requireNonNull(animationFrames, "animationFrames");
			Objects.requireNonNull(mipPngBytes, "mipPngBytes");
			pngBytes = pngBytes.clone();
			animationFrames = List.copyOf(animationFrames);
			mipPngBytes = mipPngBytes.stream().map(bytes -> Objects.requireNonNull(bytes, "mip PNG bytes").clone()).toList();
			if (frameWidth < 0 || frameHeight < 0 || frameCount < 0 || frameTicks < 0 || frameRowSize < 0 || interpolationPolicy < 0) {
				throw new IllegalArgumentException("negative world mesh texture animation metadata");
			}
			if (coordinateOrigin != WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_VULKANIC
				&& coordinateOrigin != WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_MINECRAFT_TOP_LEFT) {
				throw new IllegalArgumentException("unknown world mesh texture coordinate origin " + coordinateOrigin);
			}
		}

		/** Callers cannot mutate the retained semantic texture through mip arrays. */
		@Override public List<byte[]> mipPngBytes() {
			return mipPngBytes.stream().map(byte[]::clone).toList();
		}

		public int pngByteLength() {
			return pngBytes.length;
		}

		/** Content identity of the complete semantic texture, not just mip zero. */
		public boolean sameContent(WorldMeshTextureAssetRecord other) {
			if (other == null || textureId != other.textureId
				|| samplingFilter != other.samplingFilter || samplingAddress != other.samplingAddress
				|| requestedMipLevels != other.requestedMipLevels
				|| frameWidth != other.frameWidth || frameHeight != other.frameHeight
				|| frameCount != other.frameCount || frameTicks != other.frameTicks
				|| animationFlags != other.animationFlags || frameRowSize != other.frameRowSize
				|| interpolationPolicy != other.interpolationPolicy || coordinateOrigin != other.coordinateOrigin
				|| !animationFrames.equals(other.animationFrames)
				|| !java.util.Arrays.equals(pngBytes, other.pngBytes)
				|| mipPngBytes.size() != other.mipPngBytes.size()) return false;
			for (int mip = 0; mip < mipPngBytes.size(); mip++) {
				if (!java.util.Arrays.equals(mipPngBytes.get(mip), other.mipPngBytes.get(mip))) return false;
			}
			return true;
		}

		@Override
		public byte[] pngBytes() {
			return pngBytes.clone();
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

		@Override
		public byte[] indexBytes() {
			return indexBytes.clone();
		}

		public int indexByteLength() {
			return indexBytes.length;
		}
	}

	/** No Java-generated corners, UV cells, material policy or GPU objects. */
	public record WorldExperienceOrbInstanceRecord(long meshKey, long meshGeneration, int meshIndex,
		float[] entityTransform, float[] cameraOrientation, int entityId) {
		public WorldExperienceOrbInstanceRecord {
			if (meshKey == 0 || meshGeneration == 0 || meshIndex < 0
				|| entityTransform == null || entityTransform.length != 16
				|| cameraOrientation == null || cameraOrientation.length != 4)
				throw new IllegalArgumentException("invalid orb placement semantics");
			entityTransform = entityTransform.clone();
			cameraOrientation = cameraOrientation.clone();
			for (float value : entityTransform) if (!Float.isFinite(value))
				throw new IllegalArgumentException("orb entity transform must be finite");
			for (float value : cameraOrientation) if (!Float.isFinite(value))
				throw new IllegalArgumentException("orb camera orientation must be finite");
		}
		@Override public float[] entityTransform() { return entityTransform.clone(); }
		@Override public float[] cameraOrientation() { return cameraOrientation.clone(); }
	}

	/** Compact copied DH generic-object semantics; Rust owns face topology. */
	public record WorldDistantHorizonsGenericBoxRecord(
		float minX, float minY, float minZ,
		float maxX, float maxY, float maxZ,
		int colorArgb, int packedLight,
		float northShading, float southShading, float eastShading,
		float westShading, float topShading, float bottomShading,
		boolean ssaoEnabled
	) {
		public WorldDistantHorizonsGenericBoxRecord {
			if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
				|| !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)
				|| minX > maxX || minY > maxY || minZ > maxZ
				|| !Float.isFinite(northShading) || !Float.isFinite(southShading)
				|| !Float.isFinite(eastShading) || !Float.isFinite(westShading)
				|| !Float.isFinite(topShading) || !Float.isFinite(bottomShading)) {
				throw new IllegalArgumentException("invalid copied DH generic box semantics");
			}
		}
	}

	static MemorySegment encodeExperienceOrbInstances(Arena arena, List<WorldExperienceOrbInstanceRecord> orbs, int meshCount) {
		if (meshCount < 0 || (long) meshCount + orbs.size() > 65_536)
			throw new IllegalArgumentException("combined mesh/orb frame bound exceeded");
		var layout = Struct.WORLD_EXPERIENCE_ORB_INSTANCE;
		var records = layout.array(arena, orbs.size());
		int previous = 0;
		for (int i = 0; i < orbs.size(); i++) {
			var orb = orbs.get(i);
			if (orb.meshIndex() < previous || orb.meshIndex() > meshCount)
				throw new IllegalArgumentException("invalid orb mesh ordering");
			previous = orb.meshIndex();
			var item = Abi.item(records, layout, i);
			layout.setInt(item, 0, layout.byteSize());
			layout.setInt(item, 1, orb.meshIndex());
			layout.setLong(item, 2, orb.meshKey());
			layout.setLong(item, 3, orb.meshGeneration());
			item.asSlice(layout.offset(4), 64).copyFrom(MemorySegment.ofArray(orb.entityTransform));
			item.asSlice(layout.offset(5), 16).copyFrom(MemorySegment.ofArray(orb.cameraOrientation));
			layout.setInt(item, 6, orb.entityId());
			layout.setInt(item, 7, 0);
		}
		return records;
	}

	static MemorySegment encodeDistantHorizonsGenericBoxes(
		Arena arena, List<WorldDistantHorizonsGenericBoxRecord> boxes
	) {
		if (boxes.size() > 10_000) {
			throw new IllegalArgumentException("too many DH generic-box semantics");
		}
		var layout = Struct.WORLD_DH_GENERIC_BOX;
		MemorySegment records = layout.array(arena, boxes.size());
		for (int i = 0; i < boxes.size(); i++) {
			var box = Objects.requireNonNull(boxes.get(i), "DH generic box");
			MemorySegment item = Abi.item(records, layout, i);
			layout.setInt(item, 0, layout.byteSize());
			layout.setInt(item, 1, box.ssaoEnabled() ? 1 : 0);
			long minOffset = layout.offset(2);
			long maxOffset = layout.offset(3);
			item.set(ValueLayout.JAVA_FLOAT, minOffset, box.minX());
			item.set(ValueLayout.JAVA_FLOAT, minOffset + Float.BYTES, box.minY());
			item.set(ValueLayout.JAVA_FLOAT, minOffset + 2L * Float.BYTES, box.minZ());
			item.set(ValueLayout.JAVA_FLOAT, maxOffset, box.maxX());
			item.set(ValueLayout.JAVA_FLOAT, maxOffset + Float.BYTES, box.maxY());
			item.set(ValueLayout.JAVA_FLOAT, maxOffset + 2L * Float.BYTES, box.maxZ());
			layout.setInt(item, 4, box.colorArgb());
			layout.setInt(item, 5, box.packedLight());
			long shadingOffset = layout.offset(6);
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset, box.northShading());
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset + Float.BYTES, box.southShading());
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset + 2L * Float.BYTES, box.eastShading());
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset + 3L * Float.BYTES, box.westShading());
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset + 4L * Float.BYTES, box.topShading());
			item.set(ValueLayout.JAVA_FLOAT, shadingOffset + 5L * Float.BYTES, box.bottomShading());
		}
		return records;
	}

	public record WorldExperienceOrbAssetRecord(long meshKey, long meshGeneration,
		int icon, int red, int blue, int packedLight) {
		public WorldExperienceOrbAssetRecord {
			if (meshKey == 0 || meshGeneration == 0 || icon < 0 || icon > 10
				|| red < 0 || red > 255 || blue < 0 || blue > 255) {
				throw new IllegalArgumentException("invalid immutable experience orb appearance");
			}
		}
	}

	static MemorySegment encodeExperienceOrbAssets(Arena arena, List<WorldExperienceOrbAssetRecord> orbs) {
		if (orbs.size() > 16_384) throw new IllegalArgumentException("too many experience orb assets");
		var layout = Struct.WORLD_EXPERIENCE_ORB_ASSET;
		MemorySegment records = layout.array(arena, orbs.size());
		for (int i = 0; i < orbs.size(); i++) {
			var orb = Objects.requireNonNull(orbs.get(i), "orb");
			MemorySegment item = Abi.item(records, layout, i);
			layout.setInt(item, 0, layout.byteSize());
			layout.setInt(item, 1, orb.icon());
			layout.setLong(item, 2, orb.meshKey());
			layout.setLong(item, 3, orb.meshGeneration());
			layout.setInt(item, 4, orb.red());
			layout.setInt(item, 5, orb.blue());
			layout.setInt(item, 6, orb.packedLight());
			layout.setInt(item, 7, 0);
		}
		return records;
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
		int terrainMaterialBits,
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
		int indexCount,
		int sourceFacing
	) {
		public WorldMeshSectionRecord(int materialId, int textureId, int materialMode,
			int cullPolicy, int winding, int indexOffset, int indexCount) {
			this(materialId, textureId, materialMode, cullPolicy, winding, indexOffset, indexCount, 6);
		}
	}

	public record WorldMeshAssetRecord(
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		List<WorldMeshVertexRecord> vertices,
		byte[] indexBytes,
		List<WorldMeshSectionRecord> sections,
		String entityIdentity
	) {
		public WorldMeshAssetRecord {
			Objects.requireNonNull(vertices, "vertices");
			Objects.requireNonNull(indexBytes, "indexBytes");
			Objects.requireNonNull(sections, "sections");
			Objects.requireNonNull(entityIdentity, "entityIdentity");
			vertices = List.copyOf(vertices);
			indexBytes = indexBytes.clone();
			sections = List.copyOf(sections);
		}

		public WorldMeshAssetRecord(
			long meshKey,
			long meshGeneration,
			int vertexLayoutVersion,
			int indexType,
			List<WorldMeshVertexRecord> vertices,
			byte[] indexBytes,
			List<WorldMeshSectionRecord> sections
		) {
			this(meshKey, meshGeneration, vertexLayoutVersion, indexType, vertices, indexBytes, sections, "");
		}

		@Override
		public byte[] indexBytes() {
			return indexBytes.clone();
		}

		public int indexByteLength() {
			return indexBytes.length;
		}

		public boolean indicesFitVertexCount(int vertexCount) {
			int stride = indexType == INDEX_U16 ? Short.BYTES : indexType == INDEX_U32 ? Integer.BYTES : 0;
			if (stride == 0 || indexBytes.length % stride != 0) return false;
			for (int offset = 0; offset < indexBytes.length; offset += stride) {
				long vertexIndex = stride == Short.BYTES
					? (indexBytes[offset] & 0xffL) | ((indexBytes[offset + 1] & 0xffL) << 8)
					: (indexBytes[offset] & 0xffL) | ((indexBytes[offset + 1] & 0xffL) << 8)
						| ((indexBytes[offset + 2] & 0xffL) << 16) | ((indexBytes[offset + 3] & 0xffL) << 24);
				if (vertexIndex >= vertexCount) return false;
			}
			return true;
		}

		public boolean hasSameIndexPayload(WorldMeshAssetRecord other) {
			return other != null && java.util.Arrays.equals(indexBytes, other.indexBytes);
		}
	}

	/** Semantic DH LOD vertex, independent of the producer's legacy GL VAO. */
	public record WorldLodVertexRecord(
		int localX,
		int localY,
		int localZ,
		int packedLightAndMicroOffset,
		int colorRgba,
		int materialId,
		int normalIndex
	) {
		public WorldLodVertexRecord {
			if (localX < 0 || localX > 0xFFFF || localY < 0 || localY > 0xFFFF || localZ < 0 || localZ > 0xFFFF
				|| packedLightAndMicroOffset < 0 || packedLightAndMicroOffset > 0xFFFF
				|| materialId < 0 || materialId > 15 || normalIndex < 0 || normalIndex > 5) {
				throw new IllegalArgumentException("world LOD vertex field outside its semantic range");
			}
		}
	}

	public record WorldLodSegmentRecord(int layer, List<WorldLodVertexRecord> vertices, byte[] packedVertexBytes) {
		public WorldLodSegmentRecord {
			Objects.requireNonNull(vertices, "vertices");
			Objects.requireNonNull(packedVertexBytes, "packedVertexBytes");
			if (!vertices.isEmpty() && packedVertexBytes.length != 0) {
				throw new IllegalArgumentException("world LOD segment cannot mix structured and packed vertices");
			}
			if (vertices.isEmpty() && packedVertexBytes.length == 0) {
				throw new IllegalArgumentException("world LOD segment must contain semantic vertices");
			}
			if (packedVertexBytes.length % 16 != 0 || (packedVertexBytes.length / 16) % 4 != 0) {
				throw new IllegalArgumentException("packed world LOD segment must be 16-byte quad aligned");
			}
			vertices = List.copyOf(vertices);
		}
		public WorldLodSegmentRecord(int layer, List<WorldLodVertexRecord> vertices) {
			this(layer, vertices, new byte[0]);
		}
		public static WorldLodSegmentRecord packed(int layer, byte[] bytes) {
			return new WorldLodSegmentRecord(layer, List.of(), bytes);
		}
		public boolean hasPackedVertices() { return packedVertexBytes.length != 0; }
	}

	public record WorldLodColumnAssetRecord(
		long columnKey,
		long columnGeneration,
		int vertexLayoutVersion,
		int originX,
		int originY,
		int originZ,
		List<WorldLodSegmentRecord> segments
	) {
		public WorldLodColumnAssetRecord {
			Objects.requireNonNull(segments, "segments");
			segments = List.copyOf(segments);
		}
	}

	public record WorldLodColumnRetirementRecord(long columnKey, long columnGeneration) {
	}

	/** Explicit release of one immutable world-mesh generation in Rust. */
	public record WorldMeshAssetRetirementRecord(long meshKey, long meshGeneration) {
		public WorldMeshAssetRetirementRecord {
			if (meshKey == 0L || meshGeneration == 0L) {
				throw new IllegalArgumentException("world mesh retirement key and generation must be non-zero");
			}
		}
	}

	/** Stable semantic identity for a reduced DH quad. It is not an atlas or
	 * renderer object; Rust resolves any future material resources itself. */
	public record WorldLodMaterialIdentityRecord(String blockStateIdentity, String biomeIdentity) {
		public WorldLodMaterialIdentityRecord {
			Objects.requireNonNull(blockStateIdentity, "blockStateIdentity");
			Objects.requireNonNull(biomeIdentity, "biomeIdentity");
			if (blockStateIdentity.isBlank() || biomeIdentity.isBlank()) {
				throw new IllegalArgumentException("world LOD material identities must be non-blank");
			}
		}
	}

	/** One material reference per emitted quad in one compact DH segment. */
	public record WorldLodSegmentMaterialProvenanceRecord(
		int layer, int segmentIndex, int[] quadMaterialIds, byte[] quadVariantStates,
		long[] quadVariantPositions) {
		public WorldLodSegmentMaterialProvenanceRecord(int layer, int segmentIndex, int[] quadMaterialIds) {
			this(layer, segmentIndex, quadMaterialIds,
				new byte[Objects.requireNonNull(quadMaterialIds, "quadMaterialIds").length],
				new long[quadMaterialIds.length]);
		}

		public WorldLodSegmentMaterialProvenanceRecord {
			if (layer <= 0 || layer > 4 || segmentIndex < 0) {
				throw new IllegalArgumentException("world LOD segment provenance has an invalid layer or index");
			}
			Objects.requireNonNull(quadMaterialIds, "quadMaterialIds");
			Objects.requireNonNull(quadVariantStates, "quadVariantStates");
			Objects.requireNonNull(quadVariantPositions, "quadVariantPositions");
			if (quadVariantStates.length != quadMaterialIds.length
				|| quadVariantPositions.length != quadMaterialIds.length) {
				throw new IllegalArgumentException("world LOD variant provenance must align with material IDs");
			}
			quadMaterialIds = quadMaterialIds.clone();
			quadVariantStates = quadVariantStates.clone();
			quadVariantPositions = quadVariantPositions.clone();
		}

		@Override
		public int[] quadMaterialIds() {
			return quadMaterialIds.clone();
		}

		@Override
		public byte[] quadVariantStates() {
			return quadVariantStates.clone();
		}

		@Override
		public long[] quadVariantPositions() {
			return quadVariantPositions.clone();
		}
	}

	/** One exact, copied atlas face for a reduced DH material identity. */
	public record WorldLodFaceMaterialRecord(
		int materialId,
		int face,
		int faceLayer,
		String atlasIdentity,
		String spriteIdentity,
		float u0,
		float v0,
		float u1,
		float v1,
		int uvCornerOrder,
		long variantPosition,
		boolean tinted,
		int tintArgb
	) {
		public WorldLodFaceMaterialRecord {
			if (materialId <= 0 || face < 0 || face > 5 || faceLayer < 0 || faceLayer > 3) {
				throw new IllegalArgumentException("world LOD face material has an invalid material ID, face, or layer");
			}
			Objects.requireNonNull(atlasIdentity, "atlasIdentity");
			Objects.requireNonNull(spriteIdentity, "spriteIdentity");
			if (atlasIdentity.isBlank() || spriteIdentity.isBlank()
				|| !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
				|| u0 < 0.0F || v0 < 0.0F || u1 > 1.0F || v1 > 1.0F || u0 >= u1 || v0 >= v1) {
				throw new IllegalArgumentException("world LOD face material must contain a normalized atlas region");
			}
			if (!validUvCornerOrder(uvCornerOrder)) {
				throw new IllegalArgumentException("world LOD face material UV corner order must be a permutation");
			}
		}

		public WorldLodFaceMaterialRecord(
			int materialId, int face, String atlasIdentity, String spriteIdentity,
			float u0, float v0, float u1, float v1
		) {
			this(materialId, face, 0, atlasIdentity, spriteIdentity, u0, v0, u1, v1, 0x78, 0L, false, 0xffffffff);
		}

		public WorldLodFaceMaterialRecord(
			int materialId, int face, String atlasIdentity, String spriteIdentity,
			float u0, float v0, float u1, float v1, int uvCornerOrder
		) {
			this(materialId, face, 0, atlasIdentity, spriteIdentity, u0, v0, u1, v1, uvCornerOrder, 0L, false, 0xffffffff);
		}

		private static boolean validUvCornerOrder(int order) {
			if (order < 0 || order > 0xff) {
				return false;
			}
			int mask = 0;
			for (int index = 0; index < 4; index++) {
				int corner = order >>> (index * 2) & 0x3;
				int bit = 1 << corner;
				if ((mask & bit) != 0) {
					return false;
				}
				mask |= bit;
			}
			return mask == 0xf;
		}
	}

	/** Generation-bound copied material provenance sidecar for a DH column.
	 * The color-only route does not render this table as textures. */
	public record WorldLodColumnMaterialProvenanceRecord(
		long columnKey,
		long columnGeneration,
		List<WorldLodMaterialIdentityRecord> identities,
		List<WorldLodSegmentMaterialProvenanceRecord> segments,
		List<WorldLodFaceMaterialRecord> faceMaterials
	) {
		public WorldLodColumnMaterialProvenanceRecord(
			long columnKey,
			long columnGeneration,
			List<WorldLodMaterialIdentityRecord> identities,
			List<WorldLodSegmentMaterialProvenanceRecord> segments
		) {
			this(columnKey, columnGeneration, identities, segments, List.of());
		}

		public WorldLodColumnMaterialProvenanceRecord {
			if (columnGeneration <= 0L) {
				throw new IllegalArgumentException("world LOD material provenance generation must be non-zero");
			}
			Objects.requireNonNull(identities, "identities");
			Objects.requireNonNull(segments, "segments");
			Objects.requireNonNull(faceMaterials, "faceMaterials");
			identities = List.copyOf(identities);
			segments = List.copyOf(segments);
			faceMaterials = List.copyOf(faceMaterials);
		}
	}

	/** One visible semantic DH LOD segment in legacy render-list order. */
	public record WorldLodColumnInstanceRecord(
		long columnKey,
		long columnGeneration,
		int layer,
		int segmentIndex,
		int order
	) {
		public WorldLodColumnInstanceRecord {
			if (columnGeneration <= 0L || layer <= 0 || layer > 4 || segmentIndex < 0 || order < 0) {
				throw new IllegalArgumentException("world LOD instance has an invalid semantic field");
			}
		}
	}

	/**
	 * Resolved, backend-neutral render semantics for one Distant Horizons frame.
	 * This is intentionally data only: it carries no shader, framebuffer,
	 * lightmap, or native resource identity from the legacy renderer.
	 */
	public record WorldLodRenderFrameRecord(
		boolean enabled,
		int flags,
		int worldYOffset,
		float[] combinedMatrix,
		float[] modelViewMatrix,
		float[] projectionMatrix,
		float[] projectionInverseMatrix,
		float clipDistance,
		float microOffset,
		float noiseIntensity,
		float earthRadius,
		int noiseSteps,
		int noiseDropoff,
		float[] cameraWorldPosition,
		float[] dhFogParameters,
		int maxLevelHeight,
		float[] ssaoParameters
	) {
		public WorldLodRenderFrameRecord {
			combinedMatrix = copyFiniteMatrix(combinedMatrix, "combined");
			modelViewMatrix = copyFiniteMatrix(modelViewMatrix, "model-view");
			projectionMatrix = copyFiniteMatrix(projectionMatrix, "projection");
			projectionInverseMatrix = copyFiniteMatrix(projectionInverseMatrix, "projection inverse");
			cameraWorldPosition = copyFiniteCameraPosition(cameraWorldPosition);
			dhFogParameters = copyFiniteDhFogParameters(dhFogParameters);
			ssaoParameters = copyFiniteSsaoParameters(ssaoParameters);
			if (flags < 0 || flags > 0xFF || !Float.isFinite(clipDistance) || !Float.isFinite(microOffset)
				|| !Float.isFinite(noiseIntensity) || !Float.isFinite(earthRadius)) {
				throw new IllegalArgumentException("world LOD render frame contains an invalid semantic field");
			}
			if (enabled && (clipDistance < 0.0F || microOffset <= 0.0F)) {
				throw new IllegalArgumentException("enabled world LOD render frame has invalid clip or micro-offset semantics");
			}
		}

		/** Compatibility constructor for callers that do not provide SSAO semantics. */
		public WorldLodRenderFrameRecord(
			boolean enabled,
			int flags,
			int worldYOffset,
			float[] combinedMatrix,
			float[] modelViewMatrix,
			float[] projectionMatrix,
			float[] projectionInverseMatrix,
			float clipDistance,
			float microOffset,
			float noiseIntensity,
			float earthRadius,
			int noiseSteps,
			int noiseDropoff,
			float[] cameraWorldPosition,
			float[] dhFogParameters,
			int maxLevelHeight
		) {
			this(enabled, flags, worldYOffset, combinedMatrix, modelViewMatrix, projectionMatrix,
				projectionInverseMatrix, clipDistance, microOffset, noiseIntensity, earthRadius,
				noiseSteps, noiseDropoff, cameraWorldPosition, dhFogParameters, maxLevelHeight,
				new float[8]);
		}

		public static WorldLodRenderFrameRecord disabled() {
			return new WorldLodRenderFrameRecord(false, 0, 0, new float[16], new float[16], new float[16], new float[16], 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, new float[3], new float[20], 0, new float[8]);
		}

		@Override
		public float[] combinedMatrix() {
			return combinedMatrix.clone();
		}

		@Override
		public float[] modelViewMatrix() {
			return modelViewMatrix.clone();
		}

		@Override
		public float[] projectionMatrix() {
			return projectionMatrix.clone();
		}

		@Override
		public float[] projectionInverseMatrix() {
			return projectionInverseMatrix.clone();
		}

		private static float[] copyFiniteMatrix(float[] matrix, String label) {
			Objects.requireNonNull(matrix, label + " matrix");
			if (matrix.length != 16) {
				throw new IllegalArgumentException("world LOD " + label + " matrix must contain 16 floats");
			}
			float[] copy = matrix.clone();
			for (float value : copy) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world LOD render frame must contain finite values");
				}
			}
			return copy;
		}

		@Override
		public float[] cameraWorldPosition() {
			return cameraWorldPosition.clone();
		}

		@Override
		public float[] dhFogParameters() {
			return dhFogParameters.clone();
		}

		@Override
		public float[] ssaoParameters() {
			return ssaoParameters.clone();
		}

		private static float[] copyFiniteCameraPosition(float[] position) {
			Objects.requireNonNull(position, "camera world position");
			if (position.length != 3) {
				throw new IllegalArgumentException("world LOD camera world position must contain three floats");
			}
			float[] copy = position.clone();
			for (float value : copy) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world LOD camera world position must be finite");
				}
			}
			return copy;
		}

		private static float[] copyFiniteDhFogParameters(float[] parameters) {
			Objects.requireNonNull(parameters, "DH fog parameters");
			if (parameters.length != 20) {
				throw new IllegalArgumentException("world LOD DH fog parameters must contain 20 floats");
			}
			float[] copy = parameters.clone();
			for (float value : copy) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world LOD DH fog parameters must be finite");
				}
			}
			return copy;
		}

		private static float[] copyFiniteSsaoParameters(float[] parameters) {
			Objects.requireNonNull(parameters, "SSAO parameters");
			if (parameters.length != 8) {
				throw new IllegalArgumentException("world LOD SSAO parameters must contain eight floats");
			}
			float[] copy = parameters.clone();
			for (float value : copy) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world LOD SSAO parameters must be finite");
				}
			}
			return copy;
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
		persistentGuiMeshTopologies.clear();
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
			profileLong(segment, offset, 118),
			profileLong(segment, offset, 119),
			profileLong(segment, offset, 120),
			profileLong(segment, offset, 121),
			profileLong(segment, offset, 122),
			profileLong(segment, offset, 123),
			profileLong(segment, offset, 124),
			profileLong(segment, offset, 125),
			new WholeFrameProfileExtension(
				profileLong(segment, offset, 126),
				profileLong(segment, offset, 127),
				profileLong(segment, offset, 128),
				profileLong(segment, offset, 129),
				profileLong(segment, offset, 130)
			)
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

	/** Exact orthographic extent; layout/clipping bounds remain integer-valued. */
	public record GuiProjectionRecord(float width, float height) {
		public GuiProjectionRecord {
			if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
				throw new IllegalArgumentException("GUI projection must be finite and positive");
			}
		}
		public void validateLayout(int width, int height) {
			if (Math.ceil(this.width) != width || Math.ceil(this.height) != height) {
				throw new IllegalArgumentException("GUI projection must ceil to the explicit layout extent");
			}
		}
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
		int guiHeight,
		long sequence
	) {
		public GuiSpriteRecord(
			int stratum, int spriteId, int selectedSlot, float progressFraction, int fillDirection, int colorArgb,
			int x, int y, int width, int height, int guiWidth, int guiHeight
		) {
			this(stratum, spriteId, selectedSlot, progressFraction, fillDirection, colorArgb,
				x, y, width, height, guiWidth, guiHeight, 0L);
		}

		public GuiSpriteRecord withSequence(long value) {
			return new GuiSpriteRecord(
				this.stratum, this.spriteId, this.selectedSlot, this.progressFraction, this.fillDirection,
				this.colorArgb, this.x, this.y, this.width, this.height, this.guiWidth, this.guiHeight, value
			);
		}

	}

	/** Immutable tiled draw semantics; Rust owns repetition, wrapping and batching. */
	public record GuiTiledQuadRecord(
		int stratum, long assetId, int[] bounds, int tileWidth, int tileHeight,
		float[] uv, float[] pose, float z, int colorArgb, int guiWidth, int guiHeight,
		long sequence, int clipMode, int[] clip
	) {
		public GuiTiledQuadRecord {
			bounds = Objects.requireNonNull(bounds, "bounds").clone();
			uv = Objects.requireNonNull(uv, "uv").clone();
			pose = Objects.requireNonNull(pose, "pose").clone();
			clip = Objects.requireNonNull(clip, "clip").clone();
			if (bounds.length != 4 || uv.length != 4 || pose.length != 6 || clip.length != 4
				|| stratum <= 0 || assetId == 0 || tileWidth <= 0 || tileHeight <= 0
				|| guiWidth <= 0 || guiHeight <= 0 || !Float.isFinite(z)
				|| bounds[2] <= bounds[0] || bounds[3] <= bounds[1]
				|| uv[2] <= uv[0] || uv[3] <= uv[1] || (clipMode != 0 && clipMode != 1)) {
				throw new IllegalArgumentException("invalid semantic tiled GUI command");
			}
			for (float value : uv) if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite tile UV");
			for (float value : pose) if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite tile pose");
			if (clipMode == 0 && !java.util.Arrays.equals(clip, new int[4])) throw new IllegalArgumentException("disabled tile clip must be empty");
			if (clipMode == 1 && (clip[0] < 0 || clip[1] < 0 || clip[2] < 0 || clip[3] < 0
				|| (long)clip[0] + clip[2] > guiWidth || (long)clip[1] + clip[3] > guiHeight)) {
				throw new IllegalArgumentException("tile clip must be frame-local");
			}
		}
		@Override public int[] bounds() { return bounds.clone(); }
		@Override public float[] uv() { return uv.clone(); }
		@Override public float[] pose() { return pose.clone(); }
		@Override public int[] clip() { return clip.clone(); }
		public GuiTiledQuadRecord withSequence(long value) {
			return new GuiTiledQuadRecord(stratum, assetId, bounds, tileWidth, tileHeight, uv, pose,
				z, colorArgb, guiWidth, guiHeight, value, clipMode, clip);
		}
	}

	/** Authored corners in the standard 16-unit item cell, not screen coordinates. */
	public record GuiItemRasterGeometryRecord(float x0, float y0, float x1, float y1, float x3, float y3) {
		public static final GuiItemRasterGeometryRecord FULL = new GuiItemRasterGeometryRecord(0,0,16,0,0,16);
		public GuiItemRasterGeometryRecord {
			float area = (x1-x0)*(y3-y0)-(y1-y0)*(x3-x0);
			for (float value : new float[] {x0,y0,x1,y1,x3,y3,x1+x3-x0,y1+y3-y0}) {
				if (!Float.isFinite(value) || value < 0 || value > 16) {
					throw new IllegalArgumentException("invalid bounded item-local corner");
				}
			}
			if (!Float.isFinite(area) || Math.abs(area) <= 0.000001F) {
				throw new IllegalArgumentException("invalid item-local area");
			}
		}
		public float[] corners() { return new float[] {x0,y0,x1,y1,x3,y3}; }
	}

	/** Backend-neutral affine glyph/image primitive in GUI logical coordinates. */
	public record GuiItemRasterLayerRecord(long assetId, int colorArgb, int materialMode,
		GuiItemRasterGeometryRecord geometry, float u0, float v0, float u1, float v1, float[] modelTransform) {
		public GuiItemRasterLayerRecord(long assetId, int colorArgb, int materialMode,
			GuiItemRasterGeometryRecord geometry, float u0, float v0, float u1, float v1) {
			this(assetId,colorArgb,materialMode,geometry,u0,v0,u1,v1,
				new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, -0.5F,-0.5F,-0.5F,1});
		}
		public GuiItemRasterLayerRecord {
			java.util.Objects.requireNonNull(geometry, "item layer geometry");
			if (modelTransform.length != 16) throw new IllegalArgumentException("item model transform requires16 values");
			for (float value : modelTransform) if (!Float.isFinite(value))
				throw new IllegalArgumentException("non-finite item model transform");
			modelTransform = modelTransform.clone();
			if (assetId == 0 || (materialMode != 1 && materialMode != 2)
				|| !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
				|| u0 < 0 || v0 < 0 || u1 > 1 || v1 > 1 || u0 >= u1 || v0 >= v1) {
				throw new IllegalArgumentException("invalid semantic item layer");
			}
		}
		@Override public float[] modelTransform() { return modelTransform.clone(); }
		@Override public boolean equals(Object other) {
			return other instanceof GuiItemRasterLayerRecord layer && assetId==layer.assetId && colorArgb==layer.colorArgb
				&& materialMode==layer.materialMode && geometry.equals(layer.geometry)
				&& Float.compare(u0,layer.u0)==0 && Float.compare(v0,layer.v0)==0
				&& Float.compare(u1,layer.u1)==0 && Float.compare(v1,layer.v1)==0
				&& java.util.Arrays.equals(modelTransform,layer.modelTransform);
		}
		@Override public int hashCode() {
			return 31*java.util.Objects.hash(assetId,colorArgb,materialMode,geometry,u0,v0,u1,v1)
				+java.util.Arrays.hashCode(modelTransform);
		}
	}

	public record GuiAffineQuadRecord(
		int stratum,
		long assetId,
		float x0,
		float y0,
		float x1,
		float y1,
		float x3,
		float y3,
		float z,
		float u0,
		float v0,
		float u1,
		float v1,
		int colorArgb,
		int guiWidth,
		int guiHeight,
		long sequence,
		int clipMode,
		int clipLeft,
		int clipTop,
		int clipWidth,
		int clipHeight,
		int materialMode,
		int itemRasterScale,
		GuiItemRasterGeometryRecord itemRasterGeometry,
		List<GuiItemRasterLayerRecord> itemRasterLayers
	) {
		public GuiAffineQuadRecord(
			int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
			float z, float u0, float v0, float u1, float v1, int colorArgb, int guiWidth, int guiHeight,
			long sequence, int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			int materialMode, int itemRasterScale, GuiItemRasterGeometryRecord itemRasterGeometry
		) {
			this(stratum,assetId,x0,y0,x1,y1,x3,y3,z,u0,v0,u1,v1,colorArgb,guiWidth,guiHeight,
				sequence,clipMode,clipLeft,clipTop,clipWidth,clipHeight,materialMode,itemRasterScale,itemRasterGeometry,List.of());
		}
		public GuiAffineQuadRecord(
			int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
			float z, float u0, float v0, float u1, float v1, int colorArgb, int guiWidth, int guiHeight,
			long sequence, int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			int materialMode, int itemRasterScale
		) {
			this(stratum,assetId,x0,y0,x1,y1,x3,y3,z,u0,v0,u1,v1,colorArgb,guiWidth,guiHeight,
				sequence,clipMode,clipLeft,clipTop,clipWidth,clipHeight,materialMode,itemRasterScale,
				GuiItemRasterGeometryRecord.FULL);
		}
		public GuiAffineQuadRecord(
			int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
			float z, float u0, float v0, float u1, float v1, int colorArgb, int guiWidth, int guiHeight,
			long sequence, int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight, int materialMode
		) {
			this(stratum, assetId, x0, y0, x1, y1, x3, y3, z, u0, v0, u1, v1,
				colorArgb, guiWidth, guiHeight, sequence, clipMode, clipLeft, clipTop, clipWidth, clipHeight, materialMode, 0);
		}
		public GuiAffineQuadRecord(
			int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
			float z, float u0, float v0, float u1, float v1, int colorArgb, int guiWidth, int guiHeight,
			long sequence, int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight
		) {
			this(stratum, assetId, x0, y0, x1, y1, x3, y3, z, u0, v0, u1, v1,
				colorArgb, guiWidth, guiHeight, sequence, clipMode, clipLeft, clipTop, clipWidth, clipHeight, 0);
		}
		public GuiAffineQuadRecord(
			int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
			float z, float u0, float v0, float u1, float v1, int colorArgb, int guiWidth, int guiHeight
		) {
			this(stratum, assetId, x0, y0, x1, y1, x3, y3, z, u0, v0, u1, v1,
				colorArgb, guiWidth, guiHeight, 0L, 0, 0, 0, 0, 0);
		}

		public GuiAffineQuadRecord {
			itemRasterLayers = List.copyOf(itemRasterLayers);
			if (itemRasterLayers.size() > 64 || (!itemRasterLayers.isEmpty() && itemRasterScale == 0)) {
				throw new IllegalArgumentException("invalid bounded semantic item layers");
			}
			java.util.Objects.requireNonNull(itemRasterGeometry, "item-local geometry");
			if (itemRasterScale < 0 || itemRasterScale > 256
				|| (itemRasterScale != 0 && ((materialMode != 1 && materialMode != 2) || z != 0.0F
					|| u0 < 0.0F || v0 < 0.0F || u1 > 1.0F || v1 > 1.0F || u0 >= u1 || v0 >= v1))) {
				throw new IllegalArgumentException("invalid full-item raster semantics");
			}
			if (materialMode != 0 && materialMode != 1 && materialMode != 2) {
				throw new IllegalArgumentException("invalid semantic GUI affine material");
			}
			if (assetId == 0L || guiWidth <= 0 || guiHeight <= 0
				|| !Float.isFinite(x0) || !Float.isFinite(y0) || !Float.isFinite(x1) || !Float.isFinite(y1)
				|| !Float.isFinite(x3) || !Float.isFinite(y3) || !Float.isFinite(z)
				|| !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
				|| u0 < -GUI_UV_OVERLAP_LIMIT || u0 > 1.0F + GUI_UV_OVERLAP_LIMIT
				|| v0 < -GUI_UV_OVERLAP_LIMIT || v0 > 1.0F + GUI_UV_OVERLAP_LIMIT
				|| u1 < -GUI_UV_OVERLAP_LIMIT || u1 > 1.0F + GUI_UV_OVERLAP_LIMIT
				|| v1 < -GUI_UV_OVERLAP_LIMIT || v1 > 1.0F + GUI_UV_OVERLAP_LIMIT
				|| (clipMode == 0 && (clipLeft != 0 || clipTop != 0 || clipWidth != 0 || clipHeight != 0))
				|| (clipMode == 1 && (clipLeft < 0 || clipTop < 0 || clipWidth < 0 || clipHeight < 0
					|| (long)clipLeft + clipWidth > guiWidth || (long)clipTop + clipHeight > guiHeight))
				|| (clipMode != 0 && clipMode != 1)) {
				throw new IllegalArgumentException("invalid semantic GUI affine quad");
			}
		}

		public GuiAffineQuadRecord withSequence(long value) {
			return new GuiAffineQuadRecord(
				this.stratum, this.assetId, this.x0, this.y0, this.x1, this.y1, this.x3, this.y3,
				this.z, this.u0, this.v0, this.u1, this.v1, this.colorArgb, this.guiWidth, this.guiHeight, value,
				this.clipMode, this.clipLeft, this.clipTop, this.clipWidth, this.clipHeight, this.materialMode, this.itemRasterScale, this.itemRasterGeometry, this.itemRasterLayers
			);
		}

		public GuiAffineQuadRecord withStratum(int value) {
			if (value < 0) {
				throw new IllegalArgumentException("negative semantic GUI stratum");
			}
			return new GuiAffineQuadRecord(
				value, this.assetId, this.x0, this.y0, this.x1, this.y1, this.x3, this.y3,
				this.z, this.u0, this.v0, this.u1, this.v1, this.colorArgb, this.guiWidth, this.guiHeight,
				this.sequence, this.clipMode, this.clipLeft, this.clipTop, this.clipWidth, this.clipHeight, this.materialMode, this.itemRasterScale, this.itemRasterGeometry, this.itemRasterLayers
			);
		}

		public GuiAffineQuadRecord withClip(int left, int top, int width, int height) {
			return new GuiAffineQuadRecord(
				this.stratum, this.assetId, this.x0, this.y0, this.x1, this.y1, this.x3, this.y3,
				this.z, this.u0, this.v0, this.u1, this.v1, this.colorArgb, this.guiWidth, this.guiHeight,
				this.sequence, 1, left, top, width, height, this.materialMode, this.itemRasterScale, this.itemRasterGeometry, this.itemRasterLayers
			);
		}

		public GuiAffineQuadRecord withMaterialMode(int value) {
			return new GuiAffineQuadRecord(stratum, assetId, x0, y0, x1, y1, x3, y3,
				z, u0, v0, u1, v1, colorArgb, guiWidth, guiHeight, sequence,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, value, itemRasterScale, itemRasterGeometry, itemRasterLayers);
		}

		public GuiAffineQuadRecord withItemRasterScale(int value) {
			return new GuiAffineQuadRecord(stratum, assetId, x0, y0, x1, y1, x3, y3,
				z, u0, v0, u1, v1, colorArgb, guiWidth, guiHeight, sequence,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, materialMode, value, itemRasterGeometry, itemRasterLayers);
		}

		public GuiAffineQuadRecord withItemRasterGeometry(GuiItemRasterGeometryRecord value) {
			if (itemRasterScale == 0) throw new IllegalArgumentException("item geometry requires an item raster");
			return new GuiAffineQuadRecord(stratum, assetId, x0, y0, x1, y1, x3, y3,
				z, u0, v0, u1, v1, colorArgb, guiWidth, guiHeight, sequence,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, materialMode, itemRasterScale, value, itemRasterLayers);
		}

		public GuiAffineQuadRecord withItemRasterLayers(List<GuiItemRasterLayerRecord> value) {
			return new GuiAffineQuadRecord(stratum, assetId, x0, y0, x1, y1, x3, y3,
				z, u0, v0, u1, v1, colorArgb, guiWidth, guiHeight, sequence,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, materialMode, itemRasterScale, itemRasterGeometry, value);
		}
	}

	/** Copied semantic vertex for one Rust-owned standard-3D GUI item layer. */
	public record GuiMeshVertexRecord(
		float[] position,
		float[] atlasUv,
		float[] localUv,
		int colorArgb,
		int normalPacked,
		int sourceFace,
		int sourceFoilType
	) {
		public GuiMeshVertexRecord(float[] position, float[] atlasUv, float[] localUv, int colorArgb, int normalPacked) {
			this(position, atlasUv, localUv, colorArgb, normalPacked, 0, 0);
		}
		public GuiMeshVertexRecord {
			if (sourceFace < 0 || sourceFace > 6 || sourceFoilType < 0 || sourceFoilType > 1
				|| (sourceFoilType != 0 && sourceFace == 0)) throw new IllegalArgumentException("invalid GUI baked face/foil semantics");
			position = checkedFiniteCopy(position, 3, "GUI mesh position");
			atlasUv = checkedFiniteCopy(atlasUv, 2, "GUI mesh atlas UV");
			localUv = checkedFiniteCopy(localUv, 2, "GUI mesh local UV");
		}

		@Override public float[] position() { return this.position.clone(); }
		@Override public float[] atlasUv() { return this.atlasUv.clone(); }
		@Override public float[] localUv() { return this.localUv.clone(); }
	}

	/**
	 * Copies a dense CPU semantic vertex stream into an immutable list view. The
	 * bridge recognizes the private view and writes its primitive arrays directly
	 * into the confined FFI arena, avoiding one object and three tiny arrays per
	 * vertex. This is still ordinary owned CPU data; no native or renderer state is
	 * retained by the view.
	 */
	public static List<GuiMeshVertexRecord> packedGuiMeshVertices(
		float[] positions, float[] atlasUvs, float[] localUvs, int[] colors, int[] normals, int vertexCount
	) {
		return new PackedGuiMeshVertices(positions, atlasUvs, localUvs, colors, normals, vertexCount);
	}

	/** Copies a dense CPU index stream into the matching immutable list contract. */
	public static List<Integer> packedGuiMeshIndices(int[] indices, int indexCount) {
		return new PackedGuiMeshIndices(indices, indexCount);
	}

	private static final class PackedGuiMeshVertices extends AbstractList<GuiMeshVertexRecord> implements RandomAccess {
		private final float[] positions;
		private final float[] atlasUvs;
		private final float[] localUvs;
		private final int[] colors;
		private final int[] normals;

		private PackedGuiMeshVertices(
			float[] positions, float[] atlasUvs, float[] localUvs, int[] colors, int[] normals, int vertexCount
		) {
			if (vertexCount <= 0 || positions == null || atlasUvs == null || localUvs == null
				|| colors == null || normals == null || positions.length < vertexCount * 3
				|| atlasUvs.length < vertexCount * 2 || localUvs.length < vertexCount * 2
				|| colors.length < vertexCount || normals.length < vertexCount) {
				throw new IllegalArgumentException("invalid packed GUI mesh vertex stream");
			}
			this.positions = Arrays.copyOf(positions, vertexCount * 3);
			this.atlasUvs = Arrays.copyOf(atlasUvs, vertexCount * 2);
			this.localUvs = Arrays.copyOf(localUvs, vertexCount * 2);
			this.colors = Arrays.copyOf(colors, vertexCount);
			this.normals = Arrays.copyOf(normals, vertexCount);
			for (float value : this.positions) if (!Float.isFinite(value)) throw new IllegalArgumentException("GUI mesh position must be finite");
			for (float value : this.atlasUvs) if (!Float.isFinite(value)) throw new IllegalArgumentException("GUI mesh atlas UV must be finite");
			for (float value : this.localUvs) if (!Float.isFinite(value)) throw new IllegalArgumentException("GUI mesh local UV must be finite");
		}

		@Override public int size() { return colors.length; }

		@Override public GuiMeshVertexRecord get(int index) {
			Objects.checkIndex(index, size());
			int position = index * 3;
			int uv = index * 2;
			return new GuiMeshVertexRecord(
				new float[] {positions[position], positions[position + 1], positions[position + 2]},
				new float[] {atlasUvs[uv], atlasUvs[uv + 1]},
				new float[] {localUvs[uv], localUvs[uv + 1]}, colors[index], normals[index]
			);
		}

		private MemorySegment encode(Arena targetArena) {
			MemorySegment vertices = Struct.GUI_MESH_VERTEX.array(targetArena, size());
			for (int vertexIndex = 0; vertexIndex < size(); vertexIndex++) {
				MemorySegment item = Abi.item(vertices, Struct.GUI_MESH_VERTEX, vertexIndex);
				int position = vertexIndex * 3;
				int uv = vertexIndex * 2;
				for (int component = 0; component < 3; component++) item.set(ValueLayout.JAVA_FLOAT,
					Struct.GUI_MESH_VERTEX.offset(0) + component * 4L, positions[position + component]);
				for (int component = 0; component < 2; component++) item.set(ValueLayout.JAVA_FLOAT,
					Struct.GUI_MESH_VERTEX.offset(1) + component * 4L, atlasUvs[uv + component]);
				for (int component = 0; component < 2; component++) item.set(ValueLayout.JAVA_FLOAT,
					Struct.GUI_MESH_VERTEX.offset(2) + component * 4L, localUvs[uv + component]);
				Struct.GUI_MESH_VERTEX.setInt(item, 3, colors[vertexIndex]);
				Struct.GUI_MESH_VERTEX.setInt(item, 4, normals[vertexIndex]);
				Struct.GUI_MESH_VERTEX.setInt(item, 5, 0);
				Struct.GUI_MESH_VERTEX.setInt(item, 6, 0);
			}
			return vertices;
		}
	}

	private static final class PackedGuiMeshIndices extends AbstractList<Integer> implements RandomAccess {
		private final int[] values;

		private PackedGuiMeshIndices(int[] indices, int indexCount) {
			if (indices == null || indexCount <= 0 || indices.length < indexCount)
				throw new IllegalArgumentException("invalid packed GUI mesh index stream");
			this.values = Arrays.copyOf(indices, indexCount);
			for (int index : values) if (index < 0) throw new IllegalArgumentException("GUI mesh index must be nonnegative");
		}

		@Override public int size() { return values.length; }
		@Override public Integer get(int index) { return values[Objects.checkIndex(index, values.length)]; }

		private void validateVertexCount(int vertexCount) {
			for (int index : values) if (index >= vertexCount) throw new IllegalArgumentException("GUI mesh index out of range");
		}

		private MemorySegment encode(Arena targetArena) {
			MemorySegment indices = targetArena.allocate((long)values.length * Integer.BYTES, Integer.BYTES);
			MemorySegment.copy(values, 0, indices, ValueLayout.JAVA_INT, 0, values.length);
			return indices;
		}
	}

	/** One coarse copied material layer. Backends see no Java renderer state. */
	/** Immutable standard foil inputs; texture coordinates are computed only in Rust. */
	public enum StandardFoilKind {
		ITEM(1), ENTITY(2), ARMOR(3), ARMOR_ORTHOGRAPHIC(4);
		private final int wireValue;
		StandardFoilKind(int wireValue) { this.wireValue = wireValue; }
		public int wireValue() { return this.wireValue; }
	}

	public record StandardItemFoilRecord(long clockMillis, double speed, float strength, StandardFoilKind kind) {
		public StandardItemFoilRecord(long clockMillis, double speed, float strength) {
			this(clockMillis, speed, strength, StandardFoilKind.ITEM);
		}
		public StandardItemFoilRecord {
			Objects.requireNonNull(kind, "kind");
			if (clockMillis < 0 || !Double.isFinite(speed) || speed < 0.0 || speed > 1.0
				|| !Float.isFinite(strength) || strength < 0.0F || strength > 1.0F) {
				throw new IllegalArgumentException("invalid standard item foil semantics");
			}
		}
	}

	/** Copied unscaled model/normal poses, never Java-computed decal UVs or inverse texture matrices. */
	public record GuiDecalFoilRecord(float[] modelPose, float[] normalPose, boolean nativeItemLayout) {
		public GuiDecalFoilRecord(float[] modelPose, float[] normalPose) {
			this(modelPose,normalPose,false);
		}
		public static GuiDecalFoilRecord forNativeItemLayout() {
			return new GuiDecalFoilRecord(new float[16],new float[9],true);
		}
		public GuiDecalFoilRecord {
			modelPose = checkedFiniteCopy(modelPose, 16, "GUI decal model pose");
			normalPose = checkedFiniteCopy(normalPose, 9, "GUI decal normal pose");
			if (nativeItemLayout) {
				for (float value:modelPose) if (Float.floatToRawIntBits(value)!=0) throw new IllegalArgumentException("native item layout cannot carry caller raster pose");
				for (float value:normalPose) if (Float.floatToRawIntBits(value)!=0) throw new IllegalArgumentException("native item layout cannot carry caller normal pose");
			} else if (modelPose[3] != 0 || modelPose[7] != 0 || modelPose[11] != 0 || modelPose[15] != 1)
				throw new IllegalArgumentException("GUI decal model pose must be affine");
		}
		@Override public float[] modelPose() { return modelPose.clone(); }
		@Override public float[] normalPose() { return normalPose.clone(); }
	}

	/** Immutable model-space bounds and GUI scale; never a Java raster pose or GPU handle. */
	public record GuiBlockItemRasterRecord(int guiScale, double[] modelBounds, boolean oversizedGui) {
		public GuiBlockItemRasterRecord(int guiScale, double[] modelBounds) {
			this(guiScale,modelBounds,false);
		}
		public GuiBlockItemRasterRecord {
			if (guiScale <= 0 || guiScale > 256 || modelBounds == null || modelBounds.length != 6)
				throw new IllegalArgumentException("invalid semantic block item layout");
			modelBounds = modelBounds.clone();
			for (double value : modelBounds) if (!Double.isFinite(value)) throw new IllegalArgumentException("nonfinite item bounds");
			for (int axis = 0; axis < 3; axis++) if (modelBounds[axis] > modelBounds[axis+3])
				throw new IllegalArgumentException("unordered item bounds");
		}
		@Override public double[] modelBounds() { return modelBounds.clone(); }
	}

	/** Semantic model identity and authored animation flag, never a GPU/cache handle. */
	public record GuiItemCacheRecord(long identity, boolean animated) {
		public GuiItemCacheRecord { if (identity <= 0) throw new IllegalArgumentException("Invalid GUI item identity"); }
	}

	public record GuiMeshBatchRecord(
		int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
		float alphaCutoff, float[] modelTransform, float[] guiPose,
		int left, int top, int right, int bottom, int guiWidth, int guiHeight,
		int renderWidth, int renderHeight, int guardPixels,
		int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
		List<GuiMeshVertexRecord> vertices, List<Integer> indices, StandardItemFoilRecord itemFoil, int itemRasterScale,
		GuiDecalFoilRecord decalFoil, GuiBlockItemRasterRecord blockItemRaster, GuiItemCacheRecord itemCache
	) {
		private static final ThreadLocal<Boolean> TRUSTED_COPY = ThreadLocal.withInitial(() -> false);

		/**
		 * Builds a frame-local batch from already-owned immutable semantic views.
		 * The canonical constructor still validates every field; only redundant
		 * defensive list/array copies are skipped for this confined handoff.
		 */
		public static GuiMeshBatchRecord trustedOwned(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices, StandardItemFoilRecord itemFoil,
			int itemRasterScale, GuiDecalFoilRecord decalFoil, GuiBlockItemRasterRecord blockItemRaster,
			GuiItemCacheRecord itemCache
		) {
			TRUSTED_COPY.set(true);
			try {
				return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, sequence,
					alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
					renderWidth, renderHeight, guardPixels, clipMode, clipLeft, clipTop, clipWidth, clipHeight,
					vertices, indices, itemFoil, itemRasterScale, decalFoil, blockItemRaster, itemCache);
			} finally {
				TRUSTED_COPY.set(false);
			}
		}

		public GuiMeshBatchRecord(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices, StandardItemFoilRecord itemFoil, int itemRasterScale,
			GuiDecalFoilRecord decalFoil, GuiBlockItemRasterRecord blockItemRaster
		) {
			this(stratum, layerIndex, materialMode, lightingMode, assetId, sequence, alphaCutoff, modelTransform, guiPose,
				left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guardPixels,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, itemFoil, itemRasterScale, decalFoil, blockItemRaster, null);
		}
		public GuiMeshBatchRecord(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices, StandardItemFoilRecord itemFoil, int itemRasterScale,
			GuiDecalFoilRecord decalFoil
		) {
			this(stratum, layerIndex, materialMode, lightingMode, assetId, sequence, alphaCutoff, modelTransform, guiPose,
				left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guardPixels,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, itemFoil, itemRasterScale, decalFoil, null);
		}
		public GuiMeshBatchRecord(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices, StandardItemFoilRecord itemFoil, int itemRasterScale
		) {
			this(stratum, layerIndex, materialMode, lightingMode, assetId, sequence, alphaCutoff, modelTransform, guiPose,
				left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guardPixels,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, itemFoil, itemRasterScale, null);
		}
		public GuiMeshBatchRecord(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			int clipMode, int clipLeft, int clipTop, int clipWidth, int clipHeight,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices
		) {
			this(stratum, layerIndex, materialMode, lightingMode, assetId, sequence, alphaCutoff, modelTransform, guiPose,
				left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guardPixels,
				clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, null, 0);
		}

		public GuiMeshBatchRecord(
			int stratum, int layerIndex, int materialMode, int lightingMode, long assetId, long sequence,
			float alphaCutoff, float[] modelTransform, float[] guiPose,
			int left, int top, int right, int bottom, int guiWidth, int guiHeight,
			int renderWidth, int renderHeight, int guardPixels,
			List<GuiMeshVertexRecord> vertices, List<Integer> indices
		) {
			this(stratum, layerIndex, materialMode, lightingMode, assetId, sequence, alphaCutoff, modelTransform, guiPose,
				left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guardPixels,
				0, 0, 0, 0, 0, vertices, indices);
		}

		public GuiMeshBatchRecord {
			if (itemCache != null && (itemRasterScale == 0 && blockItemRaster == null
				|| !itemCache.animated() && itemFoil != null))
				throw new IllegalArgumentException("Item cache requires coherent native item animation semantics");
			if ((materialMode == GUI_MESH_MATERIAL_ENTITY_CUTOUT_NO_CULL
				|| materialMode == GUI_MESH_MATERIAL_ENTITY_TRANSLUCENT_NO_CULL
				|| materialMode == GUI_MESH_MATERIAL_ENTITY_DECAL_CUTOUT_NO_CULL)
				&& (lightingMode != GUI_MESH_LIGHTING_ENTITY_PREVIEW || alphaCutoff != 0.1F
					|| itemRasterScale != 0 || itemFoil != null))
				throw new IllegalArgumentException("entity-preview material requires native preview lighting and cutout semantics");
			if (materialMode == GUI_MESH_MATERIAL_MODEL_OVERLAY && (itemRasterScale == 0
				|| lightingMode != GUI_MESH_LIGHTING_FRONT_MODEL || alphaCutoff != 0.0F || itemFoil != null))
				throw new IllegalArgumentException("model overlay requires native front-lit base geometry without cutout or foil");
			if (blockItemRaster != null && (itemRasterScale != 0 || renderWidth != 0 || renderHeight != 0 || guardPixels != 0
				|| decalFoil != null || !(materialMode == 4 ? lightingMode == 1 && itemFoil != null
					: materialMode >= 1 && materialMode <= 3 && lightingMode == GUI_MESH_LIGHTING_INVENTORY_BLOCK)))
				throw new IllegalArgumentException("conflicting native block item layout semantics");
			if (decalFoil != null && itemFoil == null) throw new IllegalArgumentException("decal foil requires explicit timing and strength");
			if (decalFoil != null && decalFoil.nativeItemLayout() != (itemRasterScale > 0)) throw new IllegalArgumentException("decal and item raster coordinate spaces differ");
			if (lightingMode == GUI_MESH_LIGHTING_FRONT_MODEL && (itemRasterScale == 0 || materialMode == 4 || decalFoil != null || blockItemRaster != null))
				throw new IllegalArgumentException("front model lighting requires a native base item mesh");
			if (itemRasterScale < 0 || (itemRasterScale > 0 && ((lightingMode != 1 && lightingMode != GUI_MESH_LIGHTING_FRONT_MODEL) || renderWidth != 0
				|| renderHeight != 0 || guardPixels != 0))) throw new IllegalArgumentException("conflicting flat item raster semantics");
			if (itemFoil != null && materialMode != 4) throw new IllegalArgumentException("standard foil requires glint material");
			if (itemFoil != null && itemFoil.kind() == StandardFoilKind.ARMOR)
				throw new IllegalArgumentException("perspective armor foil is not a GUI entity-preview material");
			if (itemFoil != null && itemFoil.kind() == StandardFoilKind.ARMOR_ORTHOGRAPHIC
				&& (lightingMode != GUI_MESH_LIGHTING_ENTITY_PREVIEW || itemRasterScale != 0
					|| blockItemRaster != null || decalFoil != null))
				throw new IllegalArgumentException("orthographic armor foil requires explicit GUI entity-preview semantics");
			if (itemFoil != null && itemFoil.kind() == StandardFoilKind.ENTITY
				&& (itemRasterScale == 0 || blockItemRaster != null || decalFoil != null || lightingMode != 1))
				throw new IllegalArgumentException("entity foil requires native front-lit model item layout");
			if (assetId == 0L || layerIndex < 0 || (materialMode != 1 && materialMode != 2 && materialMode != 3 && materialMode != 4 && materialMode != GUI_MESH_MATERIAL_PANORAMA && materialMode != GUI_MESH_MATERIAL_MODEL_OVERLAY
				&& materialMode != GUI_MESH_MATERIAL_ENTITY_CUTOUT_NO_CULL && materialMode != GUI_MESH_MATERIAL_ENTITY_TRANSLUCENT_NO_CULL
				&& materialMode != GUI_MESH_MATERIAL_ENTITY_DECAL_CUTOUT_NO_CULL)
				|| (lightingMode != 1 && lightingMode != 2 && lightingMode != GUI_MESH_LIGHTING_INVENTORY_BLOCK && lightingMode != GUI_MESH_LIGHTING_FRONT_MODEL && lightingMode != GUI_MESH_LIGHTING_ENTITY_PREVIEW) || !Float.isFinite(alphaCutoff)
				|| guiWidth <= 0 || guiHeight <= 0 || (itemRasterScale == 0 && blockItemRaster == null && (renderWidth <= guardPixels * 2 || renderHeight <= guardPixels * 2))
				|| left >= right || top >= bottom) throw new IllegalArgumentException("invalid semantic GUI mesh batch");
			if (clipMode == 0) {
				if (clipLeft != 0 || clipTop != 0 || clipWidth != 0 || clipHeight != 0) throw new IllegalArgumentException("disabled GUI mesh clip must be zero");
			} else if (clipMode != 1 || clipLeft < 0 || clipTop < 0 || clipWidth < 0 || clipHeight < 0
				|| clipLeft > guiWidth || clipTop > guiHeight || clipWidth > guiWidth - clipLeft || clipHeight > guiHeight - clipTop) {
				throw new IllegalArgumentException("invalid GUI mesh clip rectangle");
			}
			if (!TRUSTED_COPY.get()) {
				modelTransform = checkedFiniteCopy(modelTransform, 16, "GUI mesh model transform");
				guiPose = checkedFiniteCopy(guiPose, 6, "GUI mesh GUI pose");
				vertices = vertices instanceof PackedGuiMeshVertices ? vertices : List.copyOf(vertices);
				indices = indices instanceof PackedGuiMeshIndices ? indices : List.copyOf(indices);
			}
			if (vertices.isEmpty() || indices.isEmpty()) throw new IllegalArgumentException("GUI mesh batch requires geometry");
			if (indices instanceof PackedGuiMeshIndices packed) packed.validateVertexCount(vertices.size());
			else for (int index : indices) if (index < 0 || index >= vertices.size()) throw new IllegalArgumentException("GUI mesh index out of range");
		}

		@Override public float[] modelTransform() { return this.modelTransform.clone(); }
		@Override public float[] guiPose() { return this.guiPose.clone(); }

		public GuiMeshBatchRecord withSequence(long value) {
			// Sequencing changes only ordering metadata. These arrays and immutable
			// lists were already validated and copied by the canonical constructor;
			// keep sharing those owned values during the per-frame scheduler flush.
			TRUSTED_COPY.set(true);
			try {
				return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, value,
					alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
					renderWidth, renderHeight, guardPixels, clipMode, clipLeft, clipTop, clipWidth, clipHeight,
					vertices, indices, itemFoil, itemRasterScale, decalFoil, blockItemRaster, itemCache);
			} finally {
				TRUSTED_COPY.set(false);
			}
		}

		public GuiMeshBatchRecord withItemFoil(StandardItemFoilRecord value) {
			return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, sequence,
				alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
				renderWidth, renderHeight, guardPixels, clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, value, itemRasterScale, decalFoil, blockItemRaster, itemCache);
		}

		public GuiMeshBatchRecord withDecalFoil(GuiDecalFoilRecord value) {
			return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, sequence,
				alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
				renderWidth, renderHeight, guardPixels, clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, itemFoil, itemRasterScale, value, blockItemRaster, itemCache);
		}

		/** Input vertices retain ORIGINAL model-space normals when this layout is present. */
		public GuiMeshBatchRecord withBlockItemRaster(GuiBlockItemRasterRecord value) {
			Objects.requireNonNull(value, "block item layout");
			return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, sequence,
				alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
				0, 0, 0, clipMode, clipLeft, clipTop, clipWidth, clipHeight, vertices, indices, itemFoil, itemRasterScale, decalFoil, value, itemCache);
		}

		public GuiMeshBatchRecord withItemCache(GuiItemCacheRecord value) {
			return new GuiMeshBatchRecord(stratum, layerIndex, materialMode, lightingMode, assetId, sequence,
				alphaCutoff, modelTransform, guiPose, left, top, right, bottom, guiWidth, guiHeight,
				renderWidth, renderHeight, guardPixels, clipMode, clipLeft, clipTop, clipWidth, clipHeight,
				vertices, indices, itemFoil, itemRasterScale, decalFoil, blockItemRaster, value);
		}
	}

	private static float[] checkedFiniteCopy(float[] values, int length, String name) {
		if (values == null || values.length != length) throw new IllegalArgumentException(name + " length");
		float[] copy = values.clone();
		for (float value : copy) if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
		return copy;
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
		,
		int entityId,
		int entityColorArgb,
		int outlineColorArgb
	) {
		public WorldLineSegmentRecord(
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
			this(
				stratum,
				style,
				depthPolicy,
				colorArgb,
				lineWidth,
				startX,
				startY,
				startZ,
				endX,
				endY,
				endZ,
				viewportWidth,
				viewportHeight,
				0,
				0,
				0
			);
		}

		public WorldLineSegmentRecord {
			if (viewportWidth <= 0 || viewportHeight <= 0 || !Float.isFinite(lineWidth) || lineWidth <= 0.0F
				|| !Float.isFinite(startX) || !Float.isFinite(startY) || !Float.isFinite(startZ)
				|| !Float.isFinite(endX) || !Float.isFinite(endY) || !Float.isFinite(endZ)) {
				throw new IllegalArgumentException("world line segment contains invalid copied geometry");
			}
		}
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
			if (viewportWidth <= 0 || viewportHeight <= 0) throw new IllegalArgumentException("world crack quad viewport");
			vertices = checkedFiniteCopy(vertices, 12, "world crack quad vertices");
			vertices = vertices.clone();
		}

		@Override
		public float[] vertices() {
			return vertices.clone();
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
			if (viewportWidth <= 0 || viewportHeight <= 0 || !Float.isFinite(borderSize)
				|| !Float.isFinite(distanceToBorder) || !Float.isFinite(scrollU) || !Float.isFinite(scrollV)
				|| !Float.isFinite(uvU) || !Float.isFinite(uvV) || !Float.isFinite(uvWidth) || !Float.isFinite(uvHeight)) {
				throw new IllegalArgumentException("world border quad contains invalid copied state");
			}
			vertices = checkedFiniteCopy(vertices, 12, "world border quad vertices");
			vertices = vertices.clone();
		}

		@Override
		public float[] vertices() {
			return vertices.clone();
		}
	}

	/** Game surface semantics, not native pipeline or GPU state. */
	public enum ParticleSurface {
		ORDINARY_OPAQUE(0), ORDINARY_TRANSLUCENT(1), TERRAIN_OPAQUE(2), TERRAIN_CUTOUT(3), TERRAIN_TRANSLUCENT(4);
		private final int wireValue;
		ParticleSurface(int wireValue) { this.wireValue = wireValue; }
		public int wireValue() { return wireValue; }
	}
	/** Immutable particle inputs. Geometry and raster policy belong to Rust. */
	public record WorldParticleQuadRecord(int textureId, ParticleSurface surface, int materialIndex,
		float[] center, float[] rotation, float size, float[] uvBounds, int colorArgb, int packedLight) {
		public WorldParticleQuadRecord(int textureId, boolean translucent, int materialIndex,
			float[] center, float[] rotation, float size, float[] uvBounds, int colorArgb, int packedLight) {
			this(textureId, translucent ? ParticleSurface.ORDINARY_TRANSLUCENT : ParticleSurface.ORDINARY_OPAQUE,
				materialIndex, center, rotation, size, uvBounds, colorArgb, packedLight);
		}
		public boolean translucent() {
			return surface == ParticleSurface.ORDINARY_TRANSLUCENT || surface == ParticleSurface.TERRAIN_TRANSLUCENT;
		}
		public WorldParticleQuadRecord {
			Objects.requireNonNull(surface, "particle surface");
			if (textureId == 0 || materialIndex < 0 || !Float.isFinite(size))
				throw new IllegalArgumentException("invalid particle identity, order, or size");
			center = checkedFiniteCopy(center, 3, "particle center");
			rotation = checkedFiniteCopy(rotation, 4, "particle orientation");
			uvBounds = checkedFiniteCopy(uvBounds, 4, "particle sprite bounds");
		}
		@Override public float[] center() { return center.clone(); }
		@Override public float[] rotation() { return rotation.clone(); }
		@Override public float[] uvBounds() { return uvBounds.clone(); }
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
		int viewportHeight,
		int sourceProgram,
		int sourceUvSpace,
		int sourceColorArgb,
		int packedLight,
		int vertex0ColorArgb,
		int vertex1ColorArgb,
		int vertex2ColorArgb,
		int vertex3ColorArgb,
		int vertex0PackedLight,
		int vertex1PackedLight,
		int vertex2PackedLight,
		int vertex3PackedLight,
		int blockEntityId
	) {
		public WorldMaterialQuadRecord {
			if (blockEntityId < -1 || viewportWidth <= 0 || viewportHeight <= 0
				|| viewportWidth > 16384 || viewportHeight > 16384
				|| !Float.isFinite(p0X) || !Float.isFinite(p0Y) || !Float.isFinite(p0Z)
				|| !Float.isFinite(p1X) || !Float.isFinite(p1Y) || !Float.isFinite(p1Z)
				|| !Float.isFinite(p2X) || !Float.isFinite(p2Y) || !Float.isFinite(p2Z)
				|| !Float.isFinite(p3X) || !Float.isFinite(p3Y) || !Float.isFinite(p3Z)
				|| !Float.isFinite(uv0U) || !Float.isFinite(uv0V)
				|| !Float.isFinite(uv1U) || !Float.isFinite(uv1V)
				|| !Float.isFinite(uv2U) || !Float.isFinite(uv2V)
				|| !Float.isFinite(uv3U) || !Float.isFinite(uv3V)) {
				throw new IllegalArgumentException("world material quad contains invalid copied geometry");
			}
			if (blockEntityId < -1) {
				throw new IllegalArgumentException("world material quad block entity id must be >= -1");
			}
		}

		public WorldMaterialQuadRecord(
			int stratum, int materialId, int textureId, int materialMode, int depthPolicy, int cullPolicy,
			int topology, int winding, int colorArgb,
			float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
			float p2X, float p2Y, float p2Z, float p3X, float p3Y, float p3Z,
			float uv0U, float uv0V, float uv1U, float uv1V, float uv2U, float uv2V, float uv3U, float uv3V,
		int viewportWidth, int viewportHeight, int sourceProgram, int sourceUvSpace, int sourceColorArgb, int packedLight
		) {
			this(
				stratum, materialId, textureId, materialMode, depthPolicy, cullPolicy, topology, winding, colorArgb,
				p0X, p0Y, p0Z, p1X, p1Y, p1Z, p2X, p2Y, p2Z, p3X, p3Y, p3Z,
				uv0U, uv0V, uv1U, uv1V, uv2U, uv2V, uv3U, uv3V,
				viewportWidth, viewportHeight, sourceProgram, sourceUvSpace, sourceColorArgb, packedLight,
				 sourceColorArgb, sourceColorArgb, sourceColorArgb, sourceColorArgb,
				 packedLight, packedLight, packedLight, packedLight,
				 activeSemanticBlockEntityId()
			);
		}

		/** Backward-compatible vertex-modulated constructor; captures only the
		 * active copied semantic block-entity scope. */
		public WorldMaterialQuadRecord(
			int stratum, int materialId, int textureId, int materialMode, int depthPolicy, int cullPolicy,
			int topology, int winding, int colorArgb,
			float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
			float p2X, float p2Y, float p2Z, float p3X, float p3Y, float p3Z,
			float uv0U, float uv0V, float uv1U, float uv1V, float uv2U, float uv2V, float uv3U, float uv3V,
			int viewportWidth, int viewportHeight, int sourceProgram, int sourceUvSpace, int sourceColorArgb, int packedLight,
			int vertex0ColorArgb, int vertex1ColorArgb, int vertex2ColorArgb, int vertex3ColorArgb,
			int vertex0PackedLight, int vertex1PackedLight, int vertex2PackedLight, int vertex3PackedLight
		) {
			this(stratum, materialId, textureId, materialMode, depthPolicy, cullPolicy, topology, winding, colorArgb,
				p0X, p0Y, p0Z, p1X, p1Y, p1Z, p2X, p2Y, p2Z, p3X, p3Y, p3Z,
				uv0U, uv0V, uv1U, uv1V, uv2U, uv2V, uv3U, uv3V,
				viewportWidth, viewportHeight, sourceProgram, sourceUvSpace, sourceColorArgb, packedLight,
				vertex0ColorArgb, vertex1ColorArgb, vertex2ColorArgb, vertex3ColorArgb,
				vertex0PackedLight, vertex1PackedLight, vertex2PackedLight, vertex3PackedLight,
				activeSemanticBlockEntityId());
		}

		public boolean hasVertexModulation() {
			return vertex0ColorArgb != sourceColorArgb || vertex1ColorArgb != sourceColorArgb
				|| vertex2ColorArgb != sourceColorArgb || vertex3ColorArgb != sourceColorArgb
				|| vertex0PackedLight != packedLight || vertex1PackedLight != packedLight
				|| vertex2PackedLight != packedLight || vertex3PackedLight != packedLight;
		}
	}

	private record WorldMaterialKeyRecord(
		int stratum,
		int materialId,
		int textureId,
		int materialMode,
		int depthPolicy,
		int cullPolicy,
		int topology,
		int winding,
		int sourceProgram
		,
		int blockEntityId
	) {
		static int hash(WorldMaterialQuadRecord quad) {
			int hash = quad.stratum();
			hash = 31 * hash + quad.materialId();
			hash = 31 * hash + quad.textureId();
			hash = 31 * hash + quad.materialMode();
			hash = 31 * hash + quad.depthPolicy();
			hash = 31 * hash + quad.cullPolicy();
			hash = 31 * hash + quad.topology();
			hash = 31 * hash + quad.winding();
			hash = 31 * hash + quad.sourceProgram();
			hash = 31 * hash + quad.blockEntityId();
			return hash ^ (hash >>> 16);
		}

		boolean matches(WorldMaterialQuadRecord quad) {
			return stratum == quad.stratum()
				&& materialId == quad.materialId()
				&& textureId == quad.textureId()
				&& materialMode == quad.materialMode()
				&& depthPolicy == quad.depthPolicy()
				&& cullPolicy == quad.cullPolicy()
				&& topology == quad.topology()
				&& winding == quad.winding()
				&& sourceProgram == quad.sourceProgram()
				&& blockEntityId == quad.blockEntityId();
		}

		static WorldMaterialKeyRecord from(WorldMaterialQuadRecord quad) {
			return new WorldMaterialKeyRecord(
				quad.stratum(),
				quad.materialId(),
				quad.textureId(),
				quad.materialMode(),
				quad.depthPolicy(),
				quad.cullPolicy(),
				quad.topology(),
				quad.winding(),
				quad.sourceProgram(),
				quad.blockEntityId()
			);
		}
	}

	/** Full-precision semantic input; camera quantization and matrix lowering belong to Rust. */
	public record TerrainFrameCamera(double x, double y, double z) {
		public TerrainFrameCamera {
			if (!TerrainSectionPlacement.isBoundedTerrainCamera(x)
				|| !TerrainSectionPlacement.isBoundedTerrainCamera(y)
				|| !TerrainSectionPlacement.isBoundedTerrainCamera(z)) {
				throw new IllegalArgumentException("terrain frame camera must be finite and bounded");
			}
		}
	}

	/** Immutable semantic section origin; frame camera and Rust matrix lowering remain separate. */
	public record TerrainSectionPlacement(int x, int y, int z) {
		public TerrainSectionPlacement {
			if (!isAlignedTerrainOrigin(x) || !isAlignedTerrainOrigin(y) || !isAlignedTerrainOrigin(z))
				throw new IllegalArgumentException("terrain section origin must be aligned and bounded");
		}

		private static boolean isAlignedTerrainOrigin(int coordinate) {
			return coordinate % 16 == 0 && Math.abs((long)coordinate) <= 30_000_000L;
		}

		private static boolean isBoundedTerrainCamera(double coordinate) {
			return Double.isFinite(coordinate) && Math.abs(coordinate) <= 30_000_000.0;
		}
	}

    private static void encodeModelSubmissionOrder(MemorySegment item,Integer order) {
        Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item,28,order==null?0:1);
        Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item,29,order==null?0:order);
    }

	private static void encodeWorldItemFoil(MemorySegment item, StandardItemFoilRecord foil) {
		Struct.WORLD_MESH_INSTANCE_RECORD.setInt(item, 20, foil == null ? 0 : foil.kind().wireValue());
		Struct.WORLD_MESH_INSTANCE_RECORD.setLong(item, 21, foil == null ? 0L : foil.clockMillis());
		item.set(ValueLayout.JAVA_DOUBLE, Struct.WORLD_MESH_INSTANCE_RECORD.offset(22), foil == null ? 0.0 : foil.speed());
		Struct.WORLD_MESH_INSTANCE_RECORD.setFloat(item, 23, foil == null ? 0.0F : foil.strength());
	}

	/** Original copied item poses; native code owns inverse/decal projection. */
	public record WorldDecalFoilRecord(boolean firstPerson, boolean trustedNormals,
		float[] modelPose, float[] normalPose) {
		public WorldDecalFoilRecord {
			if (modelPose.length != 16 || normalPose.length != 9) {
				throw new IllegalArgumentException("world decal poses require 16 model and 9 normal floats");
			}
			for (float value : modelPose) if (!Float.isFinite(value))
				throw new IllegalArgumentException("world decal model pose must be finite");
			for (float value : normalPose) if (!Float.isFinite(value))
				throw new IllegalArgumentException("world decal normal pose must be finite");
			if (modelPose[3] != 0 || modelPose[7] != 0 || modelPose[11] != 0 || modelPose[15] != 1)
				throw new IllegalArgumentException("world decal model pose must be affine");
			modelPose = modelPose.clone();
			normalPose = normalPose.clone();
		}
		@Override public float[] modelPose() { return modelPose.clone(); }
		@Override public float[] normalPose() { return normalPose.clone(); }
	}

	private static void encodeWorldDecalFoil(MemorySegment item, WorldDecalFoilRecord decal) {
		var layout = Struct.WORLD_MESH_INSTANCE_RECORD;
		layout.setInt(item, 24, decal == null ? 0 : decal.firstPerson() ? 2 : 1);
		layout.setInt(item, 25, decal == null || decal.trustedNormals() ? 0 : 1);
		float[] model = decal == null ? null : decal.modelPose;
		float[] normal = decal == null ? null : decal.normalPose;
		for (int i = 0; i < 16; i++) item.set(ValueLayout.JAVA_FLOAT,
			layout.offset(26) + i * Float.BYTES, model == null ? 0.0F : model[i]);
		for (int i = 0; i < 9; i++) item.set(ValueLayout.JAVA_FLOAT,
			layout.offset(27) + i * Float.BYTES, normal == null ? 0.0F : normal[i]);
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
		int viewportHeight,
		int entityId,
		int entityColorArgb,
		int outlineColorArgb,
		int flags,
		int blockEntityId,
		TerrainSectionPlacement terrainPlacement,
		StandardItemFoilRecord itemFoil,
		WorldDecalFoilRecord decalFoil,
		Integer modelSubmissionOrder,
		int packedLight
	) {
		private static final float[] TERRAIN_IDENTITY_TRANSFORM = {
			1.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 1.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 1.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F
		};

		public static WorldMeshInstanceRecord staticTerrain(
			long meshKey, long meshGeneration, int depthPolicy, int cullPolicy, int winding,
			int flags, int viewportWidth, int viewportHeight, TerrainSectionPlacement placement
		) {
			return new WorldMeshInstanceRecord(
				60, meshKey, meshGeneration, -1, depthPolicy, cullPolicy, winding, 0xFFFFFFFF,
				TERRAIN_IDENTITY_TRANSFORM, viewportWidth, viewportHeight, 0, 0, 0, flags, -1,
				Objects.requireNonNull(placement, "placement"), null, null, null, 0
			);
		}

		private static boolean isTerrainIdentityTransform(float[] transform) {
			return transform.length == 16 && Arrays.equals(transform, TERRAIN_IDENTITY_TRANSFORM);
		}
		public WorldMeshInstanceRecord(int stratum, long meshKey, long meshGeneration, int meshSectionIndex,
            int depthPolicy, int cullPolicy, int winding, int colorArgb, float[] transform,
            int viewportWidth, int viewportHeight, int entityId, int entityColorArgb,
            int outlineColorArgb, int flags, int blockEntityId, TerrainSectionPlacement terrainPlacement,
            StandardItemFoilRecord itemFoil, WorldDecalFoilRecord decalFoil) {
            this(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,winding,colorArgb,
                transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,flags,
                blockEntityId,terrainPlacement,itemFoil,decalFoil,
				stratum == WORLD_MESH_ENTITY_STRATUM ? activeSemanticModelOrder() : null, 0);
		}

		/** Copies the per-instance vanilla UV2 light used by stable model assets. */
		public WorldMeshInstanceRecord withPackedLight(int light) {
			return new WorldMeshInstanceRecord(stratum, meshKey, meshGeneration, meshSectionIndex,
				depthPolicy, cullPolicy, winding, colorArgb, transform, viewportWidth, viewportHeight,
				entityId, entityColorArgb, outlineColorArgb, flags, blockEntityId, terrainPlacement,
				itemFoil, decalFoil, modelSubmissionOrder, light);
		}

        public WorldMeshInstanceRecord withModelSubmissionOrder(Integer order) {
            if (order != null && terrainPlacement != null)
                throw new IllegalArgumentException("terrain placement cannot carry model submission order");
            return new WorldMeshInstanceRecord(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,
                winding,colorArgb,transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,
                flags,blockEntityId,terrainPlacement,itemFoil,decalFoil,order,packedLight);
        }

		public WorldMeshInstanceRecord(int stratum, long meshKey, long meshGeneration, int meshSectionIndex,
			int depthPolicy, int cullPolicy, int winding, int colorArgb, float[] transform,
			int viewportWidth, int viewportHeight, int entityId, int entityColorArgb,
			int outlineColorArgb, int flags, int blockEntityId, TerrainSectionPlacement terrainPlacement,
			StandardItemFoilRecord itemFoil) {
			this(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,winding,colorArgb,
				transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,flags,
				blockEntityId,terrainPlacement,itemFoil,null,null,0);
		}

		public WorldMeshInstanceRecord withDecalFoil(WorldDecalFoilRecord decal) {
			return new WorldMeshInstanceRecord(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,
				winding,colorArgb,transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,
				flags,blockEntityId,terrainPlacement,itemFoil,Objects.requireNonNull(decal),modelSubmissionOrder,packedLight);
		}

		public WorldMeshInstanceRecord(int stratum, long meshKey, long meshGeneration, int meshSectionIndex,
			int depthPolicy, int cullPolicy, int winding, int colorArgb, float[] transform,
			int viewportWidth, int viewportHeight, int entityId, int entityColorArgb,
			int outlineColorArgb, int flags, int blockEntityId, TerrainSectionPlacement terrainPlacement) {
			this(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,winding,colorArgb,
				transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,flags,blockEntityId,terrainPlacement,null);
		}

		public WorldMeshInstanceRecord withItemFoil(StandardItemFoilRecord foil) {
			return new WorldMeshInstanceRecord(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,
				winding,colorArgb,transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,
				flags,blockEntityId,terrainPlacement,Objects.requireNonNull(foil),decalFoil,modelSubmissionOrder,packedLight);
		}
		public WorldMeshInstanceRecord(int stratum, long meshKey, long meshGeneration, int meshSectionIndex,
			int depthPolicy, int cullPolicy, int winding, int colorArgb, float[] transform,
			int viewportWidth, int viewportHeight, int entityId, int entityColorArgb,
			int outlineColorArgb, int flags, int blockEntityId) {
			this(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,winding,colorArgb,
				transform,viewportWidth,viewportHeight,entityId,entityColorArgb,outlineColorArgb,flags,blockEntityId,null);
		}

		public WorldMeshInstanceRecord withTerrainPlacement(TerrainSectionPlacement placement) {
			return new WorldMeshInstanceRecord(stratum,meshKey,meshGeneration,meshSectionIndex,depthPolicy,cullPolicy,
				winding,colorArgb,TERRAIN_IDENTITY_TRANSFORM,viewportWidth,viewportHeight,
				entityId,entityColorArgb,outlineColorArgb,flags,blockEntityId,Objects.requireNonNull(placement),itemFoil,decalFoil,modelSubmissionOrder,packedLight);
		}
		public WorldMeshInstanceRecord(
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
			int viewportHeight,
			int entityId,
			int entityColorArgb,
			int outlineColorArgb
		) {
			this(stratum, meshKey, meshGeneration, meshSectionIndex, depthPolicy, cullPolicy, winding,
				colorArgb, transform, viewportWidth, viewportHeight, entityId, entityColorArgb, outlineColorArgb, 0,
				activeSemanticBlockEntityId());
		}

		/** Backward-compatible explicit-flags constructor; block identity is the
		 * active copied semantic scope, never an Iris lookup. */
		public WorldMeshInstanceRecord(
			int stratum, long meshKey, long meshGeneration, int meshSectionIndex,
			int depthPolicy, int cullPolicy, int winding, int colorArgb, float[] transform,
			int viewportWidth, int viewportHeight, int entityId, int entityColorArgb,
			int outlineColorArgb, int flags
		) {
			this(stratum, meshKey, meshGeneration, meshSectionIndex, depthPolicy, cullPolicy, winding,
				colorArgb, transform, viewportWidth, viewportHeight, entityId, entityColorArgb,
				outlineColorArgb, flags, activeSemanticBlockEntityId());
		}

		public WorldMeshInstanceRecord(
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
			this(
				stratum,
				meshKey,
				meshGeneration,
				meshSectionIndex,
				depthPolicy,
				cullPolicy,
				winding,
				colorArgb,
				transform,
				viewportWidth,
				viewportHeight,
				0,
				0,
				0,
				0,
				-1
			);
		}

		public WorldMeshInstanceRecord {
            if (modelSubmissionOrder != null && (stratum != WORLD_MESH_ENTITY_STRATUM || terrainPlacement != null))
                throw new IllegalArgumentException("model submission order requires an entity mesh without terrain placement");
			if (depthPolicy < 0 || depthPolicy > 3
				|| (depthPolicy == 3 && (stratum != WORLD_MESH_ENTITY_STRATUM || itemFoil != null))) {
				throw new IllegalArgumentException("unsupported world mesh depth policy or equal-depth scope");
			}
			if (decalFoil != null && (itemFoil == null || itemFoil.kind() != StandardFoilKind.ITEM
				|| !Arrays.equals(transform, decalFoil.modelPose()))) {
				throw new IllegalArgumentException("world decal requires item foil and matching draw pose");
			}
			if (itemFoil != null && (stratum != WORLD_MESH_ENTITY_STRATUM || terrainPlacement != null || flags != 0 || blockEntityId != -1)) {
				throw new IllegalArgumentException("standard foil requires an ordinary entity mesh instance");
			}
			Objects.requireNonNull(transform, "transform");
			if (terrainPlacement != null && (stratum != 60 || meshSectionIndex != -1 || entityId != 0
				|| blockEntityId != -1 || !isTerrainIdentityTransform(transform))) {
				throw new IllegalArgumentException("terrain placement requires a complete terrain instance and neutral matrix");
			}
			if (meshKey == 0L || meshGeneration == 0L) {
				throw new IllegalArgumentException("world mesh instance key and generation must be non-zero");
			}
			int viewLayerFlags = flags & (WORLD_MESH_VIEW_LAYER_PERSPECTIVE | WORLD_MESH_VIEW_LAYER_ORTHOGRAPHIC);
			if (viewLayerFlags != 0 && (viewLayerFlags == 12 || (flags & ~12) != 0
				|| stratum != WORLD_MESH_ENTITY_STRATUM || itemFoil != null || terrainPlacement != null || blockEntityId != -1)) {
				throw new IllegalArgumentException("view layering requires one projection and an ordinary entity mesh");
			}
			if (flags < 0 || (flags & ~15) != 0) {
				throw new IllegalArgumentException("world mesh instance contains unknown semantic flags");
			}
			if ((flags & 2) != 0 && (stratum != 60 || meshSectionIndex != -1 || depthPolicy < 1 || depthPolicy > 2)) {
				throw new IllegalArgumentException("camera-sorted quads require a complete translucent terrain instance");
			}
			if (blockEntityId < -1) {
				throw new IllegalArgumentException("world mesh instance block entity id must be >= -1");
			}
			if (packedLight < 0 || packedLight > 0x00FF00FF) {
				throw new IllegalArgumentException("world mesh instance packed light must contain vanilla UV2 channels");
			}
			if ((flags & 1) != 0 && (stratum != WORLD_MESH_ENTITY_STRATUM || outlineColorArgb == 0)) {
				throw new IllegalArgumentException("outline-only mesh instances require an entity stratum and outline color");
			}
			if (transform.length != 16) {
				throw new IllegalArgumentException("world mesh instance transform must contain 16 floats");
			}
			// Terrain placement is immutable, full-precision frame data. Its neutral
			// matrix is a private canonical value used only to preserve the common
			// mesh ABI; the public accessor still returns a detached copy. General
			// model poses retain their ordinary defensive construction copy.
			transform = terrainPlacement == null ? transform.clone() : TERRAIN_IDENTITY_TRANSFORM;
		}

		@Override
		public float[] transform() {
			return transform.clone();
		}
	}

	/** One copied backend-neutral glyph quad in a shared world-text frame stream. */
	public record WorldTextQuadRecord(
		long assetId,
		long atlasGeneration,
		long atlasRevision,
		boolean colored,
		int depthPolicy,
		int packedLight,
		int colorArgb,
		double distanceToCameraSq,
		float[] modelViewMatrix,
		float[] positions,
		float[] uvs,
		int blockEntityId
	) {
		public WorldTextQuadRecord(long assetId, long atlasGeneration, long atlasRevision, boolean colored,
			int depthPolicy, int packedLight, int colorArgb, double distanceToCameraSq,
			float[] modelViewMatrix, float[] positions, float[] uvs) {
			this(assetId, atlasGeneration, atlasRevision, colored, depthPolicy, packedLight, colorArgb,
				distanceToCameraSq, modelViewMatrix, positions, uvs, -1);
		}
		public WorldTextQuadRecord {
			Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
			Objects.requireNonNull(positions, "positions");
			Objects.requireNonNull(uvs, "uvs");
			if (blockEntityId < -1) {
				throw new IllegalArgumentException("world text quad block entity id must be >= -1");
			}
			if (assetId == 0L || atlasGeneration <= 0L || atlasRevision <= 0L
				|| (depthPolicy != 1 && depthPolicy != 2 && depthPolicy != 3) || !Double.isFinite(distanceToCameraSq)
				|| distanceToCameraSq < 0.0 || modelViewMatrix.length != 16
				|| positions.length != 12 || uvs.length != 8) {
				throw new IllegalArgumentException("invalid semantic world text quad");
			}
			for (float value : modelViewMatrix) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world text matrix must be finite");
				}
			}
			for (float value : positions) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world text positions must be finite");
				}
			}
			for (float value : uvs) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("world text UVs must be finite");
				}
			}
			modelViewMatrix = modelViewMatrix.clone();
			positions = positions.clone();
			uvs = uvs.clone();
		}

		@Override
		public float[] modelViewMatrix() {
			return this.modelViewMatrix.clone();
		}

		@Override
		public float[] positions() {
			return this.positions.clone();
		}

		@Override
		public float[] uvs() {
			return this.uvs.clone();
		}
	}

	public record WorldBackgroundRecord(
		boolean enabled,
		int skyType,
		int loadIntent,
		int storeIntent,
		int colorArgb,
		int viewportWidth,
		int viewportHeight,
		boolean skyVisible,
		boolean skySunriseOrSunset,
		boolean skyDarkDisc,
		float skySunAngle,
		float skyTimeOfDay,
		float skyRainBrightness,
		float skyStarBrightness,
		int skySunriseAndSunsetColorArgb,
		int skyMoonPhase,
		float skyEndFlashIntensity,
		float skyEndFlashXAngle,
		float skyEndFlashYAngle,
		int skyColorArgb
	) {
		/** Preserves the original clear-only constructor for existing routes. */
		public WorldBackgroundRecord(
			boolean enabled,
			int skyType,
			int loadIntent,
			int storeIntent,
			int colorArgb,
			int viewportWidth,
			int viewportHeight
		) {
			this(
				enabled, skyType, loadIntent, storeIntent, colorArgb, viewportWidth, viewportHeight,
				false, false, false, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0.0F, 0.0F, 0.0F, 0
			);
		}

		/**
		 * Attaches copied vanilla sky semantics without introducing a Java render
		 * object, native handle, or pass-selection policy to the transport record.
		 */
		public WorldBackgroundRecord withSky(
			boolean visible,
			boolean sunriseOrSunset,
			boolean darkDisc,
			float sunAngle,
			float timeOfDay,
			float rainBrightness,
			float starBrightness,
			int sunriseAndSunsetColorArgb,
			int moonPhase,
			float endFlashIntensity,
			float endFlashXAngle,
			float endFlashYAngle,
			int skyColorArgb
		) {
			return new WorldBackgroundRecord(
				enabled, skyType, loadIntent, storeIntent, colorArgb, viewportWidth, viewportHeight,
				visible, sunriseOrSunset, darkDisc, sunAngle, timeOfDay, rainBrightness, starBrightness,
				sunriseAndSunsetColorArgb, moonPhase, endFlashIntensity, endFlashXAngle, endFlashYAngle, skyColorArgb
			);
		}

		public static WorldBackgroundRecord diagnosticFallback() {
			return new WorldBackgroundRecord(false, 0, 0, 0, 0, 0, 0);
		}
	}

	/** Copied world/camera semantic input consumed by the Rust-owned volume mapping. */
	public record WorldVoxelVolumeFrameRecord(
		boolean enabled,
		long worldGeneration,
		long resourceGeneration,
		float cameraX,
		float cameraY,
		float cameraZ
	) {
		public WorldVoxelVolumeFrameRecord {
			if (!Float.isFinite(cameraX) || !Float.isFinite(cameraY) || !Float.isFinite(cameraZ)) {
				throw new IllegalArgumentException("voxel-volume camera coordinates must be finite");
			}
		}

		public static WorldVoxelVolumeFrameRecord disabled() {
			return new WorldVoxelVolumeFrameRecord(false, 0L, 0L, 0.0F, 0.0F, 0.0F);
		}
	}

	/**
	 * Coarse first-person projection/depth semantics for the Rust-owned
	 * held-item pass. This contains no Java renderer, Iris state, native object,
	 * or backend policy. Per-item transforms remain mesh-instance data while
	 * the frame supplies the distinct hand model-view and projection domains.
	 */
	public record WorldFirstPersonFrameRecord(
		boolean enabled,
		boolean clearDepthBefore,
		int mainHandInstanceCount,
		float[] projectionMatrix,
		float[] modelViewMatrix
	) {
		public WorldFirstPersonFrameRecord {
			Objects.requireNonNull(projectionMatrix, "projectionMatrix");
			Objects.requireNonNull(modelViewMatrix, "modelViewMatrix");
			if (projectionMatrix.length != 16) {
				throw new IllegalArgumentException("first-person projection matrix must contain 16 floats");
			}
			if (modelViewMatrix.length != 16) {
				throw new IllegalArgumentException("first-person model-view matrix must contain 16 floats");
			}
			if (mainHandInstanceCount < 0) {
				throw new IllegalArgumentException("first-person main-hand instance count must be non-negative");
			}
			for (float value : projectionMatrix) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("first-person projection matrix must contain finite values");
				}
			}
			for (float value : modelViewMatrix) {
				if (!Float.isFinite(value)) {
					throw new IllegalArgumentException("first-person model-view matrix must contain finite values");
				}
			}
			projectionMatrix = projectionMatrix.clone();
			modelViewMatrix = modelViewMatrix.clone();
		}

		@Override
		public float[] projectionMatrix() {
			return projectionMatrix.clone();
		}

		@Override
		public float[] modelViewMatrix() {
			return modelViewMatrix.clone();
		}

		public static WorldFirstPersonFrameRecord disabled() {
			return new WorldFirstPersonFrameRecord(false, false, 0, new float[16], new float[16]);
		}
	}

	/** Copied vanilla environment semantics consumed by Rust-owned shader work. */
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
		int offHandItemLightEmission,
		boolean lightmapEnabled,
		long lightmapGeneration,
		float lightmapAmbientLightFactor,
		float lightmapSkyFactor,
		float lightmapBlockFactor,
		float lightmapNightVisionFactor,
		float lightmapDarknessScale,
		float lightmapDarkenWorldFactor,
		float lightmapBrightnessFactor,
		float lightmapSkyLightRed,
		float lightmapSkyLightGreen,
		float lightmapSkyLightBlue,
		float lightmapAmbientRed,
		float lightmapAmbientGreen,
		float lightmapAmbientBlue,
		float blindness,
		float darknessFactor,
		int eyeBrightnessBlock,
		int eyeBrightnessSky,
		float fogParameterColorRed,
		float fogParameterColorGreen,
		float fogParameterColorBlue,
		float fogParameterColorAlpha,
		float fogEnvironmentalStart,
		float fogEnvironmentalEnd,
		float fogRenderDistanceStart,
		float fogRenderDistanceEnd,
		int distantHorizonsRenderDistance,
		/// Vanilla SKY pipeline range.  This is copied gameplay fog data, not
		/// a Java uniform buffer or any Iris-owned rendering state.
		float fogSkyEnd,
		/// Vanilla CLOUDS pipeline range. This stays a copied semantic value; no
		/// Java fog UBO or renderer state crosses the VulkanicGAL boundary.
		float fogCloudsEnd
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
			0, "", "", "", 0, 0,
			false, 0L,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 0.0F, 0, 0,
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0F, 0.0F
		);
		}
	}

	/**
	 * Copied feature-family inventory for whole-frame source-route admission.
	 * These counts contain no render types, models, callbacks, Iris state, or
	 * native backend objects.
	 */
	public record WorldFeatureCoverageRecord(
		int modelSubmits,
		int modelPartSubmits,
		int blockModelSubmits,
		int ordinaryBlockSubmits,
		int itemSubmits,
		int customGeometrySubmits,
		int shadowSubmits,
		int flameSubmits,
		int nameTagSubmits,
		int textSubmits,
		int hitboxSubmits,
		int leashSubmits,
		int particleGroupSubmits
	) {
		public static WorldFeatureCoverageRecord empty() {
			return new WorldFeatureCoverageRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
		long guiMeshItemCount,
		long guiMeshBatchCount,
		long guiMeshDrawCount,
		long guiEntityPreviewItemCount,
		long guiEntityPreviewBatchCount,
		long guiEntityPreviewDrawCount,
		long guiEntityPreviewMaterialMask,
		long guiEntityPreviewVertexCount,
		long guiEntityPreviewIndexCount,
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
		long worldMeshDynamicOffsetCount,
		long guiMeshPrepareNanos,
		long guiMeshLowerNanos,
		long gpuDistantHorizonsOpaqueNanos,
		long worldMeshPageIndirectBatchCount,
		long worldMeshPageIndirectRunCount,
		long worldMeshDynamicTerrainBatchCount,
		long worldMeshDynamicNonTerrainBatchCount,
		WholeFrameProfileExtension extension
	) {
		public long worldMeshTerrainTranslucentBatchCount() {
			return extension.worldMeshTerrainTranslucentBatchCount();
		}

		public long wholeFrameNativeTotalNanos() {
			return extension.wholeFrameNativeTotalNanos();
		}

		public long worldPostSubmitConfirmNanos() {
			return extension.worldPostSubmitConfirmNanos();
		}

		public long galCommandRecordingFinishNanos() {
			return extension.galCommandRecordingFinishNanos();
		}

		public long galCommandRecordingDeferredDestroys() {
			return extension.galCommandRecordingDeferredDestroys();
		}

		public static WholeFrameProfile empty() {
			return wholeFrameProfileAt(MemorySegment.ofArray(new long[131]), 0L);
		}
	}

	private record WholeFrameProfileExtension(
		long worldMeshTerrainTranslucentBatchCount,
		long wholeFrameNativeTotalNanos,
		long worldPostSubmitConfirmNanos,
		long galCommandRecordingFinishNanos,
		long galCommandRecordingDeferredDestroys
	) { }

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
		private static final MethodHandle FRAME_CANCEL = downcall("mattmc_vulkanic_gal_frame_cancel", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle FRAME_SHUTDOWN = downcall("mattmc_vulkanic_gal_frame_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_SUBMIT_FRAME = downcall("mattmc_vulkanic_gal_gui_submit_frame", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WHOLE_FRAME_SUBMIT = downcall("mattmc_vulkanic_gal_whole_frame_submit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_PRIMITIVES_SUBMIT = downcall("mattmc_vulkanic_gal_world_primitives_submit", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_gui_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_UPDATE_RAW_IMAGES = downcall("mattmc_vulkanic_gal_gui_update_raw_images", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle GUI_UPDATE_ATLAS_REFERENCES = downcall("mattmc_vulkanic_gal_gui_update_atlas_references", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_TEXT_UPDATE_IMAGES = downcall("mattmc_vulkanic_gal_world_text_update_images", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_BORDER_UPDATE_ASSET = downcall("mattmc_vulkanic_gal_world_border_update_asset", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_CRACK_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_crack_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_MATERIAL_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_material_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_MESH_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_mesh_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle ATLAS_ANIMATION_STAGE_ASSETS = downcall("mattmc_vulkanic_gal_atlas_animation_stage_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle ATLAS_ANIMATION_TICK = downcall("mattmc_vulkanic_gal_atlas_animation_tick", FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
			ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.ADDRESS));
		private static final MethodHandle WORLD_LOD_UPDATE_ASSETS = downcall("mattmc_vulkanic_gal_world_lod_update_assets", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
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

		static int frameCancel(long contextId, MemorySegment request, MemorySegment status) {
			try {
				return (int) FRAME_CANCEL.invokeExact(contextId, request, status);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to cancel Rust VulkanicGAL frame", throwable);
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

		static int guiUpdateAtlasReferences(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) GUI_UPDATE_ATLAS_REFERENCES.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL GUI atlas references", throwable);
			}
		}

		static int guiUpdateRawImages(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) GUI_UPDATE_RAW_IMAGES.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL raw GUI images", throwable);
			}
		}

		static int worldBorderUpdateAsset(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_BORDER_UPDATE_ASSET.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world-border asset", throwable);
			}
		}

		static int worldTextUpdateImages(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_TEXT_UPDATE_IMAGES.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world text images", throwable);
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

		static int atlasAnimationTick(long contextId, int textureId, long generation, long tick,
			MemorySegment ids, long count, int onlyVisible, MemorySegment accepted, MemorySegment status) {
			try {
				return (int) ATLAS_ANIMATION_TICK.invokeExact(contextId, textureId, generation, tick,
					ids, count, onlyVisible, accepted, status);
			} catch (Throwable error) {
				throw new IllegalStateException("atlas animation tick downcall failed", error);
			}
		}

		static int atlasAnimationStageAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) ATLAS_ANIMATION_STAGE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to stage Rust atlas animation assets", throwable);
			}
		}

		static int worldMeshUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_MESH_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world mesh assets", throwable);
			}
		}

		static int worldLodUpdateAssets(long contextId, MemorySegment request, MemorySegment result) {
			try {
				return (int) WORLD_LOD_UPDATE_ASSETS.invokeExact(contextId, request, result);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Failed to update Rust VulkanicGAL world LOD assets", throwable);
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
			FRAME_CANCEL(100),
			DESTROY_DESC(43),
			GUI_SPRITE_REQUEST(44),
			GUI_FRAME_SUBMIT(45),
			GUI_FRAME_SUBMIT_RESULT(46),
			GUI_ASSET_PAYLOAD(47),
			GUI_ASSET_UPDATE(48),
			WINDOWED_VULKAN_CONTEXT_CREATE(49),
			WORLD_LINE_SEGMENT_REQUEST(50),
			WORLD_PARTICLE_QUAD_REQUEST(108),
			WORLD_EXPERIENCE_ORB_ASSET(109),
			WORLD_EXPERIENCE_ORB_INSTANCE(110),
			WORLD_DH_GENERIC_BOX(111),
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
					SHADER_PACK_ASSET_UPDATE(77),
					WORLD_LOD_VERTEX(78),
					WORLD_LOD_SEGMENT_RECORD(79),
					WORLD_LOD_COLUMN_ASSET_RECORD(80),
					WORLD_LOD_COLUMN_RETIREMENT_RECORD(81),
					WORLD_LOD_ASSET_UPDATE(82),
					WORLD_LOD_COLUMN_INSTANCE_RECORD(83),
					WORLD_LOD_RENDER_FRAME(84),
					WORLD_LOD_MATERIAL_IDENTITY_RECORD(85),
					WORLD_LOD_SEGMENT_MATERIAL_PROVENANCE_RECORD(86),
					WORLD_LOD_COLUMN_MATERIAL_PROVENANCE_RECORD(87),
					WORLD_MESH_ASSET_RETIREMENT_RECORD(99),
			WORLD_LOD_FACE_MATERIAL_RECORD(88),
			WORLD_FEATURE_COVERAGE(89),
			GUI_RAW_IMAGE_ASSET_PAYLOAD(90),
			GUI_RAW_IMAGE_UPDATE(91),
			GUI_AFFINE_QUAD_REQUEST(92),
			WORLD_TEXT_QUAD_REQUEST(93),
			WORLD_TEXT_IMAGE_ASSET_PAYLOAD(94),
			WORLD_TEXT_IMAGE_UPDATE(95),
			// Coarse semantic GUI-item mesh layouts. They are accepted only by the
			// explicit Rust-owned standard-3D GUI route.
			GUI_MESH_VERTEX(96),
			GUI_MESH_BATCH_REQUEST(97),
			WORLD_FIRST_PERSON_FRAME(98),
			GUI_TILED_QUAD_REQUEST(101),
			SPRITE_ANIMATION_MIP(102),
			SPRITE_ANIMATION_SOURCE(103),
			ATLAS_ANIMATION_ASSET_UPDATE(104),
			GUI_ATLAS_REFERENCE(105),
			GUI_ATLAS_REFERENCE_UPDATE(106),
			GUI_ITEM_RASTER_LAYER(107);

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

		static void writeCachedBytes(
			Arena arena, MemorySegment segment, Struct struct, int field,
			Map<String, MemorySegment> cache, String value
		) {
			byte[] bytes = utf8Identity(value);
			MemorySegment encoded = cache.computeIfAbsent(value,
				ignored -> bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes));
			long base = struct.offset(field);
			segment.set(ValueLayout.ADDRESS, base, encoded);
			segment.set(ValueLayout.JAVA_LONG, base + 8, bytes.length);
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
