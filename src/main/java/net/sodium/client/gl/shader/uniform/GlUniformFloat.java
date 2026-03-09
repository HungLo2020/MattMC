package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;

public class GlUniformFloat extends GlUniform<Float> {
    public GlUniformFloat(int index) {
        super(index);
    }

    @Override
    public void set(Float value) {
        this.setFloat(value);
    }

    public void setFloat(float value) {
        VulkanicAPI.setUniform1f(VulkanicAPI.getCommandContext(), this.index, value);
    }
}
