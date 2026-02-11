package net.sodium.client.gl.buffer;

import net.sodium.client.gl.GlObject;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

public abstract class GlBuffer extends GlObject {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        this.setHandle(VulkanicAPI.allocateBufferObject(CTX));
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}
