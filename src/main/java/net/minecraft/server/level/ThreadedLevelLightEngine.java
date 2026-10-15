package net.minecraft.server.level;

import net.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ThreadedLevelLightEngine extends LevelLightEngine implements AutoCloseable {
	public static final int DEFAULT_BATCH_SIZE = 1000;
	private static final Logger LOGGER = LogUtils.getLogger();
	private final ConsecutiveExecutor consecutiveExecutor;
	private final NativeLightTasks lightTasks = new NativeLightTasks();
	private final Runnable propagation = super::runLightUpdates;
	private final ChunkMap chunkMap;
	private final ChunkTaskDispatcher taskDispatcher;

	public ThreadedLevelLightEngine(
		LightChunkGetter lightChunkGetter, ChunkMap chunkMap, boolean bl, ConsecutiveExecutor consecutiveExecutor, ChunkTaskDispatcher chunkTaskDispatcher
	) {
		super(lightChunkGetter, true, bl);
		this.chunkMap = chunkMap;
		this.taskDispatcher = chunkTaskDispatcher;
		this.consecutiveExecutor = consecutiveExecutor;
	}

	public void close() {
	}

	@Override
	public int runLightUpdates() {
		throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Ran automatically on a different thread!"));
	}

	@Override
	public void checkBlock(BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.immutable();
		this.addTask(
			SectionPos.blockToSectionCoord(blockPos.getX()),
			SectionPos.blockToSectionCoord(blockPos.getZ()),
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.checkBlock(blockPos2)), () -> "checkBlock " + blockPos2)
		);
	}

	protected void updateChunkStatus(ChunkPos chunkPos) {
		this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			super.retainData(chunkPos, false);
			super.setLightEnabled(chunkPos, false);

			for (int i = this.getMinLightSection(); i < this.getMaxLightSection(); i++) {
				super.queueSectionData(LightLayer.BLOCK, SectionPos.of(chunkPos, i), null);
				super.queueSectionData(LightLayer.SKY, SectionPos.of(chunkPos, i), null);
			}

			for (int i = this.levelHeightAccessor.getMinSectionY(); i <= this.levelHeightAccessor.getMaxSectionY(); i++) {
				super.updateSectionStatus(SectionPos.of(chunkPos, i), true);
			}
		}), () -> "updateChunkStatus " + chunkPos + " true"));
	}

	@Override
	public void updateSectionStatus(SectionPos sectionPos, boolean bl) {
		this.addTask(
			sectionPos.x(),
			sectionPos.z(),
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.updateSectionStatus(sectionPos, bl)), () -> "updateSectionStatus " + sectionPos + " " + bl)
		);
	}

	@Override
	public void propagateLightSources(ChunkPos chunkPos) {
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.propagateLightSources(chunkPos)), () -> "propagateLight " + chunkPos)
		);
	}

	@Override
	public void setLightEnabled(ChunkPos chunkPos, boolean bl) {
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.setLightEnabled(chunkPos, bl)), () -> "enableLight " + chunkPos + " " + bl)
		);
	}

	@Override
	public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
		this.addTask(
			sectionPos.x(),
			sectionPos.z(),
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.queueSectionData(lightLayer, sectionPos, dataLayer)), () -> "queueData " + sectionPos)
		);
	}

	private void addTask(int i, int j, ThreadedLevelLightEngine.TaskType taskType, Runnable runnable) {
		this.addTask(i, j, this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(i, j)), taskType, runnable);
	}

	private void addTask(int i, int j, IntSupplier intSupplier, ThreadedLevelLightEngine.TaskType taskType, Runnable runnable) {
		this.taskDispatcher.submit(() -> {
			if (this.lightTasks.append(taskType == TaskType.POST_UPDATE, runnable)) {
				this.runUpdate();
			}
		}, ChunkPos.asLong(i, j), intSupplier);
	}

	@Override
	public void retainData(ChunkPos chunkPos, boolean bl) {
		this.addTask(
			chunkPos.x,
			chunkPos.z,
			() -> 0,
			ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
			Util.name((Runnable)(() -> super.retainData(chunkPos, bl)), () -> "retainData " + chunkPos)
		);
	}

	public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunkAccess, boolean bl) {
		ChunkPos chunkPos = chunkAccess.getPos();
		this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			LevelChunkSection[] levelChunkSections = chunkAccess.getSectionsForRead();

			for (int i = 0; i < chunkAccess.getSectionsCount(); i++) {
				LevelChunkSection levelChunkSection = levelChunkSections[i];
				if (!levelChunkSection.hasOnlyAir()) {
					int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
					super.updateSectionStatus(SectionPos.of(chunkPos, j), false);
				}
			}
		}), () -> "initializeLight: " + chunkPos));
		return CompletableFuture.supplyAsync(() -> {
			super.setLightEnabled(chunkPos, bl);
			super.retainData(chunkPos, false);
			return chunkAccess;
		}, runnable -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunkAccess, boolean bl) {
		ChunkPos chunkPos = chunkAccess.getPos();
		chunkAccess.setLightCorrect(false);
		this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name((Runnable)(() -> {
			if (!bl) {
				super.propagateLightSources(chunkPos);
			}

			if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
				LOGGER.debug("LIT {}", chunkPos);
			}
		}), () -> "lightChunk " + chunkPos + " " + bl));
		return CompletableFuture.supplyAsync(() -> {
			chunkAccess.setLightCorrect(true);
			return chunkAccess;
		}, runnable -> this.addTask(chunkPos.x, chunkPos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	public void tryScheduleUpdate() {
		if (this.lightTasks.requestSchedule(this.lightTasks.pending() != 0 || super.hasLightWork())) {
			this.consecutiveExecutor.schedule(() -> {
				this.runUpdate();
				this.lightTasks.finishSchedule();
			});
		}
	}

	private void runUpdate() {
		this.lightTasks.runUpdate(this.propagation);
	}

	public CompletableFuture<?> waitForPendingTasks(int i, int j) {
		return CompletableFuture.runAsync(() -> {}, runnable -> this.addTask(i, j, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable));
	}

	static enum TaskType {
		PRE_UPDATE,
		POST_UPDATE;
	}
}
