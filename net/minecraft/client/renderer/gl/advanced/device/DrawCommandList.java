package net.minecraft.client.renderer.gl.advanced.device;

import net.minecraft.client.renderer.gl.advanced.tessellation.GlIndexType;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType);

    void endTessellating();

    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
