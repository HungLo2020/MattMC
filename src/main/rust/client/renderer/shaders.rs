pub mod vertex_shader {
    vulkano_shaders::shader! {
        ty: "vertex",
        path: "src/main/rust/shaders/vertex.glsl",
    }
}

pub mod fragment_shader {
    vulkano_shaders::shader! {
        ty: "fragment",
        path: "src/main/rust/shaders/fragment.glsl",
    }
}
