package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral integer query key for Vulkanic frontend APIs.
 */
public enum VulkanicIntegerQuery {
    CONTEXT_FLAGS,
    MAX_TEXTURE_SIZE,
    MAX_TEXTURE_IMAGE_UNITS,
    MAX_DRAW_BUFFERS,
    MAX_SHADER_STORAGE_BUFFER_BINDINGS,
    UNIFORM_BUFFER_OFFSET_ALIGNMENT,
    TEXTURE_BINDING_2D,
    FRAMEBUFFER_BINDING,
    MAX_COLOR_ATTACHMENTS,
    NUM_EXTENSIONS,
    MAX_LABEL_LENGTH,
    TEXTURE_MAX_LEVEL;

    /**
     * Converts a legacy GL integer pname constant into a typed query when known.
     */
    public static Optional<VulkanicIntegerQuery> fromLegacyGlPName(int pname) {
        return switch (pname) {
            case VulkanicAPI.GL_CONTEXT_FLAGS -> Optional.of(CONTEXT_FLAGS);
            case VulkanicAPI.GL_MAX_TEXTURE_SIZE -> Optional.of(MAX_TEXTURE_SIZE);
            case VulkanicAPI.GL_MAX_TEXTURE_IMAGE_UNITS -> Optional.of(MAX_TEXTURE_IMAGE_UNITS);
            case VulkanicAPI.GL_MAX_DRAW_BUFFERS -> Optional.of(MAX_DRAW_BUFFERS);
            case VulkanicAPI.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS -> Optional.of(MAX_SHADER_STORAGE_BUFFER_BINDINGS);
            case VulkanicAPI.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT -> Optional.of(UNIFORM_BUFFER_OFFSET_ALIGNMENT);
            case VulkanicAPI.GL_TEXTURE_BINDING_2D -> Optional.of(TEXTURE_BINDING_2D);
            case VulkanicAPI.GL_FRAMEBUFFER_BINDING -> Optional.of(FRAMEBUFFER_BINDING);
            case VulkanicAPI.GL_MAX_COLOR_ATTACHMENTS -> Optional.of(MAX_COLOR_ATTACHMENTS);
            case VulkanicAPI.GL_NUM_EXTENSIONS -> Optional.of(NUM_EXTENSIONS);
            case VulkanicAPI.GL_MAX_LABEL_LENGTH -> Optional.of(MAX_LABEL_LENGTH);
            case VulkanicAPI.GL_TEXTURE_MAX_LEVEL -> Optional.of(TEXTURE_MAX_LEVEL);
            default -> Optional.empty();
        };
    }
}