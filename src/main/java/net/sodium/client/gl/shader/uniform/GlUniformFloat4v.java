package net.sodium.client.gl.shader.uniform;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class GlUniformFloat4v extends GlUniform<float[]> {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    public GlUniformFloat4v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 4) {
            throw new IllegalArgumentException("value.length != 4");
        }

        VulkanicAPI.assignUniformFloat4v(CTX, this.index, value);
    }

    public void set(float x, float y, float z, float w) {
        VulkanicAPI.assignUniformFloat4(CTX, this.index, x, y, z, w);
    }
}
