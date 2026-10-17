package net.minecraft.server.level;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/** Rust owns chunk-task ordering and membership; Java temporarily pins callables. */
public class ChunkTaskPriorityQueue {
    public static final int PRIORITY_LEVEL_COUNT = ChunkLevel.MAX_LEVEL + 2;
    private final NativeChunkTasks queue = new NativeChunkTasks(PRIORITY_LEVEL_COUNT);
    private final String name;

    public ChunkTaskPriorityQueue(String name) { this.name = name; }

    protected void resortChunkTasks(int oldPriority, ChunkPos pos, int newPriority) {
        if (oldPriority >= PRIORITY_LEVEL_COUNT) return;
        // Frozen indexes the source list before dereferencing ChunkPos.
        if (oldPriority < 0) throw new ArrayIndexOutOfBoundsException(oldPriority);
        queue.resort(pos.toLong(), oldPriority, newPriority);
    }
    protected void submit(Runnable task, long pos, int priority) { queue.submit(task, pos, priority); }
    protected void release(long pos, boolean clear) { queue.release(pos, clear); }
    @Nullable public TasksForChunk pop() { return queue.pop(); }
    public boolean hasWork() { return queue.hasWork(); }
    public String toString() { return name + " " + queue.top() + "..."; }
    public record TasksForChunk(long chunkPos, List<Runnable> tasks) {}
}
