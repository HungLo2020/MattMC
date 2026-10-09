//! CPU ownership for built-in DH cloud motion, placement and culling.
//! API hooks and semantic world color queries remain in caller order.
mod culling;
pub(crate) mod ffi;
#[cfg(test)]
mod tests;

#[derive(Clone, Copy, Debug)]
pub struct Inputs {
    pub enabled: bool,
    pub now_millis: i64,
    pub speed: f32,
    pub camera: [f64; 3],
    pub look: [f32; 3],
    pub radius_chunks: i32,
    pub max_height: i32,
}
#[derive(Clone, Copy, Debug)]
pub struct Prepared {
    pub active: bool,
    // Disabled providers keep their previous origin/time; no publication.
    pub origin: Option<[f32; 3]>,
}
pub struct Group {
    width: i32,
    offset: [i32; 2],
    last_millis: i64,
    delta_x: f32,
    previous_color: u32,
}
impl Group {
    pub fn new(width: i32, offset: [i32; 2], last_millis: i64) -> Option<Self> {
        if width <= 0 || offset.iter().any(|v| !(-5..=5).contains(v)) {
            return None;
        }
        Some(Self {
            width,
            offset,
            last_millis,
            delta_x: 0.0,
            previous_color: 0xffffffff,
        })
    }
    pub fn prepare(&mut self, frame: Inputs) -> Prepared {
        if !frame.enabled {
            return Prepared {
                active: false,
                origin: None,
            };
        }
        // Preserve Java integer wrapping, float operation order and remainder.
        let delta_time = frame.now_millis.wrapping_sub(self.last_millis) as f32 / 1000.0;
        self.last_millis = frame.now_millis;
        self.delta_x -= frame.speed * delta_time;
        self.delta_x %= self.width as f32;
        let mut camera = [frame.camera[0] as i32, frame.camera[2] as i32];
        for value in &mut camera {
            if *value < 0 {
                *value = value.wrapping_sub(self.width);
            }
        }
        let instance = camera.map(|value| (value / self.width).wrapping_mul(self.width) as f32);
        let origin = [
            self.delta_x
                + self.offset[0].wrapping_mul(self.width) as f32
                + instance[0]
                + (self.width / 2) as f32,
            frame.max_height.wrapping_add(200) as f32,
            self.offset[1].wrapping_mul(self.width) as f32 + instance[1] + (self.width / 2) as f32,
        ];
        Prepared {
            active: !culling::culled(
                origin,
                self.width,
                self.offset,
                frame.camera,
                frame.look,
                frame.radius_chunks,
            ),
            origin: Some(origin),
        }
    }
    /// Called only after the active-provider Java semantic color query returns.
    /// Box API projections still apply their original current-color comparison.
    pub fn color_changed(&mut self, color: u32) -> bool {
        let changed = self.previous_color != color;
        self.previous_color = color;
        changed
    }
}
