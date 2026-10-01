use crate::render::worldrender::features::particle::*;
fn particle() -> ParticleQuad {
    ParticleQuad {
        center: [1.25, -0.7, 2.5],
        rotation: [0.2, -0.3, 0.4, 0.5],
        size: 0.3,
        uv_bounds: [0.8, 0.2, 0.1, 0.9],
        color_argb: 0x7fabcdef,
        packed_light: 0x00f00080,
        texture_id: 0x50415254,
        translucent: true,
    }
}
#[test]
fn frozen_joml_1_10_5_general_rotation_reference() {
    // Captured with the dependency used by Frozen, evaluating its exact
    // renderVertex expression (rotate, mul, add), not this Rust algorithm.
    let reference = [
        [1.5611111, -0.6222222, 2.7777777],
        [0.98333335, -0.46666664, 2.7333333],
        [0.9388889, -0.7777778, 2.2222223],
        [1.5166667, -0.93333334, 2.2666667],
    ];
    let quad = particle().lower([1280, 720]).unwrap();
    for (actual, expected) in quad
        .vertices
        .iter()
        .flatten()
        .zip(reference.iter().flatten())
    {
        assert!(
            (actual - expected).abs() <= 0.00000024,
            "{actual} != {expected}"
        );
    }
    assert_eq!(quad.uvs, [[0.2, 0.9], [0.2, 0.1], [0.8, 0.1], [0.8, 0.9]]);
    assert_eq!(quad.depth_policy, WORLD_DEPTH_POLICY_TEST_WRITE);
    assert_eq!(quad.cull_policy, WORLD_CULL_BACK);
    assert_eq!(quad.source_program, WORLD_MATERIAL_SOURCE_PARTICLES);
    assert_eq!(quad.vertex_color_argb, [0x7fabcdef; 4]);
    assert_eq!(quad.vertex_packed_light, [0x00f00080; 4]);
}
#[test]
fn signed_size_zero_size_and_opaque_policy() {
    let mut p = particle();
    p.center = [0.0; 3];
    p.rotation = [0.0, 0.0, 0.0, 1.0];
    p.size = -2.0;
    p.translucent = false;
    let q = p.lower([1280, 720]).unwrap();
    assert_eq!(
        q.vertices,
        [
            [-2.0, 2.0, 0.0],
            [-2.0, -2.0, 0.0],
            [2.0, -2.0, 0.0],
            [2.0, 2.0, 0.0]
        ]
    );
    assert_eq!(q.material_mode, WORLD_MATERIAL_MODE_OPAQUE);
    p.size = 0.0;
    assert_eq!(p.lower([1280, 720]).unwrap().vertices, [[0.0; 3]; 4]);
}
#[test]
fn invalid_semantics_fail_before_material_admission() {
    let mut invalid = Vec::new();
    let mut p = particle();
    p.rotation = [0.0; 4];
    invalid.push(p);
    let mut p = particle();
    p.rotation[0] = f32::MAX;
    invalid.push(p);
    let mut p = particle();
    p.center[0] = f32::NAN;
    invalid.push(p);
    let mut p = particle();
    p.size = f32::INFINITY;
    invalid.push(p);
    let mut p = particle();
    p.uv_bounds[0] = p.uv_bounds[1];
    invalid.push(p);
    let mut p = particle();
    p.uv_bounds[0] = 5000.0;
    invalid.push(p);
    let mut p = particle();
    p.texture_id = 0;
    invalid.push(p);
    for p in invalid {
        assert!(p.lower([1280, 720]).is_err());
    }
    for viewport in [[0, 720], [1280, 0], [16385, 720]] {
        assert!(particle().lower(viewport).is_err());
    }
}

#[test]
fn terrain_fragment_keeps_block_atlas_uv_tint_light_and_surface_class() {
    let mut p = particle();
    p.translucent = false;
    // TerrainParticle.getU0/getU1 reverses U for its quarter-sprite fragment.
    p.uv_bounds = [0.5, 0.25, 0.25, 0.5];
    let ordinary = p.lower([1280, 720]).unwrap();
    for alpha_tested in [false, true] {
        let q = TerrainParticleQuad {
            quad: p,
            alpha_tested,
        }
        .lower([1280, 720])
        .unwrap();
        assert_eq!(q.vertices, ordinary.vertices);
        assert_eq!(q.uvs, [[0.25, 0.5], [0.25, 0.25], [0.5, 0.25], [0.5, 0.5]]);
        assert_eq!(
            q.source_uv_space,
            WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
        );
        assert_eq!(q.source_program, WORLD_MATERIAL_SOURCE_PARTICLES);
        assert_eq!(q.color_argb, p.color_argb);
        assert_eq!(q.vertex_color_argb, [p.color_argb; 4]);
        assert_eq!(q.vertex_packed_light, [p.packed_light; 4]);
        assert_eq!(q.depth_policy, WORLD_DEPTH_POLICY_TEST_WRITE);
        assert_eq!(q.cull_policy, WORLD_CULL_BACK);
        assert_eq!(
            q.material_mode,
            if alpha_tested {
                WORLD_MATERIAL_MODE_CUTOUT
            } else {
                WORLD_MATERIAL_MODE_OPAQUE
            }
        );
        assert_eq!(
            q.material_id,
            if alpha_tested {
                WORLD_MATERIAL_ID_CUTOUT_TEXTURED
            } else {
                WORLD_MATERIAL_ID_OPAQUE_TEXTURED
            }
        );
    }
}

#[test]
fn translucent_terrain_keeps_blending_block_uvs_tint_light_and_depth_write() {
    let mut p = particle();
    p.uv_bounds = [0.5, 0.25, 0.25, 0.5];
    p.color_argb = 0x80604020;
    let q = p
        .lower_surface(ParticleSurface::TerrainTranslucent, [1280, 720])
        .unwrap();
    assert_eq!(q.material_mode, WORLD_MATERIAL_MODE_TRANSLUCENT);
    assert_eq!(q.material_id, WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED);
    assert_eq!(
        q.source_uv_space,
        WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
    );
    assert_eq!(q.source_program, WORLD_MATERIAL_SOURCE_PARTICLES);
    assert_eq!(q.vertex_color_argb, [0x80604020; 4]);
    assert_eq!(q.vertex_packed_light, [p.packed_light; 4]);
    assert_eq!(q.depth_policy, WORLD_DEPTH_POLICY_TEST_WRITE);
    assert_eq!(q.cull_policy, WORLD_CULL_BACK);
    assert_eq!(q.uvs, [[0.25, 0.5], [0.25, 0.25], [0.5, 0.25], [0.5, 0.5]]);
    assert!(p
        .lower_surface(ParticleSurface::TerrainOpaque, [1280, 720])
        .is_err());
    p.translucent = false;
    assert!(p
        .lower_surface(ParticleSurface::TerrainTranslucent, [1280, 720])
        .is_err());
}

#[test]
fn terrain_fragment_rejects_incompatible_or_malformed_semantics() {
    assert!(TerrainParticleQuad {
        quad: particle(),
        alpha_tested: true
    }
    .lower([1280, 720])
    .is_err());
    let mut p = particle();
    p.translucent = false;
    p.rotation = [0.0; 4];
    assert!(TerrainParticleQuad {
        quad: p,
        alpha_tested: false
    }
    .lower([1280, 720])
    .is_err());
}
