use bytemuck::{Pod, Zeroable};
use vulkano::pipeline::graphics::vertex_input::Vertex;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Zeroable, Pod, Vertex)]
pub struct CubeVertex {
    #[format(R32G32B32_SFLOAT)]
    pub position: [f32; 3],
    #[format(R32G32B32_SFLOAT)]
    pub color: [f32; 3],
}

pub fn create_cube_vertices() -> Vec<CubeVertex> {
    // Create 36 vertices for 6 cube faces (each face = 2 triangles = 6 vertices)
    // Cube is centered at origin with size 1.0
    let vertices = vec![
        // Front face (red-ish)
        CubeVertex { position: [-0.5, -0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 0
        CubeVertex { position: [ 0.5, -0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 1
        CubeVertex { position: [ 0.5,  0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 2
        CubeVertex { position: [-0.5, -0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 3
        CubeVertex { position: [ 0.5,  0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 4
        CubeVertex { position: [-0.5,  0.5,  0.5], color: [1.0, 0.0, 0.0] },  // 5

        // Back face (green-ish)
        CubeVertex { position: [ 0.5, -0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 6
        CubeVertex { position: [-0.5, -0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 7
        CubeVertex { position: [-0.5,  0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 8
        CubeVertex { position: [ 0.5, -0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 9
        CubeVertex { position: [-0.5,  0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 10
        CubeVertex { position: [ 0.5,  0.5, -0.5], color: [0.0, 1.0, 0.0] },  // 11

        // Top face (blue-ish)
        CubeVertex { position: [-0.5,  0.5,  0.5], color: [0.0, 0.0, 1.0] },  // 12
        CubeVertex { position: [ 0.5,  0.5,  0.5], color: [0.0, 0.0, 1.0] },  // 13
        CubeVertex { position: [ 0.5,  0.5, -0.5], color: [0.0, 0.0, 1.0] },  // 14
        CubeVertex { position: [-0.5,  0.5,  0.5], color: [0.0, 0.0, 1.0] },  // 15
        CubeVertex { position: [ 0.5,  0.5, -0.5], color: [0.0, 0.0, 1.0] },  // 16
        CubeVertex { position: [-0.5,  0.5, -0.5], color: [0.0, 0.0, 1.0] },  // 17

        // Bottom face (yellow-ish)
        CubeVertex { position: [-0.5, -0.5, -0.5], color: [1.0, 1.0, 0.0] },  // 18
        CubeVertex { position: [ 0.5, -0.5, -0.5], color: [1.0, 1.0, 0.0] },  // 19
        CubeVertex { position: [ 0.5, -0.5,  0.5], color: [1.0, 1.0, 0.0] },  // 20
        CubeVertex { position: [-0.5, -0.5, -0.5], color: [1.0, 1.0, 0.0] },  // 21
        CubeVertex { position: [ 0.5, -0.5,  0.5], color: [1.0, 1.0, 0.0] },  // 22
        CubeVertex { position: [-0.5, -0.5,  0.5], color: [1.0, 1.0, 0.0] },  // 23

        // Right face (magenta-ish)
        CubeVertex { position: [ 0.5, -0.5,  0.5], color: [1.0, 0.0, 1.0] },  // 24
        CubeVertex { position: [ 0.5, -0.5, -0.5], color: [1.0, 0.0, 1.0] },  // 25
        CubeVertex { position: [ 0.5,  0.5, -0.5], color: [1.0, 0.0, 1.0] },  // 26
        CubeVertex { position: [ 0.5, -0.5,  0.5], color: [1.0, 0.0, 1.0] },  // 27
        CubeVertex { position: [ 0.5,  0.5, -0.5], color: [1.0, 0.0, 1.0] },  // 28
        CubeVertex { position: [ 0.5,  0.5,  0.5], color: [1.0, 0.0, 1.0] },  // 29

        // Left face (cyan-ish)
        CubeVertex { position: [-0.5, -0.5, -0.5], color: [0.0, 1.0, 1.0] },  // 30
        CubeVertex { position: [-0.5, -0.5,  0.5], color: [0.0, 1.0, 1.0] },  // 31
        CubeVertex { position: [-0.5,  0.5,  0.5], color: [0.0, 1.0, 1.0] },  // 32
        CubeVertex { position: [-0.5, -0.5, -0.5], color: [0.0, 1.0, 1.0] },  // 33
        CubeVertex { position: [-0.5,  0.5,  0.5], color: [0.0, 1.0, 1.0] },  // 34
        CubeVertex { position: [-0.5,  0.5, -0.5], color: [0.0, 1.0, 1.0] },  // 35
    ];

    vertices
}
