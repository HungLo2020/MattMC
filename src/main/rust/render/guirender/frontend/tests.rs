use crate::render::guirender::frontend::*;
use crate::render::vulkanic::test_support::{vulkan_capabilities, vulkan_gal};
use crate::render::vulkanic::commands::ClearColor;
use crate::render::vulkanic::frame::{FrameSurfaceDesc, PresentMode};
use crate::render::guirender::mesh::{
    GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshVertex,
};
use crate::render::vulkanic::handles::HandleKind;
use crate::render::vulkanic::resources::{
    Extent3d, FrameTargetDesc, RenderTargetDesc, TextureFormat,
};

#[test]
fn blur_boundary_plan_partitions_node_phase_orders_without_backend_state() {
    let plan = plan_gui_blur_boundary(2, [0, 1, 2, 5, 6, 100]).unwrap();
    assert_eq!(2, plan.boundary_stratum);
    assert_eq!(4, plan.before_count);
    assert_eq!(2, plan.after_count);
}

#[test]
fn blur_boundary_plan_rejects_negative_and_overflowing_boundaries() {
    assert!(plan_gui_blur_boundary(-1, []).is_err());
    assert!(plan_gui_blur_boundary(i32::MAX, []).is_err());
}

#[test]
fn gui_draw_receipt_rejects_semantics_without_rust_draw() {
    let missing = GuiFrontend::require_gui_draw_receipt(
        &GuiSubmitStats {
            sprite_count: 1,
            ..GuiSubmitStats::default()
        },
        &[],
    )
    .unwrap_err()
    .to_string();
    assert!(
        missing.contains("GUI source writer recorded 1 semantic items but no draw operations")
    );

    GuiFrontend::require_gui_draw_receipt(
        &GuiSubmitStats {
            affine_quad_count: 1,
            ..GuiSubmitStats::default()
        },
        &[CommandOp::Draw {
            vertices: 3,
            instances: 1,
        }],
    )
    .expect("a Rust GUI draw should satisfy the semantic receipt");
}

/// Exercises the same dynamic raw-image, uniform, indexed-draw, and
/// `BlendMode::InverseSrcColorModulate` path used by the whole-frame HUD.  A pipeline
/// descriptor assertion cannot catch a later frontend regression that
/// loses the loaded target or samples the raw mask as black.
#[test]
fn vulkan_dynamic_vignette_affine_quad_darkens_the_loaded_target() {
    let Some(bytes) = vulkan_gui_sample(
        GUI_VIGNETTE_BLIT_STRATUM,
        vec![191, 191, 191, 255],
        0.0,
        1.0,
        None,
    ) else {
        return;
    };
    // 0.8 * (1 - 191 / 255) is approximately 51/255.
    for channel in &bytes[..3] {
        assert!(
            (*channel as i16 - 51).abs() <= 1,
            "unexpected GUI vignette: {bytes:?}"
        );
    }
}

#[test]
fn vulkan_gui_sampler_preserves_fractional_uv_intervals() {
    // At the destination pixel center, U=.275 selects texel 1 in a
    // four-texel nearest-filtered image. Rounding the region origin and
    // extent before interpolating incorrectly selects texel 0.
    let Some(bytes) = vulkan_gui_sample(
        GUI_OPAQUE_BLIT_STRATUM,
        vec![
            255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 255, 255,
        ],
        0.1,
        0.45,
        None,
    ) else {
        return;
    };
    assert_eq!(&[0, 255, 0, 255], bytes.as_slice());
}

#[test]
fn vulkan_gui_tiles_sample_repeated_atlas_region_and_partial_last_tile() {
    use crate::render::guirender::tiling::GuiTileGeometry;
    // Five logical units contain two full 2-unit tiles and one half tile.
    // First sample the second full tile, then translate to sample the last
    // partial tile. Both centers are .25 of a full tile into the same atlas
    // interval [.1,.9], so U=.3 selects green, not the unstaged neighbor.
    for translation in [0.0, -0.4] {
        let Some(bytes) = vulkan_gui_sample(
            GUI_OPAQUE_BLIT_STRATUM,
            vec![
                255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 255, 255,
            ],
            0.1,
            0.9,
            Some(GuiTileGeometry {
                bounds: [0, 0, 5, 1],
                tile_extent: [2, 1],
                uv: [0.1, 0.0, 0.9, 1.0],
                pose: [0.2, 0.0, 0.0, 1.0, translation, 0.0],
            }),
        ) else {
            return;
        };
        assert_eq!(
            &[0, 255, 0, 255],
            bytes.as_slice(),
            "translation={translation}"
        );
    }
}

fn gui_tiled_request() -> GuiTiledQuadRequest {
    GuiTiledQuadRequest {
        geometry: crate::render::guirender::tiling::GuiTileGeometry {
            bounds: [7, 11, 71, 43],
            tile_extent: [32, 32],
            uv: [0.25, 0.5, 0.75, 1.0],
            pose: [1.0, 0.0, 0.0, 1.0, 0.0, 0.0],
        },
        stratum: GUI_OPAQUE_BLIT_STRATUM,
        asset_id: 41,
        z: 0.25,
        color_argb: 0xffaabbcc,
        gui_extent: [320, 180],
        projection_extent: [319.75, 179.5],
        sequence: 11,
        clip: Some([1, 2, 100, 50]),
    }
}

#[test]
fn gui_tiled_command_preserves_parent_identity_material_clip_and_projection() {
    let request = gui_tiled_request();
    let quads = lower_tiled_request(request.clone()).unwrap();
    assert_eq!(2, quads.len());
    for quad in quads {
        assert_eq!(request.sequence, quad.sequence);
        assert_eq!(request.stratum, quad.stratum);
        assert_eq!(request.asset_id, quad.asset_id);
        assert_eq!(request.z, quad.z);
        assert_eq!(request.color_argb, quad.color_argb);
        assert_eq!(request.projection_extent, quad.projection_extent);
        assert_eq!(1, quad.clip_mode);
        assert_eq!(
            [1, 2, 100, 50],
            [
                quad.clip_left,
                quad.clip_top,
                quad.clip_width,
                quad.clip_height
            ]
        );
    }
    let mut invalid = request;
    invalid.clip = Some([319, 0, 2, 1]);
    assert!(lower_tiled_request(invalid).is_err());
}

#[test]
fn gui_tiled_children_do_not_consume_or_collide_with_other_parent_sequences() {
    let request = gui_tiled_request();
    let mut ordinary = lower_tiled_request(request.clone()).unwrap().remove(0);
    ordinary.sequence = 12;
    let ordered = order_gui_requests_with_tiles(
        Vec::new(),
        vec![ordinary.clone()],
        Vec::new(),
        vec![request.clone()],
    )
    .unwrap();
    let GuiFrameRequest::AffineBatch(batch) = &ordered[0] else {
        panic!("expected compatible batch")
    };
    assert_eq!(
        vec![11, 11, 12],
        batch.iter().map(|q| q.sequence).collect::<Vec<_>>()
    );
    ordinary.sequence = 11;
    assert!(order_gui_requests_with_tiles(
        Vec::new(),
        vec![ordinary],
        Vec::new(),
        vec![request.clone()]
    )
    .is_err());
    let mut maximum = request;
    maximum.sequence = u64::MAX;
    assert!(
        order_gui_requests_with_tiles(Vec::new(), Vec::new(), Vec::new(), vec![maximum])
            .is_ok()
    );
}

#[test]
fn gui_tiled_frame_preflight_bounds_the_sum_before_expansion() {
    let mut request = gui_tiled_request();
    request.geometry.bounds = [0, 0, 4096, 4096];
    request.geometry.uv = [0.0, 0.0, 1.0, 1.0];
    let four = vec![request.clone(); 4];
    assert_eq!(
        GUI_MAX_EXPANDED_AFFINE_QUADS,
        preflight_tiled_affine_count(&four, 0).unwrap()
    );
    assert!(preflight_tiled_affine_count(&four, 1).is_err());
    assert!(preflight_tiled_affine_count(&vec![request; 5], 0).is_err());
    assert!(preflight_tiled_affine_count(&[], usize::MAX).is_err());
}

fn vulkan_gui_sample(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
) -> Option<Vec<u8>> {
    vulkan_gui_sample_with_effect(stratum, pixels, u0, u1, tiles, 1, false)
}

#[test]
fn vulkan_invert_post_effect_preserves_asymmetric_framebuffer_rows() {
    let Some(bytes) = vulkan_gui_sample_with_effect(
        GUI_OPAQUE_BLIT_STRATUM,
        vec![20, 60, 100, 255, 180, 220, 240, 255],
        0.0,
        1.0,
        None,
        2,
        true,
    ) else {
        return;
    };
    let expected = [192u8, 168, 144, 255, 96, 72, 60, 255];
    assert_eq!(8, bytes.len());
    for (actual, expected) in bytes.iter().zip(expected) {
        assert!(
            (*actual as i16 - expected as i16).abs() <= 1,
            "inversion must preserve source row order and vanilla 0.8 mix: {bytes:?}"
        );
    }
}

fn vulkan_gui_sample_with_effect(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
    height: u32,
    invert: bool,
) -> Option<Vec<u8>> {
    vulkan_gui_sample_with_reused_image(
        stratum, pixels, u0, u1, tiles, height, invert, false, None, false,
    )
}

#[test]
fn vulkan_gui_reused_image_preserves_opaque_pixels_after_alpha_preparation() {
    let bytes = vulkan_gui_sample_with_reused_image(
        GUI_OPAQUE_BLIT_STRATUM,
        vec![17, 83, 211, 255],
        0.0,
        1.0,
        None,
        1,
        false,
        true,
        None,
        false,
    )
    .expect("Vulkan is required for reused GUI image regression");
    assert_eq!(bytes, vec![17, 83, 211, 255]);
}

#[test]
fn vulkan_gui_private_atlas_binding_samples_the_declared_region_without_copied_pixels() {
    let bytes = vulkan_gui_sample_with_reused_image(
        1,
        vec![
            255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 0, 255,
        ],
        0.0,
        1.0,
        None,
        2,
        false,
        false,
        Some([1, 0, 1, 2]),
        false,
    )
    .expect("Vulkan required for owner-backed atlas sampling regression");
    assert_eq!(
        bytes,
        vec![0, 255, 0, 255, 255, 255, 0, 255],
        "sample only the right atlas column and preserve semantic row orientation"
    );
}

#[test]
fn vulkan_mixed_gui_scheduler_preserves_raw_atlas_raw_blend_order() {
    let bytes = vulkan_gui_sample_with_reused_image(
        1,
        vec![
            255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 0, 255,
        ],
        0.0,
        1.0,
        None,
        2,
        false,
        false,
        Some([1, 0, 1, 2]),
        true,
    )
    .expect("Vulkan required for mixed GUI ownership regression");
    assert_eq!(bytes, vec![0, 127, 128, 255, 127, 127, 128, 255]);
}

#[test]
fn vulkan_mixed_gui_scheduler_tiles_local_coordinates_inside_the_atlas_region() {
    use crate::render::guirender::tiling::GuiTileGeometry;
    let bytes = vulkan_gui_sample_with_reused_image(
        1,
        vec![
            255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 255, 255,
        ],
        0.0,
        1.0,
        Some(GuiTileGeometry {
            bounds: [0, 0, 5, 1],
            tile_extent: [2, 1],
            uv: [0.0, 0.0, 1.0, 1.0],
            pose: [0.2, 0.0, 0.0, 1.0, 0.0, 0.0],
        }),
        1,
        false,
        false,
        Some([1, 0, 2, 1]),
        false,
    )
    .expect("Vulkan required for tiled GUI atlas regression");
    assert_eq!(bytes, vec![0, 255, 0, 255]);
}

fn vulkan_gui_sample_with_reused_image(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
    height: u32,
    invert: bool,
    prewarm_alpha: bool,
    atlas_region: Option<[u32; 4]>,
    mixed: bool,
) -> Option<Vec<u8>> {
    vulkan_gui_sample_with_blur(
        stratum,
        pixels,
        u0,
        u1,
        tiles,
        height,
        invert,
        prewarm_alpha,
        atlas_region,
        mixed,
        None,
    )
}

#[test]
fn vulkan_owned_atlas_draws_on_both_sides_of_blur_boundary() {
    for stratum in [0, 100] {
        let pixels = vec![255, 0, 0, 255, 0, 255, 0, 255];
        let actual = vulkan_gui_sample_with_blur(
            stratum,
            pixels,
            0.0,
            1.0,
            None,
            1,
            false,
            false,
            Some([1, 0, 1, 1]),
            false,
            Some((2, 2)),
        )
        .expect("Vulkan required for atlas blur boundary regression");
        assert_eq!(actual, vec![0, 255, 0, 255], "stratum {stratum}");
    }
}

fn vulkan_gui_sample_with_blur(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
    height: u32,
    invert: bool,
    prewarm_alpha: bool,
    atlas_region: Option<[u32; 4]>,
    mixed: bool,
    blur: Option<(i32, i32)>,
) -> Option<Vec<u8>> {
    vulkan_gui_sample_with_material(
        stratum,
        pixels,
        u0,
        u1,
        tiles,
        height,
        invert,
        prewarm_alpha,
        atlas_region,
        mixed,
        blur,
        crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
    )
}

#[test]
fn vulkan_gui_flat_item_material_modulates_atlas_without_lighting_unlit_controls() {
    use crate::render::guirender::items::material::GuiAffineMaterial;
    use crate::render::shaderpack::vanilla::lightmap::{
        VanillaLightmapFrame, VanillaLightmapInputs,
    };
    let mut material = GuiAffineMaterial::FlatItemPending;
    material
        .resolve(Some(VanillaLightmapFrame {
            generation: 1,
            inputs: VanillaLightmapInputs {
                ambient_light_factor: 0.0,
                sky_factor: 1.0,
                block_factor: 1.5,
                night_vision_factor: 0.0,
                darkness_scale: 0.0,
                darken_world_factor: 0.0,
                brightness_factor: 0.0,
                sky_light_color: [1.0; 3],
                ambient_color: [1.0; 3],
            },
        }))
        .unwrap();
    for blur in [None, Some((2, 2))] {
        for stratum in [0, 100] {
            let pixels = vec![255, 0, 0, 255, 255, 255, 255, 255];
            let actual = vulkan_gui_sample_with_material(
                stratum,
                pixels.clone(),
                0.0,
                1.0,
                None,
                1,
                false,
                false,
                Some([1, 0, 1, 1]),
                false,
                blur,
                material,
            )
            .expect("Vulkan required for explicit GUI item material regression");
            assert_eq!(actual, vec![252, 252, 252, 255]);
            let mixed = vulkan_gui_sample_with_material(
                stratum,
                pixels,
                0.0,
                1.0,
                None,
                1,
                false,
                false,
                Some([1, 0, 1, 1]),
                true,
                blur,
                material,
            )
            .expect("Vulkan required for unlit material isolation regression");
            assert_eq!(mixed, vec![126, 126, 254, 255]);
        }
    }
}

fn vulkan_gui_sample_with_material(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
    height: u32,
    invert: bool,
    prewarm_alpha: bool,
    atlas_region: Option<[u32; 4]>,
    mixed: bool,
    blur: Option<(i32, i32)>,
    material: crate::render::guirender::items::material::GuiAffineMaterial,
) -> Option<Vec<u8>> {
    vulkan_gui_sample_with_source_format(stratum, pixels, u0, u1, tiles, height, invert, prewarm_alpha, atlas_region, mixed, blur, material, GuiRawImageSourceFormat::Rgba8)
}

