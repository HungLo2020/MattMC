package net.minecraft.client.renderer.sodium.api.vertex.serializer;

public interface VertexSerializer {
    void serialize(long srcBuffer, long dstBuffer, int count);
}
