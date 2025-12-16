package net.minecraft.client.renderer.sodium.gl.arena.staging;

import net.minecraft.client.renderer.gl.advanced.buffer.GlBuffer;
import net.minecraft.client.renderer.gl.advanced.device.CommandList;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(CommandList commandList, ByteBuffer data, GlBuffer dst, long writeOffset);

    void flush(CommandList commandList);

    void delete(CommandList commandList);

    void flip();

    long getUploadSizeLimit(long frameDuration);
}