fn vulkan_gui_sample_with_source_format(
    stratum: u32,
    pixels: Vec<u8>,
    u0: f32,
    u1: f32,
    tiles: Option<crate::render::guirender::tiling::GuiTileGeometry>,
    height: u32,
    invert: bool,
    prewarm_alpha: bool,
    atlas_region: Option<[u32; 4]>,
    mixed: bool,
    blur: Option<(i32, i32)>,
    material: crate::render::guirender::items::material::GuiAffineMaterial,
    source_format: GuiRawImageSourceFormat,
) -> Option<Vec<u8>> {
    let mut gal = match vulkan_gal("MattMC GUI vignette frontend conformance") {
        Ok(gal) => gal,
        Err(error) => {
            eprintln!("skipping Vulkan GUI vignette frontend conformance: {error}");
            return None;
        }
    };
    let extent = Extent3d {
        width: 1,
        height,
        depth: 1,
    };
    let color = gal
        .create_texture(TextureDesc {
            label: "gui-vignette-frontend.color".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::ColorAttachment, TextureUsage::TransferSrc],
        })
        .unwrap();
    let color_view = gal
        .create_texture_view(TextureViewDesc {
            label: "gui-vignette-frontend.color-view".to_owned(),
            texture: color,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let target = gal
        .create_render_target(RenderTargetDesc {
            label: "gui-vignette-frontend.target".to_owned(),
            color_views: vec![color_view],
            depth_stencil_view: None,
            extent,
        })
        .unwrap();
    let clear_pass = gal
        .create_render_pass(RenderPassDesc {
            label: "gui-vignette-frontend.clear-pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let readback = gal
        .create_buffer(BufferDesc {
            label: "gui-vignette-frontend.readback".to_owned(),
            size: 4 * u64::from(height),
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    let mut frontend = GuiFrontend::default();
    let mut world = crate::render::worldrender::WorldPrimitiveFrontend::default();
    let atlas_reference = if let Some([x, y, width, region_height]) = atlas_region {
        use crate::render::worldrender::WorldMeshTextureAssetPayload;
        use crate::render::scene::textures::WORLD_MATERIAL_TEXTURE_STONE;
        let image_width = (pixels.len() / 4) as u32 / height;
        let mut encoded = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut encoded, image_width, height);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            encoder
                .write_header()
                .unwrap()
                .write_image_data(&pixels)
                .unwrap();
        }
        world
            .apply_world_mesh_asset_update(
                &mut gal,
                1,
                Vec::new(),
                vec![WorldMeshTextureAssetPayload {
                    texture_id: WORLD_MATERIAL_TEXTURE_STONE,
                    png_bytes: encoded,
                    mip_png_bytes: Vec::new(),
                    frame_width: 0,
                    frame_height: 0,
                    frame_count: 1,
                    frame_ticks: 1,
                    animation_flags: 0,
                    frame_row_size: 0,
                    interpolation_policy: 0,
                    animation_frames: Vec::new(),
                    coordinate_origin: 0,
                    sampling: None,
                    requested_mip_levels: 1,
                }],
            )
            .unwrap();
        let reference = GuiAtlasReference {
            asset_id: 41,
            atlas: world
                .accepted_gui_atlas_incarnation(WORLD_MATERIAL_TEXTURE_STONE)
                .unwrap(),
            x,
            y,
            width,
            height: region_height,
        };
        frontend
            .stage_owned_atlas_references(&mut gal, &world, 1, &[reference])
            .unwrap();
        assert!(
            frontend.raw_images.is_empty(),
            "native atlas sampling must not stage copied GUI pixels"
        );
        Some(reference)
    } else {
        None
    };
    if atlas_reference.is_none() {
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 41,
                    format: source_format,
                    width: (pixels.len() / source_format.bytes_per_pixel()) as u32 / height,
                    height,
                    pixels,
                }],
            )
            .unwrap();
    }
    if prewarm_alpha {
        frontend
            .ensure_resources(
                &mut gal,
                TextureGroup::Dynamic(41),
                ColorFormat::Rgba8Unorm,
                None,
                &mut GuiSubmitStats::default(),
            )
            .unwrap();
    }
    let template = GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        material,
        stratum,
        asset_id: 41,
        x0: 0.0,
        y0: 0.0,
        x1: 1.0,
        y1: 0.0,
        x3: 0.0,
        y3: height as f32,
        z: 0.0,
        u0,
        v0: 0.0,
        u1,
        v1: 1.0,
        color_argb: 0xffff_ffff,
        gui_width: 1,
        gui_height: height,
        projection_extent: [1.0, height as f32],
        sequence: 1,
        clip_mode: 0,
        clip_left: 0,
        clip_top: 0,
        clip_width: 0,
        clip_height: 0,
    };
    let (quads, tiled) = if let Some(geometry) = tiles {
        (
            Vec::new(),
            vec![GuiTiledQuadRequest {
                geometry,
                stratum,
                asset_id: 41,
                z: 0.0,
                color_argb: 0xffff_ffff,
                gui_extent: [1, 1],
                projection_extent: [1.0, 1.0],
                sequence: 1,
                clip: None,
            }],
        )
    } else {
        (vec![template], Vec::new())
    };
    let mut quads = quads;
    if mixed {
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 42,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![0, 0, 255, 128],
                }],
            )
            .unwrap();
        let mut before = quads[0].clone();
        before.material = crate::render::guirender::items::material::GuiAffineMaterial::Unlit;
        before.asset_id = 42;
        before.sequence = 1;
        let mut after = before.clone();
        after.sequence = 3;
        quads[0].sequence = 2;
        // Deliberately scrambled input: semantic scheduling must put the
        // blue control before and after the opaque atlas sample.
        quads = vec![after, quads.remove(0), before];
    }
    let quad_count = preflight_tiled_affine_count(&tiled, quads.len()).unwrap();
    let (mut ops, stats) = if atlas_reference.is_some() {
        let before = gal.metrics().resource_creates;
        if let Some(valid) = quads.iter().find(|quad| quad.asset_id == 41) {
            let mut invalid = valid.clone();
            invalid.asset_id = 999;
            invalid.sequence = 99;
            assert!(frontend
                .append_owned_atlas_quads(
                    &mut gal,
                    &mut world,
                    clear_pass,
                    target,
                    color_view,
                    None,
                    ColorFormat::Rgba8Unorm,
                    None,
                    false,
                    &[valid.clone(), invalid]
                )
                .is_err());
            assert_eq!(
                before,
                gal.metrics().resource_creates,
                "invalid semantic batch must reject before GPU allocation"
            );
        }
        assert!(
            frontend
                .append_frame_ops_with_tiled_quads_to_target(
                    &mut gal,
                    1,
                    target,
                    color_view,
                    Some(clear_pass),
                    None,
                    None,
                    false,
                    Vec::new(),
                    quads.clone(),
                    Vec::new(),
                    tiled.clone()
                )
                .is_err(),
            "mixed atlas submission must not bypass explicit owner access"
        );
        assert_eq!(before, gal.metrics().resource_creates);
        let duplicate_affine = quads
            .iter()
            .find(|quad| quad.asset_id == 41)
            .map(|quad| vec![quad.clone(), quad.clone()])
            .unwrap_or_default();
        let duplicate_tiles = tiled
            .first()
            .map(|tile| vec![tile.clone(), tile.clone()])
            .unwrap_or_default();
        assert!(
            frontend
                .append_frame_ops_with_owned_atlases_to_target(
                    &mut gal,
                    Some(&mut world),
                    1,
                    target,
                    color_view,
                    Some(clear_pass),
                    None,
                    None,
                    false,
                    Vec::new(),
                    duplicate_affine,
                    Vec::new(),
                    duplicate_tiles
                )
                .is_err(),
            "duplicate parent commands must still reject even though tile children share an ID"
        );
        assert_eq!(before, gal.metrics().resource_creates);
        if let Some((boundary, radius)) = blur {
            frontend
                .append_frame_ops_with_owned_atlases_and_blur_boundary(
                    &mut gal,
                    Some(&mut world),
                    1,
                    target,
                    color_view,
                    Vec::new(),
                    quads,
                    Vec::new(),
                    tiled,
                    boundary,
                    radius,
                    false,
                )
                .unwrap()
        } else {
            frontend
                .append_frame_ops_with_owned_atlases_to_target(
                    &mut gal,
                    Some(&mut world),
                    1,
                    target,
                    color_view,
                    Some(clear_pass),
                    None,
                    None,
                    false,
                    Vec::new(),
                    quads,
                    Vec::new(),
                    tiled,
                )
                .unwrap()
        }
    } else {
        frontend
            .append_frame_ops_with_tiled_quads_to_target(
                &mut gal,
                1,
                target,
                color_view,
                None,
                None,
                None,
                false,
                Vec::new(),
                quads,
                Vec::new(),
                tiled,
            )
            .unwrap()
    };
    assert_eq!(quad_count as u64, stats.affine_quad_count);
    let mut commands = vec![
        CommandOp::Barrier(texture_barrier(
            color,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )),
        CommandOp::BeginPass {
            pass: clear_pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    r: 0.8,
                    g: 0.8,
                    b: 0.8,
                    a: 1.0,
                }),
            }],
            depth_stencil: None,
        },
        CommandOp::EndPass,
    ];
    commands.append(&mut ops);
    if invert {
        commands.extend(
            frontend
                .append_invert_post_effect(&mut gal, target, color_view)
                .unwrap(),
        );
    }
    commands.extend([
        CommandOp::Barrier(texture_barrier(
            color,
            TextureUsageState::ColorAttachment,
            TextureUsageState::TransferSrc,
        )),
        CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: 4,
            rows_per_image: height,
            texture: color,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }),
        CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )),
        CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: 4 * u64::from(height),
        },
    ]);
    let list = gal
        .create_command_list(CommandListDesc {
            label: "gui-vignette-frontend.commands".to_owned(),
            operations: commands,
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "gui-vignette-frontend.submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
    let bytes = gal
        .completed_host_reads()
        .iter()
        .rev()
        .find(|read| read.buffer == readback)
        .expect("GUI vignette frontend must produce a readback")
        .bytes
        .clone();
    frontend.reset(&mut gal).unwrap();
    world.reset(&mut gal);
    for handle in [clear_pass, target, color_view, color, readback] {
        gal.destroy(handle).unwrap();
    }
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys,
        "GUI readback fixture must retire draw bindings, source owner and target resources"
    );
    Some(bytes)
}

#[test]
fn blur_boundary_replay_builds_owned_snapshot_copy_and_fullscreen_draw() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let (ops, _stats) = frontend
        .append_frame_ops_with_blur_boundary(
            &mut gal,
            1,
            target,
            target,
            Vec::new(),
            Vec::new(),
            Vec::new(),
            1,
            0,
            false,
        )
        .unwrap();
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert_eq!(
        6,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count(),
        "Frozen's menu blur requires all three horizontal/vertical pairs"
    );
    let configs = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::HostWriteBuffer { data, .. } if data.len() == 64 => Some(data),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(6, configs.len());
    for (index, bytes) in configs.into_iter().enumerate() {
        let x = f32::from_le_bytes(bytes[..4].try_into().unwrap());
        let y = f32::from_le_bytes(bytes[4..8].try_into().unwrap());
        assert_eq!(
            [x, y],
            if index % 2 == 0 {
                [1.0, 0.0]
            } else {
                [0.0, 1.0]
            }
        );
    }
    assert!(std::str::from_utf8(BLUR_FRAGMENT_SHADER_VULKAN)
        .unwrap()
        .contains("gl_FragCoord.xy * texel"));
    gal.submit(SubmissionBatch {
        label: "gui-blur-replay".to_owned(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "gui-blur-replay-commands".to_owned(),
            operations: ops,
        })],
    })
    .unwrap();
    let (next_ops, _) = frontend
        .append_frame_ops_with_blur_boundary(
            &mut gal,
            1,
            target,
            target,
            Vec::new(),
            Vec::new(),
            Vec::new(),
            1,
            0,
            false,
        )
        .unwrap();
    assert!(matches!(
        next_ops.iter().find(|op| matches!(
            op,
            CommandOp::Barrier(ResourceBarrier {
                before: TextureUsageState::ShaderRead,
                after: TextureUsageState::TransferDst,
                ..
            })
        )),
        Some(CommandOp::Barrier(ResourceBarrier {
            before: TextureUsageState::ShaderRead,
            after: TextureUsageState::TransferDst,
            ..
        }))
    ));
    gal.submit(SubmissionBatch {
        label: "gui-blur-replay-next-frame".to_owned(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "gui-blur-replay-next-frame-commands".to_owned(),
            operations: next_ops,
        })],
    })
    .unwrap();
}

#[test]
fn invert_post_effect_replay_is_rust_owned_and_precedes_gui_composition() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let ops = frontend
        .append_invert_post_effect(&mut gal, target, target)
        .unwrap();
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    let draw_index = ops
        .iter()
        .position(|op| {
            matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            )
        })
        .expect("invert effect must emit a fullscreen draw");
    assert!(ops[..draw_index].iter().any(|op| matches!(
        op,
        CommandOp::Barrier(ResourceBarrier {
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            ..
        })
    )));
    gal.create_command_list(CommandListDesc {
        label: "gui-invert-replay".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn bounded_custom_post_effect_replay_uses_owned_snapshot_and_explicit_bindings() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_custom",
            &[
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".to_owned()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "minecraft:main".to_owned(),
                    uniform_blocks: vec![vec![7; 16]],
                },
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".to_owned()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "minecraft:main".to_owned(),
                    uniform_blocks: vec![vec![7; 16]],
                },
            ],
        )
        .unwrap();
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::BindResourceSet { .. })));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::HostWriteBuffer { data, .. } if data == &vec![7; 16]
    )));
    for (index, op) in ops.iter().enumerate() {
        if let CommandOp::HostWriteBuffer { buffer, data, .. } = op {
            if data == &vec![7; 16] {
                assert!(
                    matches!(ops.get(index + 1), Some(CommandOp::Barrier(barrier))
                    if barrier.resource == *buffer
                        && barrier.before == TextureUsageState::TransferDst
                        && barrier.after == TextureUsageState::ShaderRead),
                    "every post-effect uniform upload needs an explicit read dependency"
                );
            }
        }
    }
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    gal.create_command_list(CommandListDesc {
        label: "gui-custom-post-effect-replay".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn custom_post_effect_texture_input_uploads_and_binds_rust_owned_pixels() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D MaskSampler;
out vec4 fragColor;
void main() { fragColor = texture(MaskSampler, texCoord); }
"#;
    let pixels: Vec<u8> = (1..=24).collect();
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_texture_input",
            &[CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Reverse,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec![String::new()],
                input_images: vec![Some(CustomPostEffectImage {
                    path: "textures/effect/mask.png".to_owned(),
                    width: 2,
                    height: 3,
                    pixels_rgba8: pixels.clone(),
                    bilinear: false,
                })],
                input_use_depth: vec![false],
                output_target: "minecraft:main".to_owned(),
                uniform_blocks: Vec::new(),
            }],
        )
        .unwrap();
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::HostWriteBuffer { data, .. } if data == &pixels
    )));
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyBufferToTexture(_))));
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::BindResourceSet { .. })));
    assert!(!ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert!(
        !ops.iter().any(|op| matches!(op, CommandOp::CopyTexture(_))),
        "uploaded image rows must not inherit framebuffer row conversion"
    );
    let second_ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_texture_input",
            &[CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Reverse,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec![String::new()],
                input_images: vec![Some(CustomPostEffectImage {
                    path: "textures/effect/mask.png".to_owned(),
                    width: 2,
                    height: 3,
                    pixels_rgba8: pixels,
                    bilinear: false,
                })],
                input_use_depth: vec![false],
                output_target: "minecraft:main".to_owned(),
                uniform_blocks: Vec::new(),
            }],
        )
        .unwrap();
    assert!(!second_ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyBufferToTexture(_))));
    let depth_error = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_depth_input",
            &[CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["minecraft:main".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![true],
                output_target: "minecraft:main".to_owned(),
                uniform_blocks: Vec::new(),
            }],
        )
        .unwrap_err();
    assert!(depth_error
        .to_string()
        .contains("requires a Rust-owned render-target depth attachment"));
}

#[test]
fn custom_post_effect_depth_input_uses_color_only_alias_and_explicit_barriers() {
    let mut gal = mock_gal();
    let (target, color_view, depth_texture) = depth_render_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let source = CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: br#"#version 330
out vec2 texCoord; void main() { texCoord = vec2(0.0); }"#
            .to_vec(),
        fragment_shader: br#"#version 330
in vec2 texCoord; uniform sampler2D MainDepthSampler; out vec4 fragColor;
void main() { fragColor = texture(MainDepthSampler, texCoord); }"#
            .to_vec(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec!["minecraft:main".to_owned()],
        input_images: vec![None],
        input_use_depth: vec![true],
        output_target: "minecraft:main".to_owned(),
        uniform_blocks: Vec::new(),
    };
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            color_view,
            "minecraft:test-depth-alias",
            &[source],
        )
        .unwrap();
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(ResourceBarrier { resource, before: TextureUsageState::DepthStencilAttachment, after: TextureUsageState::ShaderRead, .. }) if *resource == depth_texture
    )));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(ResourceBarrier { resource, before: TextureUsageState::ShaderRead, after: TextureUsageState::DepthStencilAttachment, .. }) if *resource == depth_texture
    )));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { target: pass_target, depth_stencil: None, .. } if *pass_target != target
    )));
}

#[test]
fn frame_gui_depth_declaration_requires_a_complete_pair() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let error = frontend
        .append_frame_ops_with_affine_quads_to_target(
            &mut gal,
            1,
            target,
            target,
            None,
            None,
            Some(TextureFormat::Depth32Float),
            false,
            Vec::new(),
            Vec::new(),
        )
        .unwrap_err();
    assert!(error
        .to_string()
        .contains("depth attachment and depth format"));
    let (_, depth_view) = gal.frame_target_owned_depth_attachment(target).unwrap();
    let (ops, _) = frontend
        .append_frame_ops_with_affine_quads_to_target(
            &mut gal,
            1,
            target,
            target,
            None,
            Some(depth_view),
            Some(TextureFormat::Depth32Float),
            false,
            Vec::new(),
            Vec::new(),
        )
        .unwrap();
    gal.create_command_list(CommandListDesc {
        label: "gui-frame-owned-depth-pass".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn creeper_post_effect_replay_owns_intermediate_target_and_two_fullscreen_passes() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let ops = frontend
        .append_creeper_post_effect(&mut gal, target, target)
        .unwrap();
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { target: pass_target, .. } if *pass_target != target
    )));
    gal.create_command_list(CommandListDesc {
        label: "gui-creeper-replay".to_owned(),
        operations: ops,
    })
    .unwrap();
    let next_ops = frontend
        .append_creeper_post_effect(&mut gal, target, target)
        .unwrap();
    assert!(next_ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(ResourceBarrier {
            before: TextureUsageState::ShaderRead,
            after: TextureUsageState::ColorAttachment,
            ..
        })
    )));
}

#[test]
fn bounded_custom_post_effect_replay_owns_one_intermediate_target() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_intermediate",
            &[
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".to_owned()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "intermediate".to_owned(),
                    uniform_blocks: Vec::new(),
                },
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["intermediate".to_owned()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "minecraft:main".to_owned(),
                    uniform_blocks: Vec::new(),
                },
            ],
        )
        .unwrap();
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { target: pass_target, .. } if *pass_target != target
    )));
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    gal.create_command_list(CommandListDesc {
        label: "gui-custom-intermediate-replay".to_owned(),
        operations: ops.clone(),
    })
    .unwrap();
    let intermediate = &frontend.custom_post_effect_intermediates["intermediate"];
    let begin = ops
        .iter()
        .position(|op| {
            matches!(op, CommandOp::BeginPass { target, .. }
        if *target == intermediate.target)
        })
        .unwrap();
    assert!(ops[..begin].iter().any(|op| matches!(op, CommandOp::Barrier(barrier)
        if barrier.resource == intermediate.texture && barrier.before == TextureUsageState::Undefined
            && barrier.after == TextureUsageState::ColorAttachment)));
    assert_eq!(TextureUsageState::ShaderRead, intermediate.usage);
}

#[test]
fn custom_post_effect_sampler_info_packs_explicit_output_and_input_extents() {
    let image = CustomPostEffectImage {
        path: "test".into(),
        width: 8,
        height: 4,
        pixels_rgba8: vec![0; 8 * 4 * 4],
        bilinear: false,
    };
    let bytes = custom_sampler_info_bytes(
        Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        &[None, Some(image)],
    );
    assert_eq!(32, bytes.len());
    let values = bytes
        .chunks_exact(4)
        .map(|b| f32::from_le_bytes(b.try_into().unwrap()))
        .collect::<Vec<_>>();
    assert_eq!(vec![320.0, 180.0, 320.0, 180.0, 8.0, 4.0, 0.0, 0.0], values);
}

