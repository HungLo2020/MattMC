//! Private CPU ABI: existing input owners in, immutable retained columns out.
use super::*;
fn aligned<T>(p: *const T) -> bool {
    !p.is_null() && p as usize % std::mem::align_of::<T>() == 0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_columns_build(
    pairs: *const usize,
    count: i32,
    heights: *const Heightmaps,
    lights: *const Field,
    min_y: i32,
    chunk_x: i32,
    chunk_z: i32,
    compression: i32,
) -> *mut Columns {
    if !aligned(pairs)
        || !aligned(heights)
        || !aligned(lights)
        || !(1..=255).contains(&count)
        || !(0..=255).contains(&compression)
    {
        return std::ptr::null_mut();
    }
    let raw = std::slice::from_raw_parts(pairs, count as usize * 3);
    let mut sections = Vec::with_capacity(count as usize);
    let mut counts = Vec::with_capacity(count as usize);
    for p in raw.chunks_exact(3) {
        let b = p[0] as *const live::Owner;
        let q = p[1] as *const biomes::Owner;
        let c = p[2] as *const counters::Owner;
        if !aligned(b) || !aligned(q) || !aligned(c) {
            return std::ptr::null_mut();
        }
        sections.push((&*b, &*q));
        counts.push(&*c);
    }
    production(Input {
        sections: Sections::Columns(&sections, Some(&counts)), heights: &*heights, lights: &*lights,
        min_y, chunk_x, chunk_z, compression: compression as u8,
    })
}
/// Direct consumer of the retained block/counter/biome bindings.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_columns_from_chunk(
    owner: *const super::super::inputs::Chunk, heights: *const Heightmaps, lights: *const Field,
    chunk_x: i32, chunk_z: i32, compression: i32,
) -> *mut Columns {
    if !aligned(owner) || !aligned(heights) || !aligned(lights) || !(0..=255).contains(&compression) { return std::ptr::null_mut(); }
    production(Input {
        sections: Sections::Retained(&*owner), heights: &*heights, lights: &*lights,
        min_y: (*owner).min_y, chunk_x, chunk_z, compression: compression as u8,
    })
}
fn production(input: Input<'_>) -> *mut Columns {
    let Some(catalog) = crate::content::block::collision::installed() else { return std::ptr::null_mut(); };
    let Some(registry) = crate::content::block::installed() else { return std::ptr::null_mut(); };
    let facts = |id: u32| {
        if id as usize >= registry.state_count() { return None; }
        let dh = catalog.dh(id)?;
        let flags = registry.flags(crate::content::block::StateId(id as u16));
        let air = flags.contains(crate::content::block::StateFlags::AIR);
        Some(Facts { state: if air { 0 } else { id }, air, solid: dh.solid,
            liquid: flags.contains(crate::content::block::StateFlags::HAS_FLUID), opacity: dh.opacity })
    };
    build(input, facts).map_or(std::ptr::null_mut(), |value| std::sync::Arc::into_raw(std::sync::Arc::<Columns>::from(value)) as *mut Columns)
}
/// Ten address-sized metadata fields, valid until this owner's release:
/// points/count, offsets, materials/count, position, chunkX/Z, compression, hash.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_columns_view(owner: *const Columns, output: *mut usize) -> i32 {
    if !aligned(owner) || !aligned(output) {
        return 0;
    }
    let o = &*owner;
    std::slice::from_raw_parts_mut(output, 10).copy_from_slice(&[
        o.points.as_ptr() as usize,
        o.points.len(),
        o.offsets.as_ptr() as usize,
        o.materials.as_ptr() as usize,
        o.materials.len(),
        o.pos as usize,
        o.chunk_x as isize as usize,
        o.chunk_z as isize as usize,
        o.compression as usize,
        o.source_hash() as isize as usize,
    ]);
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_columns_release(owner: *mut Columns) {
    if !owner.is_null() {
        drop(std::sync::Arc::from_raw(owner));
    }
}
