package net.vulkanic.backends.opengl;

import net.vulkanic.DescriptorSet;
import net.vulkanic.DescriptorSetLayout;
import net.vulkanic.Buffer;
import net.vulkanic.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenGL implementation of DescriptorSet interface.
 * 
 * In OpenGL, this tracks texture and buffer bindings that will be applied when drawing.
 */
public class GLDescriptorSet implements DescriptorSet {
    
    /**
     * Represents a single bound resource.
     */
    public static class Binding {
        public enum Type { TEXTURE, UNIFORM_BUFFER, STORAGE_BUFFER }
        
        public final Type type;
        public final long resourceHandle;
        
        public Binding(Type type, long resourceHandle) {
            this.type = type;
            this.resourceHandle = resourceHandle;
        }
    }
    
    private final long handle;
    private final DescriptorSetLayout layout;
    private final Map<Integer, Binding> bindings = new HashMap<>();
    
    public GLDescriptorSet(long handle, DescriptorSetLayout layout) {
        this.handle = handle;
        this.layout = layout;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    @Override
    public DescriptorSetLayout getLayout() {
        return layout;
    }
    
    // OpenGL-specific accessors
    public void setBinding(int binding, Binding.Type type, long resourceHandle) {
        bindings.put(binding, new Binding(type, resourceHandle));
    }
    
    public Map<Integer, Binding> getBindings() {
        return bindings;
    }
}
