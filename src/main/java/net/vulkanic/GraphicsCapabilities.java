package net.vulkanic;

/**
 * Abstraction for graphics API capabilities.
 * This wraps the underlying graphics API capabilities without exposing OpenGL or Vulkan directly.
 */
public class GraphicsCapabilities {
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
        boolean gl_ARB_buffer_storage, boolean gl_ARB_vertex_attrib_binding, boolean gl_ARB_direct_state_access,
        boolean gl_ARB_debug_output, boolean gl_KHR_debug, boolean gl_AMD_debug_output,
        boolean gl_KHR_no_error, boolean gl_EXT_debug_label, boolean gl_ARB_timer_query,
        boolean gl_KHR_parallel_shader_compile, boolean gl_ARB_parallel_shader_compile,
        boolean gl_ARB_multi_bind, boolean gl_ARB_tessellation_shader,
        boolean gl_ARB_shader_storage_buffer_object, boolean gl_ARB_shader_image_load_store,
        boolean gl_EXT_shader_image_load_store, boolean gl_ARB_draw_buffers_blend,
        boolean gl_NVX_gpu_memory_info
    ) {
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
        
        this.GL_ARB_buffer_storage = gl_ARB_buffer_storage;
        this.GL_ARB_vertex_attrib_binding = gl_ARB_vertex_attrib_binding;
        this.GL_ARB_direct_state_access = gl_ARB_direct_state_access;
        this.GL_ARB_debug_output = gl_ARB_debug_output;
        this.GL_KHR_debug = gl_KHR_debug;
        this.GL_AMD_debug_output = gl_AMD_debug_output;
        this.GL_KHR_no_error = gl_KHR_no_error;
        this.GL_EXT_debug_label = gl_EXT_debug_label;
        this.GL_ARB_timer_query = gl_ARB_timer_query;
        this.GL_KHR_parallel_shader_compile = gl_KHR_parallel_shader_compile;
        this.GL_ARB_parallel_shader_compile = gl_ARB_parallel_shader_compile;
        this.GL_ARB_multi_bind = gl_ARB_multi_bind;
        this.GL_ARB_tessellation_shader = gl_ARB_tessellation_shader;
        this.GL_ARB_shader_storage_buffer_object = gl_ARB_shader_storage_buffer_object;
        this.GL_ARB_shader_image_load_store = gl_ARB_shader_image_load_store;
        this.GL_EXT_shader_image_load_store = gl_EXT_shader_image_load_store;
        this.GL_ARB_draw_buffers_blend = gl_ARB_draw_buffers_blend;
        this.GL_NVX_gpu_memory_info = gl_NVX_gpu_memory_info;
    }
}
