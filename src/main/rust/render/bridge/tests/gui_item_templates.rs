use super::*;
use crate::render::bridge::gui::{
    mattmc_vulkanic_gal_gui_item_template_create as create,
    mattmc_vulkanic_gal_gui_item_template_release as release,
};

fn slice<T>(values: &[T]) -> FfiSlice<T> {
    FfiSlice {
        ptr: values.as_ptr(),
        count: values.len() as u64,
    }
}
fn empty<T>() -> FfiSlice<T> {
    FfiSlice {
        ptr: std::ptr::null(),
        count: 0,
    }
}
fn prototype<'a>(vertices: &'a [FfiGuiMeshVertex], indices: &'a [u32]) -> FfiGuiMeshBatchRequest {
    let mut batch = gui_mesh_batch_request(vertices, indices);
    batch.block_item_layout = 1;
    batch.block_item_scale = 2;
    batch.block_model_bounds = [0., 0., 0., 1., 1., 1.];
    batch.lighting_mode = 3;
    batch.render_width = 0;
    batch.render_height = 0;
    batch.guard_pixels = 0;
    batch
}
fn vertices() -> [FfiGuiMeshVertex; 3] {
    [
        FfiGuiMeshVertex {
            position: [0., 0., 0.],
            atlas_uv: [0., 0.],
            local_uv: [0., 0.],
            color_argb: u32::MAX,
            normal_packed: 0,
            source_face: 6,
            source_foil_type: 0,
        },
        FfiGuiMeshVertex {
            position: [1., 0., 0.],
            atlas_uv: [1., 0.],
            local_uv: [1., 0.],
            color_argb: u32::MAX,
            normal_packed: 0,
            source_face: 6,
            source_foil_type: 0,
        },
        FfiGuiMeshVertex {
            position: [0., 1., 0.],
            atlas_uv: [0., 1.],
            local_uv: [0., 1.],
            color_argb: u32::MAX,
            normal_packed: 0,
            source_face: 6,
            source_foil_type: 0,
        },
    ]
}
fn instance(id: u64, facts: &[FfiGuiItemLayerFoil]) -> FfiGuiMeshBatchRequest {
    let mut batch = gui_mesh_batch_request(&[], &[]);
    batch.layer_index = 0;
    batch.material_mode = 0;
    batch.lighting_mode = 0;
    batch.asset_id = 0;
    batch.alpha_cutoff = 0.;
    batch.model_transform = [0.; 16];
    batch.render_width = 0;
    batch.render_height = 0;
    batch.guard_pixels = 0;
    batch.vertices = empty();
    batch.indices = empty();
    batch.native_item_template = id;
    batch.item_layer_foils = slice(facts);
    batch
}
fn decode(batches: &[FfiGuiMeshBatchRequest]) -> GalResult<Vec<GuiMeshBatchRequest>> {
    unsafe { crate::render::bridge::gui::decode_gui_mesh_batches(slice(batches), 320, 180) }
}

#[test]
fn gui_item_templates_own_geometry_and_reject_released_or_ambiguous_instances() {
    let mut vertices = vertices();
    let batches = [prototype(&vertices, &[0, 1, 2])];
    let mut id = 0;
    assert_eq!(0, unsafe {
        create(
            FFI_ABI_VERSION,
            batches.as_ptr(),
            1,
            [u32::MAX].as_ptr(),
            1,
            &mut id,
        )
    });
    vertices[0].position = [77., 77., 77.];
    let facts = [FfiGuiItemLayerFoil {
        mode: 0,
        clock_millis: 0,
        speed: 0.,
        strength: 0.,
    }];
    let mut item = instance(id, &facts);
    item.sequence = 99;
    let acquired = decode(&[item]).unwrap();
    assert_eq!(99, acquired[0].sequence);
    assert_eq!([0., 0., 0.], acquired[0].vertices[0].position);
    item.asset_id = 7;
    assert_eq!(
        StatusCode::InvalidArgument,
        decode(&[item]).unwrap_err().code
    );
    item.asset_id = 0;
    release(id);
    assert_eq!(StatusCode::StaleHandle, decode(&[item]).unwrap_err().code);
    assert_eq!([0., 0., 0.], acquired[0].vertices[0].position);
    let mut next = 0;
    assert_eq!(0, unsafe {
        create(
            FFI_ABI_VERSION,
            batches.as_ptr(),
            1,
            [u32::MAX].as_ptr(),
            1,
            &mut next,
        )
    });
    assert_ne!(id, next);
    release(next);
}

