package net.minecraft.client.renderer.sodium.render.chunk.compile;

import net.minecraft.client.renderer.chunk.advanced.vertex.format.ChunkVertexType;
import net.minecraft.client.renderer.chunk.advanced.compile.pipeline.BlockRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class ChunkBuildContext {
    public final ChunkBuildBuffers buffers;
    public final BlockRenderCache cache;

    public ChunkBuildContext(ClientLevel level, ChunkVertexType vertexType) {
        this.buffers = new ChunkBuildBuffers(vertexType);
        this.cache = new BlockRenderCache(Minecraft.getInstance(), level);
    }

    public void cleanup() {
        this.buffers.destroy();
        this.cache.cleanup();
    }
}
