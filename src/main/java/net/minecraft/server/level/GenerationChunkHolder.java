package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jetbrains.annotations.Nullable;

public abstract class GenerationChunkHolder {
	private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
	private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
	public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
	public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK);
	protected final ChunkPos pos;
	private final NativeChunkGeneration.Holder generation;

	final NativeChunkGeneration.Holder generationOwner() { return this.generation; }
    /** Opaque binding capture without dispatching an instance method. */
    static NativeChunkGeneration.Holder generationOwner(GenerationChunkHolder holder) { return holder.generation; }

	public GenerationChunkHolder(ChunkPos chunkPos) {
		this.pos = chunkPos;
		if (chunkPos.getChessboardDistance(ChunkPos.ZERO) > ChunkPos.MAX_COORDINATE_VALUE) {
			throw new IllegalStateException("Trying to create chunk out of reasonable bounds: " + chunkPos);
		}
		this.generation = new NativeChunkGeneration.Holder();
	}

	public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus chunkStatus, ChunkMap chunkMap) {
        FutureFrame frame = new FutureFrame(chunkStatus, null, null, chunkMap);
        this.generation.futureControl(4, frame);
        return frame.returned;
    }

	CompletableFuture<ChunkResult<ChunkAccess>> applyStep(
		ChunkStep chunkStep, GeneratingChunkMap generatingChunkMap, StaticCache2D<GenerationChunkHolder> staticCache2D
	) {
		if (this.isStatusDisallowed(chunkStep.targetStatus())) {
			return UNLOADED_CHUNK_FUTURE;
		} else {
			return this.acquireStatusBump(chunkStep.targetStatus()) ? generatingChunkMap.applyStep(this, chunkStep, staticCache2D).handle((chunkAccess, throwable) -> {
				if (throwable != null) {
					CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception chunk generation/loading");
					MinecraftServer.setFatalException(new ReportedException(crashReport));
				} else {
					this.completeFuture(chunkStep.targetStatus(), chunkAccess);
				}

				return ChunkResult.of(chunkAccess);
			}) : this.getOrCreateFuture(chunkStep.targetStatus());
		}
	}

	protected void updateHighestAllowedStatus(ChunkMap chunkMap) {
		this.generation.futureControl(5, new FutureFrame(null, null, null, chunkMap));
	}

	public void replaceProtoChunk(ImposterProtoChunk imposterProtoChunk) {
		CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = NativeChunkGeneration.completedChunkFuture(ChunkResult.of(imposterProtoChunk));

		for (int i = 0; i < CHUNK_STATUSES.size() - 1; i++) {
			CompletableFuture<ChunkResult<ChunkAccess>> completableFuture2 = (CompletableFuture<ChunkResult<ChunkAccess>>)this.generation.future(i);
			Objects.requireNonNull(completableFuture2);
			ChunkAccess chunkAccess = (ChunkAccess)((ChunkResult)completableFuture2.getNow(NOT_DONE_YET)).orElse(null);
			if (!(chunkAccess instanceof ProtoChunk)) {
				throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + chunkAccess);
			}

			if (this.generation.compareFuture(i, completableFuture2, completableFuture) != completableFuture2) {
				throw new IllegalStateException("Future changed by other thread while trying to replace it");
			}
		}
	}

	void removeTask(ChunkGenerationTask chunkGenerationTask) {
		this.generation.removeTask(chunkGenerationTask);
	}

	private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus chunkStatus) {
		FutureFrame frame = new FutureFrame(chunkStatus, null, null);
		this.generation.futureControl(0, frame);
		return frame.returned;
	}

	private void completeFuture(ChunkStatus chunkStatus, ChunkAccess chunkAccess) {
		this.generation.futureControl(1, new FutureFrame(chunkStatus, ChunkResult.of(chunkAccess), null));
	}

    /** Actual future/status effects. The native invocation owns loops and choices;
     * nested callbacks receive separate frames and retain their captured objects. */
    private final class FutureFrame implements NativeChunkGeneration.FutureEffect {
        ChunkStatus requested, allowedBefore, started;
        final ChunkResult<ChunkAccess> result;
        final ChunkMap map;
        CompletableFuture<ChunkResult<ChunkAccess>> current, candidate, returned;
        ChunkGenerationTask task, nextTask;
        CompletableFuture<Void> fence;
        FutureFrame(ChunkStatus requested, ChunkResult<ChunkAccess> result,
                CompletableFuture<ChunkResult<ChunkAccess>> candidate) {
            this(requested, result, candidate, null);
        }
        FutureFrame(ChunkStatus requested, ChunkResult<ChunkAccess> result,
                CompletableFuture<ChunkResult<ChunkAccess>> candidate, ChunkMap map) {
            this.requested = requested; this.result = result; this.candidate = candidate; this.map = map;
        }
        public long apply(int operation, int slot) {
            switch (operation) {
                case 1: return GenerationChunkHolder.this.isStatusDisallowed(requested) ? 1 : 0;
                case 2: return requested.getIndex();
                case 3:
                    current = (CompletableFuture<ChunkResult<ChunkAccess>>)generation.future(slot);
                    return current == null ? 0 : 1;
                case 4: candidate = NativeChunkGeneration.newChunkFuture(); break;
                case 5:
                    current = (CompletableFuture<ChunkResult<ChunkAccess>>)generation.compareFuture(slot, null, candidate);
                    return current == null ? 0 : 1;
                case 6: returned = current; break;
                case 7: returned = candidate; break;
                case 8: returned = UNLOADED_CHUNK_FUTURE; break;
                case 9: return candidate.complete(UNLOADED_CHUNK) ? 1 : 0;
                case 10: return generation.compareFuture(slot, candidate, null) == candidate ? 1 : 0;
                case 11: throw new IllegalStateException("Nothing else should replace the future here");
                case 12: candidate = NativeChunkGeneration.completedChunkFuture(result); break;
                case 13: return current.complete(result) ? 1 : 0;
                case 14: return current.getNow(NOT_DONE_YET).isSuccess() ? 1 : 0;
                case 15: throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                case 16: Thread.yield(); break;
                case 17: candidate = current; break;
                case 18: current = returned; break;
                case 19: return current.isDone() ? 1 : 0;
                case 20:
                    task = (ChunkGenerationTask)generation.task();
                    return task == null ? 0 : 1;
                case 21: return requested.isAfter(task.targetStatus) ? 1 : 0;
                case 22: nextTask = map.scheduleGenerationTask(requested, GenerationChunkHolder.this.getPos()); break;
                case 23: nextTask = null; break;
                case 24:
                    task = generation.exchangeTask(nextTask);
                    return task == null ? 0 : 1;
                case 26: task.markForCancellation(); break;
                case 30:
                    Object request = requested == ChunkStatus.FULL ? generation.fullRequest() : NativeChunkGeneration.Holder.REQUEST_FALLBACK;
                    if (request == NativeChunkGeneration.Holder.REQUEST_DISALLOWED) return 1;
                    if (request == NativeChunkGeneration.Holder.REQUEST_FALLBACK) return 2;
                    current = (CompletableFuture<ChunkResult<ChunkAccess>>)request;
                    return 3;
                case 31:
                    allowedBefore = generation.allowed();
                    return allowedBefore == null ? 0 : 1;
                case 32:
                    requested = ChunkLevel.generationStatus(GenerationChunkHolder.this.getTicketLevel());
                    generation.allowed(requested);
                    return requested == null ? 0 : 1;
                case 33: return requested.isBefore(allowedBefore) ? 1 : 0;
                case 34: return requested.getIndex();
                case 35: return allowedBefore.getIndex();
                case 36:
                    started = generation.started();
                    return started == null ? 0 : 1;
                case 37: return requested.isAfter(started) ? 1 : 0;
                case 38: return requested == ChunkStatus.EMPTY ? 1 : 0;
                case 39: requested = requested.getParent(); break;
                case 40: fence = NativeChunkGeneration.newChunkFuture(); break;
                case 41: generation.saveFence(fence); break;
                case 42: GenerationChunkHolder.this.addSaveDependency(generation.saveFence()); break;
                case 43: fence = generation.saveFence(); break;
                case 44: fence.complete(null); break;
                case 45: throw new IllegalStateException("More releases than claims. Count: " + slot);
                default: throw new IllegalStateException("Invalid native holder future effect " + operation);
            }
            return 0;
        }
    }

	private boolean acquireStatusBump(ChunkStatus chunkStatus) {
		ChunkStatus chunkStatus2 = chunkStatus == ChunkStatus.EMPTY ? null : chunkStatus.getParent();
		ChunkStatus chunkStatus3 = (ChunkStatus)this.generation.bump(chunkStatus2, chunkStatus);
		if (chunkStatus3 == chunkStatus2) {
			return true;
		} else if (chunkStatus3 != null && !chunkStatus.isAfter(chunkStatus3)) {
			return false;
		} else {
			throw new IllegalStateException("Unexpected last startedWork status: " + chunkStatus3 + " while trying to start: " + chunkStatus);
		}
	}

	private boolean isStatusDisallowed(ChunkStatus chunkStatus) {
		ChunkStatus chunkStatus2 = this.generation.allowed();
		return chunkStatus2 == null || chunkStatus.isAfter(chunkStatus2);
	}

	protected abstract void addSaveDependency(CompletableFuture<?> completableFuture);

	public void increaseGenerationRefCount() {
		this.generation.futureControl(6, new FutureFrame(null, null, null));
	}

	public void decreaseGenerationRefCount() {
		this.generation.futureControl(7, new FutureFrame(null, null, null));
	}

	@Nullable
	public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus chunkStatus) {
		CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = (CompletableFuture<ChunkResult<ChunkAccess>>)this.generation.future(chunkStatus.getIndex());
		return completableFuture == null ? null : (ChunkAccess)((ChunkResult)completableFuture.getNow(NOT_DONE_YET)).orElse(null);
	}

	@Nullable
	public ChunkAccess getChunkIfPresent(ChunkStatus chunkStatus) {
		return this.isStatusDisallowed(chunkStatus) ? null : this.getChunkIfPresentUnchecked(chunkStatus);
	}

	@Nullable
	public ChunkAccess getLatestChunk() {
		ChunkStatus chunkStatus = (ChunkStatus)this.generation.started();
		if (chunkStatus == null) {
			return null;
		} else {
			ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
			return chunkAccess != null ? chunkAccess : this.getChunkIfPresentUnchecked(chunkStatus.getParent());
		}
	}

	@Nullable
	public ChunkStatus getPersistedStatus() {
		CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = (CompletableFuture<ChunkResult<ChunkAccess>>)this.generation.future(ChunkStatus.EMPTY.getIndex());
		ChunkAccess chunkAccess = completableFuture == null ? null : (ChunkAccess)((ChunkResult)completableFuture.getNow(NOT_DONE_YET)).orElse(null);
		return chunkAccess == null ? null : chunkAccess.getPersistedStatus();
	}

	public ChunkPos getPos() {
		return this.pos;
	}

	public FullChunkStatus getFullStatus() {
		return ChunkLevel.fullStatus(this.getTicketLevel());
	}

	public abstract int getTicketLevel();

	public abstract int getQueueLevel();

	@VisibleForDebug
	public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
		List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList();

		for (int i = 0; i < CHUNK_STATUSES.size(); i++) {
			list.add(Pair.of((ChunkStatus)CHUNK_STATUSES.get(i), (CompletableFuture)this.generation.future(i)));
		}

		return list;
	}

	@Nullable
	@VisibleForDebug
	public ChunkStatus getLatestStatus() {
		ChunkStatus chunkStatus = (ChunkStatus)this.generation.started();
		if (chunkStatus == null) {
			return null;
		} else {
			ChunkAccess chunkAccess = this.getChunkIfPresentUnchecked(chunkStatus);
			return chunkAccess != null ? chunkStatus : chunkStatus.getParent();
		}
	}
}
