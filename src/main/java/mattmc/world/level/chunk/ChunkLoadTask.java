package mattmc.world.level.chunk;

/**
 * Represents a chunk loading task with priority information.
 * Used to prioritize chunk loading based on distance from player.
 */
public class ChunkLoadTask implements Comparable<ChunkLoadTask> {
    private final int chunkX;
    private final int chunkZ;
    private final double distanceSquared;
    private final TaskType type;
    
    public enum TaskType {
        GENERATION,  // Generate chunk terrain
        DISK_LOAD,   // Load chunk from disk
        MESHING      // Build chunk mesh
    }
    
    public ChunkLoadTask(int chunkX, int chunkZ, double distanceSquared, TaskType type) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.distanceSquared = distanceSquared;
        this.type = type;
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public double getDistanceSquared() {
        return distanceSquared;
    }
    
    public TaskType getType() {
        return type;
    }
    
    @Override
    public int compareTo(ChunkLoadTask other) {
        // Closer chunks have higher priority (lower distance = higher priority)
        return Double.compare(this.distanceSquared, other.distanceSquared);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkLoadTask)) return false;
        ChunkLoadTask other = (ChunkLoadTask) obj;
        return chunkX == other.chunkX && chunkZ == other.chunkZ && type == other.type;
    }
    
    @Override
    public int hashCode() {
        return 31 * (31 * chunkX + chunkZ) + type.hashCode();
    }
}
