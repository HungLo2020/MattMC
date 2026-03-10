package net.vulkanic.backends.opengl;

import net.vulkanic.DescriptorSetHandle;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;

/**
 * OpenGL implementation of descriptor-set allocation semantics.
 */
public final class OpenGLDescriptorSetHandle implements DescriptorSetHandle {

    private final OpenGLDescriptorPoolHandle pool;
    private final String layoutKey;
    private final PipelineDescriptor.ResourceLayout layout;
    private PipelineResourceBindings bindings;
    private boolean closed;

    OpenGLDescriptorSetHandle(OpenGLDescriptorPoolHandle pool,
            String layoutKey,
            PipelineDescriptor.ResourceLayout layout) {
        this.pool = pool;
        this.layoutKey = layoutKey;
        this.layout = layout;
        this.closed = false;
    }

    @Override
    public boolean isValid() {
        return !closed && pool.isValid();
    }

    @Override
    public String layoutKey() {
        return layoutKey;
    }

    @Override
    public PipelineDescriptor.ResourceLayout layout() {
        return layout;
    }

    public void updateBindings(PipelineResourceBindings bindings) {
        ensureValid();
        if (bindings == null) {
            throw new IllegalArgumentException("bindings must not be null");
        }
        bindings.validateAgainst(layout);
        this.bindings = bindings;
    }

    public PipelineResourceBindings requireBindings() {
        ensureValid();
        if (bindings == null) {
            throw new IllegalStateException(
                "Descriptor set has not been updated yet. Call updateDescriptorSet(...) before binding.");
        }
        return bindings;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bindings = null;
        pool.release(this);
    }

    private void ensureValid() {
        if (!isValid()) {
            throw new IllegalStateException("Descriptor set is not valid");
        }
    }
}
