package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.vulkanic.VulkanicAPI;

import java.nio.file.Path;

/**
 * Dev-only isolated rendering subsystem benchmark, run by Rust VulkanicGAL on
 * the active backend.
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
		if (!ran) {
			ran = true;
			String requested = System.getProperty("mattmc.dev.graphicsSubsystemBenchmark.backend", "");
			String backend = requested.equalsIgnoreCase("rust-vulkan") || requested.equalsIgnoreCase("rust-opengl")
				? requested
				: VulkanicAPI.usesRustVulkanPresenter() ? "rust-vulkan" : "rust-opengl";
			RustGraphicsSubsystemBenchmark.run(minecraft, STATUS_PATH, ITERATIONS, backend);
		}
		if (!stopIssued) {
			stopIssued = true;
			minecraft.stop();
		}
	}
}
