package net.vulkanic.bridge;

import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.TracyCompat;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Queue;

public final class RustGalFrameQueue {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String GUI_AFTER_MINECRAFT = "gui.after_minecraft";
	private static final int TEST_SIZE = 4;
	private static final int TEST_TEXTURE_BYTES = TEST_SIZE * TEST_SIZE * 4;
	private static final Queue<DeferredBatch> GUI_BATCHES = new ArrayDeque<>();
	private static VulkanicGalBridge bridge;
	private static Resources resources;
	private static Thread renderThread;
	private static int configuredWidth;
	private static int configuredHeight;
	private static long nextCorrelationId = 1L;
	private static boolean testBatchQueuedThisFrame;
	private static boolean testBatchExecuted;

	private RustGalFrameQueue() {
	}

	public static void enqueueTestGuiBatchIfRequested(Minecraft minecraft) {
		if (!Boolean.getBoolean("mattmc.dev.rustGalDeferredGuiTest")) {
			return;
		}
		if (testBatchExecuted || testBatchQueuedThisFrame) {
			return;
		}
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		testBatchQueuedThisFrame = true;
		enqueueGuiBatch(new DeferredBatch(GUI_AFTER_MINECRAFT, "test.deferred.textured.gui"));
	}

	public static void enqueueGuiBatch(DeferredBatch batch) {
		synchronized (GUI_BATCHES) {
			GUI_BATCHES.add(batch);
		}
	}

	public static void executeGuiStratum(Minecraft minecraft) {
		Queue<DeferredBatch> ready = new ArrayDeque<>();
		synchronized (GUI_BATCHES) {
			while (!GUI_BATCHES.isEmpty()) {
				DeferredBatch batch = GUI_BATCHES.peek();
				if (!GUI_AFTER_MINECRAFT.equals(batch.stratum())) {
					break;
				}
				ready.add(GUI_BATCHES.remove());
			}
		}
		if (ready.isEmpty()) {
			testBatchQueuedThisFrame = false;
			return;
		}
		try {
			ensureRenderThreadAndContext(minecraft);
			while (!ready.isEmpty()) {
				execute(minecraft, ready.remove());
			}
		} finally {
			testBatchQueuedThisFrame = false;
		}
	}

	public static void shutdown() {
		VulkanicGalBridge existing = bridge;
		bridge = null;
		resources = null;
		renderThread = null;
		configuredWidth = 0;
		configuredHeight = 0;
		testBatchExecuted = false;
		synchronized (GUI_BATCHES) {
			GUI_BATCHES.clear();
		}
		if (existing != null) {
			try {
				existing.shutdownFrame();
			} finally {
				existing.close();
			}
		}
	}

