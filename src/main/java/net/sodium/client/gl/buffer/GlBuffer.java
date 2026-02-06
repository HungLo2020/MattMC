package net.sodium.client.gl.buffer;

import net.sodium.client.gl.GlObject;
import net.vulkanic.VulkanicAPI;

public abstract class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        this.setHandle(VulkanicAPI.allocateBufferObject());
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}
