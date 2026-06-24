package net.sodium.client.gl.arena.staging;

import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;

/**
 * Placeholder staging object for backend paths that upload chunk buffers through
 * backend-owned GpuBuffer operations instead of Sodium's legacy GL buffer copies.
 */
public class NoopStagingBuffer implements StagingBuffer {
    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset) {
        throw new UnsupportedOperationException("NoopStagingBuffer cannot stage legacy GL buffer copies");
    }

    @Override
    public void flush(CommandList commandList) {
    }

    @Override
    public void delete(CommandList commandList) {
    }

    @Override
    public void flip() {
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return Long.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "Noop";
    }
}
