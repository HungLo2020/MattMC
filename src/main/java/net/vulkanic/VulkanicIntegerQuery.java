package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral integer query key for Vulkanic frontend APIs.
 */
public enum VulkanicIntegerQuery {
    CONTEXT_FLAGS,
    CURRENT_PROGRAM,
    VERTEX_ARRAY_BINDING,
    ARRAY_BUFFER_BINDING,
    ELEMENT_ARRAY_BUFFER_BINDING,
    ACTIVE_TEXTURE,
    BLEND_EQUATION_RGB,
    BLEND_EQUATION_ALPHA,
    BLEND_SRC_RGB,
    BLEND_SRC_ALPHA,
    BLEND_DST_RGB,
    BLEND_DST_ALPHA,
    DEPTH_WRITEMASK,
    DEPTH_FUNC,
    STENCIL_FUNC,
    STENCIL_REF,
    STENCIL_VALUE_MASK,
    STENCIL_FAIL,
    STENCIL_PASS_DEPTH_FAIL,
    STENCIL_PASS_DEPTH_PASS,
    STENCIL_WRITEMASK,
    CULL_FACE_MODE,
    POLYGON_MODE,
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
    TEXTURE_MAX_LEVEL,
    GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX;

    /**
     * Converts a legacy GL integer pname constant into a typed query when known.
     */
    public static Optional<VulkanicIntegerQuery> fromLegacyGlPName(int pname) {
        return switch (pname) {
            case VulkanicAPI.GL_CONTEXT_FLAGS -> Optional.of(CONTEXT_FLAGS);
            case VulkanicAPI.GL_CURRENT_PROGRAM -> Optional.of(CURRENT_PROGRAM);
            case VulkanicAPI.GL_VERTEX_ARRAY_BINDING -> Optional.of(VERTEX_ARRAY_BINDING);
            case VulkanicAPI.GL_ARRAY_BUFFER_BINDING -> Optional.of(ARRAY_BUFFER_BINDING);
            case VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER_BINDING -> Optional.of(ELEMENT_ARRAY_BUFFER_BINDING);
            case VulkanicAPI.GL_ACTIVE_TEXTURE -> Optional.of(ACTIVE_TEXTURE);
            case VulkanicAPI.GL_BLEND_EQUATION_RGB -> Optional.of(BLEND_EQUATION_RGB);
            case VulkanicAPI.GL_BLEND_EQUATION_ALPHA -> Optional.of(BLEND_EQUATION_ALPHA);
            case VulkanicAPI.GL_BLEND_SRC_RGB -> Optional.of(BLEND_SRC_RGB);
            case VulkanicAPI.GL_BLEND_SRC_ALPHA -> Optional.of(BLEND_SRC_ALPHA);
            case VulkanicAPI.GL_BLEND_DST_RGB -> Optional.of(BLEND_DST_RGB);
            case VulkanicAPI.GL_BLEND_DST_ALPHA -> Optional.of(BLEND_DST_ALPHA);
            case VulkanicAPI.GL_DEPTH_WRITEMASK -> Optional.of(DEPTH_WRITEMASK);
            case VulkanicAPI.GL_DEPTH_FUNC -> Optional.of(DEPTH_FUNC);
            case VulkanicAPI.GL_STENCIL_FUNC -> Optional.of(STENCIL_FUNC);
            case VulkanicAPI.GL_STENCIL_REF -> Optional.of(STENCIL_REF);
            case VulkanicAPI.GL_STENCIL_VALUE_MASK -> Optional.of(STENCIL_VALUE_MASK);
            case VulkanicAPI.GL_STENCIL_FAIL -> Optional.of(STENCIL_FAIL);
            case VulkanicAPI.GL_STENCIL_PASS_DEPTH_FAIL -> Optional.of(STENCIL_PASS_DEPTH_FAIL);
            case VulkanicAPI.GL_STENCIL_PASS_DEPTH_PASS -> Optional.of(STENCIL_PASS_DEPTH_PASS);
            case VulkanicAPI.GL_STENCIL_WRITEMASK -> Optional.of(STENCIL_WRITEMASK);
            case VulkanicAPI.GL_CULL_FACE_MODE -> Optional.of(CULL_FACE_MODE);
            case VulkanicAPI.GL_POLYGON_MODE -> Optional.of(POLYGON_MODE);
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
            case VulkanicAPI.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX -> Optional.of(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
            default -> Optional.empty();
        };
    }
}