#[test]
fn gui_item_templates_expand_mixed_frames_and_refresh_independent_source_clocks() {
    let vertices = vertices();
    let base = prototype(&vertices, &[0, 1, 2]);
    let foil = |layer| {
        let mut batch = base;
        batch.layer_index = layer;
        batch.material_mode = 4;
        batch.lighting_mode = 1;
        batch.item_foil_mode = 1;
        batch.item_foil_clock_millis = 3;
        batch.item_foil_speed = 1.;
        batch.item_foil_strength = 1.;
        batch
    };
    let batches = [base, foil(1), foil(2)];
    let mut id = 0;
    assert_eq!(0, unsafe {
        create(
            FFI_ABI_VERSION,
            batches.as_ptr(),
            3,
            [u32::MAX, 1, 2].as_ptr(),
            3,
            &mut id,
        )
    });
    let mut facts = [
        FfiGuiItemLayerFoil {
            mode: 0,
            clock_millis: 0,
            speed: 0.,
            strength: 0.,
        },
        FfiGuiItemLayerFoil {
            mode: 1,
            clock_millis: 17,
            speed: 0.25,
            strength: 0.5,
        },
        FfiGuiItemLayerFoil {
            mode: 1,
            clock_millis: 19,
            speed: 0.75,
            strength: 1.,
        },
    ];
    let item = instance(id, &facts);
    let mut inline = base;
    inline.sequence = 100;
    let decoded = decode(&[item, inline]).unwrap();
    assert_eq!(4, decoded.len());
    assert_eq!(17, decoded[1].item_foil.unwrap().clock_millis);
    assert_eq!(19, decoded[2].item_foil.unwrap().clock_millis);
    assert_eq!(100, decoded[3].sequence);
    facts[1].speed = 2.;
    assert!(decode(&[item]).is_err());
    facts[1].speed = 0.25;
    assert_eq!(
        StatusCode::LengthOverflow,
        decode(&vec![item; GUI_MESH_MAX_BATCHES / 3 + 1])
            .unwrap_err()
            .code
    );
    release(id);
}

#[test]
fn gui_item_templates_registration_is_transactional_and_layouts_are_explicit() {
    let vertices = vertices();
    let batches = [prototype(&vertices, &[0, 1, 2])];
    let mut id = 91;
    assert_eq!(StatusCode::InvalidArgument as i32, unsafe {
        create(
            FFI_ABI_VERSION - 1,
            batches.as_ptr(),
            1,
            [u32::MAX].as_ptr(),
            1,
            &mut id,
        )
    });
    assert_eq!(0, id);
    assert_eq!(StatusCode::InvalidArgument as i32, unsafe {
        create(
            FFI_ABI_VERSION,
            batches.as_ptr(),
            1,
            [0].as_ptr(),
            1,
            &mut id,
        )
    });
    assert_eq!(0, id);
    let layout = crate::render::bridge::layout::layout_for_struct(97).unwrap();
    assert_eq!(44, layout.field_count);
    assert_eq!(
        std::mem::offset_of!(FfiGuiMeshBatchRequest, native_item_template) as u32,
        layout.field_offsets[42]
    );
    assert_eq!(
        std::mem::offset_of!(FfiGuiMeshBatchRequest, item_layer_foils) as u32,
        layout.field_offsets[43]
    );
    let foil = crate::render::bridge::layout::layout_for_struct(118).unwrap();
    assert_eq!(4, foil.field_count);
    assert_eq!(
        std::mem::size_of::<FfiGuiItemLayerFoil>() as u32,
        foil.byte_size
    );
}