	private static void execute(Minecraft minecraft, DeferredBatch batch) {
		Window window = minecraft.getWindow();
		ensureConfigured(window);
		long correlationId = nextCorrelationId++;
		VulkanicGalBridge.AcquiredFrame frame = bridge.acquireFrame(correlationId, window.getWidth(), window.getHeight());
		if (frame.status() == 4 || frame.frameTarget() == 0L) {
			return;
		}
		VulkanicGalBridge.ResourceResults passResult = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.frameRenderPass(9000, batch.label() + ".pass", frame.frameTarget())
				.build());
		long pass = passResult.handle(0);
		VulkanicGalBridge.SubmissionBatch submit = bridge.submissionBatchBuilder(batch.label() + ".submit")
			.hostWrite(resources.uploadBuffer, 0, textureBytes())
			.barrier(resources.uploadBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_TRANSFER_SRC, false)
			.hostWrite(resources.indexBuffer, 0, indexBytes())
			.barrier(resources.indexBuffer, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
			.barrier(resources.texture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_TRANSFER_DST, true)
			.copyBufferToTexture(resources.uploadBuffer, resources.texture, TEST_SIZE, TEST_SIZE)
			.barrier(resources.texture, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, true)
			.beginFramePass(pass, frame.frameTarget())
			.bindGraphicsPipeline(resources.pipeline)
			.bindResourceSet(resources.pipelineLayout, resources.resourceSet)
			.setIndexBuffer(resources.indexBuffer)
			.drawIndexed(6)
			.endPass()
			.build();
		VulkanicGalBridge.Status status = bridge.submit(submit);
		TracyCompat.message("gal.frame.deferred producer=test-gui stratum=" + batch.stratum() + " frame=" + frame.frameId() + " submission=" + status.submissionId());
		VulkanicGalBridge.Completion completion = pollCompletion(status.submissionId());
		if (!completion.complete()) {
			throw new IllegalStateException("Rust VulkanicGAL deferred GUI submission did not complete: requested=" + completion.requestedSubmissionId()
				+ " completed=" + completion.completedSubmissionId());
		}
		bridge.presentFrame(frame.frameId(), correlationId, status.submissionId());
		bridge.retire(status.submissionId());
		bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.destroy(pass, VulkanicGalBridge.HANDLE_RENDER_PASS)
				.destroy(frame.frameTarget(), VulkanicGalBridge.HANDLE_FRAME_TARGET)
				.build());
		if ("test.deferred.textured.gui".equals(batch.label())) {
			testBatchExecuted = true;
		}
		LOGGER.info("Rust VulkanicGAL deferred GUI batch executed: stratum={}, frame={}, submission={}", batch.stratum(), frame.frameId(), status.submissionId());
	}

	private static VulkanicGalBridge.Completion pollCompletion(long submission) {
		long deadline = System.nanoTime() + 2_000_000_000L;
		VulkanicGalBridge.Completion completion = bridge.completion(submission);
		while (!completion.complete() && System.nanoTime() < deadline) {
			Thread.onSpinWait();
			completion = bridge.completion(submission);
		}
		return completion;
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
			resources = createResources(bridge);
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
			bridge.configureFrame("minecraft.borrowed.opengl.default", width, height, VulkanicGalBridge.FORMAT_RGBA8);
		} else {
			bridge.resizeFrame(nextCorrelationId++, width, height);
		}
		configuredWidth = width;
		configuredHeight = height;
	}

	private static Resources createResources(VulkanicGalBridge bridge) {
		VulkanicGalBridge.ResourceResults base = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.buffer(1, "frame.upload.texture", TEST_TEXTURE_BYTES, VulkanicGalBridge.MEMORY_UPLOAD,
					VulkanicGalBridge.BUFFER_TRANSFER_SRC | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(2, "frame.index", 24, VulkanicGalBridge.MEMORY_UPLOAD,
					VulkanicGalBridge.BUFFER_INDEX | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.texture(3, "frame.sampled", VulkanicGalBridge.FORMAT_RGBA8, TEST_SIZE, TEST_SIZE,
					VulkanicGalBridge.TEXTURE_SAMPLED | VulkanicGalBridge.TEXTURE_TRANSFER_DST)
				.sampler(4, "frame.sampler")
				.shader(5, "frame.vertex", VulkanicGalBridge.SHADER_VERTEX, VERTEX_SHADER_OPENGL)
				.shader(6, "frame.fragment", VulkanicGalBridge.SHADER_FRAGMENT, FRAGMENT_SHADER_OPENGL)
				.build());
		long uploadBuffer = base.handle(0);
		long indexBuffer = base.handle(1);
		long texture = base.handle(2);
		long sampler = base.handle(3);
		long vertex = base.handle(4);
		long fragment = base.handle(5);
		VulkanicGalBridge.ResourceResults views = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.textureView(10, "frame.sampled.view", texture, VulkanicGalBridge.FORMAT_RGBA8)
				.build());
		long sampledView = views.handle(0);
		VulkanicGalBridge.ResourceResults layout = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.resourceLayout(20, "frame.resource.layout",
					new VulkanicGalBridge.BindingDesc(0, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE, 1, false),
					new VulkanicGalBridge.BindingDesc(1, VulkanicGalBridge.BINDING_SAMPLER, 1, false))
				.build());
		long resourceLayout = layout.handle(0);
		VulkanicGalBridge.ResourceResults set = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.resourceSet(21, "frame.resource.set", resourceLayout,
					new VulkanicGalBridge.Binding(0, 0, sampledView, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE),
					new VulkanicGalBridge.Binding(1, 0, sampler, VulkanicGalBridge.BINDING_SAMPLER))
				.build());
		long resourceSet = set.handle(0);
		VulkanicGalBridge.ResourceResults pipelineLayout = bridge.resourceBatch(
			bridge.resourceBatchBuilder().pipelineLayout(30, "frame.pipeline.layout", resourceLayout).build());
		long pipelineLayoutHandle = pipelineLayout.handle(0);
		VulkanicGalBridge.ResourceResults pipeline = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.graphicsPipelineNoDepth(31, "frame.pipeline", pipelineLayoutHandle, vertex, fragment)
				.build());
		return new Resources(uploadBuffer, indexBuffer, texture, pipeline.handle(0), pipelineLayoutHandle, resourceSet);
	}

	private static byte[] textureBytes() {
		return new byte[] {
			(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)255, 0, 0, (byte)255, (byte)255, (byte)255, (byte)255, 0, (byte)255,
			(byte)255, (byte)255, (byte)255, (byte)255, 0, (byte)128, (byte)255, (byte)255, (byte)255, 0, (byte)255, (byte)255, 32, 32, 32, (byte)255,
			(byte)255, (byte)128, 0, (byte)255, 0, (byte)255, (byte)128, (byte)255, (byte)128, 0, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255,
			64, 64, 64, (byte)255, (byte)255, 0, (byte)128, (byte)255, 0, (byte)255, (byte)255, (byte)255, (byte)128, (byte)255, 0, (byte)255
		};
	}

	private static byte[] indexBytes() {
		byte[] bytes = new byte[24];
		int[] values = {0, 1, 2, 2, 3, 0};
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

	public record DeferredBatch(String stratum, String label) {
	}

	private record Resources(long uploadBuffer, long indexBuffer, long texture, long pipeline, long pipelineLayout, long resourceSet) {
	}

	private static final String VERTEX_SHADER_OPENGL = """
		#version 330 core
		out vec2 v_uv;
		const vec2 pos[4] = vec2[4](
		    vec2(-0.06, -0.06),
		    vec2( 0.06, -0.06),
		    vec2( 0.06,  0.06),
		    vec2(-0.06,  0.06)
		);
		const vec2 uv[4] = vec2[4](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0)
		);
		void main() {
		    gl_Position = vec4(pos[gl_VertexID], 0.0, 1.0);
		    v_uv = uv[gl_VertexID];
		}
		""";

	private static final String FRAGMENT_SHADER_OPENGL = """
		#version 330 core
		uniform sampler2D tex0;
		in vec2 v_uv;
		out vec4 out_color;
		void main() {
		    out_color = texture(tex0, v_uv) * vec4(1.0, 1.0, 1.0, 0.80);
		}
		""";
}
