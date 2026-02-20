package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;

public class GlUniformFloat3v extends GlUniform<float[]> {
    public GlUniformFloat3v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 3) {
            throw new IllegalArgumentException("value.length != 3");
        }

        VulkanicAPI.setUniform3fv(VulkanicAPI.getImmediateContext(), this.index, value);
    }

    public void set(float x, float y, float z) {
        VulkanicAPI.setUniform3f(VulkanicAPI.getImmediateContext(), this.index, x, y, z);
    }
}
