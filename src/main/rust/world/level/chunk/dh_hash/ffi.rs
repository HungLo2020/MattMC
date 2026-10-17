use super::*;
use super::super::inputs::Chunk;
use crate::world::level::lighting::dh::sources::Sources;
fn tagged(value: Option<i32>) -> i64 { value.map_or(0, |v| (1i64 << 32) | v as u32 as i64) }
unsafe fn aligned<'a, T>(pointer: *const T) -> Option<&'a T> {
    if pointer.is_null() || pointer as usize % std::mem::align_of::<T>() != 0 { None } else { Some(&*pointer) }
}
#[no_mangle]
pub extern "C" fn mattmc_dh_hash_domain_create(states: i32, biomes: i32) -> *mut Domain {
    Domain::new(states as usize, biomes as usize).map_or(std::ptr::null_mut(), |d| Box::into_raw(Box::new(d)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_hash_domain_register(domain: *const Domain, biome: i32, id: i32, text: *const u16, length: i32) -> i32 {
    let Some(domain) = aligned(domain) else { return 0; };
    if !(0..=1).contains(&biome) || id < 0 || !(0..=1_048_576).contains(&length) || length != 0 && aligned(text).is_none() { return 0; }
    let text = if length == 0 { &[] } else { std::slice::from_raw_parts(text, length as usize) };
    i32::from(domain.register(biome != 0, id as usize, text))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_hash_domain_release(domain: *mut Domain) { if !domain.is_null() { drop(Box::from_raw(domain)); } }
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_hash_sample(domain: *const Domain, chunk: *const Chunk, heights: *const Heightmaps) -> i64 {
    let (Some(domain), Some(chunk), Some(heights)) = (aligned(domain), aligned(chunk), aligned(heights)) else { return 0; };
    tagged(sample(Sections::Retained(chunk), chunk.min_y, heights, &domain.0.read().unwrap()))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_hash_finish(domain: *const Domain, chunk: *const Chunk, sources: *const Sources,
    positions: *const Position, count: i32, hash: i32) -> i64 {
    let (Some(domain), Some(chunk)) = (aligned(domain), aligned(chunk)) else { return 0; };
    let facts = domain.0.read().unwrap();
    if !sources.is_null() {
        let Some(sources) = aligned(sources) else { return 0; };
        if sources.min_y != chunk.min_y { return 0; }
        tagged(finish(Sections::Retained(chunk), chunk.min_y, &facts, hash, sources.positions.iter().map(|&index|
            Position { x: (index & 15) as i32, y: sources.min_y + (index / 256) as i32, z: ((index / 16) & 15) as i32 })))
    } else {
        if !(0..=1_048_576).contains(&count) || count != 0 && aligned(positions).is_none() { return 0; }
        let positions = if count == 0 { &[] } else { std::slice::from_raw_parts(positions, count as usize) };
        tagged(finish(Sections::Retained(chunk), chunk.min_y, &facts, hash, positions.iter().copied()))
    }
}
