use super::*;
#[test]
fn native_candidate_owner_matches_query_selection_and_rejects_bad_count() {
    use crate::render::scene::background::WORLD_BACKGROUND_SKY_OVERWORLD;
    use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
    use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
    // Exercise the actual producer ABI with a retained query, including a
    // holder override and player-only role. Existing frame-selection tests
    // independently compare this admission policy with full frame planning.
    let shared = std::sync::Arc::new(std::sync::Mutex::new(None));
    let query = EntityShadowQuery::new(shared.clone());
    let pointer = candidates::mattmc_entity_shadow_candidates_create();
    unsafe {
        assert_eq!(
            candidates::mattmc_entity_shadow_candidates_grow(pointer, 64),
            1
        );
        let store = &mut *pointer;
        for i in 0..64 {
            store.slots[i].flags = (i as u32) & 31;
            store.slots[i].bounds = [i as f64, -1., -1., i as f64 + 1., 1., 1.];
            store.slots[i].holder = [-1., -1., -1., 1., 1., 1.];
        }
        let matrix = [
            1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
        ];
        let camera = [0.; 3];
        let call = |count| {
            mattmc_vulkanic_gal_entity_shadow_query_select_candidates(
                &query,
                WORLD_BACKGROUND_SKY_OVERWORLD,
                0.,
                matrix.as_ptr(),
                matrix.as_ptr(),
                160.,
                32,
                camera.as_ptr(),
                count,
                pointer,
            )
        };
        assert_eq!(call(65), -2);
        assert_eq!(call(64), UNDECIDED);
        assert_eq!(call(0), DECIDED);
        for entities in [false, true] {
            for player in [false, true] {
                let source = ShaderPackSource::new("candidate-owner", 1, vec![
                    ShaderSourceFile::new("gbuffers_terrain.fsh", "const float shadowDistance = 80.0;\nconst float entityShadowDistanceMul = 0.5;\n"),
                    ShaderSourceFile::new("shaders.properties", format!("shadow.culling=false\nshadowEntities={entities}\nshadowPlayer={player}\n")),
                ]).unwrap();
                *shared.lock().unwrap() = source
                    .shadow_policy_for_scope(TerrainProgramScope::Default)
                    .unwrap();
                for count in [1, 7, 32, 64] {
                    let mut expected = vec![9; count];
                    let frame = EntityShadowFrame {
                        time_of_day: 0.,
                        projection: matrix,
                        view: matrix,
                        distances: ShadowCasterFrameDistances {
                            render_distance_blocks: 160.,
                            configured_shadow_distance_chunks: 32,
                        },
                    };
                    let expected_status = query.select(
                        WORLD_BACKGROUND_SKY_OVERWORLD,
                        frame,
                        (&(*pointer).slots)[..count]
                            .iter()
                            .map(|slot| slot.inputs(camera)),
                        &mut expected,
                    );
                    // Unresolved custom hooks deliberately defer the entire
                    // query; partially written output must not be consumed.
                    let status = call(count as u32);
                    assert_eq!(
                        status,
                        if expected_status.is_some() {
                            DECIDED
                        } else {
                            UNDECIDED
                        }
                    );
                    if status == DECIDED {
                        assert_eq!(&(&(*pointer).admitted)[..count], expected.as_slice());
                    }
                }
            }
        }
        candidates::mattmc_entity_shadow_candidates_release(pointer);
    }
}

#[test]
fn candidate_entry_point_matches_frozen_world_bounds() {
    use crate::render::scene::background::WORLD_BACKGROUND_SKY_OVERWORLD;
    use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
    use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
    let fixture = include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../DevUtils/tests/rendering/fixtures/frozen_entity_bounds.tsv"
    ));
    let shared = std::sync::Arc::new(std::sync::Mutex::new(None));
    let query = EntityShadowQuery::new(shared.clone());
    let pointer = candidates::mattmc_entity_shadow_candidates_create();
    let matrix = [
        1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
    ];
    let mut count = 0;
    unsafe {
        assert_eq!(
            candidates::mattmc_entity_shadow_candidates_grow(pointer, 1),
            1
        );
        for line in fixture.lines().filter(|line| !line.starts_with('#')) {
            let fields: Vec<_> = line.split('\t').collect();
            let source = ShaderPackSource::new("native-capture-oracle",1,vec![
                ShaderSourceFile::new("gbuffers_terrain.fsh", "const float shadowDistance = 80.0;\nconst float voxelDistance = 16.0;\nconst float shadowDistanceRenderMul = 0.5;\nconst float entityShadowDistanceMul = 1.0;\n"),
                ShaderSourceFile::new("shaders.properties",format!("shadow.culling={}\n",fields[0])),
            ]).unwrap();
            *shared.lock().unwrap() = source
                .shadow_policy_for_scope(TerrainProgramScope::Default)
                .unwrap();
            let camera: [f64; 3] = std::array::from_fn(|axis| fields[axis + 1].parse().unwrap());
            (&mut (*pointer).slots)[0] = candidates::Candidate {
                flags: 1,
                bounds: std::array::from_fn(|axis| fields[axis + 4].parse().unwrap()),
                ..Default::default()
            };
            let status = mattmc_vulkanic_gal_entity_shadow_query_select_candidates(
                &query,
                WORLD_BACKGROUND_SKY_OVERWORLD,
                0.,
                matrix.as_ptr(),
                matrix.as_ptr(),
                160.,
                32,
                camera.as_ptr(),
                1,
                pointer,
            );
            assert_eq!(status, DECIDED, "{line}");
            assert_eq!(
                (&(*pointer).admitted)[0] != 0,
                fields[10].parse::<bool>().unwrap(),
                "{line}"
            );
            count += 1;
        }
        candidates::mattmc_entity_shadow_candidates_release(pointer);
    }
    assert_eq!(count, 108);
}
