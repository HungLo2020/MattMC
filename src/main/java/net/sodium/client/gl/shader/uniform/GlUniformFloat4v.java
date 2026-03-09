package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;

public class GlUniformFloat4v extends GlUniform<float[]> {
    public GlUniformFloat4v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 4) {
            throw new IllegalArgumentException("value.length != 4");
        }

        VulkanicAPI.setUniform4fv(VulkanicAPI.getCommandContext(), this.index, value);
    }

    public void set(float x, float y, float z, float w) {
        VulkanicAPI.setUniform4f(VulkanicAPI.getCommandContext(), this.index, x, y, z, w);
    }
}
