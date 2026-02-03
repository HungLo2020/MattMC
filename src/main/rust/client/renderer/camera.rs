use glam::{Mat4, Vec3};
use std::time::Instant;

pub struct Camera {
    start_time: Instant,
}

impl Camera {
    pub fn new() -> Self {
        Self {
            start_time: Instant::now(),
        }
    }

    pub fn get_view_matrix(&self) -> Mat4 {
        // Camera positioned at (0, 0, 3) looking at the origin
        Mat4::look_at_rh(
            Vec3::new(0.0, 0.0, 3.0),  // Camera position
            Vec3::new(0.0, 0.0, 0.0),  // Look at point
            Vec3::new(0.0, 1.0, 0.0),  // Up vector
        )
    }

    pub fn get_projection_matrix(&self, aspect_ratio: f32) -> Mat4 {
        // Vulkan-compatible perspective projection
        // Vulkan has Y-axis inverted and depth range [0, 1] (not [-1, 1] like OpenGL)
        let fov_y = 45.0_f32.to_radians();
        let near = 0.1;
        let far = 100.0;
        
        // Create perspective matrix for Vulkan
        let f = 1.0 / (fov_y / 2.0).tan();
        
        Mat4::from_cols_array(&[
            f / aspect_ratio, 0.0,  0.0,                           0.0,
            0.0,             -f,    0.0,                           0.0,  // Note: -f for Vulkan Y-flip
            0.0,              0.0,  far / (near - far),           -1.0,
            0.0,              0.0,  (near * far) / (near - far),   0.0,
        ])
    }

    pub fn get_model_matrix(&self) -> Mat4 {
        // Rotate the cube based on time elapsed
        let elapsed = self.start_time.elapsed().as_secs_f32();
        let rotation_y = elapsed * 0.5; // Rotate around Y axis
        let rotation_x = elapsed * 0.3; // Rotate around X axis

        Mat4::from_rotation_y(rotation_y) * Mat4::from_rotation_x(rotation_x)
    }

    pub fn get_mvp_matrix(&self, aspect_ratio: f32) -> Mat4 {
        self.get_projection_matrix(aspect_ratio) * self.get_view_matrix() * self.get_model_matrix()
    }
}