#[test]
fn custom_post_effect_external_target_lowering_uses_owned_attachment_pass() {
    let mut gal = mock_gal();
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let mut make_target = |label: &str| {
        let texture = gal
            .create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::ColorAttachment,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        let target = gal
            .create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![view],
                depth_stencil_view: None,
                extent,
            })
            .unwrap();
        let pass = gal
            .create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: None,
            })
            .unwrap();
        let sampler = gal
            .create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: SamplerFilter::Linear,
                mag_filter: SamplerFilter::Linear,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })
            .unwrap();
        (target, view, pass, sampler)
    };
    let (main_target, main_view, main_pass, main_sampler) = make_target("external-main");
    let (external_target, external_view, external_pass, external_sampler) =
        make_target("external-role");
    let plan = crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectExecutionPlan {
        effect_name: "minecraft:external-test".to_owned(),
        intermediate_targets: Vec::new(),
        ordered_passes: vec![crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectPass {
            vertex_shader: "vertex".to_owned(),
            fragment_shader: "fragment".to_owned(),
            inputs: vec![crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectInput {
                sampler_name: "InSampler".to_owned(),
                target: "minecraft:main".to_owned(),
                texture_path: None,
                texture_width: None,
                texture_height: None,
                bilinear: true,
                use_depth_buffer: false,
            }],
            output: "minecraft:translucent".to_owned(),
            uniform_blocks: BTreeSet::new(),
            uniform_values: BTreeMap::new(),
        }],
    };
    let external = crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectExternalTargetBindings::new(
        &plan,
        BTreeMap::from([
            (
                "minecraft:main".to_owned(),
                crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectExternalTargetBinding {
                    render_pass: main_pass,
                    render_target: main_target,
                    color_attachment: main_view,
                    depth_attachment: None,
                    sampler: main_sampler,
                    color_usage: TextureUsageState::ColorAttachment,
                    depth_usage: None,
                },
            ),
            (
                "minecraft:translucent".to_owned(),
                crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectExternalTargetBinding {
                    render_pass: external_pass,
                    render_target: external_target,
                    color_attachment: external_view,
                    depth_attachment: None,
                    sampler: external_sampler,
                    color_usage: TextureUsageState::ColorAttachment,
                    depth_usage: None,
                },
            ),
        ]),
    ).unwrap();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let mut frontend = GuiFrontend::default();
    let ops = frontend
        .append_custom_post_effect_with_external_targets(
            &mut gal,
            main_target,
            main_view,
            "minecraft:external-test",
            &[CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["minecraft:main".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![false],
                output_target: "minecraft:translucent".to_owned(),
                uniform_blocks: Vec::new(),
            }],
            Some(&external),
        )
        .unwrap();
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::CopyTexture(TextureImageCopyRegion { src_texture, .. })
            if *src_texture == gal.pass_target_color_texture(main_target).unwrap()
    )));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { pass, target, .. }
            if *pass == external_pass && *target == external_target
    )));
    gal.create_command_list(CommandListDesc {
        label: "gui-custom-external-target".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn bounded_custom_post_effect_replay_owns_multiple_private_targets() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test_multi_intermediate",
            &[
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".into()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "first".into(),
                    uniform_blocks: Vec::new(),
                },
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".into()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "second".into(),
                    uniform_blocks: Vec::new(),
                },
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["first".into()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "minecraft:main".into(),
                    uniform_blocks: Vec::new(),
                },
            ],
        )
        .unwrap();
    let private_passes = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BeginPass {
                target: pass_target,
                ..
            } if *pass_target != target => Some(*pass_target),
            _ => None,
        })
        .collect::<std::collections::BTreeSet<_>>();
    assert_eq!(2, private_passes.len());
    assert_eq!(
        3,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    gal.create_command_list(CommandListDesc {
        label: "gui-custom-multi-intermediate-replay".into(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn bounded_custom_post_effect_binds_distinct_input_snapshots() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D MainSampler;
uniform sampler2D PrivateSampler;
out vec4 fragColor;
void main() { fragColor = texture(MainSampler, texCoord) + texture(PrivateSampler, texCoord) * 0.0; }
"#;
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test-distinct-inputs",
            &[
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 1,
                    input_bilinear: vec![false; 1],
                    input_targets: vec!["minecraft:main".to_owned()],
                    input_images: vec![None],
                    input_use_depth: vec![false],
                    output_target: "private".to_owned(),
                    uniform_blocks: Vec::new(),
                },
                CustomPostEffectSource {
                    input_row_order:
                        crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    sampler_info_uniform: None,
                    vertex_shader: vertex.to_vec(),
                    fragment_shader: fragment.to_vec(),
                    input_count: 2,
                    input_bilinear: vec![false; 2],
                    input_targets: vec!["minecraft:main".to_owned(), "private".to_owned()],
                    input_images: vec![None, None],
                    input_use_depth: vec![false, false],
                    output_target: "minecraft:main".to_owned(),
                    uniform_blocks: Vec::new(),
                },
            ],
        )
        .unwrap();
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. }))
            .count()
    );
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count()
    );
    gal.create_command_list(CommandListDesc {
        label: "gui-custom-distinct-input-replay".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn custom_post_effect_reversed_frame_snapshots_reuse_scratch_and_retire_all_resources() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let mut live = None;
    for iteration in 0..8 {
        let source = row_conversion_fixture(false, 2);
        let ops = frontend
            .append_custom_post_effect(
                &mut gal,
                target,
                target,
                "row-conversion-fixture",
                &[source],
            )
            .unwrap();
        let scratch = frontend.custom_post_effect_resources[0]
            .frame_copy_scratch
            .unwrap();
        assert_eq!(2, ops.iter().filter(|op| matches!(op,
            CommandOp::CopyTexture(region) if region.row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse
                && region.src_texture == scratch)).count());
        let starts_undefined = ops.iter().any(|op| matches!(op,
            CommandOp::Barrier(ResourceBarrier { resource, before: TextureUsageState::Undefined, .. })
                if *resource == scratch));
        assert_eq!(iteration == 0, starts_undefined);
        let list = gal
            .create_command_list(CommandListDesc {
                label: "row-fixture".into(),
                operations: ops,
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "row-fixture".into(),
                command_lists: vec![list],
            })
            .unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
        let count = gal.metrics().resource_creates - gal.metrics().resource_destroys;
        assert_eq!(*live.get_or_insert(count), count);
    }
    frontend.reset(&mut gal).unwrap();
    gal.destroy(target).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

fn row_conversion_fixture(depth: bool, count: usize) -> CustomPostEffectSource {
    CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Reverse,
        input_bilinear: vec![false; count],
        sampler_info_uniform: None,
        vertex_shader: b"#version 450\nvoid main() { gl_Position=vec4(0.0); }".to_vec(),
        fragment_shader:
            b"#version 450\nlayout(location=0) out vec4 color; void main() { color=vec4(1.0); }"
                .to_vec(),
        input_count: count,
        input_targets: vec!["minecraft:main".into(); count],
        input_images: vec![None; count],
        input_use_depth: vec![depth; count],
        output_target: "minecraft:main".into(),
        uniform_blocks: vec![],
    }
}

#[test]
fn custom_post_effect_depth_does_not_substitute_main_for_a_color_only_private_target() {
    let mut gal = mock_gal();
    let (target, color, _) = depth_render_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let mut produce = row_conversion_fixture(false, 1);
    produce.output_target = "private-color".into();
    let mut consume = row_conversion_fixture(true, 1);
    consume.input_targets[0] = "private-color".into();
    let creates = gal.metrics().resource_creates;
    let result = frontend.append_custom_post_effect(
        &mut gal,
        target,
        color,
        "missing-named-depth",
        &[produce, consume],
    );
    assert!(
        result.is_err(),
        "a named depth input must not sample main depth instead"
    );
    assert_eq!(
        creates,
        gal.metrics().resource_creates,
        "reject missing depth before graph allocation"
    );
}

#[test]
fn custom_post_effect_reversed_depth_uses_matching_owned_snapshot() {
    let mut gal = mock_gal();
    let (target, color_view, depth) = depth_render_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let ops = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            color_view,
            "depth-row-conversion",
            &[row_conversion_fixture(true, 1)],
        )
        .unwrap();
    let resources = &frontend.custom_post_effect_resources[0];
    assert!(resources.frame_copy_scratch.is_none());
    let snapshot = resources.snapshots[0];
    let info = gal.texture_view_info(resources.snapshot_views[0]).unwrap();
    assert_eq!(TextureFormat::Depth32Float, info.format);
    assert_ne!(depth, info.texture);
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyTexture(region)
        if region.src_texture == depth && region.dst_texture == snapshot
            && region.row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse)));
    assert!(!ops
        .iter()
        .any(|op| matches!(op, CommandOp::Barrier(ResourceBarrier {
        resource, after: TextureUsageState::ShaderRead, .. }) if *resource == depth)));
    gal.create_command_list(CommandListDesc {
        label: "depth-row-fixture".into(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn custom_post_effect_target_filters_are_explicit_and_invalidate_cached_resources() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let mut previous = Vec::new();
    for flags in [vec![false, true], vec![true, false]] {
        let source = CustomPostEffectSource {
            input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            input_bilinear: flags.clone(), sampler_info_uniform: None,
            vertex_shader: b"#version 450\nvoid main() { gl_Position=vec4(0.0); }".to_vec(),
            fragment_shader: b"#version 450\nlayout(location=0) out vec4 color; void main() { color=vec4(1.0); }".to_vec(),
            input_count: 2, input_targets: vec!["minecraft:main".into(); 2],
            input_images: vec![None; 2], input_use_depth: vec![false; 2],
            output_target: "minecraft:main".into(), uniform_blocks: Vec::new(),
        };
        frontend
            .append_custom_post_effect(
                &mut gal,
                target,
                target,
                "minecraft:sampler-fixture",
                &[source],
            )
            .unwrap();
        for handle in &previous {
            assert!(
                gal.sampler_descriptor_for_test(*handle).is_err(),
                "changed filters must retire old bindings"
            );
        }
        let samplers = &frontend.custom_post_effect_resources[0].target_samplers;
        assert_eq!(2, samplers.len());
        for (handle, bilinear) in samplers.iter().zip(flags) {
            let desc = gal.sampler_descriptor_for_test(*handle).unwrap();
            let expected = if bilinear {
                SamplerFilter::Linear
            } else {
                SamplerFilter::Nearest
            };
            assert_eq!(expected, desc.min_filter);
            assert_eq!(expected, desc.mag_filter);
        }
        previous = samplers.clone();
    }
    frontend.reset(&mut gal).unwrap();
    gal.destroy(target).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn cancelled_post_effect_preparation_restarts_with_fresh_image_states() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let source = |input: &str, output: &str| CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: b"#version 450\nvoid main() { gl_Position = vec4(0.0); }".to_vec(),
        fragment_shader:
            b"#version 450\nlayout(location=0) out vec4 color; void main() { color=vec4(1.0); }"
                .to_vec(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec![input.into()],
        input_images: vec![None],
        input_use_depth: vec![false],
        output_target: output.into(),
        uniform_blocks: Vec::new(),
    };
    let sources = [
        source("minecraft:main", "swap"),
        source("swap", "minecraft:main"),
    ];
    let unsubmitted = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:cancel-fixture",
            &sources,
        )
        .unwrap();
    let old_texture = frontend.custom_post_effect_intermediates["swap"].texture;
    assert!(frontend
        .custom_post_effect_snapshot_initialized
        .iter()
        .flatten()
        .all(|v| *v));
    drop(unsubmitted);
    frontend.discard_prepared_post_effects(&mut gal);
    assert!(frontend.custom_post_effect_resources.is_empty());
    assert!(frontend.custom_post_effect_snapshot_initialized.is_empty());
    let retry = frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:cancel-fixture",
            &sources,
        )
        .unwrap();
    let new_texture = frontend.custom_post_effect_intermediates["swap"].texture;
    assert_ne!(old_texture, new_texture);
    assert!(retry
        .iter()
        .any(|op| matches!(op, CommandOp::Barrier(barrier)
        if barrier.resource == new_texture && barrier.before == TextureUsageState::Undefined
            && barrier.after == TextureUsageState::ColorAttachment)));
    let list = gal
        .create_command_list(CommandListDesc {
            label: "post-effect-cancel-retry".into(),
            operations: retry,
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "post-effect-cancel-retry".into(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.retire_through(token.submission).unwrap();
    frontend.reset(&mut gal).unwrap();
    gal.destroy(target).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn custom_post_effect_resize_and_uniform_changes_keep_resources_bounded() {
    let mut gal = mock_gal();
    let initial = frame_target(&mut gal);
    gal.destroy(initial).unwrap();
    let mut frontend = GuiFrontend::default();
    let mut expected_live = None;
    for iteration in 0..32u64 {
        let width = if iteration % 2 == 0 { 320 } else { 640 };
        let height = if iteration % 2 == 0 { 180 } else { 360 };
        let target = gal
            .create_frame_target(FrameTargetDesc {
                label: "post-effect-resize-test".into(),
                frame_id: iteration + 2,
                render_target: crate::render::vulkanic::frame::FrameRenderTargetId(
                    iteration + 2,
                ),
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
                color_format: TextureFormat::Rgba8Unorm,
            })
            .unwrap();
        let source = |input: &str, output: &str| {
            CustomPostEffectSource {
            input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            sampler_info_uniform: Some(1),
            vertex_shader: b"#version 450\nvoid main() { gl_Position = vec4(0.0); }".to_vec(),
            fragment_shader: b"#version 450\nlayout(location=0) out vec4 color; void main() { color=vec4(1.0); }".to_vec(),
            input_count: 1,
                    input_bilinear: vec![false; 1], input_targets: vec![input.into()], input_images: vec![None],
            input_use_depth: vec![false], output_target: output.into(),
            uniform_blocks: vec![vec![iteration as u8; 16], vec![0; 16]],
        }
        };
        let ops = frontend
            .append_custom_post_effect(
                &mut gal,
                target,
                target,
                "minecraft:resize-fixture",
                &[
                    source("minecraft:main", "swap"),
                    source("swap", "minecraft:main"),
                ],
            )
            .unwrap();
        let expected_extents = [width as f32, height as f32, width as f32, height as f32]
            .into_iter()
            .flat_map(f32::to_le_bytes)
            .collect::<Vec<_>>();
        assert_eq!(
            2,
            ops.iter()
                .filter(|op| matches!(op,
            CommandOp::HostWriteBuffer { data, .. } if data == &expected_extents))
                .count()
        );
        let list = gal
            .create_command_list(CommandListDesc {
                label: "post-effect-resize-test".into(),
                operations: ops,
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "post-effect-resize-test".into(),
                command_lists: vec![list],
            })
            .unwrap();
        gal.retire_through(token.submission).unwrap();
        frontend.clear_frame_passes_for_targets(&mut gal, &[target]);
        gal.destroy(target).unwrap();
        assert_eq!(2, frontend.custom_post_effect_resources.len());
        assert_eq!(1, frontend.custom_post_effect_intermediates.len());
        let live = gal.metrics().resource_creates - gal.metrics().resource_destroys;
        assert_eq!(
            *expected_live.get_or_insert(live),
            live,
            "resizing and changing uniform bytes must replace, not accumulate, resources"
        );
    }
    frontend.reset(&mut gal).unwrap();
    gal.retire_completed().unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn custom_post_effect_cache_invalidates_changed_pass_suffix() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let source = |marker: &[u8], output: &str| CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: vertex.to_vec(),
        fragment_shader: [fragment, marker].concat(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec!["minecraft:main".to_owned()],
        input_images: vec![None],
        input_use_depth: vec![false],
        output_target: output.to_owned(),
        uniform_blocks: Vec::new(),
    };
    frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test-cache-suffix",
            &[
                source(b"//a", "first"),
                source(b"//b", "second"),
                source(b"//c", "minecraft:main"),
            ],
        )
        .unwrap();
    assert_eq!(3, frontend.custom_post_effect_resources.len());
    frontend
        .append_custom_post_effect(
            &mut gal,
            target,
            target,
            "minecraft:test-cache-suffix",
            &[
                source(b"//a", "first"),
                source(b"//changed", "minecraft:main"),
            ],
        )
        .unwrap();
    assert_eq!(2, frontend.custom_post_effect_resources.len());
    assert_eq!(1, frontend.custom_post_effect_intermediates.len());
}

#[test]
fn custom_post_effect_rejects_forward_and_feedback_target_edges() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let forward_reference = frontend.append_custom_post_effect(
        &mut gal,
        target,
        target,
        "minecraft:test-forward-reference",
        &[
            CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["later".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![false],
                output_target: "first".to_owned(),
                uniform_blocks: Vec::new(),
            },
            CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["minecraft:main".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![false],
                output_target: "later".to_owned(),
                uniform_blocks: Vec::new(),
            },
        ],
    );
    assert!(forward_reference.is_err());

    let feedback = frontend.append_custom_post_effect(
        &mut gal,
        target,
        target,
        "minecraft:test-feedback-reference",
        &[
            CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["minecraft:main".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![false],
                output_target: "loop".to_owned(),
                uniform_blocks: Vec::new(),
            },
            CustomPostEffectSource {
                input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                sampler_info_uniform: None,
                vertex_shader: vertex.to_vec(),
                fragment_shader: fragment.to_vec(),
                input_count: 1,
                input_bilinear: vec![false; 1],
                input_targets: vec!["loop".to_owned()],
                input_images: vec![None],
                input_use_depth: vec![false],
                output_target: "loop".to_owned(),
                uniform_blocks: Vec::new(),
            },
        ],
    );
    assert!(feedback.is_err());
}

#[test]
fn custom_post_effect_rejects_oversized_uniform_payload_before_allocation() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let source = CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: b"#version 330\nvoid main() {}".to_vec(),
        fragment_shader: b"#version 330\nvoid main() {}".to_vec(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec!["minecraft:main".to_owned()],
        input_images: vec![None],
        input_use_depth: vec![false],
        output_target: "minecraft:main".to_owned(),
        uniform_blocks: vec![vec![0; MAX_CUSTOM_POST_EFFECT_UNIFORM_BYTES + 1]],
    };
    let result = frontend.append_custom_post_effect(
        &mut gal,
        target,
        target,
        "minecraft:test-uniform-bound",
        &[source],
    );
    assert!(result.is_err());
    assert!(frontend.custom_post_effect_resources.is_empty());

    let bounded_source = || CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: b"#version 330\nvoid main() {}".to_vec(),
        fragment_shader: b"#version 330\nvoid main() {}".to_vec(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec!["minecraft:main".to_owned()],
        input_images: vec![None],
        input_use_depth: vec![false],
        output_target: "minecraft:main".to_owned(),
        uniform_blocks: vec![vec![0; MAX_CUSTOM_POST_EFFECT_UNIFORM_BYTES]],
    };
    let graph_result = frontend.append_custom_post_effect(
        &mut gal,
        target,
        target,
        "minecraft:test-uniform-graph-bound",
        &[bounded_source(), bounded_source(), bounded_source()],
    );
    assert!(graph_result.is_err());
    assert!(frontend.custom_post_effect_resources.is_empty());
}

#[test]
fn spider_post_effect_replay_emits_blur_clip_and_blit_passes() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let ops = frontend
        .append_spider_post_effect(&mut gal, target, target)
        .unwrap();
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::CopyFrameTargetToTexture { .. })));
    assert_eq!(
        8,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    gal.create_command_list(CommandListDesc {
        label: "gui-spider-replay".to_owned(),
        operations: ops,
    })
    .unwrap();
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn blur_boundary_replay_copies_owned_render_targets_with_explicit_attachment_barriers() {
    let mut gal = mock_gal();
    let source_texture = gal
        .create_texture(TextureDesc {
            label: "test-gui-source-texture".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::ColorAttachment, TextureUsage::TransferSrc],
        })
        .unwrap();
    let source_view = gal
        .create_texture_view(TextureViewDesc {
            label: "test-gui-source-view".to_owned(),
            texture: source_texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let source_target = gal
        .create_render_target(RenderTargetDesc {
            label: "test-gui-source-target".to_owned(),
            color_views: vec![source_view],
            depth_stencil_view: None,
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
        })
        .unwrap();
    let mut frontend = GuiFrontend::default();
    let (ops, _) = frontend
        .append_frame_ops_with_blur_boundary(
            &mut gal,
            1,
            source_target,
            source_view,
            Vec::new(),
            Vec::new(),
            Vec::new(),
            1,
            0,
            false,
        )
        .unwrap();
    assert!(ops.iter().any(|op| matches!(op, CommandOp::CopyTexture(_))));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::Barrier(ResourceBarrier {
            before: TextureUsageState::ColorAttachment,
            after: TextureUsageState::TransferSrc,
            ..
        })
    )));
    gal.create_command_list(CommandListDesc {
        label: "gui-blur-owned-target".to_owned(),
        operations: ops,
    })
    .unwrap();
}

