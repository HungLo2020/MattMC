//! Bounded particle CPU capture and frame transport. No GPU resources live here.
use crate::render::bridge::abi::FfiWorldParticleQuadRequest as Particle;

const LIMIT: usize = 262_144;
const FRAME_LIMIT: usize = 65_536;
const EMPTY: Particle = Particle {
    byte_size: std::mem::size_of::<Particle>() as u32,
    texture_id: 1, surface_kind: 0, material_index: 0,
    center: [0.; 3], rotation: [0., 0., 0., 1.], size: 0.,
    uv_bounds: [0., 1., 0., 1.], color_argb: 0, packed_light: 0,
};

#[repr(C)]
pub struct Buffer {
    // The single Java producer writes complete slots, then publishes count.
    data: *mut Particle,
    capacity: u32,
    count: u32,
    slots: Vec<Particle>,
}
impl Buffer {
    fn new() -> Self { Self { data: std::ptr::null_mut(), capacity: 0, count: 0, slots: Vec::new() } }
    fn grow(&mut self, minimum: usize) -> bool {
        if minimum > LIMIT || self.count as usize > self.slots.len() { return false; }
        if minimum <= self.slots.len() { return true; }
        let next = minimum.max(64).next_power_of_two().min(LIMIT);
        if self.slots.try_reserve(next - self.slots.len()).is_err() { return false; }
        self.slots.resize(next, EMPTY);
        self.data = self.slots.as_mut_ptr(); self.capacity = next as u32;
        true
    }
    fn active(&self) -> Option<&[Particle]> { self.slots.get(..self.count as usize) }
}

#[no_mangle]
pub extern "C" fn mattmc_particle_buffer_create() -> *mut Buffer { Box::into_raw(Box::new(Buffer::new())) }
/// # Safety
/// A live, exclusively borrowed owner created here; no concurrent views.
#[no_mangle]
pub unsafe extern "C" fn mattmc_particle_buffer_grow(owner: *mut Buffer, minimum: u32) -> i32 {
    owner.as_mut().map_or(0, |b| i32::from(b.grow(minimum as usize)))
}
/// # Safety
/// A live owner, with its initialized count published and no concurrent writes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_particle_buffer_snapshot(owner: *const Buffer) -> *mut Buffer {
    let Some(input) = owner.as_ref().and_then(Buffer::active) else { return std::ptr::null_mut(); };
    let mut result = Buffer::new();
    if result.slots.try_reserve_exact(input.len()).is_err() { return std::ptr::null_mut(); }
    result.slots.extend_from_slice(input);
    result.data = result.slots.as_mut_ptr(); result.capacity = input.len() as u32; result.count = input.len() as u32;
    Box::into_raw(Box::new(result))
}
/// # Safety
/// Release exactly once, after all users and borrowed views have finished.
#[no_mangle]
pub unsafe extern "C" fn mattmc_particle_buffer_release(owner: *mut Buffer) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

fn valid_quad(p: &Particle) -> bool {
    let q = p.rotation;
    let length = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3];
    let u = (p.uv_bounds[1]-p.uv_bounds[0]).abs();
    let v = (p.uv_bounds[3]-p.uv_bounds[2]).abs();
    p.center.iter().chain(p.rotation.iter()).chain(p.uv_bounds.iter()).all(|x| x.is_finite())
        && p.size.is_finite() && length.is_finite() && length > 1.0e-8
        && u.is_finite() && v.is_finite() && u > 0. && v > 0. && u <= 4096. && v <= 4096.
}
/// Copy a whole captured layer into the native frame after atomic validation.
/// # Safety
/// Distinct live owners, exclusive destination and immutable initialized source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_particle_buffer_append_layer(
    owner: *mut Buffer, source: *const Buffer, texture: u32, surface: u32, order: u32,
) -> i32 {
    if owner.is_null() || source.is_null() || std::ptr::eq(owner.cast_const(), source) { return -1; }
    let dst = &mut *owner;
    let Some(input) = (&*source).active() else { return -1; };
    if texture == 0 || surface > 4 || !input.iter().all(valid_quad) { return -2; }
    let first = dst.count as usize;
    if !dst.grow(first.saturating_add(input.len())) { return -3; }
    for (out, p) in dst.slots[first..first+input.len()].iter_mut().zip(input) {
        *out = *p; out.texture_id = texture; out.surface_kind = surface; out.material_index = order;
    }
    dst.count = (first+input.len()) as u32;
    input.len() as i32
}

/// Encode a frame using the material stream's compact-prefix mapping. The
/// original snapshot remains unchanged; malformed order leaves output untouched.
/// # Safety
/// Live immutable owner, prefix_count initialized u32s, output for count records.
#[no_mangle]
pub unsafe extern "C" fn mattmc_particle_buffer_encode(
    owner: *const Buffer, prefix: *const u32, prefix_count: u32, out: *mut Particle,
) -> i32 {
    let Some(input) = owner.as_ref().and_then(Buffer::active) else { return 0; };
    if prefix_count == 0 || prefix_count as usize > FRAME_LIMIT+1 || prefix.is_null()
        || (out.is_null() && !input.is_empty()) { return 0; }
    let prefix = std::slice::from_raw_parts(prefix, prefix_count as usize);
    let material_count = prefix.len()-1;
    if material_count+input.len() > FRAME_LIMIT || prefix[0] != 0
        || !prefix.windows(2).all(|w| w[1] >= w[0] && w[1]-w[0] <= 1) { return 0; }
    let mut previous = 0;
    for p in input {
        if p.material_index < previous || p.material_index as usize > material_count { return 0; }
        previous = p.material_index;
    }
    let modulated = material_count as u32 - prefix[material_count];
    for (i, p) in input.iter().enumerate() {
        let mut mapped = *p; mapped.material_index = modulated+prefix[p.material_index as usize];
        out.add(i).write(mapped);
    }
    1
}

#[cfg(test)]
mod tests;
