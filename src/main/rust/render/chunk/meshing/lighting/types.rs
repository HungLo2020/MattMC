#[derive(Clone, Copy, Debug)]
pub(in crate::render::chunk::meshing) struct NativeQuadLight {
    pub(in crate::render::chunk::meshing) ao: [f32; 4],
    pub(in crate::render::chunk::meshing) lm: [i32; 4],
}
