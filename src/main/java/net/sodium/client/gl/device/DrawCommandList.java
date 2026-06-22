package net.sodium.client.gl.device;

import net.sodium.client.gl.tessellation.GlIndexType;
import net.vulkanic.VulkanicIndexType;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType);

    default void multiDrawElementsBaseVertex(MultiDrawBatch batch, VulkanicIndexType indexType) {
        this.multiDrawElementsBaseVertex(batch, GlIndexType.fromVulkanicIndexType(indexType));
    }

    void endTessellating();

    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
