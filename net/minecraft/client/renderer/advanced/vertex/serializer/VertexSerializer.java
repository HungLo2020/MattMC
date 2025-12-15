package net.minecraft.client.renderer.advanced.vertex.serializer;

import org.jetbrains.annotations.ApiStatus;

public interface VertexSerializer {
    void serialize(long srcBuffer, long dstBuffer, int count);
}
