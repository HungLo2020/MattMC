//! Whole-frame counters include preparation and recording retirement.

use super::*;

#[test]
fn whole_frame_resource_profile_includes_gui_preparation_and_retirement() {
    let mut gal = gal();
    let mut world = WorldPrimitiveFrontend::default();
    let mut gui = GuiFrontend::default();
    let target = frame_target(&mut gal, 1, 128, 128);
    world
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            vec![mesh_asset(81, 1, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    let sprite = GuiSpriteRequest {
        stratum: 200,
        sprite_id: 1,
        selected_slot: -1,
        progress_fraction: 1.0,
        fill_direction: 0,
        color_argb: 0xffff_ffff,
        x: 8,
        y: 8,
        width: 15,
        height: 15,
        gui_width: 128,
        gui_height: 128,
        projection_extent: [128.0, 128.0],
        sequence: 1,
    };
    // Cold GUI preparation, reuse, then an asset change all run through
    // the real combined transaction. No resources are created inside submit.
    for (frame_id, generation) in [(1, 1), (2, 1), (3, 2)] {
        if frame_id == 3 {
            world
                .apply_world_mesh_asset_update(
                    &mut gal,
                    generation,
                    vec![mesh_asset(81, generation, IndexType::U16)],
                    Vec::new(),
                )
                .unwrap();
        }
        let creates = gal.metrics().resource_creates;
        let destroys = gal.metrics().resource_destroys;
        let mut scene = frame(Vec::new());
        scene.frame_id = frame_id;
        scene.mesh_instances.push(mesh_instance(81, generation));
        let (stats, gui_stats) = world
            .submit_whole_frame_with_gui_frontend(
                &mut gal,
                generation,
                target,
                scene,
                &mut gui,
                vec![sprite.clone()],
                Vec::new(),
                Vec::new(),
                Vec::new(),
                -1,
                0,
            )
            .unwrap();
        assert_eq!(gui_stats.sprite_count, 1);
        let created = gal.metrics().resource_creates - creates;
        let destroyed = gal.metrics().resource_destroys - destroys;
        if frame_id == 1 {
            assert!(created > 0, "cold preparation creates world/GUI resources");
        }
        if frame_id == 3 {
            assert!(
                destroyed > 0,
                "asset replacement retires old world resources"
            );
        }
        assert_eq!(
            stats.profile.gal.resource_creates_delta, created,
            "frame {frame_id}: creation profile must include frontend preparation"
        );
        assert_eq!(
            stats.profile.gal.resource_destroys_delta, destroyed,
            "frame {frame_id}: destruction profile must include recording retirement"
        );
        gal.retire_through(gal.latest_submission_id()).unwrap();
    }
    gui.reset(&mut gal).unwrap();
    world.reset(&mut gal);
    gal.destroy(target).unwrap();
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}
