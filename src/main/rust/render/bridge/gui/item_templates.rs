//! Bounded immutable CPU item owners. Frame lookup acquires shared ownership;
//! a Java lease release cannot invalidate an already decoded frame.
use super::*;
use crate::render::guirender::mesh::template::{GuiItemPlacement, GuiItemTemplate};
use std::sync::{Arc, LazyLock, Mutex};

struct Templates {
    next: u64,
    owners: std::collections::HashMap<u64, Arc<GuiItemTemplate>>,
}
static TEMPLATES: LazyLock<Mutex<Templates>> = LazyLock::new(|| {
    Mutex::new(Templates {
        next: 1,
        owners: std::collections::HashMap::new(),
    })
});

pub(super) fn resolve(id: u64) -> GalResult<Arc<GuiItemTemplate>> {
    TEMPLATES
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .owners
        .get(&id)
        .cloned()
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::StaleHandle,
                "unknown or released GUI item template",
            )
        })
}

fn register(owner: GuiItemTemplate) -> GalResult<u64> {
    let mut templates = TEMPLATES.lock().unwrap_or_else(|error| error.into_inner());
    if templates.owners.len() >= 1024 {
        return Err(GalError::ffi(
            StatusCode::ResourceLimit,
            "GUI item owner capacity exhausted",
        ));
    }
    let id = templates.next;
    templates.next = id
        .checked_add(1)
        .filter(|next| *next <= i64::MAX as u64)
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::GenerationExhausted,
                "GUI item identities exhausted",
            )
        })?;
    templates.owners.insert(id, Arc::new(owner));
    Ok(id)
}

/// Copies semantic layers once; rejects nested template references. Output is
/// zero on failure. No context/GPU operation or worker join occurs here.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_gui_item_template_create(
    version: u32,
    batches: *const FfiGuiMeshBatchRequest,
    count: u64,
    selectors: *const u32,
    source_layers: u32,
    out: *mut u64,
) -> i32 {
    let result = (|| {
        let _budget = crate::render::bridge::memory::RequestBudget::begin();
        write_out(out, 0, "GUI item owner result")?;
        if version != FFI_ABI_VERSION || count == 0 || count > GUI_MESH_MAX_BATCHES as u64 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "invalid GUI item registration version or count",
            ));
        }
        let raw = FfiSlice {
            ptr: batches,
            count,
        };
        let input = read_slice(raw, false, "GUI item template batches")?;
        if input.iter().any(|batch| {
            batch.native_item_template != 0
                || batch.item_layer_foils.count != 0
                || !batch.item_layer_foils.ptr.is_null()
        }) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "nested GUI item template",
            ));
        }
        let selectors = read_slice(
            FfiSlice {
                ptr: selectors,
                count,
            },
            false,
            "GUI item foil selectors",
        )?;
        let owned =
            super::mesh::decode_gui_mesh_batches(raw, input[0].gui_width, input[0].gui_height)?;
        let owner = GuiItemTemplate::new(
            owned,
            selectors
                .iter()
                .map(|value| (*value != u32::MAX).then_some(*value as usize))
                .collect(),
            source_layers as usize,
        )?;
        let id = register(owner)?;
        // The output was validated before registry mutation.
        write_out(out, id, "GUI item owner result")
    })();
    match result {
        Ok(()) => StatusCode::Ok as i32,
        Err(error) => error.code as i32,
    }
}

#[no_mangle]
pub extern "C" fn mattmc_vulkanic_gal_gui_item_template_release(id: u64) {
    TEMPLATES
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .owners
        .remove(&id);
}

pub(super) unsafe fn append(
    batch: &FfiGuiMeshBatchRequest,
    owner: &GuiItemTemplate,
    output: &mut Vec<GuiMeshBatchRequest>,
    payload: &mut u64,
) -> GalResult<()> {
    let facts = read_slice(batch.item_layer_foils, false, "GUI item layer foils")?;
    let foils: Vec<_> = facts
        .iter()
        .map(|foil| {
            crate::render::shared::item_foil::StandardItemFoil::decode(
                foil.mode,
                foil.clock_millis,
                foil.speed,
                foil.strength,
            )
        })
        .collect::<GalResult<_>>()?;
    owner.append(
        &GuiItemPlacement {
            stratum: batch.stratum,
            sequence: batch.sequence,
            pose: batch.gui_pose,
            bounds: [batch.left, batch.top, batch.right, batch.bottom],
            extent: [batch.gui_width as u32, batch.gui_height as u32],
            clip_mode: batch.clip_mode,
            clip: [
                batch.clip_left,
                batch.clip_top,
                batch.clip_width,
                batch.clip_height,
            ],
            cache: crate::render::guirender::mesh::GuiItemCache::decode(
                batch.item_cache_identity,
                batch.item_cache_mode,
            )?,
        },
        &foils,
        output,
        payload,
    )
}

/// A template instance carries only placement, cache and source-layer facts.
/// Reject ambiguous inline metadata instead of silently ignoring it.
pub(super) fn validate_instance(batch: &FfiGuiMeshBatchRequest) -> GalResult<()> {
    if batch.layer_index != 0
        || batch.material_mode != 0
        || batch.lighting_mode != 0
        || batch.asset_id != 0
        || batch.alpha_cutoff != 0.
        || batch.reserved0 != 0
        || batch.model_transform != [0.; 16]
        || batch.render_width != 0
        || batch.render_height != 0
        || batch.guard_pixels != 0
        || batch.vertices.count != 0
        || !batch.vertices.ptr.is_null()
        || batch.indices.count != 0
        || !batch.indices.ptr.is_null()
        || batch.item_foil_mode != 0
        || batch.item_foil_clock_millis != 0
        || batch.item_foil_speed != 0.
        || batch.item_foil_strength != 0.
        || batch.item_raster_scale != 0
        || batch.decal_foil_mode != 0
        || batch.decal_model_pose != [0.; 16]
        || batch.decal_normal_pose != [0.; 9]
        || batch.block_item_scale != 0
        || batch.block_model_bounds != [0.; 6]
        || batch.block_item_layout != 0
        || batch.native_item_transform != 0
        || batch.native_item_transform_mode != 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI template conflicts with inline mesh semantics",
        ));
    }
    Ok(())
}
