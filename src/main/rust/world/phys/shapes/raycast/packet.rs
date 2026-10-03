//! One ordinary FFM buffer argument. Offsets match NativeVoxelRaycast.Scratch.
//! All views are disjoint; the kernel keeps no pointer or shape cache.
const COORDINATES: usize = 8192;
const RAY: usize = 14360;
const BOUNDS: usize = 14432;
pub(super) const BYTES: usize = 14504;
const OUTPUT: usize = BYTES - 24;
const _: () = assert!(OUTPUT == BOUNDS + 48);

/// # Safety
/// Packet has BYTES live writable bytes, aligned to eight. Its word prefix,
/// coordinate section, nine ray doubles and six optional bounds doubles are
/// initialized as documented by the scalar/grid entry points. No aliasing.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_ray_packet(packet: *mut u8, source_len: i32,
    nx: i32, ny: i32, nz: i32, coordinate_len: i32, cubes: i32) -> i32 {
    if packet.is_null() || packet as usize % 8 != 0 { return -1; }
    super::ffi::mattmc_voxel_ray_clip(packet.cast(), source_len, nx, ny, nz,
        packet.add(COORDINATES).cast(), coordinate_len, cubes,
        packet.add(RAY).cast(), packet.add(OUTPUT).cast())
}

/// # Safety
/// Same packet ownership/capacity contract. Ray and bounds sections initialized;
/// only output is written. Metadata sections are unused for a proven cuboid.
#[no_mangle]
pub unsafe extern "C" fn mattmc_voxel_ray_box_packet(packet: *mut u8) -> i32 {
    if packet.is_null() || packet as usize % 8 != 0 { return -1; }
    super::ffi::mattmc_voxel_ray_box(packet.add(RAY).cast(), packet.add(BOUNDS).cast(),
        packet.add(OUTPUT).cast())
}
