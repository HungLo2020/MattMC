package net.minecraft.client.dev;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;

import java.io.IOException;
import java.io.Reader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Dev-only isolated rendering subsystem benchmark.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.graphicsSubsystemBenchmark=true}.
 */
public final class GraphicsSubsystemBenchmark {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.graphicsSubsystemBenchmark");
	private static final Path STATUS_PATH = Path.of(System.getProperty("mattmc.dev.graphicsSubsystemBenchmark.status", "run/graphics_subsystem_benchmark.json"));
	private static final int ITERATIONS = Math.max(1, Integer.getInteger("mattmc.dev.graphicsSubsystemBenchmark.iterations", 120));
	private static boolean ran;
	private static boolean stopIssued;

	private GraphicsSubsystemBenchmark() {
	}

	public static void runIfRequested(Minecraft minecraft) {
		if (!ENABLED) {
			return;
		}
		if (ran) {
			if (!stopIssued) {
				stopIssued = true;
				minecraft.stop();
			}
			return;
		}
		String backend = System.getProperty("mattmc.dev.graphicsSubsystemBenchmark.backend", "unknown");
		boolean rustBackend = backend.equalsIgnoreCase("rust-vulkan") || backend.equalsIgnoreCase("rust-opengl");
		// The legacy benchmark creates Java Vulkan render passes directly.  Once
		// Rust owns the whole Vulkan frame, redirect an explicitly requested
		// selected/legacy-Vulkan benchmark to the isolated Rust workload instead of
		// allowing a developer tool to reintroduce a second presenter.
		if ((net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) && !rustBackend) {
			ran = true;
			RustGraphicsSubsystemBenchmark.run(minecraft, STATUS_PATH, ITERATIONS, "rust-vulkan");
			if (!stopIssued) {
				stopIssued = true;
				minecraft.stop();
			}
			return;
		}
		if (minecraft.getMainRenderTarget() == null || (!rustBackend && VulkanicAPI.getDevice() == null)) {
			return;
		}
		ran = true;
		if (rustBackend) {
			RustGraphicsSubsystemBenchmark.run(minecraft, STATUS_PATH, ITERATIONS, backend);
			if (!stopIssued) {
				stopIssued = true;
				minecraft.stop();
			}
			return;
		}
		if (backend.equalsIgnoreCase("vulkan")) {
			precompileVulkanSubsystemPipelines(minecraft);
		}
		List<Result> results = new ArrayList<>();
		results.add(measure("resources.buffers", GraphicsSubsystemBenchmark::resourcesBuffers));
		results.add(measure("transfers.uploads", GraphicsSubsystemBenchmark::transfersUploads));
		results.add(measure("pipelines.descriptors.resource-sets", () -> pipelineDescriptorWorkload(minecraft)));
		results.add(measure("render-pass.execution", () -> renderPassExecution(minecraft)));
		results.add(measure("terrain.multidraw", () -> drawWorkload(minecraft, "terrain", RenderPipelines.DEBUG_QUADS, ITERATIONS)));
		results.add(measure("gui.text", () -> drawWorkload(minecraft, "gui", RenderPipelines.GUI, ITERATIONS)));
		results.add(measure("entities", () -> drawWorkload(minecraft, "entities", RenderPipelines.ENTITY_CUTOUT_NO_CULL, Math.max(1, ITERATIONS / 2))));
		results.add(measure("dh-style.lod-batches", () -> drawWorkload(minecraft, "dh-lod", RenderPipelines.DEBUG_SECTION_QUADS, ITERATIONS)));
		writeStatus(minecraft, results);
	}

