package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral buffer binding target for Vulkanic frontend APIs.
 */
public enum VulkanicBufferTarget {
    VERTEX,
    INDEX,
    COPY_READ,
    COPY_WRITE,
    PIXEL_PACK,
    SHADER_STORAGE,
    UNIFORM;

    /**
     * Converts a legacy GL buffer target constant into a typed target when known.
     */
    public static Optional<VulkanicBufferTarget> fromLegacyGlTarget(int target) {
        return switch (target) {
            case VulkanicAPI.GL_ARRAY_BUFFER -> Optional.of(VERTEX);
            case VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER -> Optional.of(INDEX);
            case VulkanicAPI.GL_COPY_READ_BUFFER -> Optional.of(COPY_READ);
            case VulkanicAPI.GL_COPY_WRITE_BUFFER -> Optional.of(COPY_WRITE);
            case VulkanicAPI.GL_PIXEL_PACK_BUFFER -> Optional.of(PIXEL_PACK);
            case VulkanicAPI.GL_SHADER_STORAGE_BUFFER -> Optional.of(SHADER_STORAGE);
            case VulkanicAPI.GL_UNIFORM_BUFFER -> Optional.of(UNIFORM);
            default -> Optional.empty();
        };
    }
}