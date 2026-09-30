package net.sodium.client.gl.buffer;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class GlBufferMapping {
    private final GlBuffer buffer;
    private final ByteBuffer map;

    protected boolean disposed;

    public GlBufferMapping(GlBuffer buffer, ByteBuffer map) {
        this.buffer = buffer;
        this.map = map;
    }

    public void write(ByteBuffer data, int writeOffset) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(this.map, writeOffset), data.remaining());
    }

    public void dispose() {
        this.disposed = true;
    }

}
