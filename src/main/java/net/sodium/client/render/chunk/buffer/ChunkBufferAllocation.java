package net.sodium.client.render.chunk.buffer;

/**
 * Backend-neutral allocation inside a chunk geometry arena.
 *
 * <p>Offsets are expressed in arena elements: vertices for geometry arenas and indices for
 * index arenas. Existing Sodium mesh metadata already uses these element offsets directly.</p>
 */
public interface ChunkBufferAllocation {
    long getOffset();

    long getLength();

    void delete();
}
