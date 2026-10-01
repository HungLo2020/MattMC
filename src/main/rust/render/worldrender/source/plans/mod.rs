//! Named-source frame plans for terrain, DH, materials and fullscreen consumers.

mod common;
mod terrain;
mod distant_horizons;
mod fullscreen;
mod submission;

pub(crate) use self::common::*;
pub(crate) use self::terrain::*;
pub(crate) use self::distant_horizons::*;
pub(crate) use self::fullscreen::*;
pub(crate) use self::submission::*;

use super::*;

impl WorldPrimitiveFrontend {

    /// Prepares the currently supported terrain writers as one private source
    /// frame. It accepts ordinary opaque/cutout terrain and optional opaque
    /// Distant Horizons ranges, then stages every scoped fullscreen stage and
    /// a Rust-owned copy to one acquired frame target. It still does not
    /// acquire, present, or select the route.
    ///
    /// The returned plan is intentionally inert until its caller appends it
    /// to one combined submission. On any DH preparation failure, every
    /// normal-terrain reservation and named-color bootstrap is discarded with
    /// the DH staging so no partial source frame can leak into a later route.
    pub(in crate::render::worldrender) fn prepare_named_source_frame_plan(
        &mut self,
        gal: &mut VulkanicGal,
        programs: &LoweredSourceTerrainPrograms,
        world_generation: u64,
        graph_generation: u64,
        frame: &WorldPrimitiveFrame,
        batches: &[MeshBatch],
        shadow_batches: &[MeshBatch],
        extent: Extent3d,
        depth_texture: Handle,
        depth_view: Handle,
        shadow_targets: TerrainSourceShadowPassTargets,
        main_depth: NamedSourceMainDepthInputs,
        final_frame_target: Handle,
        clear_values: ShaderPackColorBootstrapClearValues,
    ) -> GalResult<PreparedNamedSourceFramePlan> {
        // The copied vanilla celestial quads are represented by the source
        // skytextured writer in this route. If that writer is absent, reject
        // the frame instead of silently drawing them through gbuffers_textured.
        if (frame
            .material_quads
            .iter()
            .any(|quad| quad.material_id == WORLD_MATERIAL_ID_CELESTIAL)
            || frame_has_end_sky_quads(frame))
            && !self
                .shader_runtime
                .as_ref()
                .ok_or_else(|| GalError::backend("shader runtime vanished before celestial admission"))?
                .prepared_lowered_pre_terrain_celestial_program()?
                .is_some()
        {
            return Err(GalError::unsupported_feature(
                "selected source celestial quads require a Rust-owned gbuffers_skytextured writer",
            ));
        }
        let final_color_attachment = gal.pass_target_color_attachment(final_frame_target)?;
        let shader_pack_generation = programs.opaque.shader_pack_generation;
        let main_depth_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before named source depth staging")
            })?
            .ensure_candidate_source_main_depth_resources(
                gal,
                TerrainSourceMainDepthInput {
                    shader_pack_generation,
                    world_generation,
                    shader_graph_generation: main_depth.graph_generation,
                    main_depth_view: main_depth.main_depth_view,
                    before_translucency_view: Some(main_depth.before_translucency_view),
                    previous_view: Some(main_depth.previous_view),
                    sampler: main_depth.sampler,
                },
            )?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "named source terrain/DH plan requires Rust-owned main-depth semantic resources",
                )
            })?;
        self.append_candidate_source_frame_resources(
            shader_pack_generation,
            world_generation,
            frame.frame_id,
            std::slice::from_ref(&main_depth_resources),
        )?;
        let source_sky_initializer = if source_sky_initializer_requested(frame) {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::backend("shader runtime vanished before source sky discovery")
            })?;
            runtime
                .prepared_lowered_pre_terrain_sky_program()?
                .is_some()
                || runtime
                    .prepared_lowered_pre_terrain_celestial_program()?
                    .is_some()
        } else {
            false
        };
        self.source_terrain_mesh_frame_memo = Some(std::collections::HashMap::new());
        let terrain = self.prepare_named_source_terrain_frame_plan(
            gal,
            programs,
            world_generation,
            graph_generation,
            frame,
            batches,
            shadow_batches,
            extent,
            depth_texture,
            depth_view,
            shadow_targets,
            clear_values,
            source_sky_initializer,
        );
        self.source_terrain_mesh_frame_memo = None;
        let mut terrain = terrain?;
        terrain.main_depth_history = Some((
            main_depth.targets,
            TerrainDepthHistoryPlan {
                prior_before_translucency_valid: false,
                prior_previous_valid: false,
                extent,
            },
        ));
        terrain.pre_terrain_sky = match self.prepare_pre_terrain_source_sky_consumer(
            gal,
            frame,
            terrain.color_targets(),
            &main_depth_resources,
        ) {
            Ok(sky) => sky.into_iter().collect(),
            Err(error) => {
                PreparedNamedSourceFramePlan {
                    terrain,
                    distant_horizons: None,
                    fullscreen_consumers: Vec::new(),
                    final_output: None,
                }
                .discard(self, gal);
                return Err(error);
            }
        };
        let celestial = match self.prepare_pre_terrain_source_celestial_consumers(
            gal,
            frame,
            terrain.color_targets(),
            &main_depth_resources,
        ) {
            Ok(consumers) => consumers,
            Err(error) => {
                PreparedNamedSourceFramePlan {
                    terrain,
                    distant_horizons: None,
                    fullscreen_consumers: Vec::new(),
                    final_output: None,
                }
                .discard(self, gal);
                return Err(error);
            }
        };
        terrain.pre_terrain_sky.extend(celestial);
        let color_targets = terrain.color_targets().clone();
        let distant_horizons = match self.prepare_named_source_distant_horizons_frame_plan(
            gal,
            frame,
            color_targets,
        ) {
            Ok(distant_horizons) => distant_horizons,
            Err(error) => {
                PreparedNamedSourceFramePlan {
                    terrain,
                    distant_horizons: None,
                    fullscreen_consumers: Vec::new(),
                    final_output: None,
                }
                .discard(self, gal);
                return Err(error);
            }
        };
        // Without DH, a depth-copyable solid hand is drawn before depthtex1 and
        // the deferred chain and merged into main depth (Iris order), so every
        // fullscreen consumer samples ordinary main depth. The separate
        // post-hand view remains only for the legacy late-hand ordering.
        let hands_merged_into_main_depth = terrain
                .hands
                .as_ref()
                .is_some_and(|hands| hands.copies_world_depth);
        let fullscreen_main_depth_resources = if hands_merged_into_main_depth {
            main_depth_resources.clone()
        } else if let Some(hands) = terrain.hands.as_ref() {
            match self.stage_post_hand_main_depth_resources(
                gal,
                &hands.targets,
                graph_generation,
                main_depth.sampler,
                &main_depth_resources,
            ) {
                Ok(resources) => resources,
                Err(error) => {
                    PreparedNamedSourceFramePlan {
                        terrain,
                        distant_horizons,
                        fullscreen_consumers: Vec::new(),
                        final_output: None,
                    }
                    .discard(self, gal);
                    return Err(error);
                }
            }
        } else {
            main_depth_resources.clone()
        };
        // Legacy late-hand ordering (non-copyable hand depth): deferred stages
        // run before that hand writer, so they must not sample its post-hand
        // view (undefined on its first frame and stale afterwards).
        let deferred_main_depth_resources = (terrain.hands.is_some()
            && !hands_merged_into_main_depth)
        .then(|| main_depth_resources.clone());
        let mut frame_resources = vec![main_depth_resources];
        if let Some(distant_horizons) = distant_horizons.as_ref() {
            frame_resources.push(distant_horizons.depth_targets.semantic_resources()?);
        }
        let merge_result = self.merge_candidate_source_frame_resources(
            shader_pack_generation,
            world_generation,
            frame.frame_id,
            terrain.color_targets(),
            distant_horizons.is_some(),
            &frame_resources,
        );
        if let Err(error) = merge_result {
            PreparedNamedSourceFramePlan {
                terrain,
                distant_horizons,
                fullscreen_consumers: Vec::new(),
                final_output: None,
            }
            .discard(self, gal);
            return Err(error);
        }
        let fullscreen_consumers = match self.prepare_complete_named_source_fullscreen_consumers(
            gal,
            frame,
            terrain.color_targets(),
            fullscreen_main_depth_resources,
            deferred_main_depth_resources,
            distant_horizons.as_ref(),
        ) {
            Ok(consumers) => consumers,
            Err(error) => {
                PreparedNamedSourceFramePlan {
                    terrain,
                    distant_horizons,
                    fullscreen_consumers: Vec::new(),
                    final_output: None,
                }
                .discard(self, gal);
                return Err(error);
            }
        };
        let final_output = match self.source_final_output_cache.reserve(
            gal,
            &fullscreen_consumers
                .last()
                .expect("complete fullscreen staging rejects an empty chain")
                .program,
            terrain.color_targets(),
            final_frame_target,
            final_color_attachment,
            main_depth.main_depth_view,
            main_depth.graph_generation,
        ) {
            Ok(plan) => plan,
            Err(error) => {
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                PreparedNamedSourceFramePlan {
                    terrain,
                    distant_horizons,
                    fullscreen_consumers: Vec::new(),
                    final_output: None,
                }
                .discard(self, gal);
                return Err(error);
            }
        };
        self.write_selected_source_sky_receipt(frame, &terrain.pre_terrain_sky);
        Ok(PreparedNamedSourceFramePlan {
            terrain,
            distant_horizons,
            fullscreen_consumers,
            final_output: Some(final_output),
        })
    }
}
