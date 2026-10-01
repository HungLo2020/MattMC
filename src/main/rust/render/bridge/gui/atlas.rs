//! GUI atlas-reference updates: decoding and the entry point.

use super::*;

pub(crate) unsafe fn decode_gui_atlas_reference_update(
    request: *const FfiGuiAtlasReferenceUpdate,
    capabilities: BackendCapabilities,
) -> GalResult<(
    u64,
    Vec<crate::render::guirender::atlas_reference::GuiAtlasReference>,
)> {
    use crate::render::guirender::atlas_reference::{
        AcceptedAtlasIncarnation, GuiAtlasReference, MAX_GUI_ATLAS_REFERENCES,
    };
    let request = read_struct(request, "GUI atlas reference update")?;
    validate_header::<FfiGuiAtlasReferenceUpdate>(request.header)?;
    reject_unknown_feature_bits(request.negotiated_feature_bits)?;
    if request.negotiated_feature_bits & !capability_feature_bits(capabilities) != 0 {
        return Err(GalError::unsupported_feature(
            "unsupported GUI atlas reference feature bits",
        ));
    }
    if request.revision == 0 || request.references.count > MAX_GUI_ATLAS_REFERENCES as u64 {
        return Err(GalError::invalid_argument(
            "invalid GUI atlas reference revision or count",
        ));
    }
    let items = read_limited_slice(request.references, true, "GUI atlas references")?;
    let mut ids = std::collections::BTreeSet::new();
    let mut owned = Vec::with_capacity(items.len());
    for item in items {
        validate_item_size::<FfiGuiAtlasReference>(item.byte_size, "GUI atlas reference")?;
        let reference = GuiAtlasReference {
            asset_id: item.asset_id,
            atlas: AcceptedAtlasIncarnation {
                texture_id: item.texture_id,
                generation: item.atlas_generation,
                width: item.atlas_width,
                height: item.atlas_height,
            },
            x: item.x,
            y: item.y,
            width: item.width,
            height: item.height,
        };
        reference.validate()?;
        if !ids.insert(reference.asset_id) {
            return Err(GalError::invalid_argument(
                "duplicate GUI atlas reference identity",
            ));
        }
        owned.push(reference);
    }
    Ok((request.revision, owned))
}

/// Declaration transport only. No rendering capability is admitted here.
#[no_mangle]
pub unsafe extern "C" fn mattmc_vulkanic_gal_gui_update_atlas_references(
    context_id: u64,
    request: *const FfiGuiAtlasReferenceUpdate,
    status_out: *mut FfiStatusResult,
) -> i32 {
    with_registry_mut(|registry| {
        let Some(context) = registry.contexts.get_mut(&context_id) else {
            let error = GalError::ffi(
                StatusCode::StaleHandle,
                format!("unknown context id {context_id}"),
            );
            write_status_out(status_out, status_result_from_error(&error));
            return error.code as i32;
        };
        context.ffi_calls += 1;
        context.ffi_output_bytes = context
            .ffi_output_bytes
            .saturating_add(size_of::<FfiStatusResult>() as u64);
        let result = decode_gui_atlas_reference_update(request, context.gal.capabilities())
            .and_then(|(revision, references)| {
                context.ffi_input_bytes = context.ffi_input_bytes.saturating_add(
                    size_of::<FfiGuiAtlasReferenceUpdate>() as u64
                        + references.len() as u64 * size_of::<FfiGuiAtlasReference>() as u64,
                );
                context.gui_frontend.stage_owned_atlas_references(
                    &mut context.gal,
                    &context.world_primitive_frontend,
                    revision,
                    &references,
                )
            });
        match result {
            Ok(()) => {
                write_status_out(status_out, status_ok(context));
                StatusCode::Ok as i32
            }
            Err(error) => {
                set_last_error(context, &error);
                write_status_out(status_out, status_error(Some(context), &error));
                error.code as i32
            }
        }
    })
}
