package net.vulkanic;

/**
 * Relationship between an explicit Vulkanic render-target descriptor and a
 * legacy framebuffer's currently tracked attachment contract.
 */
public enum VulkanicRenderTargetCompatibility {
    EXACT,
    DESCRIPTOR_SUFFIX,
    DESCRIPTOR_ATTACHMENTLESS,
    MISMATCH;

    public boolean isEquivalent() {
        return this == EXACT;
    }

    public boolean isCompatible() {
        return this != MISMATCH;
    }

    public boolean allowsDescriptorBackedRenderPass() {
        return this == EXACT;
    }
}
