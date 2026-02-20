package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;

public class GlUniformFloat2v extends GlUniform<float[]> {
    public GlUniformFloat2v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 2) {
            throw new IllegalArgumentException("value.length != 2");
        }

        VulkanicAPI.assignUniformFloat2v(this.index, value);
    }

    public void set(float x, float y) {
        VulkanicAPI.setUniform2f(VulkanicAPI.getImmediateContext(), this.index, x, y);
    }
}
