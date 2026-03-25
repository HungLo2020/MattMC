package net.vulkanic;

/**
 * Abstraction for graphics API capabilities.
 * This wraps the underlying graphics API capabilities without exposing OpenGL or Vulkan directly.
 */
public class GraphicsCapabilities {
    private final GraphicsBackendType backendType;

    // OpenGL version flags
    public final boolean OpenGL11;
    public final boolean OpenGL12;
    public final boolean OpenGL13;
    public final boolean OpenGL14;
    public final boolean OpenGL15;
    public final boolean OpenGL20;
    public final boolean OpenGL21;
    public final boolean OpenGL30;
    public final boolean OpenGL31;
    public final boolean OpenGL32;
    public final boolean OpenGL33;
    public final boolean OpenGL40;
    public final boolean OpenGL41;
    public final boolean OpenGL42;
    public final boolean OpenGL43;
    public final boolean OpenGL44;
    public final boolean OpenGL45;
    public final boolean OpenGL46;
    
    // Extension flags
    public final boolean GL_ARB_buffer_storage;
    public final boolean GL_ARB_vertex_attrib_binding;
    public final boolean GL_ARB_direct_state_access;
    public final boolean GL_ARB_debug_output;
    public final boolean GL_KHR_debug;
    public final boolean GL_AMD_debug_output;
    public final boolean GL_KHR_no_error;
    public final boolean GL_EXT_debug_label;
    public final boolean GL_ARB_timer_query;
    public final boolean GL_KHR_parallel_shader_compile;
    public final boolean GL_ARB_parallel_shader_compile;
    public final boolean GL_ARB_multi_bind;
    public final boolean GL_ARB_tessellation_shader;
    public final boolean GL_ARB_shader_storage_buffer_object;
    public final boolean GL_ARB_shader_image_load_store;
    public final boolean GL_EXT_shader_image_load_store;
    public final boolean GL_ARB_draw_buffers_blend;
    public final boolean GL_NVX_gpu_memory_info;
    
    /**
     * Constructor for creating GraphicsCapabilities.
     * This constructor accepts individual capability flags extracted from the underlying graphics API.
     * 
     * @apiNote This constructor should ONLY be called from backend implementations 
     *          (e.g., OpenGLBackend, VulkanBackend). Client code should never instantiate
     *          GraphicsCapabilities directly.
     */
    public GraphicsCapabilities(
        // OpenGL version flags
        boolean openGL11, boolean openGL12, boolean openGL13, boolean openGL14, boolean openGL15,
        boolean openGL20, boolean openGL21,
        boolean openGL30, boolean openGL31, boolean openGL32, boolean openGL33,
        boolean openGL40, boolean openGL41, boolean openGL42, boolean openGL43, boolean openGL44, boolean openGL45, boolean openGL46,
        // Extension flags
        boolean glArbBufferStorage, boolean glArbVertexAttribBinding, boolean glArbDirectStateAccess,
        boolean glArbDebugOutput, boolean glKhrDebug, boolean glAmdDebugOutput,
        boolean glKhrNoError, boolean glExtDebugLabel, boolean glArbTimerQuery,
        boolean glKhrParallelShaderCompile, boolean glArbParallelShaderCompile,
        boolean glArbMultiBind, boolean glArbTessellationShader,
        boolean glArbShaderStorageBufferObject, boolean glArbShaderImageLoadStore,
        boolean glExtShaderImageLoadStore, boolean glArbDrawBuffersBlend,
        boolean glNvxGpuMemoryInfo
    ) {
        this(
            GraphicsBackendType.OPENGL,
            openGL11, openGL12, openGL13, openGL14, openGL15,
            openGL20, openGL21,
            openGL30, openGL31, openGL32, openGL33,
            openGL40, openGL41, openGL42, openGL43, openGL44, openGL45, openGL46,
            glArbBufferStorage, glArbVertexAttribBinding, glArbDirectStateAccess,
            glArbDebugOutput, glKhrDebug, glAmdDebugOutput,
            glKhrNoError, glExtDebugLabel, glArbTimerQuery,
            glKhrParallelShaderCompile, glArbParallelShaderCompile,
            glArbMultiBind, glArbTessellationShader,
            glArbShaderStorageBufferObject, glArbShaderImageLoadStore,
            glExtShaderImageLoadStore, glArbDrawBuffersBlend,
            glNvxGpuMemoryInfo
        );
    }

