package net.sodium.client.render.chunk.shader;

import net.blaze3d.opengl.GlProgram;
import net.blaze3d.opengl.Uniform;
import net.blaze3d.pipeline.RenderPipeline;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Vulkan chunk-terrain pipelines that should reuse the currently active Sodium chunk program.
 */
public final class SharedChunkProgramOverrides {
	private static final Set<RenderPipeline> TRACKED_PIPELINES = ConcurrentHashMap.newKeySet();
	private static final ThreadLocal<net.sodium.client.gl.shader.GlProgram<? extends ChunkShaderInterface>> ACTIVE_CHUNK_PROGRAM = new ThreadLocal<>();

	private SharedChunkProgramOverrides() {
	}

	public static void register(RenderPipeline pipeline) {
		TRACKED_PIPELINES.add(pipeline);
	}

	public static void unregister(RenderPipeline pipeline) {
		TRACKED_PIPELINES.remove(pipeline);
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

	@Nullable
	public static GlProgram createOverride(RenderPipeline pipeline) {
		if (!TRACKED_PIPELINES.contains(pipeline)) {
			return null;
		}

		var activeProgram = ACTIVE_CHUNK_PROGRAM.get();
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