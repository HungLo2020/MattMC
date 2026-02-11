package net.sodium.client.gl.shader.uniform;

import net.sodium.client.gl.buffer.GlBuffer;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

public class GlUniformBlock {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(GlBuffer buffer) {
        VulkanicAPI.bindUniformBufferBase(CTX, this.binding, buffer.handle());
    }
}
