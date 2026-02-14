package net.vulkanic.backends.opengl;

import net.vulkanic.DescriptorSetLayout;
import net.vulkanic.DescriptorSetLayoutBuilder;

import java.util.List;

/**
 * OpenGL implementation of DescriptorSetLayout interface.
 */
public class GLDescriptorSetLayout implements DescriptorSetLayout {
    
    private final long handle;
    private final List<DescriptorSetLayoutBuilder.Binding> bindings;
    
    public GLDescriptorSetLayout(long handle, List<DescriptorSetLayoutBuilder.Binding> bindings) {
        this.handle = handle;
        this.bindings = bindings;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public int getBindingCount() {
        return bindings.size();
    }
    
    // OpenGL-specific accessor
    public List<DescriptorSetLayoutBuilder.Binding> getBindings() {
        return bindings;
    }
}
