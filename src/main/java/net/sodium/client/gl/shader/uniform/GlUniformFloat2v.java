package net.sodium.client.gl.shader.uniform;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class GlUniformFloat2v extends GlUniform<float[]> {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    public GlUniformFloat2v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 2) {
            throw new IllegalArgumentException("value.length != 2");
        }

        VulkanicAPI.assignUniformFloat2v(CTX, this.index, value);
    }

    public void set(float x, float y) {
        VulkanicAPI.assignUniformFloat2(CTX, this.index, x, y);
    }
}
