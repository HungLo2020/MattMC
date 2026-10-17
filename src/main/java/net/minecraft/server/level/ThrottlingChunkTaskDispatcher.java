package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.Executor;
import java.util.StringJoiner;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class ThrottlingChunkTaskDispatcher extends ChunkTaskDispatcher {
	private final NativePlayerLoading.Admission admission;
	private final String executorSchedulerName;

	public ThrottlingChunkTaskDispatcher(TaskScheduler<Runnable> taskScheduler, Executor executor, int i) {
		super(taskScheduler, executor);
		this.admission = new NativePlayerLoading.Admission(i);
		this.executorSchedulerName = taskScheduler.name();
	}

	@Override
	protected void onRelease(long l) {
		this.admission.released(l);
	}

	@Nullable
	@Override
	protected ChunkTaskPriorityQueue.TasksForChunk popTasks() {
		return this.admission.mayPop() ? super.popTasks() : null;
	}

	@Override
	protected void scheduleForExecution(ChunkTaskPriorityQueue.TasksForChunk tasksForChunk) {
		this.admission.started(tasksForChunk.chunkPos());
		super.scheduleForExecution(tasksForChunk);
	}

	@VisibleForTesting
	public String getDebugStatus() {
		StringJoiner positions = new StringJoiner(",");
		try (NativePlayerLoading.Walk walk = this.admission.walk()) {
			while (walk.next(-1) != NativePlayerLoading.DONE) {
				long key = walk.key();
				positions.add(key + ":" + new ChunkPos(key));
			}
		}
		return this.executorSchedulerName + "=[" + positions + "], s=" + this.sleeping;
	}
}
