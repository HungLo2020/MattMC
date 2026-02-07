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
     * Creates a GraphicsCapabilities instance by wrapping the underlying OpenGL capabilities.
     * This constructor should only be called from the OpenGL backend.
     */
    public GraphicsCapabilities(org.lwjgl.opengl.GLCapabilities glCaps) {
        this.OpenGL11 = glCaps.OpenGL11;
        this.OpenGL12 = glCaps.OpenGL12;
        this.OpenGL13 = glCaps.OpenGL13;
        this.OpenGL14 = glCaps.OpenGL14;
        this.OpenGL15 = glCaps.OpenGL15;
        this.OpenGL20 = glCaps.OpenGL20;
        this.OpenGL21 = glCaps.OpenGL21;
        this.OpenGL30 = glCaps.OpenGL30;
        this.OpenGL31 = glCaps.OpenGL31;
        this.OpenGL32 = glCaps.OpenGL32;
        this.OpenGL33 = glCaps.OpenGL33;
        this.OpenGL40 = glCaps.OpenGL40;
        this.OpenGL41 = glCaps.OpenGL41;
        this.OpenGL42 = glCaps.OpenGL42;
        this.OpenGL43 = glCaps.OpenGL43;
        this.OpenGL44 = glCaps.OpenGL44;
        this.OpenGL45 = glCaps.OpenGL45;
        this.OpenGL46 = glCaps.OpenGL46;
        
        this.GL_ARB_buffer_storage = glCaps.GL_ARB_buffer_storage;
        this.GL_ARB_vertex_attrib_binding = glCaps.GL_ARB_vertex_attrib_binding;
        this.GL_ARB_direct_state_access = glCaps.GL_ARB_direct_state_access;
        this.GL_ARB_debug_output = glCaps.GL_ARB_debug_output;
        this.GL_KHR_debug = glCaps.GL_KHR_debug;
        this.GL_AMD_debug_output = glCaps.GL_AMD_debug_output;
        this.GL_KHR_no_error = glCaps.GL_KHR_no_error;
        this.GL_EXT_debug_label = glCaps.GL_EXT_debug_label;
        this.GL_ARB_timer_query = glCaps.GL_ARB_timer_query;
        this.GL_KHR_parallel_shader_compile = glCaps.GL_KHR_parallel_shader_compile;
        this.GL_ARB_parallel_shader_compile = glCaps.GL_ARB_parallel_shader_compile;
        this.GL_ARB_multi_bind = glCaps.GL_ARB_multi_bind;
        this.GL_ARB_tessellation_shader = glCaps.GL_ARB_tessellation_shader;
        this.GL_ARB_shader_storage_buffer_object = glCaps.GL_ARB_shader_storage_buffer_object;
        this.GL_ARB_shader_image_load_store = glCaps.GL_ARB_shader_image_load_store;
        this.GL_EXT_shader_image_load_store = glCaps.GL_EXT_shader_image_load_store;
        this.GL_ARB_draw_buffers_blend = glCaps.GL_ARB_draw_buffers_blend;
        this.GL_NVX_gpu_memory_info = glCaps.GL_NVX_gpu_memory_info;
    }
}