	private static Result measure(String name, Workload workload) {
		long started = System.nanoTime();
		try {
			WorkloadCounts counts = workload.run();
			return new Result(name, "ok", System.nanoTime() - started, counts, "");
		} catch (Throwable throwable) {
			return new Result(name, "failed", System.nanoTime() - started, WorkloadCounts.EMPTY, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private static void precompileVulkanSubsystemPipelines(Minecraft minecraft) {
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.DEBUG_QUADS, (location, type) -> loadShaderSource(minecraft, location, type));
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.GUI, (location, type) -> loadShaderSource(minecraft, location, type));
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.ENTITY_CUTOUT_NO_CULL, (location, type) -> loadShaderSource(minecraft, location, type));
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.DEBUG_SECTION_QUADS, (location, type) -> loadShaderSource(minecraft, location, type));
	}

	private static String loadShaderSource(Minecraft minecraft, ResourceLocation location, ShaderType type) {
		ResourceLocation shaderFile = type.idConverter().idToFile(location);
		try (Reader reader = minecraft.getResourceManager().getResourceOrThrow(shaderFile).openAsReader()) {
			StringBuilder source = new StringBuilder(8192);
			char[] buffer = new char[4096];
			int read;
			while ((read = reader.read(buffer)) >= 0) {
				source.append(buffer, 0, read);
			}
			return source.toString();
		} catch (IOException exception) {
			return null;
		}
	}

	private static WorkloadCounts resourcesBuffers() {
		int created = 0;
		for (int i = 0; i < ITERATIONS; i++) {
			try (GpuBuffer ignored = VulkanicAPI.createBuffer(() -> "graphics subsystem resource buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, 4096)) {
				created++;
			}
		}
		return new WorkloadCounts(0, 0, 0, 0, 0, created, 0, 0);
	}

	private static WorkloadCounts transfersUploads() {
		CommandEncoder encoder = VulkanicAPI.createCommandEncoder();
		ByteBuffer data = directData(4096);
		int transfers = 0;
		try (GpuBuffer buffer = VulkanicAPI.createBuffer(() -> "graphics subsystem upload buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, 4096)) {
			for (int i = 0; i < ITERATIONS; i++) {
				data.position(0);
				encoder.writeToBuffer(buffer.slice(), data);
				transfers++;
			}
		}
		return new WorkloadCounts(0, 0, 0, transfers, 0, 1, 0, 0);
	}

	private static WorkloadCounts pipelineDescriptorWorkload(Minecraft minecraft) {
		RenderTarget target = minecraft.getMainRenderTarget();
		int pipelineBinds = 0;
		try (RenderPass pass = VulkanicAPI.createCommandEncoder().createRenderPass(
			() -> "graphics subsystem pipeline/resource-set pass",
			target.getColorTextureView(),
			OptionalInt.empty(),
			target.getDepthTextureView(),
			OptionalDouble.empty()
		)) {
			for (int i = 0; i < ITERATIONS; i++) {
				pass.setPipeline((i & 1) == 0 ? RenderPipelines.DEBUG_QUADS : RenderPipelines.GUI);
				pipelineBinds++;
			}
		}
		return new WorkloadCounts(0, 0, 1, 0, pipelineBinds, 0, 0, pipelineBinds);
	}

	private static WorkloadCounts renderPassExecution(Minecraft minecraft) {
		RenderTarget target = minecraft.getMainRenderTarget();
		int passes = 0;
		for (int i = 0; i < Math.max(1, ITERATIONS / 4); i++) {
			try (RenderPass ignored = VulkanicAPI.createCommandEncoder().createRenderPass(
				() -> "graphics subsystem render-pass",
				target.getColorTextureView(),
				OptionalInt.empty(),
				target.getDepthTextureView(),
				OptionalDouble.empty()
			)) {
				passes++;
			}
		}
		return new WorkloadCounts(0, 0, passes, 0, 0, 0, 0, 0);
	}

	private static WorkloadCounts drawWorkload(Minecraft minecraft, String label, net.blaze3d.pipeline.RenderPipeline pipeline, int iterations) {
		RenderTarget target = minecraft.getMainRenderTarget();
		ByteBuffer vertices = directData(4096);
		ByteBuffer indices = directIndexData(768);
		int draws = 0;
		try (
			GpuBuffer vertexBuffer = VulkanicAPI.createBuffer(() -> "graphics subsystem " + label + " vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
			GpuBuffer indexBuffer = VulkanicAPI.createBuffer(() -> "graphics subsystem " + label + " indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indices);
			RenderPass pass = VulkanicAPI.createCommandEncoder().createRenderPass(
				() -> "graphics subsystem " + label + " pass",
				target.getColorTextureView(),
				OptionalInt.empty(),
				target.getDepthTextureView(),
				OptionalDouble.empty()
			)
		) {
			pass.setPipeline(pipeline);
			pass.setVertexBuffer(0, vertexBuffer);
			pass.setIndexBuffer(indexBuffer, net.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			for (int i = 0; i < iterations; i++) {
				pass.drawIndexed(0, 0, 6, 1);
				draws++;
			}
		}
		return new WorkloadCounts(draws, 0, 1, 0, 0, 2, 0, 0);
	}

	private static ByteBuffer directData(int bytes) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
		for (int i = 0; i < bytes; i++) {
			buffer.put((byte)(i * 31));
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer directIndexData(int bytes) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
		for (int i = 0; i < bytes / 2; i++) {
			buffer.putShort((short)(i % 4));
		}
		buffer.flip();
		return buffer;
	}

	private static void writeStatus(Minecraft minecraft, List<Result> results) {
		try {
			Path parent = STATUS_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(STATUS_PATH, json(minecraft, results), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static String json(Minecraft minecraft, List<Result> results) {
		StringBuilder json = new StringBuilder(8192);
		json.append("{\n");
		field(json, "schema", "mattmc-graphics-subsystem-benchmark-v1", true);
		field(json, "status", results.stream().allMatch(result -> result.status().equals("ok")) ? "complete" : "partial", true);
		field(json, "backend", System.getProperty("mattmc.dev.graphicsSubsystemBenchmark.backend", "unknown"), true);
		field(json, "shaders", System.getProperty("mattmc.dev.graphicsSubsystemBenchmark.shaders", "unknown"), true);
		json.append("  \"iterations\": ").append(ITERATIONS).append(",\n");
		json.append("  \"window\": { \"width\": ").append(minecraft.getWindow().getWidth()).append(", \"height\": ").append(minecraft.getWindow().getHeight()).append(" },\n");
		json.append("  \"workloads\": [\n");
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			json.append("    {\n");
			field(json, "name", result.name(), 6, true);
			field(json, "status", result.status(), 6, true);
			field(json, "error", result.error(), 6, true);
			json.append("      \"totalNanos\": ").append(result.totalNanos()).append(",\n");
			json.append("      \"perOperationNanos\": ").append(format(result.perOperationNanos())).append(",\n");
			json.append("      \"counts\": ").append(result.counts().json()).append("\n");
			json.append("    }").append(i + 1 == results.size() ? "\n" : ",\n");
		}
		json.append("  ]\n");
		json.append("}\n");
		return json.toString();
	}

	private static void field(StringBuilder json, String key, String value, boolean comma) {
		field(json, key, value, 2, comma);
	}

	private static void field(StringBuilder json, String key, String value, int indent, boolean comma) {
		json.append(" ".repeat(indent)).append('"').append(key).append("\": \"").append(escape(value)).append('"');
		if (comma) {
			json.append(',');
		}
		json.append('\n');
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private interface Workload {
		WorkloadCounts run();
	}

	private record Result(String name, String status, long totalNanos, WorkloadCounts counts, String error) {
		double perOperationNanos() {
			return counts.totalOperations() <= 0 ? totalNanos : (double)totalNanos / counts.totalOperations();
		}
	}

	private record WorkloadCounts(int draw, int dispatch, int pass, int transfer, int pipeline, int resource, int descriptor, int apiCall) {
		static final WorkloadCounts EMPTY = new WorkloadCounts(0, 0, 0, 0, 0, 0, 0, 0);

		int totalOperations() {
			return draw + dispatch + pass + transfer + pipeline + resource + descriptor + apiCall;
		}

		String json() {
			return "{ \"draw\": " + draw
				+ ", \"dispatch\": " + dispatch
				+ ", \"pass\": " + pass
				+ ", \"transfer\": " + transfer
				+ ", \"pipeline\": " + pipeline
				+ ", \"resource\": " + resource
				+ ", \"descriptor\": " + descriptor
				+ ", \"apiCall\": " + apiCall + " }";
		}
	}
}
