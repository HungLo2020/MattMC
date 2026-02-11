package net.sodium.client.gl.shader.uniform;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.CommandContext;

public class GlUniformInt extends GlUniform<Integer> {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    
    public GlUniformInt(int index) {
        super(index);
    }

    @Override
    public void set(Integer value) {
        this.setInt(value);
    }

    public void setInt(int value) {
        VulkanicAPI.assignUniformInteger(CTX, this.index, value);
    }
}
