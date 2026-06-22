package net.sodium.client.render.device;

import net.sodium.client.gl.buffer.GlBufferTarget;
import net.vulkanic.VulkanicBufferTarget;

public enum RenderBufferTarget {
    VERTEX(GlBufferTarget.ARRAY_BUFFER, VulkanicBufferTarget.VERTEX),
    INDEX(GlBufferTarget.ELEMENT_BUFFER, VulkanicBufferTarget.INDEX);

    private final GlBufferTarget glTarget;
    private final VulkanicBufferTarget vulkanicTarget;

    RenderBufferTarget(GlBufferTarget glTarget, VulkanicBufferTarget vulkanicTarget) {
        this.glTarget = glTarget;
        this.vulkanicTarget = vulkanicTarget;
    }

    public GlBufferTarget toGlBufferTarget() {
        return this.glTarget;
    }

    public VulkanicBufferTarget toVulkanicBufferTarget() {
        return this.vulkanicTarget;
    }
}
