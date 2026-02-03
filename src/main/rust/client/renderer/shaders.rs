pub mod vertex_shader {
    vulkano_shaders::shader! {
        ty: "vertex",
        src: r"
            #version 460

            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;

            layout(push_constant) uniform PushConstants {
                mat4 mvp;
            } push_constants;

            layout(location = 0) out vec3 frag_color;

            void main() {
                gl_Position = push_constants.mvp * vec4(position, 1.0);
                frag_color = color;
            }
        ",
    }
}

pub mod fragment_shader {
    vulkano_shaders::shader! {
        ty: "fragment",
        src: r"
            #version 460

            layout(location = 0) in vec3 frag_color;
            layout(location = 0) out vec4 out_color;

            void main() {
                out_color = vec4(frag_color, 1.0);
            }
        ",
    }
}