#[test]
fn blur_boundary_declares_scratch_targets_for_source_frame_gui_validation() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let (ops, stats) = frontend
        .append_frame_ops_with_tiled_blur_boundary(
            &mut gal,
            1,
            target,
            target,
            Vec::new(),
            Vec::new(),
            Vec::new(),
            Vec::new(),
            1,
            0,
            false,
        )
        .unwrap();
    let scratch_targets: Vec<Handle> = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BeginPass { target: pass_target, .. } if *pass_target != target => {
                Some(*pass_target)
            }
            _ => None,
        })
        .collect();
    assert!(!scratch_targets.is_empty(), "blur must ping-pong through scratch targets");
    for scratch in &scratch_targets {
        assert!(stats.owned_intermediate_targets.contains(scratch));
    }
    crate::render::worldrender::WorldPrimitiveFrontend::validate_source_gui_ops(
        &ops,
        target,
        &stats.owned_intermediate_targets,
    )
    .expect("menu blur scratch passes are declared GUI-owned intermediates");
    crate::render::worldrender::WorldPrimitiveFrontend::validate_source_gui_ops(&ops, target, &[])
        .expect_err("undeclared blur scratch targets must still be rejected");
}

#[test]
fn custom_post_effect_reports_owned_intermediates_for_source_frame_gui_validation() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let vertex = br#"#version 330
out vec2 texCoord;
void main() { texCoord = vec2(0.0); }
"#;
    let fragment = br#"#version 330
in vec2 texCoord;
uniform sampler2D InSampler;
out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"#;
    let pass = |input: &str, output: &str| CustomPostEffectSource {
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        sampler_info_uniform: None,
        vertex_shader: vertex.to_vec(),
        fragment_shader: fragment.to_vec(),
        input_count: 1,
        input_bilinear: vec![false; 1],
        input_targets: vec![input.to_owned()],
        input_images: vec![None],
        input_use_depth: vec![false],
        output_target: output.to_owned(),
        uniform_blocks: Vec::new(),
    };
    let (ops, owned_targets) = frontend
        .append_custom_post_effect_with_owned_targets(
            &mut gal,
            target,
            target,
            "minecraft:test_owned_intermediate",
            &[
                pass("minecraft:main", "intermediate"),
                pass("intermediate", "minecraft:main"),
            ],
            None,
        )
        .unwrap();
    let intermediate = frontend.custom_post_effect_intermediates["intermediate"].target;
    assert_eq!(vec![intermediate], owned_targets);
    crate::render::worldrender::WorldPrimitiveFrontend::validate_source_gui_ops(
        &ops,
        target,
        &owned_targets,
    )
    .expect("declared custom post-effect intermediate is admitted");
    crate::render::worldrender::WorldPrimitiveFrontend::validate_source_gui_ops(&ops, target, &[])
        .expect_err("undeclared custom post-effect intermediate must still be rejected");
}

fn mock_gal() -> VulkanicGal {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    crate::render::vulkanic::test_support::mock_gal_with_capabilities(capabilities)
}

fn frame_target(gal: &mut VulkanicGal) -> Handle {
    gal.configure_frame_surface(FrameSurfaceDesc {
        label: "test-gui-frame-surface".to_owned(),
        extent: Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        color_format: TextureFormat::Rgba8Unorm,
        present_mode: PresentMode::Fifo,
        max_frames_in_flight: 2,
    })
    .unwrap();
    gal.create_frame_target(FrameTargetDesc {
        label: "test-gui-frame-target".to_owned(),
        frame_id: 1,
        render_target: crate::render::vulkanic::frame::FrameRenderTargetId(1),
        extent: Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
        color_format: TextureFormat::Rgba8Unorm,
    })
    .unwrap()
}

fn depth_render_target(gal: &mut VulkanicGal) -> (Handle, Handle, Handle) {
    let extent = Extent3d {
        width: 320,
        height: 180,
        depth: 1,
    };
    let color = gal
        .create_texture(TextureDesc {
            label: "test-custom-depth-color".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::ColorAttachment,
                TextureUsage::TransferSrc,
            ],
        })
        .unwrap();
    let color_view = gal
        .create_texture_view(TextureViewDesc {
            label: "test-custom-depth-color-view".to_owned(),
            texture: color,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let depth = gal
        .create_texture(TextureDesc {
            label: "test-custom-depth".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::DepthStencilAttachment,
                TextureUsage::TransferSrc,
            ],
        })
        .unwrap();
    let depth_view = gal
        .create_texture_view(TextureViewDesc {
            label: "test-custom-depth-view".to_owned(),
            texture: depth,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let target = gal
        .create_render_target(RenderTargetDesc {
            label: "test-custom-depth-target".to_owned(),
            color_views: vec![color_view],
            depth_stencil_view: Some(depth_view),
            extent,
        })
        .unwrap();
    (target, color_view, depth)
}

fn request(sprite_id: u32) -> GuiSpriteRequest {
    let def = sprite_def(sprite_id).unwrap();
    GuiSpriteRequest {
        stratum: def.stratum,
        sprite_id,
        selected_slot: -1,
        progress_fraction: 1.0,
        fill_direction: 0,
        color_argb: 0xffffffff,
        x: 10,
        y: 10,
        width: def.width.min(16),
        height: def.height.min(16),
        gui_width: 320,
        gui_height: 180,
        projection_extent: [320.0, 180.0],
        sequence: 1,
    }
}

#[test]
fn native_model_item_uses_registered_owned_image_without_borrowing_an_atlas() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    let mut request = mesh_batch(0);
    request.item_raster_scale = 2;
    request.render_extent = [0, 0];
    request.guard_pixels = 0;
    request.model_transform = [
        0.8, 0., -0.6, 0., 0., 1., 0., 0., 0.6, 0., 0.8, 0., 0., 0., 0., 1.,
    ];
    request.lighting_mode = GuiMeshLightingMode::Flat;
    request.item_lighting = Some(crate::render::guirender::mesh::GuiFlatItemLighting {
        lightmap_generation: 1,
        rgb: [1.; 3],
    });
    let before = gal.metrics().resource_creates;
    assert!(frontend
        .append_mesh_items_to_target(
            &mut gal,
            None,
            1,
            target,
            target,
            None,
            None,
            None,
            vec![request.clone()],
            &mut GuiSubmitStats::default()
        )
        .is_err());
    assert_eq!(before, gal.metrics().resource_creates);
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 2,
                height: 2,
                pixels: vec![255; 16],
            }],
        )
        .unwrap();
    let mut stats = GuiSubmitStats::default();
    let ops = frontend
        .append_mesh_items_to_target(
            &mut gal,
            None,
            1,
            target,
            target,
            None,
            None,
            None,
            vec![request.clone()],
            &mut stats,
        )
        .unwrap();
    assert!(!ops.is_empty());
    assert_eq!(stats.mesh_item_count, 1);
    assert!(frontend
        .resources
        .values()
        .all(|binding| !matches!(binding.image_ownership, GuiImageOwnership::AtlasView)));
    request.asset_id = 8;
    assert!(frontend
        .preflight_mesh_atlas_commands(None, &[request])
        .is_err());
    frontend.reset(&mut gal).unwrap();
    gal.destroy(target).unwrap();
    // Creating the owned image submitted an upload. Destruction must
    // remain deferred until that actual submission has completed.
    let completed = gal.latest_submission_id();
    gal.mock_backend_mut().unwrap().complete_through(completed);
    gal.retire_through(completed).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn model_overlay_passes_finish_before_foil_without_changing_authored_layers() {
    let requests: Vec<_> = (0..6)
        .map(|i| {
            let mut request = mesh_batch(i);
            request.item_raster_scale = 3;
            request.render_extent = [0, 0];
            request.guard_pixels = 0;
            request.model_transform = [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
            ];
            request.material_mode = if i == 2 {
                GuiMeshMaterialMode::Glint
            } else if i >= 3 {
                GuiMeshMaterialMode::ModelOverlay
            } else {
                GuiMeshMaterialMode::Opaque
            };
            request.lighting_mode = if i == 2 {
                GuiMeshLightingMode::Flat
            } else {
                GuiMeshLightingMode::FrontModel
            };
            request.alpha_cutoff = if i == 2 { 0.1 } else { 0.0 };
            if i == 2 {
                request.item_foil = Some(crate::render::shared::item_foil::StandardItemFoil {
                    kind: crate::render::shared::item_foil::StandardFoilKind::Entity,
                    clock_millis: 0,
                    speed: 0.,
                    strength: 0.5,
                });
            } else {
                request.item_lighting =
                    Some(crate::render::guirender::mesh::GuiFlatItemLighting {
                        lightmap_generation: 1,
                        rgb: [1.; 3],
                    });
            }
            request
        })
        .collect();
    let mut draws = prepare_gui_mesh_draws(&requests).unwrap();
    validate_mesh_item_layers(&draws).unwrap();
    assert_eq!(
        mesh_item_layer_execution_order(&draws),
        vec![0, 1, 3, 4, 5, 2]
    );
    assert_eq!(
        draws
            .iter()
            .map(|draw| draw.layer_index)
            .collect::<Vec<_>>(),
        vec![0, 1, 2, 3, 4, 5]
    );
    // This dependency must not reorder unrelated authored item layers.
    for draw in &mut draws {
        if draw.material_mode == GuiMeshMaterialMode::ModelOverlay {
            draw.material_mode = GuiMeshMaterialMode::Translucent;
        }
    }
    assert_eq!(
        mesh_item_layer_execution_order(&draws),
        vec![0, 1, 2, 3, 4, 5]
    );
}

