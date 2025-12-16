package net.minecraft.client.renderer.gl.advanced.shader.uniform;

import net.minecraft.client.renderer.gl.advanced.buffer.GlBuffer;
import org.lwjgl.opengl.GL32C;

public class GlUniformBlock {
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(GlBuffer buffer) {
        GL32C.glBindBufferBase(GL32C.GL_UNIFORM_BUFFER, this.binding, buffer.handle());
    }
}
