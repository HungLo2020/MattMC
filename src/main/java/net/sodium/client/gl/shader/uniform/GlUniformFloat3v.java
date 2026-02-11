package net.sodium.client.gl.shader.uniform;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class GlUniformFloat3v extends GlUniform<float[]> {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    public GlUniformFloat3v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 3) {
            throw new IllegalArgumentException("value.length != 3");
        }

        VulkanicAPI.assignUniformFloat3v(CTX, this.index, value);
    }

    public void set(float x, float y, float z) {
        VulkanicAPI.assignUniformFloat3(CTX, this.index, x, y, z);
    }
}