fn mesh_batch(layer_index: u32) -> GuiMeshBatchRequest {
    GuiMeshBatchRequest {
        item_cache: None,
        persistent_geometry: None,
        block_item_raster: None,
        item_raster_scale: 0,
        item_lighting: None,
        item_foil: None,
        decal_foil: None,
        stratum: 420,
        layer_index,
        sequence: 9,
        asset_id: 7,
        material_mode: GuiMeshMaterialMode::Cutout,
        lighting_mode: GuiMeshLightingMode::Block,
        alpha_cutoff: 0.5,
        model_transform: [
            16.0, 0.0, 0.0, 0.0, 0.0, -16.0, 0.0, 0.0, 0.0, 0.0, 16.0, 0.0, 17.0, 17.0, 0.0,
            1.0,
        ],
        gui_pose: [1.0, 0.0, 0.0, 1.0, 0.0, 0.0],
        bounds: [12, 34, 28, 50],
        gui_extent: [320, 180],
        projection_extent: [320.0, 180.0],
        render_extent: [34, 34],
        guard_pixels: 1,
        clip_mode: 0,
        clip_left: 0,
        clip_top: 0,
        clip_width: 0,
        clip_height: 0,
        vertices: vec![
            GuiMeshVertex {
                position: [0.0, 0.0, 0.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [0.0, 0.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [1.0, 0.0, 0.0],
                atlas_uv: [1.0, 0.0],
                local_uv: [1.0, 0.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [0.0, 1.0, 0.0],
                atlas_uv: [0.0, 1.0],
                local_uv: [0.0, 1.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
        ].into(),
        indices: vec![0, 1, 2].into(),
    }
}

#[test]
fn mesh_geometry_reuses_only_ranges_released_by_completed_submission() {
    let batches = vec![mesh_batch(0)];
    let prepared = prepare_gui_mesh_draws(&batches)
        .expect("prepare mesh fixture")
        .pop()
        .expect("one prepared mesh draw");
    let key = gui_mesh_raster_key(&prepared);
    let mut frontend = GuiFrontend::default();
    let mut gal = mock_gal();
    let first = frontend
        .allocate_mesh_geometry(&mut gal, key, 1_024, 256)
        .expect("first private stream range");
    first.usage.accept_for_test(SubmissionId(3));
    frontend.mesh_geometry_cache.insert((key, 1, 0), first);
    let second = frontend
        .allocate_mesh_geometry(&mut gal, key, 1_024, 256)
        .expect("second private stream range");
    second.usage.accept_for_test(SubmissionId(4));
    frontend.mesh_geometry_cache.insert((key, 2, 0), second);

    frontend.reclaim_completed_mesh_geometry(SubmissionId(2));
    let while_in_flight = frontend
        .allocate_mesh_geometry(&mut gal, key, 1_024, 256)
        .expect("a distinct range while earlier work is in flight");
    assert_eq!(2_048, while_in_flight.stream.vertex_offset);
    assert_eq!(512, while_in_flight.stream.index_offset);

    frontend.reclaim_completed_mesh_geometry(SubmissionId(3));
    let reused_after_completion = frontend
        .allocate_mesh_geometry(&mut gal, key, 1_024, 256)
        .expect("a completed range is reusable");
    assert_eq!(0, reused_after_completion.stream.vertex_offset);
    assert_eq!(0, reused_after_completion.stream.index_offset);
}

#[test]
fn mesh_geometry_applies_bounded_backpressure_before_stream_exhaustion() {
    let batches = vec![mesh_batch(0)];
    let prepared = prepare_gui_mesh_draws(&batches)
        .expect("prepare mesh fixture")
        .pop()
        .expect("one prepared mesh draw");
    let key = gui_mesh_raster_key(&prepared);
    let mut frontend = GuiFrontend::default();
    let mut gal = mock_gal();
    let reserved = gal.next_submission_id();
    let full = frontend
        .allocate_mesh_geometry(
            &mut gal,
            key,
            crate::render::guirender::mesh::GUI_MESH_MAX_VERTEX_BYTES,
            crate::render::guirender::mesh::GUI_MESH_MAX_INDEX_BYTES,
        )
        .expect("the fixed stream admits one full allocation");
    let reservation_command = CommandOp::TrackSubmission(full.usage.clone());
    frontend.mesh_geometry_cache.insert((key, 1, 0), full);
    let accepted = gal
        .submit(SubmissionBatch {
            label: "accepted-stream-reservation".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "allocator-completion-fixture".into(),
                operations: vec![reservation_command],
            })],
        })
        .unwrap();
    assert_eq!(reserved, accepted.submission);

    let after_wait = frontend
        .allocate_mesh_geometry(&mut gal, key, 48, 4)
        .expect("the allocator retires the oldest range instead of growing or failing");
    assert_eq!(0, after_wait.stream.vertex_offset);
    assert_eq!(0, after_wait.stream.index_offset);
}

#[test]
fn mesh_geometry_never_retires_an_unsubmitted_frame_reservation() {
    let batches = vec![mesh_batch(0)];
    let prepared = prepare_gui_mesh_draws(&batches)
        .expect("prepare mesh fixture")
        .pop()
        .expect("one prepared mesh draw");
    let key = gui_mesh_raster_key(&prepared);
    let mut frontend = GuiFrontend::default();
    let mut gal = mock_gal();
    let reservation = frontend
        .allocate_mesh_geometry(
            &mut gal,
            key,
            crate::render::guirender::mesh::GUI_MESH_MAX_VERTEX_BYTES,
            crate::render::guirender::mesh::GUI_MESH_MAX_INDEX_BYTES,
        )
        .expect("the current frame can reserve the stream");
    let _pending_command = CommandOp::TrackSubmission(reservation.usage.clone());
    frontend
        .mesh_geometry_cache
        .insert((key, 1, 0), reservation);

    let error = frontend
        .allocate_mesh_geometry(&mut gal, key, 48, 4)
        .expect_err("a second current-frame allocation cannot retire future work");
    assert_eq!(StatusCode::InvalidArgument, error.code);
    assert_eq!(
        SubmissionId(0),
        gal.poll_completed(),
        "a failed current-frame reservation must not advance completion"
    );
}

#[test]
fn rotating_panorama_meshes_stay_within_the_bounded_rust_stream() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 6,
                pixels: vec![255; 24],
            }],
        )
        .expect("stage the Rust-owned stacked cube-map image");

    // The semantic panorama is Frozen's oversized fullscreen triangle. Its
    // three camera rays
    // change with the camera, while Rust resolves the cube face per pixel.
    for frame in 1..=10u64 {
        let mut panorama = mesh_batch(0);
        panorama.sequence = frame;
        panorama.material_mode = GuiMeshMaterialMode::Panorama;
        panorama.lighting_mode = GuiMeshLightingMode::Flat;
        panorama.alpha_cutoff = 0.0;
        panorama.model_transform = [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ];
        *panorama.vertices = vec![
            GuiMeshVertex {
                position: [0.0, 1.0, frame as f32 / 10.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [-1.0, -1.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [2.0, 1.0, frame as f32 / 10.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [3.0, -1.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [0.0, -1.0, frame as f32 / 10.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [-1.0, 3.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
        ];
        *panorama.indices = vec![0, 1, 2];
        let stats = frontend
            .submit_frame_with_affine_quads_and_mesh_batches(
                &mut gal,
                1,
                target,
                Vec::new(),
                Vec::new(),
                vec![panorama],
            )
            .expect("animated panorama frame must not exhaust persistent geometry");
        assert_eq!(1, stats.mesh_batch_count);
        assert_eq!(
            1,
            stats.mesh_draw_count,
            "Frozen's semantic panorama is a direct native-resolution frame draw, not a PIP raster plus composite"
        );
        assert!(
            stats.owned_intermediate_targets.is_empty(),
            "a panorama must not allocate a logical-GUI-sized intermediate before frame presentation"
        );
    }
    assert!(
        gal.metrics().submissions >= 10,
        "every rotating semantic panorama frame must be submitted by Rust"
    );
}

fn mesh_item_batches(sequence: u64) -> Vec<GuiMeshBatchRequest> {
    let mut layers = vec![mesh_batch(0), mesh_batch(1), mesh_batch(2)];
    for layer in &mut layers {
        layer.sequence = sequence;
    }
    layers
}

#[test]
fn mesh_raster_cache_never_reuses_one_uniform_buffer_for_different_pip_inputs() {
    let first = prepare_gui_mesh_draws(&[mesh_batch(0)])
        .expect("prepare first GUI mesh draw")
        .pop()
        .unwrap();
    let mut changed_extent_batch = mesh_batch(0);
    changed_extent_batch.render_extent = [66, 34];
    let changed_extent = prepare_gui_mesh_draws(&[changed_extent_batch])
        .expect("prepare changed-extent GUI mesh draw")
        .pop()
        .unwrap();
    let mut changed_lighting_batch = mesh_batch(0);
    changed_lighting_batch.lighting_mode = GuiMeshLightingMode::Flat;
    let changed_lighting = prepare_gui_mesh_draws(&[changed_lighting_batch])
        .expect("prepare changed-lighting GUI mesh draw")
        .pop()
        .unwrap();

    assert_ne!(
        gui_mesh_raster_key(&first),
        gui_mesh_raster_key(&changed_extent)
    );
    assert_ne!(
        gui_mesh_raster_key(&first),
        gui_mesh_raster_key(&changed_lighting)
    );
    assert_eq!(gui_mesh_raster_key(&first), gui_mesh_raster_key(&first));
}

#[test]
fn inventory_mesh_atlas_uses_explicit_owner_and_native_sprite_uv_mapping() {
    for (block_lit, overlay) in [(false, false), (true, false), (false, true)] {
        use crate::render::worldrender::WorldMeshTextureAssetPayload;
        use crate::render::worldrender::WorldPrimitiveFrontend;
        use crate::render::scene::textures::WORLD_MATERIAL_TEXTURE_STONE;
        let mut gal = mock_gal();
        let target = frame_target(&mut gal);
        let mut world = WorldPrimitiveFrontend::default();
        let mut png_bytes = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut png_bytes, 2, 1);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            encoder
                .write_header()
                .unwrap()
                .write_image_data(&[255, 0, 0, 255, 0, 255, 0, 255])
                .unwrap();
        }
        world
            .apply_world_mesh_asset_update(
                &mut gal,
                1,
                Vec::new(),
                vec![WorldMeshTextureAssetPayload {
                    texture_id: WORLD_MATERIAL_TEXTURE_STONE,
                    png_bytes,
                    mip_png_bytes: Vec::new(),
                    frame_width: 0,
                    frame_height: 0,
                    frame_count: 1,
                    frame_ticks: 1,
                    animation_flags: 0,
                    frame_row_size: 0,
                    interpolation_policy: 0,
                    animation_frames: Vec::new(),
                    coordinate_origin: 0,
                    sampling: None,
                    requested_mip_levels: 1,
                }],
            )
            .unwrap();
        let mut frontend = GuiFrontend::default();
        let reference = GuiAtlasReference {
            asset_id: 7,
            atlas: world
                .accepted_gui_atlas_incarnation(WORLD_MATERIAL_TEXTURE_STONE)
                .unwrap(),
            x: 1,
            y: 0,
            width: 1,
            height: 1,
        };
        frontend
            .stage_owned_atlas_references(&mut gal, &world, 1, &[reference])
            .unwrap();
        let mut request = mesh_batch(0);
        request.item_raster_scale = 2;
        request.render_extent = [0, 0];
        request.guard_pixels = 0;
        request.model_transform =
            crate::render::guirender::items::raster::GuiItemModelTransform::default().0;
        request.lighting_mode = GuiMeshLightingMode::Flat;
        request.item_lighting = Some(crate::render::guirender::mesh::GuiFlatItemLighting {
            lightmap_generation: 1,
            rgb: [1.0; 3],
        });
        if block_lit {
            request.item_raster_scale = 0;
            request.render_extent = [34, 34];
            request.guard_pixels = 1;
            request.lighting_mode = GuiMeshLightingMode::InventoryBlock;
        }
        if overlay {
            request.material_mode = GuiMeshMaterialMode::ModelOverlay;
            request.lighting_mode = GuiMeshLightingMode::FrontModel;
            request.alpha_cutoff = 0.0;
            let mut wrong_lighting = request.clone();
            wrong_lighting.lighting_mode = GuiMeshLightingMode::Flat;
            assert!(frontend
                .preflight_mesh_atlas_commands(Some(&world), &[wrong_lighting])
                .is_err());
            let mut wrong_cutout = request.clone();
            wrong_cutout.alpha_cutoff = 0.1;
            assert!(frontend
                .preflight_mesh_atlas_commands(Some(&world), &[wrong_cutout])
                .is_err());
        }
        let before = gal.metrics().resource_creates;
        let mut invalid = request.clone();
        invalid.asset_id = 999;
        if !block_lit {
            assert!(frontend
                .preflight_mesh_atlas_commands(Some(&world), &[invalid])
                .is_err());
        }
        let mut unsupported = request.clone();
        unsupported.item_raster_scale = 0;
        unsupported.lighting_mode = GuiMeshLightingMode::Block;
        assert!(frontend
            .preflight_mesh_atlas_commands(Some(&world), &[unsupported])
            .is_err());
        let mut foil = request.clone();
        foil.material_mode = GuiMeshMaterialMode::Glint;
        assert!(frontend
            .preflight_mesh_atlas_commands(Some(&world), &[foil])
            .is_err());
        assert!(frontend
            .append_frame_ops_with_owned_atlases_and_blur_boundary(
                &mut gal,
                None,
                1,
                target,
                target,
                Vec::new(),
                Vec::new(),
                vec![request.clone()],
                Vec::new(),
                400,
                2,
                false
            )
            .is_err());
        assert_eq!(before, gal.metrics().resource_creates);
        assert!(frontend
            .append_mesh_items_to_target(
                &mut gal,
                None,
                1,
                target,
                target,
                None,
                None,
                None,
                vec![request.clone()],
                &mut GuiSubmitStats::default()
            )
            .is_err());
        assert_eq!(before, gal.metrics().resource_creates);
        let mut stats = GuiSubmitStats::default();
        let ops = frontend
            .append_mesh_items_to_target(
                &mut gal,
                Some(&mut world),
                1,
                target,
                target,
                None,
                None,
                None,
                vec![request],
                &mut stats,
            )
            .unwrap();
        assert_eq!(stats.mesh_item_count, 1);
        assert!(frontend.raw_images.is_empty());
        assert!(frontend.dynamic_textures.is_empty());
        assert!(frontend
            .resources
            .values()
            .all(|binding| matches!(binding.image_ownership, GuiImageOwnership::AtlasView)));
        let writes: Vec<_> = ops
            .iter()
            .filter_map(|op| match op {
                CommandOp::HostWriteBuffer { data, .. } if data.len() == 3 * 48 => Some(data),
                _ => None,
            })
            .collect();
        assert_eq!(writes.len(), 1);
        let values: Vec<_> = writes[0]
            .chunks_exact(4)
            .map(|v| f32::from_le_bytes(v.try_into().unwrap()))
            .collect();
        assert_eq!(
            values[3], 0.5,
            "local U=0 resolves to the right sprite's start"
        );
        assert_eq!(
            values[15], 1.0,
            "local U=1 resolves to the right sprite's end"
        );
        frontend
            .invalidate_atlas_texture_views(&mut gal, [WORLD_MATERIAL_TEXTURE_STONE])
            .unwrap();
        assert!(frontend.atlas_views.is_empty());
        assert!(frontend.mesh_rasters.is_empty());
        frontend.reset(&mut gal).unwrap();
    }
}

#[test]
fn cached_gui_items_survive_other_items_in_the_same_frame() {
    use crate::render::guirender::mesh::GuiItemCache;
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend.apply_raw_image_update(&mut gal, 1, vec![GuiRawImageAssetPayload {
        sampling: None, asset_id: 7, format: GuiRawImageSourceFormat::Rgba8,
        width: 1, height: 1, pixels: vec![255; 4],
    }]).unwrap();
    for step in 0..3 {
        let batches = [1, if step == 2 { 3 } else { 2 }].map(|identity| {
            let mut request = mesh_batch(0);
            request.sequence = identity;
            request.item_cache = Some(GuiItemCache { identity, animated: false });
            request.item_raster_scale = 1;
            request.render_extent = [0, 0];
            request.guard_pixels = 0;
            request.lighting_mode = GuiMeshLightingMode::FrontModel;
            request.item_lighting = Some(crate::render::guirender::items::material::GuiFlatItemLighting {
                lightmap_generation: 1, rgb: [1.0; 3],
            });
            request.model_transform = [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
            ];
            request
        });
        let (operations, stats) = frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                &mut gal, 1, target, target, None, None, None, false,
                vec![], vec![], batches.into(),
            ).unwrap();
        assert_eq!(stats.mesh_item_count, 2);
        assert_eq!(stats.mesh_batch_count, [2, 0, 1][step],
            "unchanged cached items must survive other items and one identity replacement");
        let token = gal.submit(SubmissionBatch {
            label: "cached multi-item frame".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "cached multi-item commands".into(), operations,
            })],
        }).unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
    }
    frontend.reset(&mut gal).unwrap();
    gal.destroy(target).unwrap();
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn vulkan_item_cache_retains_pixels_moves_composition_and_invalidates_identity_and_reload() {
    use crate::render::guirender::items::raster::GuiItemRasterTarget;
    use crate::render::guirender::mesh::GuiItemCache;
    for animated in [false, true] {
        let mut gal = vulkan_gal("GUI item cache pixel lifetime").unwrap();
        let mut frontend = GuiFrontend::default();
        let extent = Extent3d {
            width: 32,
            height: 16,
            depth: 1,
        };
        let target = GuiItemRasterTarget::create(&mut gal, extent).unwrap();
        let readback = gal
            .create_buffer(BufferDesc {
                label: "item-cache.readback".into(),
                size: 32 * 16 * 4,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let mut first_pixels = Vec::new();
        let mut second_pixels = Vec::new();
        for step in 0..=4 {
            let generation = if step == 4 { 2 } else { 1 };
            if step == 0 || step == 4 {
                frontend
                    .apply_raw_image_update(
                        &mut gal,
                        generation,
                        vec![GuiRawImageAssetPayload {
                            sampling: None,
                            asset_id: 7,
                            format: GuiRawImageSourceFormat::Rgba8,
                            width: 1,
                            height: 1,
                            pixels: if step == 0 {
                                vec![255, 0, 0, 255]
                            } else {
                                vec![0, 0, 255, 255]
                            },
                        }],
                    )
                    .unwrap();
            }
            if step == 1 {
                // Simulate an in-place owned-atlas animation upload. This
                // must not invalidate a static item's already rasterized pixels.
                let source = frontend.dynamic_textures[&(7, GuiRawImageFormat::Rgba8)];
                gal.submit(SubmissionBatch {
                    label: "source mutation".into(),
                    command_lists: vec![CommandList::from(CommandListDesc {
                        label: "owned texture mutation".into(),
                        operations: vec![
                            CommandOp::Barrier(buffer_barrier(
                                source.upload_buffer,
                                TextureUsageState::TransferSrc,
                                TextureUsageState::TransferDst,
                            )),
                            CommandOp::HostWriteBuffer {
                                buffer: source.upload_buffer,
                                offset: 0,
                                data: vec![0, 255, 0, 255],
                            },
                            CommandOp::Barrier(buffer_barrier(
                                source.upload_buffer,
                                TextureUsageState::TransferDst,
                                TextureUsageState::TransferSrc,
                            )),
                            CommandOp::Barrier(texture_barrier(
                                source.texture,
                                TextureUsageState::ShaderRead,
                                TextureUsageState::TransferDst,
                            )),
                            CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                                buffer: source.upload_buffer,
                                buffer_offset: 0,
                                bytes_per_row: 4,
                                rows_per_image: 1,
                                texture: source.texture,
                                texture_mip: 0,
                                texture_layer: 0,
                                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                                extent: Extent3d {
                                    width: 1,
                                    height: 1,
                                    depth: 1,
                                },
                            }),
                            CommandOp::Barrier(texture_barrier(
                                source.texture,
                                TextureUsageState::TransferDst,
                                TextureUsageState::ShaderRead,
                            )),
                        ],
                    })],
                })
                .unwrap();
            }
            let mut request = mesh_batch(0);
            request.item_cache = Some(GuiItemCache {
                identity: if step == 3 { 2 } else { 1 },
                animated,
            });
            request.item_raster_scale = 1;
            request.render_extent = [0, 0];
            request.guard_pixels = 0;
            request.lighting_mode = GuiMeshLightingMode::FrontModel;
            request.item_lighting = Some(
                crate::render::guirender::items::material::GuiFlatItemLighting {
                    lightmap_generation: 1,
                    rgb: [1.0; 3],
                },
            );
            request.model_transform = [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
            ];
            for (vertex, position) in request.vertices.iter_mut().zip([
                [-0.5, -0.5, 0.],
                [0.5, -0.5, 0.],
                [-0.5, 0.5, 0.],
            ]) {
                vertex.position = position;
            }
            request.bounds = if step == 2 {
                [16, 0, 32, 16]
            } else {
                [0, 0, 16, 16]
            };
            request.gui_extent = [32, 16];
            request.projection_extent = [32., 16.];
            let (draws, stats) = frontend
                .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                    &mut gal,
                    generation,
                    target.target,
                    target.view,
                    Some(target.pass),
                    None,
                    None,
                    false,
                    vec![],
                    vec![],
                    vec![request],
                )
                .unwrap();
            let mut ops = vec![
                CommandOp::Barrier(texture_barrier(
                    target.color,
                    if step == 0 {
                        TextureUsageState::Undefined
                    } else {
                        TextureUsageState::TransferSrc
                    },
                    TextureUsageState::ColorAttachment,
                )),
                CommandOp::BeginPass {
                    pass: target.pass,
                    target: target.target,
                    colors: vec![PassAttachment {
                        view: target.view,
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.,
                            g: 0.,
                            b: 0.,
                            a: 1.,
                        }),
                    }],
                    depth_stencil: None,
                },
                CommandOp::EndPass,
            ];
            ops.extend(draws);
            ops.extend([
                CommandOp::Barrier(texture_barrier(
                    target.color,
                    TextureUsageState::ColorAttachment,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
                    buffer: readback,
                    buffer_offset: 0,
                    bytes_per_row: 32 * 4,
                    rows_per_image: 16,
                    texture: target.color,
                    texture_mip: 0,
                    texture_layer: 0,
                    texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                    extent,
                }),
                CommandOp::Barrier(buffer_barrier(
                    readback,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::HostReadBuffer {
                    buffer: readback,
                    offset: 0,
                    size: 32 * 16 * 4,
                },
            ]);
            let token = gal
                .submit(SubmissionBatch {
                    label: "cached item frame".into(),
                    command_lists: vec![CommandList::from(CommandListDesc {
                        label: "cached item commands".into(),
                        operations: ops,
                    })],
                })
                .unwrap();
            gal.retire_through_for_test(token.submission).unwrap();
            let pixels = gal
                .completed_host_reads()
                .iter()
                .rev()
                .find(|read| read.buffer == readback)
                .unwrap()
                .bytes
                .clone();
            let channel = if step == 4 {
                2
            } else if step == 3 || animated && step > 0 {
                1
            } else {
                0
            };
            assert!(pixels.chunks_exact(4).filter(|p|p[channel]>32).count()>40,
                "step {step} animated={animated}: actual raster must contain the expected colored geometry");
            assert!(pixels
                .chunks_exact(4)
                .all(|p| (0..3).all(|c| c == channel || p[c] == 0)));
            assert_eq!(
                stats.mesh_batch_count,
                u64::from(animated || step == 0 || step >= 3),
                "static pixels must be reused; animated/identity/reload paths must redraw"
            );
            if step == 0 {
                first_pixels = pixels.clone();
            }
            if step == 1 {
                if !animated {
                    assert_eq!(pixels, first_pixels);
                }
                second_pixels = pixels.clone();
            }
            if step == 2 {
                for y in 0..16 {
                    for x in 0..16 {
                        assert_eq!(
                            &pixels[(y * 32 + x + 16) * 4..][..4],
                            &second_pixels[(y * 32 + x) * 4..][..4]
                        );
                        assert_eq!(&pixels[(y * 32 + x) * 4..][..3], &[0, 0, 0]);
                    }
                }
            }
        }
        frontend.reset(&mut gal).unwrap();
        target.destroy(&mut gal).unwrap();
        gal.destroy(readback).unwrap();
        gal.retire_through(gal.latest_submission_id()).unwrap();
        assert_eq!(
            gal.metrics().resource_creates,
            gal.metrics().resource_destroys
        );
    }
}

#[test]
fn vulkan_pending_mesh_preparations_match_separate_submissions() {
    use crate::render::guirender::items::raster::GuiItemRasterTarget;
    use crate::render::guirender::mesh::GuiItemCache;
    let mut reference = Vec::new();
    for combined in [false, true] {
        let mut gal = vulkan_gal("pending GUI stream pixel regression").unwrap();
        let mut frontend = GuiFrontend::default();
        let extent = Extent3d {
            width: 32,
            height: 16,
            depth: 1,
        };
        let target = GuiItemRasterTarget::create(&mut gal, extent).unwrap();
        let readback = gal
            .create_buffer(BufferDesc {
                label: "pending-mesh.readback".into(),
                size: 32 * 16 * 4,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 7,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![255; 4],
                }],
            )
            .unwrap();
        let mut ops = vec![
            CommandOp::Barrier(texture_barrier(
                target.color,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            )),
            CommandOp::BeginPass {
                pass: target.pass,
                target: target.target,
                colors: vec![PassAttachment {
                    view: target.view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 0.,
                        g: 0.,
                        b: 0.,
                        a: 1.,
                    }),
                }],
                depth_stencil: None,
            },
            CommandOp::EndPass,
        ];
        for item in 0..2 {
            let mut request = mesh_batch(0);
            request.item_cache = Some(GuiItemCache {
                identity: item + 1,
                animated: true,
            });
            request.item_raster_scale = 1;
            request.render_extent = [0, 0];
            request.guard_pixels = 0;
            request.lighting_mode = GuiMeshLightingMode::FrontModel;
            request.item_lighting = Some(
                crate::render::guirender::items::material::GuiFlatItemLighting {
                    lightmap_generation: 1,
                    rgb: [1.0; 3],
                },
            );
            request.model_transform = [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
            ];
            for (vertex, position) in request.vertices.iter_mut().zip([
                [-0.5, -0.5, 0.],
                [0.5, -0.5, 0.],
                [-0.5, 0.5, 0.],
            ]) {
                vertex.position = position;
                vertex.color_argb = if item == 0 { 0xffff0000 } else { 0xff00ff00 };
            }
            request.bounds = if item == 0 {
                [0, 0, 16, 16]
            } else {
                [16, 0, 32, 16]
            };
            request.gui_extent = [32, 16];
            request.projection_extent = [32., 16.];
            let (draws, _) = frontend
                .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                    &mut gal,
                    1,
                    target.target,
                    target.view,
                    Some(target.pass),
                    None,
                    None,
                    false,
                    vec![],
                    vec![],
                    vec![request],
                )
                .unwrap();
            ops.extend(draws);
            if item == 0 {
                if !combined {
                    gal.submit(SubmissionBatch {
                        label: "separate first draw".into(),
                        command_lists: vec![CommandList::from(CommandListDesc {
                            label: "first draw".into(),
                            operations: std::mem::take(&mut ops),
                        })],
                    })
                    .unwrap();
                }
                // In the combined case only internal uploads have been
                // accepted. The first draw remains pending while they finish.
                gal.retire_through(gal.latest_submission_id()).unwrap();
            }
        }
        ops.extend([
            CommandOp::Barrier(texture_barrier(
                target.color,
                TextureUsageState::ColorAttachment,
                TextureUsageState::TransferSrc,
            )),
            CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
                buffer: readback,
                buffer_offset: 0,
                bytes_per_row: 32 * 4,
                rows_per_image: 16,
                texture: target.color,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            }),
            CommandOp::Barrier(buffer_barrier(
                readback,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )),
            CommandOp::HostReadBuffer {
                buffer: readback,
                offset: 0,
                size: 32 * 16 * 4,
            },
        ]);
        let accepted = gal
            .submit(SubmissionBatch {
                label: "pending GUI frame".into(),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: "pending GUI draws".into(),
                    operations: ops,
                })],
            })
            .unwrap();
        gal.retire_through_for_test(accepted.submission).unwrap();
        let pixels = gal
            .completed_host_reads()
            .iter()
            .rev()
            .find(|read| read.buffer == readback)
            .unwrap()
            .bytes
            .clone();
        assert!(
            pixels
                .chunks_exact(4)
                .filter(|p| p[0] > 32 && p[1] == 0)
                .count()
                > 40
        );
        assert!(
            pixels
                .chunks_exact(4)
                .filter(|p| p[1] > 32 && p[0] == 0)
                .count()
                > 40
        );
        if combined {
            assert_eq!(
                reference, pixels,
                "pending preparations must preserve both meshes"
            );
        } else {
            reference = pixels;
        }
        frontend.reset(&mut gal).unwrap();
        target.destroy(&mut gal).unwrap();
        gal.destroy(readback).unwrap();
        gal.retire_through(gal.latest_submission_id()).unwrap();
        assert_eq!(
            gal.metrics().resource_creates,
            gal.metrics().resource_destroys
        );
    }
}

