//! CPU ownership ABI for one complete lighting pass and independent outputs.
use super::*;
#[repr(C)]
pub struct ChunkInput {
    slot: i32,
    min_y: i32,
    count: i32,
    reserved: i32,
    owners: *const usize,
    heights: *const Heightmaps,
    previous: *const Field,
    sources: *const sources::Sources,
}
pub struct Batch {
    fields: Vec<(usize, Option<Box<Field>>)>,
    iterations: usize,
}
fn aligned<T>(pointer: *const T) -> bool {
    !pointer.is_null() && pointer as usize % std::mem::align_of::<T>() == 0
}
/// Inputs, height fields and previous light owners remain pinned for this call.
/// Returns null before publication for unsupported input. No Java callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_lighting_build(
    inputs: *const ChunkInput,
    count: i32,
    order: *const i8,
    order_len: i32,
    max_sky: i32,
    flags: i32,
) -> *mut Batch {
    if !aligned(inputs)
        || !(1..=9).contains(&count)
        || !(0..=1024).contains(&order_len)
        || order.is_null()
        || !(0..=15).contains(&max_sky)
        || !(0..=3).contains(&flags)
    {
        return std::ptr::null_mut();
    }
    let Some(catalog) = crate::content::block::collision::installed() else {
        return std::ptr::null_mut();
    };
    let Some(registry) = crate::content::block::installed() else {
        return std::ptr::null_mut();
    };
    let descriptors = std::slice::from_raw_parts(inputs, count as usize);
    let mut owners = Vec::with_capacity(count as usize);
    let mut counts = Vec::with_capacity(count as usize);
    for input in descriptors {
        if !(0..9).contains(&input.slot)
            || !(1..=256).contains(&input.count)
            || !(0..=1).contains(&input.reserved)
            || !aligned(input.owners)
            || !aligned(input.heights)
            || (!input.previous.is_null() && !aligned(input.previous))
            || (!input.sources.is_null() && !aligned(input.sources))
        {
            return std::ptr::null_mut();
        }
        if input.reserved == 1 {
            let chunk = &*(input.owners as *const crate::world::level::chunk::inputs::Chunk);
            if chunk.min_y != input.min_y || Sections::Retained(chunk).len() != input.count as usize {
                return std::ptr::null_mut();
            }
            owners.push(Vec::new()); counts.push(Vec::new());
            continue;
        }
        let pointers = std::slice::from_raw_parts(input.owners, input.count as usize * 2);
        if pointers.chunks_exact(2).any(|p| {
            !aligned(p[0] as *const live::Owner) || !aligned(p[1] as *const counters::Owner)
        }) {
            return std::ptr::null_mut();
        }
        owners.push(
            pointers
                .chunks_exact(2)
                .map(|p| &*(p[0] as *const live::Owner))
                .collect::<Vec<_>>(),
        );
        counts.push(
            pointers
                .chunks_exact(2)
                .map(|p| &*(p[1] as *const counters::Owner))
                .collect::<Vec<_>>(),
        );
    }
    let inputs = descriptors
        .iter()
        .zip(&owners)
        .zip(&counts)
        .map(|((i, owners), counts)| Input {
            slot: i.slot as usize,
            sections: if i.reserved == 1 {
                Sections::Retained(&*(i.owners as *const crate::world::level::chunk::inputs::Chunk))
            } else { Sections::Blocks(owners, Some(counts)) },
            heights: &*i.heights,
            min_y: i.min_y,
            previous: i.previous.as_ref(),
            cached_sources: i.sources.as_ref(),
        })
        .collect::<Vec<_>>();
    let emission = |id| {
        ((id as usize) < registry.state_count())
            .then(|| registry.emission(crate::content::block::StateId(id as u16)))
    };
    let Some(result) = build(
        &inputs,
        std::slice::from_raw_parts(order, order_len as usize),
        catalog,
        emission,
        max_sky as u8,
        flags & 1 != 0,
        flags & 2 != 0,
    ) else {
        return std::ptr::null_mut();
    };
    Box::into_raw(Box::new(Batch {
        fields: result
            .fields
            .into_iter()
            .map(|(s, f)| (s, Some(Box::new(f))))
            .collect(),
        iterations: result.iterations,
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_lighting_take(batch: *mut Batch, slot: i32) -> *mut Field {
    let Some(batch) = batch.as_mut() else {
        return std::ptr::null_mut();
    };
    batch
        .fields
        .iter_mut()
        .find(|(s, _)| *s == slot as usize)
        .and_then(|(_, f)| f.take())
        .map_or(std::ptr::null_mut(), Box::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_lighting_iterations(batch: *const Batch) -> i64 {
    batch.as_ref().map_or(-1, |b| b.iterations as i64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_lighting_release(batch: *mut Batch) {
    if !batch.is_null() {
        drop(Box::from_raw(batch));
    }
}
/// Ten address-sized CPU metadata values: block data/length/offsets,
/// sky data/length/offsets, section count, sources/count, minimum Y.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_light_field_view(field: *const Field, out: *mut usize) -> i32 {
    if !aligned(field) || !aligned(out) {
        return -1;
    }
    let f = &*field;
    std::slice::from_raw_parts_mut(out, 10).copy_from_slice(&[
        f.block.data.as_ptr() as usize,
        f.block.data.len(),
        f.block.offsets.as_ptr() as usize,
        f.sky.data.as_ptr() as usize,
        f.sky.data.len(),
        f.sky.offsets.as_ptr() as usize,
        f.block.offsets.len(),
        f.sources.as_ref().map_or(0, |s| s.as_ptr() as usize),
        f.sources.as_ref().map_or(usize::MAX, |s| s.len()),
        f.min_y as usize,
    ]);
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_light_field_release(field: *mut Field) {
    if !field.is_null() {
        drop(Box::from_raw(field));
    }
}

/// Inspect one section's retained policy state: its per-level counts, then
/// its constant. Zero means the section was never allocated; one means copied.
/// (Java no longer copies fields into mutable storage; tests use this.)
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_light_section_meta(
    field: *const Field,
    section: i32,
    sky: i32,
    out: *mut i16,
) -> i32 {
    if !aligned(field) || !aligned(out) || section < 0 || !(0..=1).contains(&sky) {
        return -1;
    }
    let f = &*field;
    let metadata = if sky == 1 { &f.sky_meta } else { &f.block_meta };
    let Some(entry) = metadata.get(section as usize) else {
        return -1;
    };
    let Some(meta) = entry else {
        return 0;
    };
    let output = std::slice::from_raw_parts_mut(out, 17);
    output[..16].copy_from_slice(&meta.counts);
    output[16] = i16::from(meta.constant);
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn transferred_cpu_field_and_source_views_survive_batch_destruction() {
        let mut cells = vec![0xf0; 4096];
        cells[9] = 0xf7;
        let field = Field::new(-64, &cells, Arc::from([9]));
        let batch = Box::into_raw(Box::new(Batch {
            fields: vec![
                (4, Some(Box::new(field))),
                (5, Some(Box::new(Field::new(-64, &cells, Arc::from([]))))),
            ],
            iterations: 129,
        }));
        unsafe {
            assert_eq!(mattmc_dh_lighting_iterations(batch), 129);
            let field = mattmc_dh_lighting_take(batch, 4);
            assert!(!field.is_null());
            assert!(mattmc_dh_lighting_take(batch, 4).is_null());
            mattmc_dh_lighting_release(batch);
            let mut view = [0usize; 10];
            assert_eq!(mattmc_dh_light_field_view(field, view.as_mut_ptr()), 1);
            assert_eq!(view[1], 2048);
            assert_eq!(view[4], 0);
            assert_eq!(view[6], 1);
            assert_eq!(view[8], 1);
            assert_eq!(view[9] as i32, -64);
            assert_eq!(*(view[7] as *const u32), 9);
            assert_eq!(*(view[2] as *const i32), 0);
            assert_eq!(*(view[5] as *const i32), -16);
            assert_eq!((*field).expand(), cells);
            let mut metadata = [0i16; 17];
            assert_eq!(
                mattmc_dh_light_section_meta(field, 0, 0, metadata.as_mut_ptr()),
                1
            );
            assert_eq!(metadata[0], 4095);
            assert_eq!(metadata[7], 1);
            assert_eq!(metadata[16], -1);
            assert_eq!(
                mattmc_dh_light_section_meta(field, 0, 1, metadata.as_mut_ptr()),
                1
            );
            assert_eq!(metadata[15], 4096);
            assert_eq!(metadata[16], 15);
            assert_eq!(
                mattmc_dh_light_section_meta(field, -1, 1, metadata.as_mut_ptr()),
                -1
            );
            assert_eq!(
                mattmc_dh_light_section_meta(field, 1, 1, metadata.as_mut_ptr()),
                -1
            );
            assert_eq!(
                mattmc_dh_light_section_meta(field, 0, 2, metadata.as_mut_ptr()),
                -1
            );
            assert_eq!(
                mattmc_dh_light_section_meta(field, 0, 1, std::ptr::null_mut()),
                -1
            );
            let empty = Field::new(-64, &[0; 4096], Arc::from([]));
            metadata.fill(123);
            assert_eq!(
                mattmc_dh_light_section_meta(&empty, 0, 0, metadata.as_mut_ptr()),
                0
            );
            assert_eq!(
                metadata, [123; 17],
                "Never-allocated policy leaves output untouched"
            );
            let sources = sources::mattmc_dh_light_field_sources(field);
            mattmc_dh_light_field_release(field);
            let mut source_view = [0usize; 3];
            assert_eq!(
                sources::mattmc_dh_sources_view(sources, source_view.as_mut_ptr()),
                1
            );
            assert_eq!(source_view[1], 1);
            assert_eq!(source_view[2] as i32, -64);
            assert_eq!(*(source_view[0] as *const u32), 9);
            sources::mattmc_dh_sources_release(sources);
            assert!(
                mattmc_dh_lighting_build(std::ptr::null(), 1, std::ptr::null(), 0, 15, 3).is_null()
            );
        }
    }
}
