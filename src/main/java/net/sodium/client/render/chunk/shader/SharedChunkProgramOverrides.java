package net.sodium.client.render.chunk.shader;

import net.blaze3d.opengl.GlProgram;
import net.blaze3d.opengl.Uniform;
import net.blaze3d.pipeline.RenderPipeline;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Vulkan chunk-terrain pipelines that should reuse the currently active Sodium chunk program.
 */
@SuppressWarnings("null")
public final class SharedChunkProgramOverrides {
	private static final Logger LOGGER = LoggerFactory.getLogger(SharedChunkProgramOverrides.class);
	private static final AtomicInteger DEBUG_CREATE_OVERRIDE_LOG_COUNT = new AtomicInteger();
	private static final Set<RenderPipeline> TRACKED_PIPELINES = ConcurrentHashMap.newKeySet();
	private static final Map<RenderPipeline, Set<String>> BINDABLE_SAMPLERS = new ConcurrentHashMap<>();
	private static final ThreadLocal<net.sodium.client.gl.shader.GlProgram<? extends ChunkShaderInterface>> ACTIVE_CHUNK_PROGRAM = new ThreadLocal<>();

	private SharedChunkProgramOverrides() {
	}

	public static void register(RenderPipeline pipeline) {
		register(pipeline, Set.of());
	}

	public static void register(RenderPipeline pipeline, Collection<String> bindableSamplers) {
		TRACKED_PIPELINES.add(pipeline);
		BINDABLE_SAMPLERS.put(pipeline, Set.copyOf(bindableSamplers));
	}

	public static void unregister(RenderPipeline pipeline) {
		TRACKED_PIPELINES.remove(pipeline);
		BINDABLE_SAMPLERS.remove(pipeline);
	}

	public static void unregisterAll(Collection<RenderPipeline> pipelines) {
		for (RenderPipeline pipeline : pipelines) {
			unregister(pipeline);
		}
	}

	public static void pushActiveProgram(net.sodium.client.gl.shader.GlProgram<? extends ChunkShaderInterface> program) {
		ACTIVE_CHUNK_PROGRAM.set(program);
	}

	public static void clearActiveProgram() {
		ACTIVE_CHUNK_PROGRAM.remove();
	}

	public static boolean isTracked(RenderPipeline pipeline) {
		return TRACKED_PIPELINES.contains(pipeline);
	}

	public static boolean isTrackedSolidPipeline(RenderPipeline pipeline) {
		return isTracked(pipeline) && pipeline.getLocation().toString().contains("sodium:pipeline/shared_chunk_solid");
	}

	public static int activeProgramHandle(RenderPipeline pipeline) {
		if (!isTracked(pipeline)) {
			return -1;
		}

		var activeProgram = ACTIVE_CHUNK_PROGRAM.get();
		return activeProgram == null ? -1 : activeProgram.handle();
	}

	public static Set<String> bindableSamplers(RenderPipeline pipeline) {
		Set<String> samplers = BINDABLE_SAMPLERS.get(pipeline);
		return samplers == null ? Collections.emptySet() : samplers;
	}

	@Nullable
	public static GlProgram createOverride(RenderPipeline pipeline) {
		if (!isTracked(pipeline)) {
			return null;
		}

		var activeProgram = ACTIVE_CHUNK_PROGRAM.get();
		int logIndex = DEBUG_CREATE_OVERRIDE_LOG_COUNT.getAndIncrement();
		if (logIndex < 32) {
			LOGGER.info(
				"Shared chunk override probe#{} pipeline={} activeProgramPresent={} activeProgramHandle={}",
				logIndex + 1,
				pipeline.getLocation(),
				activeProgram != null,
				activeProgram == null ? -1 : activeProgram.handle()
			);
		}
		if (activeProgram == null) {
			return null;
		}

		GlProgram wrapper = new NonOwningGlProgram(activeProgram.handle(), "shared-sodium-chunk-" + pipeline.getLocation());
		wrapper.setupUniforms(pipeline.getUniforms(), pipeline.getSamplers());
		return wrapper;
	}

	private static final class NonOwningGlProgram extends GlProgram {
		private NonOwningGlProgram(int programId, String debugLabel) {
			super(programId, debugLabel);
		}

		@Override
		public void close() {
			this.uniformsByName.values().forEach(Uniform::close);
			this.uniformsByName.clear();
		}
	}
}