#[test]
fn pending_mesh_stream_survives_interleaved_upload_completion() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![255; 4],
            }],
        )
        .unwrap();
    let prepare = |frontend: &mut GuiFrontend, gal: &mut VulkanicGal| {
        frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                gal,
                1,
                target,
                target,
                None,
                None,
                None,
                false,
                Vec::new(),
                Vec::new(),
                vec![mesh_batch(0)],
            )
            .unwrap()
            .0
    };
    let first = prepare(&mut frontend, &mut gal);
    let vertex_range = |ops: &[CommandOp]| {
        ops.iter()
            .find_map(|op| match op {
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset,
                    data,
                } if data.len() == 3 * 48 => Some((*buffer, *offset)),
                _ => None,
            })
            .expect("mesh vertex stream write")
    };
    let first_range = vertex_range(&first);
    // Preparation uploads GUI assets internally, consuming submission IDs
    // before these still-pending draw commands can be submitted.
    let uploads = gal.latest_submission_id();
    assert!(
        uploads.0 > 0,
        "fixture must actually submit internal uploads"
    );
    gal.retire_through(uploads).unwrap();
    let second = prepare(&mut frontend, &mut gal);
    assert_ne!(
        first_range,
        vertex_range(&second),
        "completed asset uploads must not release a pending draw's stream"
    );
    let second_range = vertex_range(&second);
    drop((first, second));
    let retry = prepare(&mut frontend, &mut gal);
    // Neither discarded preparation wrote its range, so the retry uploads
    // again into one of the released reservations rather than a third range.
    assert!(
        [first_range, second_range].contains(&vertex_range(&retry)),
        "discarded preparations must release reservations without a fake submission"
    );
    let accepted = gal
        .submit(SubmissionBatch {
            label: "delayed mesh draw".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "delayed mesh commands".into(),
                operations: retry,
            })],
        })
        .unwrap();
    assert!(accepted.submission > uploads);
    frontend.reclaim_completed_mesh_geometry(uploads);
    assert_eq!(
        frontend.mesh_geometry_cache.len(),
        1,
        "the draw's actual accepted submission must outlive the upload completion"
    );
    gal.retire_through(accepted.submission).unwrap();
    frontend.reclaim_completed_mesh_geometry(gal.poll_completed());
    assert!(frontend.mesh_geometry_cache.is_empty());
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn accepted_mesh_geometry_stays_resident_across_frames_until_idle() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![255; 4],
            }],
        )
        .unwrap();
    let prepare = |frontend: &mut GuiFrontend, gal: &mut VulkanicGal, batches: Vec<GuiMeshBatchRequest>| {
        frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                gal, 1, target, target, None, None, None, false, Vec::new(), Vec::new(), batches,
            )
            .unwrap()
            .0
    };
    let uploads_vertices = |ops: &[CommandOp]| {
        ops.iter().any(|op| matches!(op, CommandOp::HostWriteBuffer { data, .. } if data.len() == 3 * 48))
    };
    let submit = |gal: &mut VulkanicGal, operations: Vec<CommandOp>| {
        let token = gal
            .submit(SubmissionBatch {
                label: "resident mesh frame".into(),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: "resident mesh commands".into(),
                    operations,
                })],
            })
            .unwrap();
        gal.retire_through(token.submission).unwrap();
    };
    let first = prepare(&mut frontend, &mut gal, vec![mesh_batch(0)]);
    assert!(uploads_vertices(&first));
    submit(&mut gal, first);
    // The next frame draws the same geometry from the accepted upload.
    let second = prepare(&mut frontend, &mut gal, vec![mesh_batch(0)]);
    assert!(!uploads_vertices(&second), "unchanged accepted GUI geometry must not re-upload");
    submit(&mut gal, second);
    // Unused for the idle window, the completed range is released; frames
    // keep drawing other geometry meanwhile.
    let mut other = mesh_batch(0);
    other.vertices[0].position[0] += 0.25;
    for _ in 0..=GUI_MESH_GEOMETRY_IDLE_TRANSACTIONS + 1 {
        let frame = prepare(&mut frontend, &mut gal, vec![other.clone()]);
        submit(&mut gal, frame);
    }
    assert_eq!(frontend.mesh_geometry_cache.len(), 1, "only the still-drawn geometry stays resident");
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn discarded_mesh_preparation_does_not_publish_attachment_layout() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![255; 4],
            }],
        )
        .unwrap();
    let prepare = |frontend: &mut GuiFrontend, gal: &mut VulkanicGal| {
        frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                gal,
                1,
                target,
                target,
                None,
                None,
                None,
                false,
                Vec::new(),
                Vec::new(),
                vec![mesh_batch(0)],
            )
            .unwrap()
            .0
    };
    let discarded = prepare(&mut frontend, &mut gal);
    let depth = discarded
        .iter()
        .find_map(|op| match op {
            CommandOp::Barrier(barrier)
                if barrier.after == TextureUsageState::DepthStencilAttachment =>
            {
                Some(barrier.resource)
            }
            _ => None,
        })
        .expect("private raster depth attachment");
    drop(discarded);
    let unsubmitted_retry = prepare(&mut frontend, &mut gal);
    assert!(
        unsubmitted_retry.iter().any(|op| matches!(op,
        CommandOp::HostWriteBuffer { data, .. } if data.len() == 3 * 48)),
        "a discarded preparation cannot satisfy the retry's vertex upload"
    );
    assert!(
        unsubmitted_retry.iter().any(|op| matches!(op,
        CommandOp::HostWriteBuffer { data, .. } if data.len() == 3 * 4)),
        "a discarded preparation cannot satisfy the retry's index upload"
    );
    drop(unsubmitted_retry);
    // Even consuming its predicted ID elsewhere proves nothing about
    // this target's texture layouts.
    gal.submit(SubmissionBatch {
        label: "unrelated accepted submission".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "empty unrelated list".into(),
            operations: vec![],
        })],
    })
    .unwrap();
    let retry = prepare(&mut frontend, &mut gal);
    assert!(retry.iter().any(|op| matches!(op,
        CommandOp::Barrier(barrier) if barrier.resource == depth
            && barrier.before == TextureUsageState::Undefined
            && barrier.after == TextureUsageState::DepthStencilAttachment)));
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn owned_mesh_items_rasterize_layers_then_compose_once_into_the_gui_target() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![255, 255, 255, 255],
            }],
        )
        .expect("stage owned mesh image");
    let affine = |sequence, x| GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
        stratum: 420,
        asset_id: 7,
        x0: x,
        y0: 4.0,
        x1: x + 8.0,
        y1: 4.0,
        x3: x,
        y3: 12.0,
        z: 0.0,
        u0: 0.0,
        v0: 0.0,
        u1: 1.0,
        v1: 1.0,
        color_argb: 0xffff_ffff,
        gui_width: 320,
        gui_height: 180,
        projection_extent: [320.0, 180.0],
        sequence,
        clip_mode: 0,
        clip_left: 0,
        clip_top: 0,
        clip_width: 0,
        clip_height: 0,
    };
    let (ops, stats) = frontend
        .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
            &mut gal,
            1,
            target,
            target,
            None,
            None,
            None,
            false,
            Vec::new(),
            vec![affine(8, 2.0), affine(10, 20.0)],
            vec![mesh_batch(0), mesh_batch(1), mesh_batch(2)],
        )
        .expect("append ordered GUI item mesh layers");
    assert_eq!(1, stats.mesh_item_count);
    assert_eq!(3, stats.mesh_batch_count);
    assert_eq!(4, stats.mesh_draw_count);
    assert_eq!(
        1,
        stats.owned_intermediate_targets.len(),
        "the GUI frontend must explicitly report its one Rust-owned raster target"
    );
    assert_ne!(target, stats.owned_intermediate_targets[0]);
    assert_eq!(
        3,
        ops.iter()
            .filter(|op| matches!(
                op,
                CommandOp::DrawIndexed {
                    indices: 3,
                    instances: 1
                }
            ))
            .count()
    );
    let vertex_writes = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::HostWriteBuffer { offset, data, .. } if data.len() == 3 * 48 => {
                Some((*offset, data.clone()))
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![0],
        vertex_writes
            .iter()
            .map(|(offset, _)| *offset)
            .collect::<Vec<_>>(),
        "identical same-asset geometry should be uploaded once and reused"
    );
    let index_writes = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::HostWriteBuffer { offset, data, .. } if data.len() == 3 * 4 => {
                Some((*offset, data.clone()))
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![0],
        index_writes
            .iter()
            .map(|(offset, _)| *offset)
            .collect::<Vec<_>>()
    );
    let mesh_draw = ops
        .iter()
        .position(|op| {
            matches!(
                op,
                CommandOp::DrawIndexed {
                    indices: 3,
                    instances: 1
                }
            )
        })
        .expect("mesh raster draw");
    let composite_draw = ops
        .iter()
        .position(|op| {
            matches!(
                op,
                CommandOp::Draw {
                    vertices: 6,
                    instances: 1
                }
            )
        })
        .expect("mesh composite draw");
    let affine_draws = ops
        .iter()
        .enumerate()
        .filter_map(|(index, op)| {
            matches!(
                op,
                CommandOp::DrawIndexed {
                    indices: 6,
                    instances: 1
                }
            )
            .then_some(index)
        })
        .collect::<Vec<_>>();
    assert_eq!(2, affine_draws.len());
    assert!(
        affine_draws[0] < mesh_draw
            && mesh_draw < composite_draw
            && composite_draw < affine_draws[1]
    );
    assert_eq!(1, frontend.mesh_targets.len());
    assert_eq!(1, frontend.mesh_composites.len());
    assert_eq!(1, frontend.mesh_rasters.len());
    assert_eq!(
        1,
        frontend.mesh_shared_programs.len(),
        "the asset-local mesh raster must borrow one Rust-owned immutable program"
    );
    gal.submit(SubmissionBatch {
        label: "gui-mesh-frontend-test".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "gui-mesh-frontend-test-commands".to_string(),
            operations: ops,
        })],
    })
    .expect("GAL validates ordered mesh raster and composite passes");
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn entity_preview_submission_stats_prove_body_and_armor_raster_selection() {
    let mut gal = mock_gal();
    let target = frame_target(&mut gal);
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![255, 255, 255, 255],
            }],
        )
        .expect("stage entity-preview image");
    let mut body = mesh_batch(0);
    body.lighting_mode = GuiMeshLightingMode::EntityPreview;
    body.material_mode = GuiMeshMaterialMode::EntityTranslucentNoCull;
    body.alpha_cutoff = 0.1;
    let mut armor = mesh_batch(1);
    armor.lighting_mode = GuiMeshLightingMode::EntityPreview;
    armor.material_mode = GuiMeshMaterialMode::EntityCutoutNoCull;
    armor.alpha_cutoff = 0.1;
    let mut decal = mesh_batch(2);
    decal.lighting_mode = GuiMeshLightingMode::EntityPreview;
    decal.material_mode = GuiMeshMaterialMode::EntityDecalCutoutNoCull;
    decal.alpha_cutoff = 0.1;

    let (_, stats) = frontend
        .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
            &mut gal,
            1,
            target,
            target,
            None,
            None,
            None,
            false,
            Vec::new(),
            Vec::new(),
            vec![body.clone(), armor, decal],
        )
        .expect("select entity body and armor for native raster");
    assert_eq!(1, stats.entity_preview_item_count);
    assert_eq!(3, stats.entity_preview_batch_count);
    assert_eq!(4, stats.entity_preview_draw_count);
    assert_eq!(
        (1_u64 << 7) | (1_u64 << 8) | (1_u64 << 9),
        stats.entity_preview_material_mask
    );
    assert_eq!(9, stats.entity_preview_vertex_count);
    assert_eq!(9, stats.entity_preview_index_count);

    let mut mixed = body.clone();
    mixed.layer_index = 1;
    mixed.lighting_mode = GuiMeshLightingMode::Block;
    mixed.material_mode = GuiMeshMaterialMode::Cutout;
    mixed.alpha_cutoff = 0.5;
    let error = frontend
        .append_mesh_items_to_target(
            &mut gal,
            None,
            1,
            target,
            target,
            None,
            None,
            None,
            vec![body, mixed],
            &mut GuiSubmitStats::default(),
        )
        .expect_err("mixed entity-preview lighting must fail closed");
    assert!(error
        .to_string()
        .contains("must all use entity-preview lighting"));
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn mesh_items_share_compositor_program_and_packed_uniform_stream() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let target = frame_target(&mut gal);
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![
                GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 7,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![255, 255, 255, 255],
                },
                GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 8,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![255, 255, 255, 255],
                },
            ],
        )
        .expect("raw images");
    let mut requests = mesh_item_batches(9);
    let mut next_item = mesh_item_batches(10);
    for layer in &mut next_item {
        layer.asset_id = 8;
        layer.render_extent = [36, 36];
    }
    requests.append(&mut next_item);
    let (ops, stats) = frontend
        .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
            &mut gal,
            1,
            target,
            target,
            None,
            None,
            None,
            false,
            Vec::new(),
            Vec::new(),
            requests,
        )
        .expect("two mesh items sharing an owned raster target");
    assert_eq!(stats.mesh_item_count, 2);
    assert_eq!(stats.mesh_batch_count, 6);
    assert_eq!(stats.mesh_draw_count, 8);
    let composite_uniform_buffers = frontend
        .mesh_composites
        .values()
        .map(|resources| resources.uniform_buffer)
        .collect::<Vec<_>>();
    let composite_resource_sets = frontend
        .mesh_composites
        .values()
        .map(|resources| resources.resource_set)
        .collect::<Vec<_>>();
    let composite_bind_indices = ops
        .iter()
        .enumerate()
        .filter_map(|operation| match operation {
            (
                index,
                CommandOp::BindResourceSet {
                    set,
                    dynamic_offsets,
                    ..
                },
            ) if composite_resource_sets.contains(set) => {
                Some((index, dynamic_offsets.clone()))
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![vec![0], vec![GUI_MESH_COMPOSITE_UNIFORM_STRIDE]],
        composite_bind_indices
            .iter()
            .map(|(_, offsets)| offsets.clone())
            .collect::<Vec<_>>(),
        "each GUI item composite must bind its own uniform range"
    );
    let composite_uniform_writes = ops
        .iter()
        .filter_map(|operation| match operation {
            CommandOp::HostWriteBuffer {
                buffer,
                offset,
                data,
            } if composite_uniform_buffers.contains(buffer) => Some((*offset, data.len())),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![(
            0,
            GUI_MESH_COMPOSITE_UNIFORM_STRIDE as usize
                + crate::render::guirender::mesh::GUI_MESH_COMPOSITE_UNIFORM_BYTES
        )],
        composite_uniform_writes,
        "same-sized GUI item poses use one packed upload without overlapping dynamic ranges"
    );
    let composites = frontend.mesh_composites.values().collect::<Vec<_>>();
    assert_eq!(composites[0].uniform_buffer, composites[1].uniform_buffer);
    assert_eq!(composites[0].pipeline, composites[1].pipeline);
    assert_eq!(composites[0].pipeline_layout, composites[1].pipeline_layout);
    assert_ne!(composites[0].resource_set, composites[1].resource_set);
    assert_eq!(
        ops.iter()
            .filter(|operation| matches!(
                operation,
                CommandOp::Barrier(ResourceBarrier {
                    after: TextureUsageState::ColorAttachment,
                    ..
                })
            ))
            .count(),
        2,
        "each GUI item establishes attachment-write usage once before its raster layers"
    );
    assert_eq!(
        2,
        frontend.mesh_rasters.len(),
        "each asset retains independent mutable bindings"
    );
    assert_eq!(
        1,
        frontend.mesh_shared_programs.len(),
        "matching mesh raster contracts share exactly one immutable program"
    );
    let rasters = frontend.mesh_rasters.values().collect::<Vec<_>>();
    assert_eq!(rasters[0].pipeline, rasters[1].pipeline);
    assert_eq!(rasters[0].pipeline_layout, rasters[1].pipeline_layout);
    assert_ne!(rasters[0].resource_set, rasters[1].resource_set);
    assert_ne!(rasters[0].uniform_buffer, rasters[1].uniform_buffer);
    assert_eq!(rasters[0].vertex_buffer, rasters[1].vertex_buffer);
    assert_eq!(rasters[0].index_buffer, rasters[1].index_buffer);
    gal.submit(SubmissionBatch {
        label: "gui-mesh-reuse-target-test".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "gui".to_string(),
            operations: ops,
        })],
    })
    .expect("GAL validates sampled-to-attachment target reuse");
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn sprite_registry_ids_are_stable() {
    assert_eq!(94, SPRITES.len());
    assert_eq!("crosshair", sprite_def(1).unwrap().name);
    assert_eq!("boss-bar-overlay-progress", sprite_def(79).unwrap().name);
    assert_eq!("hunger-effect-full", sprite_def(85).unwrap().name);
    assert_eq!("air-full", sprite_def(86).unwrap().name);
    assert_eq!("air-popping", sprite_def(87).unwrap().name);
    assert_eq!("air-empty", sprite_def(88).unwrap().name);
    assert_eq!("mount-heart-container", sprite_def(89).unwrap().name);
    assert_eq!("mount-heart-full", sprite_def(90).unwrap().name);
    assert_eq!("mount-heart-half", sprite_def(91).unwrap().name);
    assert_eq!("post-effect-invert", sprite_def(92).unwrap().name);
    assert_eq!("post-effect-creeper", sprite_def(93).unwrap().name);
    assert_eq!("post-effect-spider", sprite_def(94).unwrap().name);
}

