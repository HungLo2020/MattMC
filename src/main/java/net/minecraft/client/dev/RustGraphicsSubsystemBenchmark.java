package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.TracyCompat;
import net.vulkanic.bridge.VulkanicGalBridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class RustGraphicsSubsystemBenchmark {
	private static final int WIDTH = 32;
	private static final int HEIGHT = 32;
	private static final int PIXEL_BYTES = WIDTH * HEIGHT * 4;
	private static final int LARGE_BATCH_BARRIER_COUNT = 96;

	private RustGraphicsSubsystemBenchmark() {
	}

	static void run(Minecraft minecraft, Path statusPath, int iterations, String backend) {
		int windowWidth = minecraft.getWindow().getWidth();
		int windowHeight = minecraft.getWindow().getHeight();
		BenchmarkResult[] result = new BenchmarkResult[1];
		Thread worker = new Thread(() -> result[0] = runIsolated(iterations, backend), "Rust VulkanicGAL subsystem benchmark");
		worker.setDaemon(true);
		worker.start();
		try {
			worker.join(45_000L);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			result[0] = BenchmarkResult.failed("InterruptedException: " + interrupted.getMessage());
		}
		if (worker.isAlive()) {
			result[0] = BenchmarkResult.failed("TimeoutException: Rust VulkanicGAL subsystem worker exceeded 45 seconds");
		}
		BenchmarkResult finalResult = result[0] == null ? BenchmarkResult.failed("IllegalStateException: Rust VulkanicGAL subsystem worker produced no result") : result[0];
		writeStatus(windowWidth, windowHeight, statusPath, backend, iterations, finalResult);
		awaitTracyCaptureGrace();
	}

	private static void awaitTracyCaptureGrace() {
		if (!Boolean.getBoolean("mattmc.dev.tracyCapture")) {
			return;
		}
		long graceMillis = Math.max(0L, Math.min(10_000L, Long.getLong("mattmc.dev.graphicsSubsystemBenchmark.tracyGraceMillis", 3_000L)));
		if (graceMillis <= 0L) {
			return;
		}
		long deadline = System.nanoTime() + graceMillis * 1_000_000L;
		while (System.nanoTime() < deadline) {
			try {
				Thread.sleep(Math.min(100L, Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L)));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static BenchmarkResult runIsolated(int iterations, String backend) {
		long started = System.nanoTime();
		String status = "complete";
		String error = "";
		long submission = 0;
		long ffiCalls = 0;
		long ffiBytes = 0;
		int completionPolls = 0;
		int hash = 0;
		int nonZero = 0;

		try (VulkanicGalBridge bridge = VulkanicGalBridge.create(backend)) {
			Handles handles = createResources(bridge, backend);
			for (int i = 0; i < Math.max(1, iterations); i++) {
				VulkanicGalBridge.SubmissionBatch submit = bridge.submissionBatchBuilder("java-subsystem-rust-submit-" + i)
					.hostWrite(handles.uploadTexture, 0, textureBytes())
					.barrier(handles.uploadTexture, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_TRANSFER_SRC, false)
					.hostWrite(handles.index, 0, indexBytes())
					.barrier(handles.index, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
					.hostWrite(handles.uniformA, 0, uniformBytes(i, 1))
					.barrier(handles.uniformA, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
					.hostWrite(handles.uniformB, 0, uniformBytes(i, 2))
					.barrier(handles.uniformB, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
					.hostWrite(handles.storage, 0, uniformBytes(i, 3))
					.barrier(handles.storage, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
					.barrier(handles.sampledTexture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_TRANSFER_DST, true)
					.copyBufferToTexture(handles.uploadTexture, handles.sampledTexture, 4, 4)
					.barrier(handles.sampledTexture, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, true)
					.barrier(handles.colorTexture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_COLOR_ATTACHMENT, true)
					.barrier(handles.depthTexture, VulkanicGalBridge.USAGE_UNDEFINED, VulkanicGalBridge.USAGE_DEPTH_ATTACHMENT, true)
					.beginPass(handles.renderPass, handles.renderTarget, handles.colorView, handles.depthView)
					.bindGraphicsPipeline(handles.pipeline)
					.bindResourceSet(handles.pipelineLayout, handles.resourceSet)
					.setIndexBuffer(handles.index)
					.drawIndexed(6)
					.endPass()
					.barrier(handles.colorTexture, VulkanicGalBridge.USAGE_COLOR_ATTACHMENT, VulkanicGalBridge.USAGE_TRANSFER_SRC, true)
					.copyTextureToBuffer(handles.colorTexture, handles.readback, WIDTH, HEIGHT)
					.barrier(handles.readback, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, false)
					.hostRead(handles.readback, PIXEL_BYTES)
					.largeBarrierBatch(handles.uploadTexture, LARGE_BATCH_BARRIER_COUNT)
					.build();
				VulkanicGalBridge.Status submitStatus = bridge.submit(submit);
				submission = submitStatus.submissionId();
				TracyCompat.message("gal.submission producer=java-subsystem backend=" + backend + " iteration=" + i + " id=" + submission);
				VulkanicGalBridge.Completion completion = pollCompletion(bridge, submission);
				completionPolls++;
				if (!completion.complete()) {
					throw new IllegalStateException("Rust VulkanicGAL submission " + submission + " did not complete; backend completed " + completion.completedSubmissionId());
				}
				byte[] pixels = bridge.readback(submission, handles.readback, 0, PIXEL_BYTES);
				hash = xxh32(pixels, 0x4d434741);
				nonZero = nonZero(pixels);
				VulkanicGalBridge.Status retireStatus = bridge.retire(submission);
				TracyCompat.message("gal.submission.retire producer=java-subsystem backend=" + backend + " iteration=" + i + " id=" + submission);
				ffiCalls = retireStatus.ffiCalls();
				ffiBytes = retireStatus.ffiInputBytes();
			}
		} catch (Throwable throwable) {
			status = "failed";
			error = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
		}
		return new BenchmarkResult(status, error, System.nanoTime() - started, submission, ffiCalls, ffiBytes, completionPolls, hash, nonZero);
	}

	private static VulkanicGalBridge.Completion pollCompletion(VulkanicGalBridge bridge, long submission) {
		long deadline = System.nanoTime() + 2_000_000_000L;
		VulkanicGalBridge.Completion completion = bridge.completion(submission);
		while (!completion.complete() && System.nanoTime() < deadline) {
			Thread.onSpinWait();
			completion = bridge.completion(submission);
		}
		return completion;
	}

	private static Handles createResources(VulkanicGalBridge bridge, String backend) {
		String vertexShader = backend.equalsIgnoreCase("rust-vulkan") ? VERTEX_SHADER_VULKAN : VERTEX_SHADER_OPENGL;
		String fragmentShader = backend.equalsIgnoreCase("rust-vulkan") ? FRAGMENT_SHADER_VULKAN : FRAGMENT_SHADER_OPENGL;
		VulkanicGalBridge.ResourceResults base = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.buffer(1, "java.upload.texture", 64, VulkanicGalBridge.MEMORY_UPLOAD, VulkanicGalBridge.BUFFER_TRANSFER_SRC | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(2, "java.index", 24, VulkanicGalBridge.MEMORY_UPLOAD, VulkanicGalBridge.BUFFER_INDEX | VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(3, "java.uniform.a", 64, VulkanicGalBridge.MEMORY_UPLOAD, VulkanicGalBridge.BUFFER_UNIFORM | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(4, "java.uniform.b", 64, VulkanicGalBridge.MEMORY_UPLOAD, VulkanicGalBridge.BUFFER_UNIFORM | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(5, "java.storage", 64, VulkanicGalBridge.MEMORY_UPLOAD, VulkanicGalBridge.BUFFER_STORAGE | VulkanicGalBridge.BUFFER_HOST_WRITE)
				.buffer(6, "java.readback", PIXEL_BYTES, VulkanicGalBridge.MEMORY_READBACK, VulkanicGalBridge.BUFFER_TRANSFER_DST | VulkanicGalBridge.BUFFER_HOST_READ)
				.texture(7, "java.sampled", VulkanicGalBridge.FORMAT_RGBA8, 4, 4, VulkanicGalBridge.TEXTURE_SAMPLED | VulkanicGalBridge.TEXTURE_TRANSFER_DST)
				.texture(8, "java.color", VulkanicGalBridge.FORMAT_RGBA8, WIDTH, HEIGHT, VulkanicGalBridge.TEXTURE_COLOR_ATTACHMENT | VulkanicGalBridge.TEXTURE_TRANSFER_SRC)
				.texture(9, "java.depth", VulkanicGalBridge.FORMAT_DEPTH32, WIDTH, HEIGHT, VulkanicGalBridge.TEXTURE_DEPTH_STENCIL_ATTACHMENT)
				.sampler(10, "java.sampler")
				.shader(11, "java.vertex", VulkanicGalBridge.SHADER_VERTEX, vertexShader)
				.shader(12, "java.fragment", VulkanicGalBridge.SHADER_FRAGMENT, fragmentShader)
				.build());
		long uploadTexture = base.handle(0);
		long index = base.handle(1);
		long uniformA = base.handle(2);
		long uniformB = base.handle(3);
		long storage = base.handle(4);
		long readback = base.handle(5);
		long sampledTexture = base.handle(6);
		long colorTexture = base.handle(7);
		long depthTexture = base.handle(8);
		long sampler = base.handle(9);
		long vertex = base.handle(10);
		long fragment = base.handle(11);

		VulkanicGalBridge.ResourceResults views = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.textureView(20, "java.sampled.view", sampledTexture, VulkanicGalBridge.FORMAT_RGBA8)
				.textureView(21, "java.color.view", colorTexture, VulkanicGalBridge.FORMAT_RGBA8)
				.textureView(22, "java.depth.view", depthTexture, VulkanicGalBridge.FORMAT_DEPTH32)
				.build());
		long sampledView = views.handle(0);
		long colorView = views.handle(1);
		long depthView = views.handle(2);

		VulkanicGalBridge.ResourceResults layout = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.resourceLayout(30, "java.resource.layout",
					new VulkanicGalBridge.BindingDesc(0, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE, 1, false),
					new VulkanicGalBridge.BindingDesc(1, VulkanicGalBridge.BINDING_SAMPLER, 1, false),
					new VulkanicGalBridge.BindingDesc(2, VulkanicGalBridge.BINDING_UNIFORM_BUFFER, 2, false),
					new VulkanicGalBridge.BindingDesc(3, VulkanicGalBridge.BINDING_STORAGE_BUFFER, 1, false),
					new VulkanicGalBridge.BindingDesc(4, VulkanicGalBridge.BINDING_SAMPLER, 1, true))
				.build());
		long resourceLayout = layout.handle(0);

		VulkanicGalBridge.ResourceResults set = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.resourceSet(31, "java.resource.set", resourceLayout,
					new VulkanicGalBridge.Binding(0, 0, sampledView, VulkanicGalBridge.BINDING_SAMPLED_TEXTURE),
					new VulkanicGalBridge.Binding(1, 0, sampler, VulkanicGalBridge.BINDING_SAMPLER),
					new VulkanicGalBridge.Binding(2, 0, uniformA, VulkanicGalBridge.BINDING_UNIFORM_BUFFER),
					new VulkanicGalBridge.Binding(2, 1, uniformB, VulkanicGalBridge.BINDING_UNIFORM_BUFFER),
					new VulkanicGalBridge.Binding(3, 0, storage, VulkanicGalBridge.BINDING_STORAGE_BUFFER))
				.build());
		long resourceSet = set.handle(0);

		VulkanicGalBridge.ResourceResults pipelineLayout = bridge.resourceBatch(
			bridge.resourceBatchBuilder().pipelineLayout(40, "java.pipeline.layout", resourceLayout).build());
		long pipelineLayoutHandle = pipelineLayout.handle(0);

		VulkanicGalBridge.ResourceResults pipelineTargetPass = bridge.resourceBatch(
			bridge.resourceBatchBuilder()
				.graphicsPipeline(41, "java.pipeline", pipelineLayoutHandle, vertex, fragment)
				.renderTarget(42, "java.target", colorView, depthView, WIDTH, HEIGHT)
				.build());
		long pipeline = pipelineTargetPass.handle(0);
		long target = pipelineTargetPass.handle(1);

		VulkanicGalBridge.ResourceResults pass = bridge.resourceBatch(
			bridge.resourceBatchBuilder().renderPass(43, "java.pass", target).build());

		return new Handles(uploadTexture, index, uniformA, uniformB, storage, readback, sampledTexture, colorTexture, depthTexture, colorView, depthView, pipeline, pipelineLayoutHandle, resourceSet, target, pass.handle(0));
	}

	private static byte[] uniformBytes(int iteration, int salt) {
		byte[] bytes = new byte[64];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte)(iteration * 17 + salt * 31 + i);
		}
		return bytes;
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

	private static int nonZero(byte[] bytes) {
		int count = 0;
		for (byte value : bytes) {
			if (value != 0) {
				count++;
			}
		}
		return count;
	}

	private static int xxh32(byte[] data, int seed) {
		int hash = seed + 0x165667b1 + data.length;
		int index = 0;
		while (index + 4 <= data.length) {
			int lane = (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8) | ((data[index + 2] & 0xFF) << 16) | ((data[index + 3] & 0xFF) << 24);
			hash = Integer.rotateLeft(hash + lane * 0x27d4eb2d, 17) * 0x85ebca77;
			index += 4;
		}
		while (index < data.length) {
			hash = Integer.rotateLeft(hash + (data[index++] & 0xFF) * 0x165667b1, 11) * 0x27d4eb2d;
		}
		hash ^= hash >>> 15;
		hash *= 0x85ebca77;
		hash ^= hash >>> 13;
		hash *= 0xc2b2ae3d;
		hash ^= hash >>> 16;
		return hash;
	}

	private static void writeStatus(int windowWidth, int windowHeight, Path statusPath, String backend, int iterations, BenchmarkResult result) {
		try {
			Path parent = statusPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(statusPath, "{\n"
				+ "  \"schema\": \"mattmc-graphics-subsystem-benchmark-v1\",\n"
				+ "  \"status\": \"" + escape(result.status()) + "\",\n"
				+ "  \"backend\": \"" + escape(backend) + "\",\n"
				+ "  \"implementationAttribution\": \"Rust VulkanicGAL bridge\",\n"
				+ "  \"iterations\": " + iterations + ",\n"
				+ "  \"window\": { \"width\": " + windowWidth + ", \"height\": " + windowHeight + " },\n"
				+ "  \"workloads\": [{\n"
				+ "    \"name\": \"rust-bridge.indexed-textured-depth-blend-resource-sets-transfer-readback\",\n"
				+ "    \"status\": \"" + escape(result.status().equals("complete") ? "ok" : result.status()) + "\",\n"
				+ "    \"error\": \"" + escape(result.error()) + "\",\n"
				+ "    \"totalNanos\": " + result.nanos() + ",\n"
				+ "    \"perOperationNanos\": " + String.format(Locale.ROOT, "%.3f", (double)result.nanos() / Math.max(1, iterations)) + ",\n"
				+ "    \"pixelHashXxh32\": \"" + String.format(Locale.ROOT, "%08x", result.hash()) + "\",\n"
				+ "    \"nonZeroPixelBytes\": " + result.nonZero() + ",\n"
				+ "    \"ffiCalls\": " + result.ffiCalls() + ",\n"
				+ "    \"ffiInputBytes\": " + result.ffiBytes() + ",\n"
				+ "    \"completionPolls\": " + result.completionPolls() + ",\n"
				+ "    \"lastSubmission\": " + result.submission() + ",\n"
				+ "    \"counts\": { \"draw\": " + iterations + ", \"dispatch\": 0, \"pass\": " + iterations + ", \"transfer\": " + (iterations * 7) + ", \"pipeline\": 1, \"resource\": 22, \"descriptor\": 1, \"command\": " + (iterations * (25 + LARGE_BATCH_BARRIER_COUNT)) + ", \"apiCall\": " + result.ffiCalls() + " }\n"
				+ "  }]\n"
				+ "}\n", StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private record Handles(
		long uploadTexture,
		long index,
		long uniformA,
		long uniformB,
		long storage,
		long readback,
		long sampledTexture,
		long colorTexture,
		long depthTexture,
		long colorView,
		long depthView,
		long pipeline,
		long pipelineLayout,
		long resourceSet,
		long renderTarget,
		long renderPass
	) {
	}

	private record BenchmarkResult(String status, String error, long nanos, long submission, long ffiCalls, long ffiBytes, int completionPolls, int hash, int nonZero) {
		static BenchmarkResult failed(String error) {
			return new BenchmarkResult("failed", error, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final String VERTEX_SHADER_OPENGL = """
		#version 330 core
		out vec2 v_uv;
		const vec2 pos[4] = vec2[4](
		    vec2(-0.85, -0.85),
		    vec2( 0.85, -0.85),
		    vec2( 0.85,  0.85),
		    vec2(-0.85,  0.85)
		);
		const vec2 uv[4] = vec2[4](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0)
		);
		void main() {
		    gl_Position = vec4(pos[gl_VertexID], 0.5, 1.0);
		    v_uv = uv[gl_VertexID];
		}
		""";

	private static final String VERTEX_SHADER_VULKAN = """
		#version 450
		out vec2 v_uv;
		const vec2 pos[4] = vec2[4](
		    vec2(-0.85, -0.85),
		    vec2( 0.85, -0.85),
		    vec2( 0.85,  0.85),
		    vec2(-0.85,  0.85)
		);
		const vec2 uv[4] = vec2[4](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0)
		);
		void main() {
		    gl_Position = vec4(pos[gl_VertexIndex], 0.5, 1.0);
		    v_uv = uv[gl_VertexIndex];
		}
		""";

	private static final String FRAGMENT_SHADER_OPENGL = """
		#version 430 core
		layout(std140, binding = 2) uniform Uniforms2 {
		    uint uboSalt;
		};
		layout(std430, binding = 3) readonly buffer Storage3 {
		    uint ssboSalt;
		};
		uniform sampler2D tex0;
		in vec2 v_uv;
		out vec4 out_color;
		void main() {
		    float resourceSetProof = float((uboSalt ^ ssboSalt) & 1u) * 0.001;
		    out_color = texture(tex0, v_uv) * vec4(1.0 + resourceSetProof, 1.0, 1.0, 0.75);
		}
		""";

	private static final String FRAGMENT_SHADER_VULKAN = """
		#version 450
		layout(set = 0, binding = 0) uniform texture2D tex0;
		layout(set = 0, binding = 1) uniform sampler samp0;
		layout(set = 0, binding = 2, std140) uniform Uniforms2 {
		    uint uboSalt;
		};
		layout(set = 0, binding = 3, std430) readonly buffer Storage3 {
		    uint ssboSalt;
		};
		layout(location = 0) in vec2 v_uv;
		layout(location = 0) out vec4 out_color;
		void main() {
		    float resourceSetProof = float((uboSalt ^ ssboSalt) & 1u) * 0.001;
		    out_color = texture(sampler2D(tex0, samp0), v_uv) * vec4(1.0 + resourceSetProof, 1.0, 1.0, 0.75);
		}
		""";
}
