package net.sodium.client.gl.buffer;

import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicAPI;

public enum GlBufferTarget {
    ARRAY_BUFFER(VulkanicAPI.GL_ARRAY_BUFFER, VulkanicAPI.GL_ARRAY_BUFFER_BINDING, VulkanicBufferTarget.VERTEX),
    ELEMENT_BUFFER(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER, VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER_BINDING, VulkanicBufferTarget.INDEX),
    COPY_READ_BUFFER(VulkanicAPI.GL_COPY_READ_BUFFER, VulkanicAPI.GL_COPY_READ_BUFFER, VulkanicBufferTarget.COPY_READ),
    COPY_WRITE_BUFFER(VulkanicAPI.GL_COPY_WRITE_BUFFER, VulkanicAPI.GL_COPY_WRITE_BUFFER, VulkanicBufferTarget.COPY_WRITE);

    public static final GlBufferTarget[] VALUES = GlBufferTarget.values();
    public static final int COUNT = VALUES.length;

    private final int target;
    private final int binding;
    private final VulkanicBufferTarget vulkanicTarget;

    GlBufferTarget(int target, int binding, VulkanicBufferTarget vulkanicTarget) {
        this.target = target;
        this.binding = binding;
        this.vulkanicTarget = vulkanicTarget;
    }

    public int getTargetParameter() {
        return this.target;
    }

    public int getBindingParameter() {
        return this.binding;
    }

    public VulkanicBufferTarget toVulkanicBufferTarget() {
        return this.vulkanicTarget;
    }
}
