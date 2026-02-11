package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

public class GlUniformFloat extends GlUniform<Float> {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    public GlUniformFloat(int index) {
        super(index);
    }

    @Override
    public void set(Float value) {
        this.setFloat(value);
    }

    public void setFloat(float value) {
        VulkanicAPI.assignUniformFloat(CTX, this.index, value);
    }
}
