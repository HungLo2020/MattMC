package net.sodium.client.gl.shader.uniform;

import net.sodium.client.gl.buffer.GlBuffer;
import net.vulkanic.VulkanicAPI;

public class GlUniformBlock {
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(GlBuffer buffer) {
        VulkanicAPI.bindUniformBufferBase(VulkanicAPI.getCommandContext(), this.binding, buffer.handle());
    }
}
