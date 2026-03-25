package net.vulkanic.backends.opengl;

import net.vulkanic.DescriptorPoolHandle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OpenGL implementation of descriptor pool semantics.
 *
 * <p>OpenGL does not have native descriptor pools, so this class models
 * allocation lifetime and reuse constraints as a Vulkan-prep seam.</p>
 */
public final class OpenGLDescriptorPoolHandle implements DescriptorPoolHandle {

    private final int maxSets;
    private final Set<OpenGLDescriptorSetHandle> liveSets = new LinkedHashSet<>();
    private boolean closed;

    public OpenGLDescriptorPoolHandle(int maxSets) {
        this.maxSets = maxSets;
        this.closed = false;
    }

    @Override
    public int maxSets() {
        return maxSets;
    }

    @Override
    public int allocatedSetCount() {
        return liveSets.size();
    }

    @Override
    public boolean isValid() {
        return !closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        for (OpenGLDescriptorSetHandle descriptorSet : new ArrayList<>(liveSets)) {
            descriptorSet.close();
        }

        liveSets.clear();
        closed = true;
    }

    public void reset() {
        ensureOpen();
        for (OpenGLDescriptorSetHandle descriptorSet : new ArrayList<>(liveSets)) {
            descriptorSet.close();
        }
        liveSets.clear();
    }

    public OpenGLDescriptorSetHandle allocate(String layoutKey,
            net.vulkanic.PipelineDescriptor.ResourceLayout layout) {
        ensureOpen();
        if (liveSets.size() >= maxSets) {
            throw new IllegalStateException(
                "Descriptor pool exhausted: allocated " + liveSets.size() + " of maxSets=" + maxSets);
        }
        OpenGLDescriptorSetHandle descriptorSet = new OpenGLDescriptorSetHandle(this, layoutKey, layout);
        liveSets.add(descriptorSet);
        return descriptorSet;
    }

    void release(OpenGLDescriptorSetHandle descriptorSet) {
        liveSets.remove(descriptorSet);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Descriptor pool is closed");
        }
    }
}
