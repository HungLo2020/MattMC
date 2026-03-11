package net.vulkanic;

/**
 * Backend-neutral graphics features used by higher-level systems.
 */
public enum GraphicsFeature {
    DIRECT_STATE_ACCESS,
    BUFFER_STORAGE,
    MULTI_BIND,
    TESSELLATION_SHADER,
    SHADER_STORAGE_BUFFER,
    IMAGE_LOAD_STORE,
    DRAW_BUFFERS_BLEND,
    NO_ERROR_CONTEXT,
    DEBUG_OUTPUT_CONTROL,
    DEBUG_OUTPUT_ARB,
    DEBUG_OUTPUT_AMD,
    DEBUG_CONTEXT_FLAGS,
    GPU_MEMORY_INFO
}