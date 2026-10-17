//! Scoped decode owners and independently retained section/light handoffs.
use super::*;
use crate::world::level::lighting::layers::ffi::{projection, Projection};

/// # Safety
/// Release this unique result using decoded_release. Thread's last successful
/// decode is consumed; no Java pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_take_owned() -> *mut Decoded {
    super::super::ffi::take_decoded().map_or(std::ptr::null_mut(), |decoded| {
        Box::into_raw(Box::new(decoded))
    })
}
/// # Safety
/// One unique take_owned result, after all scoped readers finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_decoded_release(owner: *mut Decoded) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
/// # Safety
/// Owner stays live; length is one aligned writable u64. Returned bytes are
/// read-only and borrowed until decoded_release.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_decoded_root(
    owner: *const Decoded,
    length: *mut u64,
) -> *const u8 {
    if length.is_null() || length as usize % 8 != 0 {
        return std::ptr::null();
    }
    let Some(owner) = owner.as_ref() else {
        return std::ptr::null();
    };
    *length = owner.root.len() as u64;
    owner.root.as_ptr()
}
/// # Safety
/// Decoded owner is live; immutable CPU registry/default IDs describe the
/// canonical factory. Null declines without consuming or changing the decode.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrate(
    owner: *const Decoded,
    block_limit: i32,
    block_bits: i32,
    default_block: i32,
    biome_limit: i32,
    biome_bits: i32,
    default_biome: i32,
) -> *mut Hydrated {
    let Some(owner) = owner.as_ref() else {
        return std::ptr::null_mut();
    };
    let config = Config {
        block_limit: block_limit as u32,
        block_bits: block_bits as usize,
        default_block,
        biome_limit: biome_limit as u32,
        biome_bits: biome_bits as usize,
        default_biome,
    };
    hydrate(owner, &config).map_or(std::ptr::null_mut(), |rows| Box::into_raw(Box::new(rows)))
}
/// # Safety
/// One unique hydrate result after synchronous row exports finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrated_release(owner: *mut Hydrated) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
/// # Safety
/// Hydrated owner remains live for the call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrated_count(owner: *const Hydrated) -> i32 {
    owner.as_ref().map_or(-1, |v| v.rows.len() as i32)
}
/// # Safety
/// Live owner and disjoint aligned56-byte output, containing no unconsumed
/// previous handles. Output: y/flags, block owner, biome owner, block-light
/// owner/view, sky-light owner/view. Each nonzero pointer is one unique lease.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrated_row(
    owner: *const Hydrated,
    index: i32,
    output: *mut u64,
) -> i32 {
    hydrated_row(owner, index, output, true)
}

/// Primary light identities remain the actual decoded allocations. Public
/// SectionData facades need no eager independent light-generation projection.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrated_row_primary(
    owner: *const Hydrated, index: i32, output: *mut u64,
) -> i32 {
    hydrated_row(owner, index, output, false)
}

unsafe fn hydrated_row(owner: *const Hydrated, index: i32, output: *mut u64, views: bool) -> i32 {
    if output.is_null() || output as usize % 8 != 0 {
        return -1;
    }
    let Some(row) = owner.as_ref().and_then(|o| o.rows.get(index as usize)) else {
        return -1;
    };
    std::ptr::write_bytes(output, 0, 7);
    let ints = output.cast::<i32>();
    *ints = row.y;
    *ints.add(1) = row.flags;
    if let Some(blocks) = &row.blocks {
        *output.add(1) = Arc::into_raw(blocks.clone()) as u64;
    }
    if let Some(biomes) = &row.biomes {
        *output.add(2) = Arc::into_raw(biomes.clone()) as u64;
    }
    for (offset, layer) in [(3, &row.block_light), (5, &row.sky_light)] {
        if let Some(layer) = layer {
            *output.add(offset) = Arc::into_raw(layer.clone()) as u64;
            if views { *output.add(offset + 1) = projection(layer) as u64; }
        }
    }
    0
}
/// # Safety
/// Same56-byte row. Consumed pointers have been set to zero; all remaining
/// pointers are unique leases from hydrated_row. This releases/clears them.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_sections_hydrated_row_release(output: *mut u64) {
    if output.is_null() || output as usize % 8 != 0 {
        return;
    }
    if *output.add(1) != 0 {
        drop(Arc::from_raw(*output.add(1) as *const live::Owner));
    }
    if *output.add(2) != 0 {
        drop(Arc::from_raw(*output.add(2) as *const biomes::Owner));
    }
    for offset in [3, 5] {
        if *output.add(offset) != 0 {
            drop(Arc::from_raw(*output.add(offset) as *const Layer));
        }
        if *output.add(offset + 1) != 0 {
            drop(Box::from_raw(*output.add(offset + 1) as *mut Projection));
        }
    }
    std::ptr::write_bytes(output.add(1), 0, 6);
}
