package net.minecraft.server.level;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;

/** Same compiled CPU queue workload on actual Frozen and Current; not server FPS. */
public final class ChunkTasksVerification {
    static final Runnable TASK = () -> {};
    static long run(int rounds) {
        long checksum = 0;
        var queue = new ChunkTaskPriorityQueue("measure");
        int levels = ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT;
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i < 256; i++) {
                long pos = ChunkPos.asLong(i, round & 7);
                queue.submit(TASK, pos, i % levels);
                queue.submit(TASK, pos, i % levels);
            }
            for (int i = 0; i < 256; i += 3) {
                queue.resortChunkTasks(i % levels, new ChunkPos(i, round & 7), (i + 7) % levels);
            }
            for (int i = 0; i < 256; i += 5) queue.release(ChunkPos.asLong(i, round & 7), true);
            while (queue.hasWork()) {
                var tasks = queue.pop();
                checksum += tasks.chunkPos() * 31 + tasks.tasks().size();
            }
        }
        return checksum;
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        int rounds = args.length == 0 ? 1500 : Integer.parseInt(args[0]);
        int warmup = args.length < 2 ? 15000 : Integer.parseInt(args[1]);
        for (int i = 0; i < 5; i++) run(warmup);
        long start = System.nanoTime(); long checksum = run(rounds); long elapsed = System.nanoTime() - start;
        System.out.println("CHUNK_TASKS " + elapsed + " " + checksum);
    }
}
