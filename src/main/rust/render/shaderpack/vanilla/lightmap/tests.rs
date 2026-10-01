use crate::render::shaderpack::vanilla::lightmap::*;
use crate::render::vulkanic::test_support::{presentation_capabilities, vulkan_capabilities};
use crate::render::vulkanic::commands::{CommandList, CommandListDesc, SubmissionBatch};

fn inputs() -> VanillaLightmapInputs {
    VanillaLightmapInputs {
        ambient_light_factor: 0.0,
        sky_factor: 1.0,
        block_factor: 1.5,
        night_vision_factor: 0.0,
        darkness_scale: 0.0,
        darken_world_factor: 0.0,
        brightness_factor: 0.0,
        sky_light_color: [1.0, 1.0, 1.0],
        ambient_color: [1.0, 1.0, 1.0],
    }
}

#[test]
fn reconstructs_vanilla_lightmap_with_block_on_x_and_sky_on_y() {
    let pixels = inputs().rgba8().unwrap();
    let dark = &pixels[0..4];
    let block_lit = &pixels[(VANILLA_LIGHTMAP_WIDTH - 1) * 4..VANILLA_LIGHTMAP_WIDTH * 4];
    let sky_lit_offset = ((VANILLA_LIGHTMAP_HEIGHT - 1) * VANILLA_LIGHTMAP_WIDTH) * 4;
    let sky_lit = &pixels[sky_lit_offset..sky_lit_offset + 4];
    assert_eq!(u8::MAX, dark[3]);
    assert!(block_lit[0] > dark[0]);
    assert!(sky_lit[2] > dark[2]);
    assert_eq!(u8::MAX, sky_lit[3]);
}

#[test]
fn rgba8_quantization_matches_the_frozen_unorm_framebuffer_contract() {
    // The Java OpenGL reference stores the lightmap in an RGBA8 render
    // target. Keep the Rust-owned semantic reconstruction on the same
    // nearest-UNORM conversion rather than systematically darkening the
    // CPU image by truncating every fractional code point.
    assert_eq!(85, unorm8(84.99 / 255.0));
    assert_eq!(85, unorm8(85.0 / 255.0));
    assert_eq!(255, unorm8(1.0));
}

#[test]
fn default_daylight_texels_match_the_frozen_opengl_capture_contract() {
    // These values are sampled from Frozen Java OpenGL's captured 16×16
    // RGBA8 lightmap for the same explicit default inputs below. This is
    // a semantic CPU-image regression test: it neither reads a Java GPU
    // texture nor permits a Java renderer fallback.
    let pixels = VanillaLightmapInputs {
        brightness_factor: 0.5,
        ..inputs()
    }
    .rgba8()
    .unwrap();
    let texel = |block: usize, sky: usize| {
        let offset = (sky * VANILLA_LIGHTMAP_WIDTH + block) * 4;
        [
            pixels[offset],
            pixels[offset + 1],
            pixels[offset + 2],
            pixels[offset + 3],
        ]
    };
    assert_eq!([25, 25, 25, 255], texel(0, 0));
    assert_eq!([39, 34, 31, 255], texel(1, 0));
    assert_eq!([252, 252, 252, 255], texel(15, 0));
    assert_eq!([204, 204, 204, 255], texel(0, 13));
    assert_eq!([252, 252, 252, 255], texel(15, 15));
}

#[test]
fn full_light_preserves_the_source_final_mix_and_invalid_inputs_reject() {
    let full = VanillaLightmapInputs {
        ambient_light_factor: 1.0,
        sky_factor: 1.0,
        block_factor: 1.5,
        night_vision_factor: 1.0,
        darkness_scale: 0.0,
        darken_world_factor: 0.0,
        brightness_factor: 1.0,
        sky_light_color: [1.0, 1.0, 1.0],
        ambient_color: [1.0, 1.0, 1.0],
    };
    assert_eq!([0.99; 3], full.texel(15, 15).unwrap());
    assert!(full.texel(16, 0).is_err());
    assert!(VanillaLightmapInputs {
        sky_factor: f32::NAN,
        ..full
    }
    .rgba8()
    .is_err());
}

#[test]
fn darkness_and_gamma_follow_the_source_order() {
    let baseline = inputs().texel(4, 8).unwrap();
    let darkened = VanillaLightmapInputs {
        darkness_scale: 0.25,
        darken_world_factor: 0.5,
        brightness_factor: 0.75,
        ..inputs()
    }
    .texel(4, 8)
    .unwrap();
    assert!(darkened[0] < baseline[0]);
    assert!(darkened[1] < baseline[1]);
}

#[test]
fn cache_is_world_and_generation_coherent_without_retaining_caller_state() {
    let frame = VanillaLightmapFrame {
        generation: 7,
        inputs: inputs(),
    };
    let mut cache = VanillaLightmapCache::default();
    assert_eq!(
        VanillaLightmapCacheUpdate::Replaced,
        cache.update(3, frame).unwrap()
    );
    let expected = cache.rgba8().to_vec();
    assert_eq!(
        VanillaLightmapCacheUpdate::Unchanged,
        cache.update(3, frame).unwrap()
    );
    assert_eq!(expected, cache.rgba8());

    let changed_same_generation = VanillaLightmapFrame {
        inputs: VanillaLightmapInputs {
            sky_factor: 0.25,
            ..inputs()
        },
        ..frame
    };
    assert!(cache.update(3, changed_same_generation).is_err());
    assert_eq!(expected, cache.rgba8());

    let stale = VanillaLightmapFrame {
        generation: 6,
        inputs: inputs(),
    };
    assert!(cache.update(3, stale).is_err());
    assert_eq!(expected, cache.rgba8());

    assert_eq!(
        VanillaLightmapCacheUpdate::Replaced,
        cache.update(4, stale).unwrap(),
        "a new world generation owns an independent dynamic-lightmap timeline"
    );
    cache.clear();
    assert_eq!(0, cache.world_generation());
    assert_eq!(0, cache.lightmap_generation());
    assert!(cache.rgba8().is_empty());
}

#[test]
fn residency_stages_a_complete_owned_upload_and_semantic_lightmap_binding() {
    let mut cache = VanillaLightmapCache::default();
    cache
        .update(
            3,
            VanillaLightmapFrame {
                generation: 7,
                inputs: inputs(),
            },
        )
        .unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let residency = VanillaLightmapResidency::create(&mut gal, &cache).unwrap();
    assert!(residency.is_compatible_with(&cache));
    let set = residency.semantic_resource_set(11).unwrap();
    assert_eq!(1, set.len());
    assert!(set
        .availability()
        .resource_for(TerrainSourceResourceRole::Lightmap)
        .is_some());

    let mut ops = Vec::new();
    residency.append_upload(&cache, &mut ops).unwrap();
    assert_eq!(5, ops.len());
    gal.submit(SubmissionBatch {
        label: "test.vanilla-lightmap.upload".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "test.vanilla-lightmap.upload.commands".to_string(),
            operations: ops,
        })],
    })
    .unwrap();
    residency.destroy(&mut gal).unwrap();
}
