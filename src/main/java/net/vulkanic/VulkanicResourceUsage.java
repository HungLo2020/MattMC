package net.vulkanic;

/**
 * Backend-neutral image/resource usage intent for Vulkanic synchronization and render-pass planning.
 *
 * <p>{@link #INFERRED} preserves the legacy migration behavior where the active backend derives a
 * suitable native state from framebuffer attachments, texture metadata, and current tracking.</p>
 */
public enum VulkanicResourceUsage {
    INFERRED,
    COLOR_ATTACHMENT_WRITE,
    DEPTH_ATTACHMENT_WRITE,
    SAMPLED_READ,
    TRANSFER_SRC,
    TRANSFER_DST,
    STORAGE_READ_WRITE,
    PRESENT,
    ATTACHMENT_FEEDBACK_LOOP
}