#[test]
fn invert_post_effect_accepts_viewport_request_and_generates_vanilla_amount_source() {
    let definition = sprite_def(GUI_POST_EFFECT_INVERT_ID).unwrap();
    let request = GuiSpriteRequest {
        stratum: 80,
        sprite_id: GUI_POST_EFFECT_INVERT_ID,
        selected_slot: -1,
        progress_fraction: -1.0,
        fill_direction: 0,
        color_argb: 0xffff_ffff,
        x: 0,
        y: 0,
        width: 1920,
        height: 1080,
        gui_width: 1920,
        gui_height: 1080,
        projection_extent: [1920.0, 1080.0],
        sequence: 1,
    };
    validate_request(&request, definition).unwrap();
    let bytes = load_sprite(definition, &std::collections::BTreeMap::new()).unwrap();
    assert_eq!(vec![204, 204, 204, 255], bytes);
}

#[test]
fn compatible_batches_split_on_group_stratum_and_size() {
    let requests = vec![
        GuiSpriteRequest {
            stratum: 300,
            sprite_id: 2,
            selected_slot: -1,
            progress_fraction: 1.0,
            fill_direction: 0,
            color_argb: 0xffffffff,
            x: 0,
            y: 0,
            width: 1,
            height: 1,
            gui_width: 320,
            gui_height: 180,
            projection_extent: [320.0, 180.0],
            sequence: 1,
        },
        GuiSpriteRequest {
            stratum: 310,
            sprite_id: 3,
            selected_slot: -1,
            progress_fraction: 1.0,
            fill_direction: 0,
            color_argb: 0xffffffff,
            x: 0,
            y: 0,
            width: 1,
            height: 1,
            gui_width: 320,
            gui_height: 180,
            projection_extent: [320.0, 180.0],
            sequence: 2,
        },
    ];
    let mut batches = Vec::<GuiBatch>::new();
    for request in requests {
        let group = sprite_def(request.sprite_id).unwrap().group;
        append_gui_quad(
            &mut batches,
            request.stratum,
            group,
            PackedGuiQuad {
                origin: [0.0, 0.0],
                axis_u: [1.0, 0.0],
                axis_v: [0.0, 1.0],
                viewport: [320.0, 180.0],
                clip: [0.0; 4],
                clip_enabled: false,
                pre_present_y_flip: false,
                uv: [0.0, 0.0, 1.0, 1.0],
                color: [1.0; 4],
                texture_mode: 1.0,
                z: 0.0,
            },
        );
    }
    assert_eq!(2, batches.len());
}

#[test]
fn mixed_gui_record_families_preserve_scheduler_order_and_reject_duplicates() {
    let mut sprite = request(1);
    sprite.stratum = 700;
    sprite.sequence = 12;
    let affine = GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
        stratum: 700,
        asset_id: 41,
        x0: 0.0,
        y0: 0.0,
        x1: 8.0,
        y1: 0.0,
        x3: 0.0,
        y3: 8.0,
        z: 0.0,
        u0: 0.0,
        v0: 0.0,
        u1: 1.0,
        v1: 1.0,
        color_argb: 0xffff_ffff,
        gui_width: 320,
        gui_height: 180,
        projection_extent: [320.0, 180.0],
        sequence: 11,
        clip_mode: 0,
        clip_left: 0,
        clip_top: 0,
        clip_width: 0,
        clip_height: 0,
    };
    let affine_second = GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        sequence: 10,
        x0: 12.0,
        x1: 20.0,
        x3: 12.0,
        ..affine.clone()
    };
    let ordered =
        order_gui_requests(vec![sprite.clone()], vec![affine.clone(), affine_second]).unwrap();
    assert!(matches!(&ordered[0], GuiFrameRequest::AffineBatch(batch) if batch.len() == 2));
    assert!(matches!(ordered[1], GuiFrameRequest::Sprite(_)));

    let error = order_gui_requests(
        vec![sprite],
        vec![GuiAffineQuadRequest {
            item_raster_layers: vec![],
            item_raster_scale: 0,
            item_raster_geometry: Default::default(),
            sequence: 12,
            ..affine
        }],
    )
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
}

#[test]
fn gui_mesh_batch_ordering_rejects_unbounded_submission_before_grouping() {
    let batches = (0..=GUI_MAX_MESH_BATCHES as u32)
        .map(mesh_batch)
        .collect::<Vec<_>>();
    let error = order_gui_requests_with_mesh(Vec::new(), Vec::new(), batches).unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
    assert!(error.to_string().contains("bounded limit"));
}

#[test]
fn affine_gui_clip_is_frame_local_and_rejects_out_of_bounds_rectangles() {
    let mut request = GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
        stratum: 700,
        asset_id: 41,
        x0: 0.0,
        y0: 0.0,
        x1: 8.0,
        y1: 0.0,
        x3: 0.0,
        y3: 8.0,
        z: 0.0,
        u0: 0.0,
        v0: 0.0,
        u1: 1.0,
        v1: 1.0,
        color_argb: 0xffff_ffff,
        gui_width: 320,
        gui_height: 180,
        projection_extent: [320.0, 180.0],
        sequence: 1,
        clip_mode: 1,
        clip_left: 12,
        clip_top: 18,
        clip_width: 64,
        clip_height: 20,
    };
    validate_affine_quad(&request).unwrap();
    request.clip_width = 309;
    let error = validate_affine_quad(&request).unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
}

#[test]
fn asset_updates_are_generation_ordered_and_copied() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let sprite = sprite_def(1).unwrap();
    let mut bytes = bundled_sprite_bytes(sprite.path).unwrap().to_vec();
    frontend
        .apply_asset_update(
            &mut gal,
            2,
            vec![GuiAssetPayload {
                sprite_id: sprite.id,
                png_bytes: bytes.clone(),
            }],
        )
        .unwrap();
    bytes.clear();
    assert_eq!(2, frontend.asset_generation);
    assert!(!frontend.asset_overrides.get(&sprite.id).unwrap().is_empty());
    let stale = frontend
        .apply_asset_update(&mut gal, 2, Vec::new())
        .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, stale.code);
    assert_eq!(2, frontend.asset_generation);
}

#[test]
fn malformed_asset_update_rolls_back_to_last_valid_assets() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let sprite = sprite_def(1).unwrap();
    let valid = bundled_sprite_bytes(sprite.path).unwrap().to_vec();
    frontend
        .apply_asset_update(
            &mut gal,
            2,
            vec![GuiAssetPayload {
                sprite_id: sprite.id,
                png_bytes: valid.clone(),
            }],
        )
        .unwrap();
    let before = frontend.asset_overrides.get(&sprite.id).unwrap().clone();
    let error = frontend
        .apply_asset_update(
            &mut gal,
            3,
            vec![GuiAssetPayload {
                sprite_id: sprite.id,
                png_bytes: vec![1, 2, 3, 4],
            }],
        )
        .unwrap_err();
    assert!(error.message.contains("failed to decode GUI sprite"));
    assert_eq!(2, frontend.asset_generation);
    assert_eq!(before, *frontend.asset_overrides.get(&sprite.id).unwrap());
}

#[test]
fn missing_asset_payloads_fall_back_to_vanilla() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_asset_update(&mut gal, 2, Vec::new())
        .unwrap();
    assert!(frontend.asset_overrides.is_empty());
    let atlas = frontend.atlas_for(TextureGroup::Alpha).unwrap();
    assert!(atlas.regions.contains_key(&2));
}

#[test]
fn static_and_raw_gui_images_share_top_origin_uv_coordinates() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_asset_update(&mut gal, 2, Vec::new())
        .unwrap();

    let mut sprite_request = request(58);
    let sprite = sprite_def(sprite_request.sprite_id).unwrap();
    sprite_request.width = sprite.width;
    sprite_request.height = sprite.height;
    sprite_request.projection_extent = [319.75, 179.5];
    let packed = frontend
        .pack_sprite(&sprite_request, sprite, false)
        .unwrap();
    let atlas = frontend.atlas_for(sprite.group).unwrap();
    let region = atlas.regions.get(&sprite.id).unwrap();
    assert_eq!(
        [
            region.x as f32 / atlas.width as f32,
            region.y as f32 / atlas.height as f32,
        ],
        packed.uv[..2]
    );

    let direct_bytes = packed_uniform_bytes(&GuiBatch {
        stratum: sprite_request.stratum,
        group: sprite.group,
        quads: vec![packed],
    })
    .unwrap();
    let direct_flip = f32::from_le_bytes(direct_bytes[44..48].try_into().unwrap());
    assert_eq!(
        319.75,
        f32::from_le_bytes(direct_bytes[32..36].try_into().unwrap())
    );
    assert_eq!(
        179.5,
        f32::from_le_bytes(direct_bytes[36..40].try_into().unwrap())
    );
    assert_eq!(
        0.0, direct_flip,
        "ordinary GUI targets must not pre-flip their stream"
    );

    let source_packed = frontend.pack_sprite(&sprite_request, sprite, true).unwrap();
    let source_bytes = packed_uniform_bytes(&GuiBatch {
        stratum: sprite_request.stratum,
        group: sprite.group,
        quads: vec![source_packed],
    })
    .unwrap();
    let source_flip = f32::from_le_bytes(source_bytes[44..48].try_into().unwrap());
    assert_eq!(
        1.0, source_flip,
        "the source overlay must precompensate its final present copy"
    );

    // Raw font images arrive in the same row-zero-is-top convention, so
    // their top-left semantic UV must address the first uploaded texel.
    let raw = GuiAffineQuadRequest {
        item_raster_layers: vec![],
        item_raster_scale: 0,
        item_raster_geometry: Default::default(),
        asset_id: 7,
        u0: 0.0,
        v0: 0.0,
        u1: 1.0,
        v1: 1.0,
        ..GuiAffineQuadRequest {
            item_raster_layers: vec![],
            item_raster_scale: 0,
            item_raster_geometry: Default::default(),
            material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
            stratum: 700,
            asset_id: 7,
            x0: 0.0,
            y0: 0.0,
            x1: 1.0,
            y1: 0.0,
            x3: 0.0,
            y3: 1.0,
            z: 0.0,
            u0: 0.0,
            v0: 0.0,
            u1: 1.0,
            v1: 1.0,
            color_argb: 0xffff_ffff,
            gui_width: 1,
            gui_height: 1,
            projection_extent: [1.0, 1.0],
            sequence: 1,
            clip_mode: 0,
            clip_left: 0,
            clip_top: 0,
            clip_width: 0,
            clip_height: 0,
        }
    };
    validate_affine_quad(&raw).unwrap();
}

#[test]
fn asset_reload_rebuilds_once_then_reuses_cached_resources() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let target = frame_target(&mut gal);
    frontend
        .apply_asset_update(&mut gal, 2, Vec::new())
        .unwrap();

    let first = frontend
        .submit_frame(
            &mut gal,
            10,
            target,
            vec![
                request(2),
                GuiSpriteRequest {
                    sequence: 2,
                    ..request(3)
                },
            ],
        )
        .unwrap();
    assert!(first.resource_creates > 0);
    assert!(first.cache_misses > 0);

    let second = frontend
        .submit_frame(
            &mut gal,
            10,
            target,
            vec![
                request(2),
                GuiSpriteRequest {
                    sequence: 2,
                    ..request(3)
                },
            ],
        )
        .unwrap();
    assert_eq!(0, second.resource_creates);
    assert!(second.cache_hits > 0);
    assert_eq!(0, second.cache_misses);
}

#[test]
fn vulkan_whole_frame_gui_submission_clears_once_before_batches() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let target = frame_target(&mut gal);

    let stats = frontend
        .submit_frame(
            &mut gal,
            10,
            target,
            vec![
                request(2),
                GuiSpriteRequest {
                    sequence: 2,
                    ..request(3)
                },
            ],
        )
        .unwrap();

    assert_eq!(2, stats.sprite_batch_count);
    assert_eq!(1, stats.command_lists);
    assert_eq!(20, stats.command_ops);
}

#[test]
fn gui_batches_with_distinct_uniform_bindings_share_one_render_pass() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let target = frame_target(&mut gal);
    let (ops, stats) = frontend
        .append_frame_ops(&mut gal, 10, target, vec![request(1), request(2)])
        .unwrap();

    assert_eq!(2, stats.sprite_batch_count);
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::BeginPass { .. }))
            .count()
    );
    assert_eq!(18, stats.command_ops);
}

#[test]
fn raw_image_assets_are_copied_validated_and_rendered_as_affine_quads() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let target = frame_target(&mut gal);
    let mut pixels = vec![0, 64, 128, 255];
    frontend
        .apply_raw_image_update(
            &mut gal,
            3,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 41,
                format: GuiRawImageSourceFormat::Alpha8,
                width: 2,
                height: 2,
                pixels: pixels.clone(),
            }],
        )
        .unwrap();
    pixels.fill(7);
    assert_eq!(vec![0, 64, 128, 255], frontend.raw_images[&41].pixels);

    let (_, stats) = frontend
        .append_frame_ops_with_affine_quads(
            &mut gal,
            9,
            target,
            Vec::new(),
            vec![GuiAffineQuadRequest {
                item_raster_layers: vec![],
                item_raster_scale: 0,
                item_raster_geometry: Default::default(),
                material: crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
                stratum: 420,
                asset_id: 41,
                x0: 10.0,
                y0: 20.0,
                x1: 18.0,
                y1: 21.0,
                x3: 9.0,
                y3: 29.0,
                z: 0.03,
                u0: 0.0,
                v0: 0.0,
                u1: 1.0,
                v1: 1.0,
                color_argb: 0xff336699,
                gui_width: 320,
                gui_height: 180,
                projection_extent: [320.0, 180.0],
                sequence: 1,
                clip_mode: 0,
                clip_left: 0,
                clip_top: 0,
                clip_width: 0,
                clip_height: 0,
            }],
        )
        .unwrap();
    assert_eq!(1, stats.affine_quad_count);
    assert_eq!(1, stats.sprite_batch_count);
    assert!(frontend.resources.contains_key(&ResourceKey::new(
        TextureGroup::Dynamic(41),
        ColorFormat::Rgba8Unorm,
        None,
    )));
}

#[test]
fn unchanged_raw_image_generation_retains_dynamic_gpu_resources() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let payload = GuiRawImageAssetPayload {
        sampling: None,
        asset_id: 41,
        format: GuiRawImageSourceFormat::Rgba8,
        width: 1,
        height: 1,
        pixels: vec![1, 2, 3, 4],
    };
    frontend
        .apply_raw_image_update(&mut gal, 1, vec![payload.clone()])
        .unwrap();
    let mut stats = GuiSubmitStats::default();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    let key = ResourceKey::new(TextureGroup::Dynamic(41), ColorFormat::Rgba8Unorm, None);
    let first = frontend.resources[&key].texture;
    frontend
        .apply_raw_image_update(&mut gal, 2, vec![payload])
        .unwrap();
    assert_eq!(first, frontend.resources[&key].texture);
}

#[test]
fn gui_textures_share_one_immutable_pipeline_per_explicit_raster_contract() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            3,
            vec![
                GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 41,
                    format: GuiRawImageSourceFormat::Alpha8,
                    width: 2,
                    height: 2,
                    pixels: vec![255; 4],
                },
                GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 42,
                    format: GuiRawImageSourceFormat::Alpha8,
                    width: 2,
                    height: 2,
                    pixels: vec![127; 4],
                },
            ],
        )
        .unwrap();
    let mut stats = GuiSubmitStats::default();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(42),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();

    assert_eq!(2, frontend.resources.len());
    assert_eq!(1, frontend.shared_pipelines.len());
    let first = &frontend.resources
        [&ResourceKey::new(TextureGroup::Dynamic(41), ColorFormat::Rgba8Unorm, None)];
    let second = &frontend.resources
        [&ResourceKey::new(TextureGroup::Dynamic(42), ColorFormat::Rgba8Unorm, None)];
    assert_eq!(first.pipeline, second.pipeline);
    assert_eq!(first.pipeline_layout, second.pipeline_layout);
    assert_ne!(first.resource_set, second.resource_set);
}

