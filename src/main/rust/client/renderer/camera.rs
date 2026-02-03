use glam::{Mat4, Vec3};

pub struct Camera {
    // Position in world space
    pub position: Vec3,
    // Rotation angles (pitch, yaw)
    pub pitch: f32,  // Up/down rotation (radians)
    pub yaw: f32,    // Left/right rotation (radians)
    // Movement speed
    pub move_speed: f32,
    pub look_sensitivity: f32,
}

impl Camera {
    pub fn new() -> Self {
        Self {
            // Start camera at a good viewing position
            position: Vec3::new(8.0, 80.0, 25.0),
            pitch: 0.0,
            yaw: 0.0,
            move_speed: 10.0,
            look_sensitivity: 0.002,
        }
    }

    /// Get the forward direction vector
    pub fn forward(&self) -> Vec3 {
        Vec3::new(
            self.yaw.sin() * self.pitch.cos(),
            -self.pitch.sin(),
            self.yaw.cos() * self.pitch.cos(),
        ).normalize()
    }

    /// Get the right direction vector
    pub fn right(&self) -> Vec3 {
        self.forward().cross(Vec3::Y).normalize()
    }

    /// Get the up direction vector
    pub fn up(&self) -> Vec3 {
        Vec3::Y
    }

    /// Update camera position based on input
    pub fn update_position(&mut self, delta_time: f32, forward: f32, right: f32, up: f32) {
        let move_amount = self.move_speed * delta_time;
        
        // Forward/backward movement (on the horizontal plane)
        let forward_dir = Vec3::new(
            self.yaw.sin(),
            0.0,
            self.yaw.cos(),
        ).normalize();
        self.position += forward_dir * forward * move_amount;
        
        // Right/left strafe
        self.position += self.right() * right * move_amount;
        
        // Up/down movement (absolute vertical)
        self.position.y += up * move_amount;
    }

    /// Update camera rotation based on mouse movement
    pub fn update_rotation(&mut self, delta_x: f32, delta_y: f32) {
        self.yaw += delta_x * self.look_sensitivity;
        self.pitch += delta_y * self.look_sensitivity;
        
        // Clamp pitch to prevent camera flipping
        self.pitch = self.pitch.clamp(-std::f32::consts::FRAC_PI_2 + 0.01, std::f32::consts::FRAC_PI_2 - 0.01);
    }

    pub fn get_view_matrix(&self) -> Mat4 {
        let target = self.position + self.forward();
        Mat4::look_at_rh(
            self.position,
            target,
            Vec3::Y,
        )
    }

    pub fn get_projection_matrix(&self, aspect_ratio: f32) -> Mat4 {
        // Vulkan-compatible perspective projection
        let fov_y = 70.0_f32.to_radians();
        let near = 0.1;
        let far = 1000.0;
        
        // Create perspective matrix for Vulkan
        let f = 1.0 / (fov_y / 2.0).tan();
        
        Mat4::from_cols_array(&[
            f / aspect_ratio, 0.0,  0.0,                           0.0,
            0.0,             -f,    0.0,                           0.0,  // Note: -f for Vulkan Y-flip
            0.0,              0.0,  far / (near - far),           -1.0,
            0.0,              0.0,  (near * far) / (near - far),   0.0,
        ])
    }

    pub fn get_mvp_matrix(&self, aspect_ratio: f32) -> Mat4 {
        // No model matrix needed - we're rendering the chunk at world origin
        self.get_projection_matrix(aspect_ratio) * self.get_view_matrix()
    }
}
