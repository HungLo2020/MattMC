//! Static model cache, model selector, and meshing-state registration exports.

use super::*;

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_clear() -> i32 {
    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    cache.clear();
    drop(cache);
    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors.clear();
    drop(selectors);
    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    states.clear();
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_register(
    model_id: i32,
    quad_address: u64,
    quad_count: i32,
    quad_stride: i32,
) -> i32 {
    if model_id < 0 || quad_count < 0 || quad_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count > 0 && quad_address == 0 {
        return ERR_NULL_POINTER;
    }
    let quad_stride = match usize::try_from(quad_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if quad_stride != std::mem::size_of::<StaticModelQuadRecord>() {
        return ERR_INVALID_ARGUMENT;
    }

    let quads = if quad_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(
            quad_address as *const StaticModelQuadRecord,
            quad_count as usize,
        )
        .to_vec()
    };

    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut cache, model_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    cache[index] = Some(quads);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_model_selector_register(
    selector_id: i32,
    kind: i32,
    entry_address: u64,
    entry_count: i32,
    entry_stride: i32,
) -> i32 {
    if selector_id < 0 || entry_count < 0 || entry_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if entry_count > 0 && entry_address == 0 {
        return ERR_NULL_POINTER;
    }
    let entry_stride = match usize::try_from(entry_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if entry_stride != std::mem::size_of::<NativeModelSelectorEntry>() {
        return ERR_INVALID_ARGUMENT;
    }

    let entries = if entry_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(
            entry_address as *const NativeModelSelectorEntry,
            entry_count as usize,
        )
        .to_vec()
    };
    let total_weight = entries
        .iter()
        .filter(|entry| entry.weight > 0)
        .map(|entry| entry.weight)
        .sum();

    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut selectors, selector_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors[index] = Some(NativeModelSelector {
        kind,
        entries,
        total_weight,
    });
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_meshing_state_register(
    state_id: i32,
    selector_id: i32,
    flags: i32,
    material_bits: i32,
    pass_id: i32,
    block_emission: i32,
    render_type: i32,
    block_id: i32,
    fluid_material_bits: i32,
    fluid_pass_id: i32,
    fluid_block_id: i32,
    skip_group: i32,
    skip_mask: i32,
    fluid_type: i32,
    fluid_own_height: f32,
    fluid_falling: i32,
    offset_type: i32,
    max_horizontal_offset: f32,
    max_vertical_offset: f32,
    tint_type: i32,
    fluid_still_u0: f32,
    fluid_still_u1: f32,
    fluid_still_v0: f32,
    fluid_still_v1: f32,
    fluid_still_shrink: f32,
    fluid_flow_u0: f32,
    fluid_flow_u1: f32,
    fluid_flow_v0: f32,
    fluid_flow_v1: f32,
    fluid_flow_shrink: f32,
    fluid_overlay_u0: f32,
    fluid_overlay_u1: f32,
    fluid_overlay_v0: f32,
    fluid_overlay_v1: f32,
    fluid_overlay_shrink: f32,
    fluid_overlay_valid: i32,
) -> i32 {
    if state_id < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut states, state_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    states[index] = Some(NativeMeshingState {
        selector_id,
        flags,
        material_bits,
        pass_id,
        block_emission,
        render_type,
        block_id,
        fluid_material_bits,
        fluid_pass_id,
        fluid_block_id,
        skip_group,
        skip_mask,
        fluid_type,
        fluid_own_height,
        fluid_falling,
        offset_type,
        max_horizontal_offset,
        max_vertical_offset,
        tint_type,
        fluid_still: FluidSprite {
            u0: fluid_still_u0,
            u1: fluid_still_u1,
            v0: fluid_still_v0,
            v1: fluid_still_v1,
            shrink: fluid_still_shrink,
        },
        fluid_flow: FluidSprite {
            u0: fluid_flow_u0,
            u1: fluid_flow_u1,
            v0: fluid_flow_v0,
            v1: fluid_flow_v1,
            shrink: fluid_flow_shrink,
        },
        fluid_overlay: FluidSprite {
            u0: fluid_overlay_u0,
            u1: fluid_overlay_u1,
            v0: fluid_overlay_v0,
            v1: fluid_overlay_v1,
            shrink: fluid_overlay_shrink,
        },
        fluid_overlay_valid,
    });
    OK
}

/// Registers a state from rendering's own columns; the block facts (air,
/// solid render, occlusion, block entity, motion, emission, fluid and
/// offsets) come from the installed block registry. `flags` carries only the
/// render-owned bits; `control` says whether fluids are forced to Java and
/// whether the native producer supports this state's fluid. Returns
/// `ERR_INVALID_ARGUMENT` without a registry or for a state it cannot
/// describe; Java then registers the state with explicit facts.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_meshing_state_register_view(
    state_id: i32,
    selector_id: i32,
    flags: i32,
    material_bits: i32,
    pass_id: i32,
    render_type: i32,
    block_id: i32,
    fluid_material_bits: i32,
    fluid_pass_id: i32,
    fluid_block_id: i32,
    skip_group: i32,
    skip_mask: i32,
    tint_type: i32,
    control: i32,
    sprites: *const f32,
    fluid_overlay_valid: i32,
) -> i32 {
    if sprites.is_null() {
        return ERR_NULL_POINTER;
    }
    let s = std::slice::from_raw_parts(sprites, 15);
    let sprite = |i: usize| FluidSprite { u0: s[i], u1: s[i + 1], v0: s[i + 2], v1: s[i + 3], shrink: s[i + 4] };
    let render = RenderColumns {
        selector_id,
        flags,
        material_bits,
        pass_id,
        render_type,
        block_id,
        fluid_material_bits,
        fluid_pass_id,
        fluid_block_id,
        skip_group,
        skip_mask,
        tint_type,
        fluid_still: sprite(0),
        fluid_flow: sprite(5),
        fluid_overlay: sprite(10),
        fluid_overlay_valid,
    };
    let Some(registry) = crate::content::block::installed() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Some(state) = state_from_registry(registry, state_id, render, control) else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut states, state_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    states[index] = Some(state);
    OK
}

/// Verification: a registered state's fields. `ints` receives selector, flags,
/// material bits, pass, emission, render type, block ID, fluid material bits,
/// fluid pass, fluid block ID, skip group, skip mask, fluid type, falling,
/// offset type, tint type and overlay validity (17); `floats` receives fluid
/// height, maximum horizontal and vertical offsets, then the still, flow and
/// overlay sprites (18). Returns `OK`, or `ERR_INVALID_ARGUMENT` when the
/// state is not registered.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_meshing_state_snapshot(state_id: i32, ints: *mut i32, floats: *mut f32) -> i32 {
    if ints.is_null() || floats.is_null() {
        return ERR_NULL_POINTER;
    }
    let Ok(states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Some(s) = state_by_id(&states, state_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    let values = [s.selector_id, s.flags, s.material_bits, s.pass_id, s.block_emission, s.render_type, s.block_id, s.fluid_material_bits,
        s.fluid_pass_id, s.fluid_block_id, s.skip_group, s.skip_mask, s.fluid_type, s.fluid_falling, s.offset_type, s.tint_type,
        s.fluid_overlay_valid];
    std::slice::from_raw_parts_mut(ints, values.len()).copy_from_slice(&values);
    let mut f = vec![s.fluid_own_height, s.max_horizontal_offset, s.max_vertical_offset];
    for sprite in [s.fluid_still, s.fluid_flow, s.fluid_overlay] {
        f.extend([sprite.u0, sprite.u1, sprite.v0, sprite.v1, sprite.shrink]);
    }
    std::slice::from_raw_parts_mut(floats, f.len()).copy_from_slice(&f);
    OK
}