#[test]
fn dynamic_gui_blend_strata_share_one_explicit_texture_resource() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 41,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 2,
                height: 2,
                pixels: vec![1; 16],
            }],
        )
        .unwrap();
    let mut stats = GuiSubmitStats::default();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    let texture_creates_after_first = gal
        .mock_backend()
        .unwrap()
        .creates
        .iter()
        .filter(|(_, kind)| *kind == HandleKind::Texture)
        .count();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::DynamicOpaque(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::DynamicLinear(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    let texture_creates_after_all_groups = gal
        .mock_backend()
        .unwrap()
        .creates
        .iter()
        .filter(|(_, kind)| *kind == HandleKind::Texture)
        .count();
    let alpha = &frontend.resources
        [&ResourceKey::new(TextureGroup::Dynamic(41), ColorFormat::Rgba8Unorm, None)];
    let opaque = &frontend.resources[&ResourceKey::new(
        TextureGroup::DynamicOpaque(41),
        ColorFormat::Rgba8Unorm,
        None,
    )];
    let linear = &frontend.resources[&ResourceKey::new(
        TextureGroup::DynamicLinear(41),
        ColorFormat::Rgba8Unorm,
        None,
    )];
    assert_eq!(alpha.texture, opaque.texture);
    assert_eq!(alpha.texture_view, opaque.texture_view);
    assert_eq!(alpha.sampler, opaque.sampler);
    assert_eq!(alpha.texture, linear.texture);
    assert_eq!(alpha.texture_view, linear.texture_view);
    assert_ne!(alpha.sampler, linear.sampler,
        "continuous semantic images require a distinct linear sampler without copying their Rust-owned texture");
    assert_eq!(1, frontend.dynamic_textures.len());
    assert_eq!(1, texture_creates_after_first);
    assert_eq!(
        texture_creates_after_first,
        texture_creates_after_all_groups
    );
    gal.mock_backend_mut().unwrap().fail_next_submit();
    assert!(frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::DynamicPremultiplied(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats
        )
        .is_err());
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::DynamicPremultiplied(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    frontend.destroy_render_resources(&mut gal);
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys,
        "reused GUI image bindings must not orphan a newly allocated upload buffer"
    );
}

#[test]
fn gui_glint_sampling_is_selected_by_material_not_scheduler_stratum() {
    let mut batch = mesh_batch(0);
    for stratum in [1, GUI_OPAQUE_BLIT_STRATUM, GUI_VIGNETTE_BLIT_STRATUM] {
        batch.stratum = stratum;
        batch.material_mode = GuiMeshMaterialMode::Glint;
        batch.alpha_cutoff = 0.1;
        let glint = prepare_gui_mesh_draws(&[batch.clone()]).unwrap().remove(0);
        assert_eq!(
            dynamic_mesh_texture_group(&glint),
            TextureGroup::DynamicGlint(batch.asset_id)
        );
        batch.material_mode = GuiMeshMaterialMode::Opaque;
        batch.alpha_cutoff = 0.0;
        let ordinary = prepare_gui_mesh_draws(&[batch.clone()]).unwrap().remove(0);
        assert_ne!(
            dynamic_mesh_texture_group(&ordinary),
            TextureGroup::DynamicGlint(batch.asset_id)
        );
    }
}

#[test]
fn gui_glint_sampler_is_explicit_shared_image_safe_and_failure_atomic() {
    for glint_first in [false, true] {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 41,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 2,
                    height: 2,
                    pixels: vec![127; 16],
                }],
            )
            .unwrap();
        let glint = TextureGroup::DynamicGlint(41);
        let ordinary = TextureGroup::Dynamic(41);
        let groups = if glint_first {
            [glint, ordinary]
        } else {
            [ordinary, glint]
        };
        for group in groups {
            if group == glint {
                gal.mock_backend_mut().unwrap().fail_next_submit();
                assert!(frontend
                    .ensure_resources(
                        &mut gal,
                        group,
                        ColorFormat::Rgba8Unorm,
                        None,
                        &mut GuiSubmitStats::default()
                    )
                    .is_err());
            }
            frontend
                .ensure_resources(
                    &mut gal,
                    group,
                    ColorFormat::Rgba8Unorm,
                    None,
                    &mut GuiSubmitStats::default(),
                )
                .unwrap();
        }
        let repeat =
            &frontend.resources[&ResourceKey::new(glint, ColorFormat::Rgba8Unorm, None)];
        let clamp =
            &frontend.resources[&ResourceKey::new(ordinary, ColorFormat::Rgba8Unorm, None)];
        assert_eq!(repeat.texture, clamp.texture);
        assert_eq!(repeat.texture_view, clamp.texture_view);
        assert_eq!(repeat.private_sampler, Some(repeat.sampler));
        assert_eq!(clamp.private_sampler, None);
        let desc = gal.sampler_descriptor_for_test(repeat.sampler).unwrap();
        assert_eq!(desc.min_filter, SamplerFilter::Linear);
        assert_eq!(desc.mag_filter, SamplerFilter::Linear);
        assert_eq!(desc.address_u, SamplerAddressMode::Repeat);
        assert_eq!(desc.address_v, SamplerAddressMode::Repeat);
        assert_eq!(
            gal.sampler_descriptor_for_test(clamp.sampler)
                .unwrap()
                .address_u,
            SamplerAddressMode::ClampToEdge
        );
        let old_sampler = repeat.sampler;
        frontend
            .apply_raw_image_update(
                &mut gal,
                2,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 41,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 2,
                    height: 2,
                    pixels: vec![255; 16],
                }],
            )
            .unwrap();
        gal.retire_through(gal.latest_submission_id()).unwrap();
        assert!(gal.sampler_descriptor_for_test(old_sampler).is_err());
        frontend.reset(&mut gal).unwrap();
        gal.retire_through(gal.latest_submission_id()).unwrap();
        assert_eq!(
            gal.metrics().resource_creates,
            gal.metrics().resource_destroys
        );
    }
}

#[test]
fn gui_glint_resource_sampling_metadata_only_reload_rebinds_without_changing_shared_sampler() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let mut generation = 0;
    let mut previous_sampler = None;
    for filter in [SamplerFilter::Nearest, SamplerFilter::Linear] {
        for address in [SamplerAddressMode::Repeat, SamplerAddressMode::ClampToEdge] {
            generation += 1;
            frontend
                .apply_raw_image_update(
                    &mut gal,
                    generation,
                    vec![GuiRawImageAssetPayload {
                        asset_id: 41,
                        format: GuiRawImageSourceFormat::Rgba8,
                        width: 2,
                        height: 2,
                        pixels: vec![127; 16],
                        sampling: Some((filter, address)),
                    }],
                )
                .unwrap();
            for group in [TextureGroup::Dynamic(41), TextureGroup::DynamicGlint(41)] {
                frontend
                    .ensure_resources(
                        &mut gal,
                        group,
                        ColorFormat::Rgba8Unorm,
                        None,
                        &mut GuiSubmitStats::default(),
                    )
                    .unwrap();
            }
            let glint = &frontend.resources[&ResourceKey::new(
                TextureGroup::DynamicGlint(41),
                ColorFormat::Rgba8Unorm,
                None,
            )];
            let ordinary = &frontend.resources
                [&ResourceKey::new(TextureGroup::Dynamic(41), ColorFormat::Rgba8Unorm, None)];
            let desc = gal.sampler_descriptor_for_test(glint.sampler).unwrap();
            assert_eq!(filter, desc.min_filter);
            assert_eq!(filter, desc.mag_filter);
            assert_eq!(address, desc.address_u);
            assert_eq!(address, desc.address_v);
            assert_eq!(glint.texture, ordinary.texture);
            assert_eq!(
                SamplerAddressMode::ClampToEdge,
                gal.sampler_descriptor_for_test(ordinary.sampler)
                    .unwrap()
                    .address_u
            );
            if let Some(old) = previous_sampler {
                assert_ne!(old, glint.sampler);
                gal.retire_through(gal.latest_submission_id()).unwrap();
                assert!(gal.sampler_descriptor_for_test(old).is_err());
            }
            previous_sampler = Some(glint.sampler);
        }
    }
    frontend.reset(&mut gal).unwrap();
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn vulkan_gui_shared_image_reload_retires_every_binding_and_upload_buffer() {
    let mut gal = vulkan_gal("GUI shared image resource bound regression").unwrap();
    let mut frontend = GuiFrontend::default();
    let mut stable_live_count = None;
    for generation in 1..=16 {
        frontend
            .apply_raw_image_update(
                &mut gal,
                generation,
                vec![GuiRawImageAssetPayload {
                    sampling: None,
                    asset_id: 41,
                    format: GuiRawImageSourceFormat::Rgba8,
                    width: 2,
                    height: 2,
                    pixels: vec![generation as u8; 16],
                }],
            )
            .unwrap();
        for group in [
            TextureGroup::Dynamic(41),
            TextureGroup::DynamicOpaque(41),
            TextureGroup::DynamicLinear(41),
            TextureGroup::DynamicPremultiplied(41),
            TextureGroup::DynamicGlint(41),
        ] {
            frontend
                .ensure_resources(
                    &mut gal,
                    group,
                    ColorFormat::Rgba8Unorm,
                    None,
                    &mut GuiSubmitStats::default(),
                )
                .unwrap();
        }
        gal.retire_through(gal.latest_submission_id()).unwrap();
        let live_count = gal.metrics().resource_creates - gal.metrics().resource_destroys;
        assert_eq!(
            *stable_live_count.get_or_insert(live_count),
            live_count,
            "changing image pixels cannot accumulate discarded upload buffers"
        );
    }
    frontend.reset(&mut gal).unwrap();
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn explicit_atlas_declarations_cannot_alias_copied_images_or_admit_fallback_sampling() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let reference = GuiAtlasReference {
        asset_id: 101,
        atlas: AcceptedAtlasIncarnation {
            texture_id: 202,
            generation: 3,
            width: 32,
            height: 16,
        },
        x: 16,
        y: 0,
        width: 16,
        height: 16,
    };
    frontend
        .stage_atlas_references(1, &[reference], |_| Some(reference.atlas))
        .unwrap();
    assert_eq!(
        frontend
            .texture_source(TextureGroup::Dynamic(101))
            .err()
            .unwrap()
            .code,
        StatusCode::UnsupportedFeature
    );
    let payload = || GuiRawImageAssetPayload {
        sampling: None,
        asset_id: 101,
        format: GuiRawImageSourceFormat::Rgba8,
        width: 1,
        height: 1,
        pixels: vec![255; 4],
    };
    assert!(frontend
        .apply_raw_image_update(&mut gal, 1, vec![payload()])
        .is_err());
    assert!(frontend.raw_images.is_empty());
    assert_eq!(frontend.raw_image_generation, 0);
    assert!(frontend.resources.is_empty());
    assert!(frontend.dynamic_textures.is_empty());
    frontend.atlas_references.clear();
    frontend
        .apply_raw_image_update(&mut gal, 1, vec![payload()])
        .unwrap();
    assert!(frontend
        .stage_atlas_references(1, &[reference], |_| Some(reference.atlas))
        .is_err());
    assert!(!frontend.atlas_references.contains(101));
    assert_eq!(
        frontend
            .texture_source(TextureGroup::Dynamic(101))
            .unwrap()
            .bytes,
        vec![255; 4]
    );
    frontend.reset(&mut gal).unwrap();
}

#[test]
fn changed_dynamic_gui_image_retires_shared_texture_before_recreation() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 41,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 2,
                height: 2,
                pixels: vec![1; 16],
            }],
        )
        .unwrap();
    let mut stats = GuiSubmitStats::default();
    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    let first_texture = frontend
        .resources
        .get(&ResourceKey::new(
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
        ))
        .unwrap()
        .texture;

    frontend
        .apply_raw_image_update(
            &mut gal,
            2,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 41,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 2,
                height: 2,
                pixels: vec![2; 16],
            }],
        )
        .unwrap();
    assert!(frontend.resources.is_empty());
    assert!(frontend.dynamic_textures.is_empty());
    gal.mock_backend_mut()
        .unwrap()
        .complete_through(SubmissionId(1));
    gal.retire_completed().unwrap();

    frontend
        .ensure_resources(
            &mut gal,
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
            &mut stats,
        )
        .unwrap();
    let second_texture = frontend
        .resources
        .get(&ResourceKey::new(
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
        ))
        .unwrap()
        .texture;
    assert_ne!(first_texture, second_texture);
    assert_eq!(
        2,
        gal.mock_backend()
            .unwrap()
            .creates
            .iter()
            .filter(|(_, kind)| *kind == HandleKind::Texture)
            .count()
    );
    assert_eq!(
        1,
        gal.mock_backend()
            .unwrap()
            .destroys
            .iter()
            .filter(|(_, kind)| *kind == HandleKind::Texture)
            .count()
    );
    frontend.destroy_render_resources(&mut gal);
}

#[test]
fn affine_pipeline_strata_select_exact_blend_groups() {
    assert_eq!(BlendMode::Disabled, dynamic_texture_group(760, 1).blend());
    assert_eq!(BlendMode::InverseSrcColorModulate, dynamic_texture_group(770, 1).blend());
    assert_eq!(BlendMode::Invert, dynamic_texture_group(780, 1).blend());
    assert_eq!(BlendMode::Invert, dynamic_texture_group(200, 1).blend());
    assert_eq!(
        BlendMode::Premultiplied,
        dynamic_texture_group(790, 1).blend()
    );
    assert_eq!(BlendMode::Additive, dynamic_texture_group(795, 1).blend());
    assert_eq!(BlendMode::Alpha, dynamic_texture_group(805, 1).blend());
    assert_eq!(BlendMode::Alpha, dynamic_texture_group(750, 1).blend());
}

#[test]
fn copied_png_gui_assets_preserve_frozen_rgba8_channels_for_vignette_blending() {
    assert_eq!(
        TextureFormat::Rgba8Unorm,
        GuiRawImageFormat::Rgba8.texture_format()
    );
    assert_eq!(4, GuiRawImageFormat::Rgba8.bytes_per_pixel());
    assert_eq!(1.0, GuiRawImageFormat::Rgba8.shader_mode());
    let shader = std::str::from_utf8(FRAGMENT_SHADER_VULKAN).unwrap();
    assert!(shader.contains("vec4 sampled = texture(sampler2D(Tex0, Samp0), v_uv)"));
    assert!(!shader.contains("texelFetch") && !shader.contains("floor(v_sprite_corner"));
    assert!(!shader.contains("v_texture_mode > 1.5"));
}

#[test]
fn every_dynamic_gui_blend_group_is_retirable() {
    assert!(TextureGroupKey::from(TextureGroup::DynamicItemRaster(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicItemCutout(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::Dynamic(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicOpaque(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicVignette(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicInvert(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicPremultiplied(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicAdditive(1)).is_dynamic());
    assert!(TextureGroupKey::from(TextureGroup::DynamicLequalDepth(1)).is_dynamic());
    assert!(!TextureGroupKey::Alpha.is_dynamic());
}

#[test]
fn malformed_raw_image_update_rolls_back_without_destroying_valid_generation() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    frontend
        .apply_raw_image_update(
            &mut gal,
            4,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 7,
                format: GuiRawImageSourceFormat::Rgba8,
                width: 1,
                height: 1,
                pixels: vec![1, 2, 3, 4],
            }],
        )
        .unwrap();
    let error = frontend
        .apply_raw_image_update(
            &mut gal,
            5,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 8,
                format: GuiRawImageSourceFormat::Alpha8,
                width: 2,
                height: 2,
                pixels: vec![1, 2, 3],
            }],
        )
        .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
    assert_eq!(4, frontend.raw_image_generation);
    assert!(frontend.raw_images.contains_key(&7));
    assert!(!frontend.raw_images.contains_key(&8));
}

#[test]
fn oversized_raw_image_pixel_count_is_rejected_before_payload_validation() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let error = frontend
        .apply_raw_image_update(
            &mut gal,
            1,
            vec![GuiRawImageAssetPayload {
                sampling: None,
                asset_id: 99,
                format: GuiRawImageSourceFormat::Alpha8,
                width: 8192,
                height: 8192,
                pixels: Vec::new(),
            }],
        )
        .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
    assert!(error.message.contains("pixels"));
    assert_eq!(0, frontend.raw_image_generation);
    assert!(frontend.raw_images.is_empty());
}

#[test]
fn failed_asset_generation_preserves_last_valid_atlas_bytes() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let sprite = sprite_def(2).unwrap();
    frontend
        .apply_asset_update(
            &mut gal,
            2,
            vec![GuiAssetPayload {
                sprite_id: sprite.id,
                png_bytes: bundled_sprite_bytes(sprite.path).unwrap().to_vec(),
            }],
        )
        .unwrap();
    let atlas_before = frontend
        .atlas_for(TextureGroup::Alpha)
        .unwrap()
        .bytes
        .clone();

    let error = frontend
        .apply_asset_update(
            &mut gal,
            3,
            vec![GuiAssetPayload {
                sprite_id: sprite.id,
                png_bytes: vec![0, 1, 2, 3],
            }],
        )
        .unwrap_err();

    assert!(error.message.contains("failed to decode GUI sprite"));
    assert_eq!(2, frontend.asset_generation);
    assert_eq!(
        atlas_before,
        frontend.atlas_for(TextureGroup::Alpha).unwrap().bytes
    );
}

#[test]
fn custom_post_effect_cleanup_orders_dependents_before_sampler_inputs() {
    let handle = |kind| Handle::new(kind, 1, 1).unwrap();
    let resources = CustomPostEffectResources {
        frame_copy_scratch: None,
        identity: "test".to_owned(),
        width: 1,
        height: 1,
        color_format: ColorFormat::Rgba8Unorm,
        snapshots: vec![handle(HandleKind::Texture)],
        snapshot_views: vec![handle(HandleKind::TextureView)],
        target_samplers: vec![handle(HandleKind::Sampler)],
        image_textures: vec![None],
        image_views: vec![None],
        image_samplers: vec![None],
        image_upload_buffers: vec![None],
        combined_samplers: vec![handle(HandleKind::CombinedTextureSampler)],
        uniform_buffers: vec![handle(HandleKind::Buffer)],
        vertex_shader: handle(HandleKind::ShaderModule),
        fragment_shader: handle(HandleKind::ShaderModule),
        resource_layout: handle(HandleKind::ResourceLayout),
        resource_set: handle(HandleKind::ResourceSet),
        pipeline_layout: handle(HandleKind::PipelineLayout),
        pipeline: handle(HandleKind::GraphicsPipeline),
    };
    let kinds = resources
        .handles_in_destroy_order()
        .into_iter()
        .map(|handle| handle.kind().unwrap())
        .collect::<Vec<_>>();
    assert_eq!(
        kinds,
        vec![
            HandleKind::GraphicsPipeline,
            HandleKind::PipelineLayout,
            HandleKind::ResourceSet,
            HandleKind::ResourceLayout,
            HandleKind::ShaderModule,
            HandleKind::ShaderModule,
            HandleKind::CombinedTextureSampler,
            HandleKind::Buffer,
            HandleKind::Sampler,
            HandleKind::TextureView,
            HandleKind::Texture,
        ]
    );
}

#[test]
fn indexed_map_image_admission_uses_frozen_rgba_and_keeps_gpu_format_explicit() {
    let mut gal = mock_gal();
    let mut frontend = GuiFrontend::default();
    let payload = GuiRawImageAssetPayload {
        sampling: None, asset_id: 991, format: GuiRawImageSourceFormat::MapColor8,
        width: 256, height: 1, pixels: (0..=255).collect(),
    };
    frontend.apply_raw_image_update(&mut gal, 1, vec![payload.clone()]).unwrap();
    let image = &frontend.raw_images[&991];
    assert_eq!(GuiRawImageFormat::Rgba8, image.format);
    assert_eq!(include_bytes!("../../../content/map_color/frozen-native-rgba.bin").as_slice(), image.pixels);
    let mut malformed = payload;
    malformed.pixels.pop();
    assert!(frontend.apply_raw_image_update(&mut gal, 2, vec![malformed]).is_err());
    assert_eq!(1, frontend.raw_image_generation);
    assert_eq!(1024, frontend.raw_images[&991].pixels.len());
    frontend.apply_raw_image_update(&mut gal, 3, vec![]).unwrap();
    assert!(frontend.raw_images.is_empty());
}

#[test]
fn vulkan_indexed_map_images_preserve_red_blue_channels_through_gal_upload_and_draw() {
    let bytes = vulkan_gui_sample_with_source_format(
        GUI_OPAQUE_BLIT_STRATUM, vec![18,50], 0.0,1.0,None,2,false,false,None,false,None,
        crate::render::guirender::items::material::GuiAffineMaterial::Unlit,
        GuiRawImageSourceFormat::MapColor8,
    ).expect("Vulkan required for indexed map channel regression");
    let frozen = include_bytes!("../../../content/map_color/frozen-native-rgba.bin");
    assert_eq!([&frozen[18*4..19*4],&frozen[50*4..51*4]].concat(),bytes);
}
