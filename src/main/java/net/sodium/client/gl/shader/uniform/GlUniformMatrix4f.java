package net.sodium.client.gl.shader.uniform;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix4fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(16);
            value.get(buf);

            CommandContext ctx = VulkanicAPI.getImmediateContext();
            VulkanicAPI.assignUniformMatrix4f(ctx, this.index, buf);
        }
    }
}
