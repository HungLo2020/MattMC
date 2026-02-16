package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;

public class GlUniformInt extends GlUniform<Integer> {
    public GlUniformInt(int index) {
        super(index);
    }

    @Override
    public void set(Integer value) {
        this.setInt(value);
    }

    public void setInt(int value) {
        net.vulkanic.CommandContext ctx = net.vulkanic.VulkanicAPI.getImmediateContext();
        VulkanicAPI.setUniformInt(ctx, this.index, value);
    }
}
