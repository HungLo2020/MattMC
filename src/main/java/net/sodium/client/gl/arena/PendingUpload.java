package net.sodium.client.gl.arena;

import net.sodium.client.util.NativeBuffer;
import net.sodium.client.render.chunk.buffer.ChunkBufferAllocation;

public class PendingUpload {
    private final NativeBuffer data;
    private ChunkBufferAllocation result;

    public PendingUpload(NativeBuffer data) {
        this.data = data;
    }

    public NativeBuffer getDataBuffer() {
        return this.data;
    }

    protected void setResult(ChunkBufferAllocation result) {
        if (this.result != null) {
            throw new IllegalStateException("Result already provided");
        }

        this.result = result;
    }

    public ChunkBufferAllocation getResult() {
        if (this.result == null) {
            throw new IllegalStateException("Result not computed");
        }

        return this.result;
    }

    public int getLength() {
        return this.data.getLength();
    }
}
