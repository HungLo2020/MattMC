package net.sodium.client.render.chunk.buffer;

import net.blaze3d.buffers.GpuBuffer;
import net.sodium.client.gl.arena.PendingUpload;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.device.CommandList;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Backend-neutral chunk geometry/index arena contract.
 *
 * <p>The current implementation is backed by Sodium's GL arena, but Vulkan terrain should depend
 * on this contract instead of constructing GL buffer wrappers at the renderer callsite.</p>
 */
public interface ChunkBufferArena {
    boolean upload(CommandList commandList, Stream<PendingUpload> stream);

    void delete(CommandList commandList);

    boolean isEmpty();

    long getDeviceUsedMemory();

    long getDeviceAllocatedMemory();

    GpuBuffer gpuBufferView(Supplier<String> label, int usage);

    GlBuffer legacyGlBuffer();
}
