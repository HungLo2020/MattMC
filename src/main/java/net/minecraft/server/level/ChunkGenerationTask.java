package net.minecraft.server.level;

import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/** Immutable cache/object binding for the retained Rust generation task. */
public class ChunkGenerationTask {
	public final ChunkStatus targetStatus;
	private final NativeChunkGeneration.Task generation;

	private ChunkGenerationTask(
		GeneratingChunkMap generatingChunkMap, ChunkStatus chunkStatus, ChunkPos chunkPos, StaticCache2D<GenerationChunkHolder> staticCache2D, int cacheRadius
	) {
		this.targetStatus = chunkStatus;
		this.generation = new NativeChunkGeneration.Task(this, generatingChunkMap, chunkStatus, chunkPos, staticCache2D, cacheRadius);
	}

	public static ChunkGenerationTask create(GeneratingChunkMap generatingChunkMap, ChunkStatus chunkStatus, ChunkPos chunkPos) {
		int i = ChunkPyramid.GENERATION_PYRAMID.getStepTo(chunkStatus).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
		StaticCache2D<GenerationChunkHolder> staticCache2D = StaticCache2D.create(
			chunkPos.x, chunkPos.z, i, (ix, j) -> generatingChunkMap.acquireGeneration(ChunkPos.asLong(ix, j))
		);
		return new ChunkGenerationTask(generatingChunkMap, chunkStatus, chunkPos, staticCache2D, i);
	}

	@Nullable
	public CompletableFuture<?> runUntilWait() { return this.generation.run(); }

	public void markForCancellation() { this.generation.cancel(); }

	public GenerationChunkHolder getCenter() { return this.generation.center(); }
}