    public GraphicsCapabilities(
        GraphicsBackendType backendType,
        // OpenGL version flags
        boolean openGL11, boolean openGL12, boolean openGL13, boolean openGL14, boolean openGL15,
        boolean openGL20, boolean openGL21,
        boolean openGL30, boolean openGL31, boolean openGL32, boolean openGL33,
        boolean openGL40, boolean openGL41, boolean openGL42, boolean openGL43, boolean openGL44, boolean openGL45, boolean openGL46,
        // Extension flags
        boolean glArbBufferStorage, boolean glArbVertexAttribBinding, boolean glArbDirectStateAccess,
        boolean glArbDebugOutput, boolean glKhrDebug, boolean glAmdDebugOutput,
        boolean glKhrNoError, boolean glExtDebugLabel, boolean glArbTimerQuery,
        boolean glKhrParallelShaderCompile, boolean glArbParallelShaderCompile,
        boolean glArbMultiBind, boolean glArbTessellationShader,
        boolean glArbShaderStorageBufferObject, boolean glArbShaderImageLoadStore,
        boolean glExtShaderImageLoadStore, boolean glArbDrawBuffersBlend,
        boolean glNvxGpuMemoryInfo
    ) {
        this.backendType = backendType;
        this.OpenGL11 = openGL11;
        this.OpenGL12 = openGL12;
        this.OpenGL13 = openGL13;
        this.OpenGL14 = openGL14;
        this.OpenGL15 = openGL15;
        this.OpenGL20 = openGL20;
        this.OpenGL21 = openGL21;
        this.OpenGL30 = openGL30;
        this.OpenGL31 = openGL31;
        this.OpenGL32 = openGL32;
        this.OpenGL33 = openGL33;
        this.OpenGL40 = openGL40;
        this.OpenGL41 = openGL41;
        this.OpenGL42 = openGL42;
        this.OpenGL43 = openGL43;
        this.OpenGL44 = openGL44;
        this.OpenGL45 = openGL45;
        this.OpenGL46 = openGL46;
        
        this.GL_ARB_buffer_storage = glArbBufferStorage;
        this.GL_ARB_vertex_attrib_binding = glArbVertexAttribBinding;
        this.GL_ARB_direct_state_access = glArbDirectStateAccess;
        this.GL_ARB_debug_output = glArbDebugOutput;
        this.GL_KHR_debug = glKhrDebug;
        this.GL_AMD_debug_output = glAmdDebugOutput;
        this.GL_KHR_no_error = glKhrNoError;
        this.GL_EXT_debug_label = glExtDebugLabel;
        this.GL_ARB_timer_query = glArbTimerQuery;
        this.GL_KHR_parallel_shader_compile = glKhrParallelShaderCompile;
        this.GL_ARB_parallel_shader_compile = glArbParallelShaderCompile;
        this.GL_ARB_multi_bind = glArbMultiBind;
        this.GL_ARB_tessellation_shader = glArbTessellationShader;
        this.GL_ARB_shader_storage_buffer_object = glArbShaderStorageBufferObject;
        this.GL_ARB_shader_image_load_store = glArbShaderImageLoadStore;
        this.GL_EXT_shader_image_load_store = glExtShaderImageLoadStore;
        this.GL_ARB_draw_buffers_blend = glArbDrawBuffersBlend;
        this.GL_NVX_gpu_memory_info = glNvxGpuMemoryInfo;
    }

    public GraphicsBackendType backendType() {
        return backendType;
    }

    public boolean supports(GraphicsFeature feature) {
        return supportsCore(feature) || supportsExtension(feature);
    }

    public boolean supportsCore(GraphicsFeature feature) {
        return switch (feature) {
            case DIRECT_STATE_ACCESS -> OpenGL45;
            case BUFFER_STORAGE -> OpenGL44;
            case MULTI_BIND -> OpenGL45;
            case TESSELLATION_SHADER -> OpenGL40;
            case SHADER_STORAGE_BUFFER -> OpenGL43;
            case IMAGE_LOAD_STORE -> OpenGL42;
            case DRAW_BUFFERS_BLEND -> OpenGL40;
            case NO_ERROR_CONTEXT -> OpenGL46;
            case DEBUG_OUTPUT_CONTROL -> OpenGL43;
            case DEBUG_OUTPUT_ARB, DEBUG_OUTPUT_AMD, GPU_MEMORY_INFO -> false;
            case DEBUG_CONTEXT_FLAGS -> OpenGL30;
        };
    }

    public boolean supportsExtension(GraphicsFeature feature) {
        return switch (feature) {
            case DIRECT_STATE_ACCESS -> GL_ARB_direct_state_access;
            case BUFFER_STORAGE -> GL_ARB_buffer_storage;
            case MULTI_BIND -> GL_ARB_multi_bind;
            case TESSELLATION_SHADER -> GL_ARB_tessellation_shader;
            case SHADER_STORAGE_BUFFER -> GL_ARB_shader_storage_buffer_object;
            case IMAGE_LOAD_STORE -> GL_ARB_shader_image_load_store || GL_EXT_shader_image_load_store;
            case DRAW_BUFFERS_BLEND -> GL_ARB_draw_buffers_blend;
            case NO_ERROR_CONTEXT -> GL_KHR_no_error;
            case DEBUG_OUTPUT_CONTROL -> GL_KHR_debug;
            case DEBUG_OUTPUT_ARB -> GL_ARB_debug_output;
            case DEBUG_OUTPUT_AMD -> GL_AMD_debug_output;
            case GPU_MEMORY_INFO -> GL_NVX_gpu_memory_info;
            case DEBUG_CONTEXT_FLAGS -> false;
        };
    }
}
