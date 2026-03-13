package net.vulkanic.backends.vulkan;

import net.vulkanic.DescriptorPoolHandle;
import net.vulkanic.PipelineDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Vulkan backend implementation of descriptor-pool allocation semantics.
 *
 * <p>This models descriptor-set lifetime and reuse constraints for the backend-neutral
 * descriptor API. Native VkDescriptorPool integration can be attached to this lifecycle
 * once Vulkan pipeline-layout creation is available.
 */
public final class VulkanDescriptorPoolHandle implements DescriptorPoolHandle {

    private final int maxSets;
    private final Set<VulkanDescriptorSetHandle> liveSets = new LinkedHashSet<>();
    private boolean closed;

    public VulkanDescriptorPoolHandle(int maxSets) {
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

        for (VulkanDescriptorSetHandle descriptorSet : new ArrayList<>(liveSets)) {
            descriptorSet.close();
        }

        liveSets.clear();
        closed = true;
    }

    public void reset() {
        ensureOpen();
        for (VulkanDescriptorSetHandle descriptorSet : new ArrayList<>(liveSets)) {
            descriptorSet.close();
        }
        liveSets.clear();
    }

    public VulkanDescriptorSetHandle allocate(String layoutKey,
                                              PipelineDescriptor.ResourceLayout layout) {
        ensureOpen();
        if (liveSets.size() >= maxSets) {
            throw new IllegalStateException(
                "Descriptor pool exhausted: allocated " + liveSets.size() + " of maxSets=" + maxSets);
        }

        VulkanDescriptorSetHandle descriptorSet = new VulkanDescriptorSetHandle(this, layoutKey, layout);
        liveSets.add(descriptorSet);
        return descriptorSet;
    }

    void release(VulkanDescriptorSetHandle descriptorSet) {
        liveSets.remove(descriptorSet);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Descriptor pool is closed");
        }
    }
}
